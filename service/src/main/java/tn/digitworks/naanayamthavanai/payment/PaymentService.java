package tn.digitworks.naanayamthavanai.payment;

import tn.digitworks.naanayamthavanai.common.AppConfig;
import tn.digitworks.naanayamthavanai.common.ApiException;
import tn.digitworks.naanayamthavanai.common.ErrorResponse;
import tn.digitworks.naanayamthavanai.loan.Installment;
import tn.digitworks.naanayamthavanai.loan.InstallmentRepository;
import tn.digitworks.naanayamthavanai.loan.Loan;
import tn.digitworks.naanayamthavanai.loan.LoanBalances;
import tn.digitworks.naanayamthavanai.loan.LoanRepository;
import tn.digitworks.naanayamthavanai.loan.LoanService;
import tn.digitworks.naanayamthavanai.payment.PaymentDtos.AllocationResponse;
import tn.digitworks.naanayamthavanai.payment.PaymentDtos.LedgerEntry;
import tn.digitworks.naanayamthavanai.payment.PaymentDtos.PaymentCreate;
import tn.digitworks.naanayamthavanai.payment.PaymentDtos.PaymentResponse;
import tn.digitworks.naanayamthavanai.payment.PaymentDtos.Recorded;
import tn.digitworks.naanayamthavanai.payment.PaymentDtos.SyncResult;
import tn.digitworks.naanayamthavanai.payment.PaymentDtos.SyncStatus;
import tn.digitworks.naanayamthavanai.payment.allocation.AllocationResult;
import tn.digitworks.naanayamthavanai.payment.allocation.AllocationResult.Allocation;
import tn.digitworks.naanayamthavanai.payment.allocation.InstallmentBalance;
import tn.digitworks.naanayamthavanai.payment.allocation.PaymentAllocator;
import tn.digitworks.naanayamthavanai.payment.allocation.PaymentException;
import tn.digitworks.naanayamthavanai.security.CurrentUser;
import tn.digitworks.naanayamthavanai.tenant.SettingsService;
import tn.digitworks.naanayamthavanai.user.AppUser;
import tn.digitworks.naanayamthavanai.user.AppUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(10);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final PaymentRepository payments;
    private final PaymentAllocationRepository allocations;
    private final InstallmentRepository installments;
    private final LoanRepository loans;
    private final LoanService loanService;
    private final SettingsService settingsService;
    private final AppUserRepository users;
    private final UpiRefCheck upiRefCheck;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final PaymentAllocator allocator = new PaymentAllocator();

    public PaymentService(PaymentRepository payments, PaymentAllocationRepository allocations,
                          InstallmentRepository installments, LoanRepository loans, LoanService loanService,
                          SettingsService settingsService, AppUserRepository users, UpiRefCheck upiRefCheck,
                          PlatformTransactionManager txManager, Clock clock) {
        this.payments = payments;
        this.allocations = allocations;
        this.installments = installments;
        this.loans = loans;
        this.loanService = loanService;
        this.settingsService = settingsService;
        this.users = users;
        this.upiRefCheck = upiRefCheck;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    // ------------------------------------------------------------------ record

    @Transactional
    public Recorded record(PaymentCreate in) {
        return doRecord(in);
    }

    /** Each payment in its own transaction, so one failure does not undo the others. */
    public List<SyncResult> sync(List<PaymentCreate> batch) {
        List<SyncResult> results = new ArrayList<>(batch.size());
        for (PaymentCreate in : batch) {
            try {
                Recorded r = tx.execute(status -> doRecord(in));
                results.add(new SyncResult(in.clientTxnId(),
                        r.created() ? SyncStatus.CREATED : SyncStatus.DUPLICATE, r.payment().id(), null));
            } catch (ApiException e) {
                results.add(failed(in, e.getCode(), e.getMessage()));
            } catch (PaymentException e) {
                results.add(failed(in, e.getCode(), e.getMessage()));
            } catch (DataIntegrityViolationException e) {
                results.add(failed(in, "DUPLICATE", "Payment was recorded concurrently; sync again"));
            }
        }
        return results;
    }

    private Recorded doRecord(PaymentCreate in) {
        CurrentUser me = CurrentUser.get();
        long tenantId = me.requireTenant();

        Optional<Payment> existing = payments.findByTenantIdAndClientTxnId(tenantId, in.clientTxnId());
        if (existing.isPresent()) {
            Payment p = existing.get();
            if (!p.getLoanId().equals(in.loanId()) || p.getAmount().compareTo(in.amount()) != 0) {
                throw ApiException.conflict("CLIENT_TXN_REUSED",
                        "clientTxnId was already used for a different payment");
            }
            return new Recorded(toResponse(p), false);
        }

        LocalDateTime paidOn = toBusinessTime(in.paidOn());
        String upiRef = upiRefCheck.resolve(tenantId, in.mode(), in.upiRefNo());

        Loan loan = loanService.lockAccessible(tenantId, in.loanId());
        if (loan.getStatus() != Loan.Status.ACTIVE) {
            throw ApiException.rule("LOAN_NOT_ACTIVE", "Payments can only be recorded on active loans");
        }

        List<Installment> rows = installments.lockByLoanId(loan.getId());
        AllocationResult result = allocator.allocate(balances(rows), in.amount(), paidOn.toLocalDate(),
                settingsService.forTenant(tenantId).allocationPolicy());

        Payment payment = payments.save(new Payment(tenantId, loan.getId(), in.amount(), in.mode(),
                upiRef, paidOn, me.userId(), in.clientTxnId(), blankToNull(in.remarks())));
        applyAndSaveAllocations(tenantId, payment, rows, result);

        if (result.loanFullyPaid()) {
            loan.close(Loan.Status.CLOSED, LocalDateTime.now(clock));
        }
        return new Recorded(toResponse(payment), true);
    }

    // ------------------------------------------------------------------ reverse

    @Transactional
    public PaymentResponse reverse(long paymentId, String reason) {
        CurrentUser me = CurrentUser.get();
        long tenantId = me.requireTenant();
        Payment original = payments.findByIdAndTenantId(paymentId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Payment"));
        if (original.getEntryType() != Payment.EntryType.PAYMENT) {
            throw ApiException.rule("CANNOT_REVERSE_REVERSAL", "A reversal entry cannot be reversed");
        }
        if (original.getPurpose() == Payment.Purpose.PRECLOSURE) {
            throw ApiException.rule("CANNOT_REVERSE_PRECLOSURE", "Pre-closure payments cannot be reversed");
        }
        Loan loan = loans.lockByIdAndTenantId(original.getLoanId(), tenantId).orElseThrow();
        if (payments.existsByReversalOf(paymentId)) {
            throw ApiException.conflict("ALREADY_REVERSED", "This payment has already been reversed");
        }
        if (loan.getStatus() == Loan.Status.PRECLOSED) {
            throw ApiException.rule("LOAN_PRECLOSED", "Payments on a pre-closed loan cannot be reversed");
        }

        List<Installment> rows = installments.lockByLoanId(loan.getId());
        List<PaymentAllocation> originalAllocs = allocations.findByPaymentIdOrderById(paymentId);
        List<Allocation> toUndo = originalAllocs.stream()
                .map(a -> new Allocation(a.getInstallmentId(), a.getAmount(), a.getPenaltyPart())).toList();
        AllocationResult result = allocator.reverse(balances(rows), toUndo);

        String name = users.findById(me.userId()).map(AppUser::getName).orElse("owner");
        Payment reversal = payments.save(Payment.reversalOf(original, UUID.randomUUID().toString(),
                LocalDateTime.now(clock), truncate("Reversed by " + name + ": " + reason.trim(), 255)));

        Map<Long, PaymentAllocation> originalByInstallment = originalAllocs.stream()
                .collect(Collectors.toMap(PaymentAllocation::getInstallmentId, Function.identity(), (a, b) -> a));
        applyBalances(rows, result.updated());
        for (Allocation a : result.allocations()) {
            PaymentAllocation o = originalByInstallment.get(a.installmentId());
            allocations.save(new PaymentAllocation(tenantId, reversal.getId(), a.installmentId(), a.amount(),
                    o == null ? ZERO : o.getInterestPart().negate(), a.penaltyPart()));
        }

        if (loan.getStatus() == Loan.Status.CLOSED) {
            loan.reopen();
        }
        return toResponse(reversal);
    }

    // ------------------------------------------------------------------ ledger

    @Transactional(readOnly = true)
    public List<LedgerEntry> ledger(long loanId) {
        long tenantId = CurrentUser.get().requireTenant();
        Loan loan = loanService.requireAccessible(tenantId, loanId);
        List<Payment> list = payments.findByLoanIdOrderByPaidOnAscIdAsc(loanId);
        Map<Long, List<PaymentAllocation>> allocsByPayment = allocationsByPayment(list);
        Map<Long, Installment> instById = installments.findByLoanIdOrderBySeqNo(loanId).stream()
                .collect(Collectors.toMap(Installment::getId, Function.identity()));
        Map<Long, String> names = userNames(list.stream().map(Payment::getCollectedBy).collect(Collectors.toSet()));

        BigDecimal running = loan.getTotalPayable();
        List<LedgerEntry> out = new ArrayList<>(list.size());
        for (Payment p : list) {
            List<PaymentAllocation> pa = allocsByPayment.getOrDefault(p.getId(), List.of());
            BigDecimal installmentPart = pa.stream().map(PaymentAllocation::getAmount).reduce(ZERO, BigDecimal::add);
            running = p.getPurpose() == Payment.Purpose.PRECLOSURE && p.getEntryType() == Payment.EntryType.PAYMENT
                    ? ZERO : running.subtract(installmentPart);
            out.add(new LedgerEntry(p.getId(), p.getClientTxnId(), p.getLoanId(), p.getAmount(), p.getEntryType(),
                    p.getPurpose(), p.getReversalOf(), p.getMode(), p.getUpiRefNo(), offset(p.getPaidOn()),
                    p.getCollectedBy(), names.get(p.getCollectedBy()), allocationResponses(pa, instById),
                    running, running));
        }
        return out;
    }

    // ------------------------------------------------------------------ shared

    /** Applies allocation results to the installment entities and saves payment_allocation rows. */
    private void applyAndSaveAllocations(long tenantId, Payment payment, List<Installment> rows,
                                         AllocationResult result) {
        Map<Long, Installment> byId = rows.stream().collect(Collectors.toMap(Installment::getId, Function.identity()));
        applyBalances(rows, result.updated());
        for (Allocation a : result.allocations()) {
            Installment inst = byId.get(a.installmentId());
            allocations.save(new PaymentAllocation(tenantId, payment.getId(), a.installmentId(), a.amount(),
                    inst.interestShareOf(a.amount()), a.penaltyPart()));
        }
    }

    public PaymentResponse toResponse(Payment p) {
        List<PaymentAllocation> pa = allocations.findByPaymentIdOrderById(p.getId());
        List<Installment> rows = installments.findByLoanIdOrderBySeqNo(p.getLoanId());
        Map<Long, Installment> instById = rows.stream().collect(Collectors.toMap(Installment::getId, Function.identity()));
        BigDecimal balance = LoanBalances.of(rows, LocalDate.now(clock)).outstanding();
        return new PaymentResponse(p.getId(), p.getClientTxnId(), p.getLoanId(), p.getAmount(), p.getEntryType(),
                p.getPurpose(), p.getReversalOf(), p.getMode(), p.getUpiRefNo(), offset(p.getPaidOn()),
                p.getCollectedBy(), userNames(Set.of(p.getCollectedBy())).get(p.getCollectedBy()),
                allocationResponses(pa, instById), balance);
    }

    private static List<InstallmentBalance> balances(List<Installment> rows) {
        return rows.stream().map(Installment::toBalance).toList();
    }

    private static void applyBalances(List<Installment> rows, List<InstallmentBalance> updated) {
        Map<Long, InstallmentBalance> byId = updated.stream()
                .collect(Collectors.toMap(InstallmentBalance::id, Function.identity()));
        rows.forEach(i -> {
            InstallmentBalance b = byId.get(i.getId());
            if (b != null) {
                i.apply(b);
            }
        });
    }

    private LocalDateTime toBusinessTime(OffsetDateTime paidOn) {
        LocalDateTime local = paidOn.atZoneSameInstant(AppConfig.BUSINESS_ZONE).toLocalDateTime();
        if (local.isAfter(LocalDateTime.now(clock).plus(MAX_CLOCK_SKEW))) {
            throw ApiException.rule("PAID_ON_IN_FUTURE", "Payment time is in the future; check the phone's clock");
        }
        return local;
    }

    private static OffsetDateTime offset(LocalDateTime t) {
        return t.atZone(AppConfig.BUSINESS_ZONE).toOffsetDateTime();
    }

    private Map<Long, List<PaymentAllocation>> allocationsByPayment(List<Payment> list) {
        if (list.isEmpty()) {
            return Map.of();
        }
        return allocations.findByPaymentIdIn(list.stream().map(Payment::getId).toList()).stream()
                .collect(Collectors.groupingBy(PaymentAllocation::getPaymentId));
    }

    private static List<AllocationResponse> allocationResponses(List<PaymentAllocation> pa, Map<Long, Installment> instById) {
        return pa.stream().map(a -> {
            Installment i = instById.get(a.getInstallmentId());
            return new AllocationResponse(a.getInstallmentId(), i == null ? 0 : i.getSeqNo(),
                    i == null ? null : i.getDueDate(), a.getAmount(), a.getPenaltyPart());
        }).toList();
    }

    private Map<Long, String> userNames(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findByIdIn(new HashSet<>(ids)).stream().collect(Collectors.toMap(AppUser::getId, AppUser::getName));
    }

    private static SyncResult failed(PaymentCreate in, String code, String message) {
        return new SyncResult(in.clientTxnId(), SyncStatus.FAILED, null, ErrorResponse.of(code, message));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
