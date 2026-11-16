package tn.digitworks.naanayamthavanai.preclosure;

import tn.digitworks.naanayamthavanai.common.AppConfig;
import tn.digitworks.naanayamthavanai.common.ApiException;
import tn.digitworks.naanayamthavanai.loan.Installment;
import tn.digitworks.naanayamthavanai.loan.InstallmentRepository;
import tn.digitworks.naanayamthavanai.loan.Loan;
import tn.digitworks.naanayamthavanai.loan.LoanService;
import tn.digitworks.naanayamthavanai.payment.Payment;
import tn.digitworks.naanayamthavanai.payment.PaymentAllocation;
import tn.digitworks.naanayamthavanai.payment.PaymentAllocationRepository;
import tn.digitworks.naanayamthavanai.payment.PaymentRepository;
import tn.digitworks.naanayamthavanai.payment.PaymentService;
import tn.digitworks.naanayamthavanai.payment.UpiRefCheck;
import tn.digitworks.naanayamthavanai.preclosure.PreclosureCalculator.Quote;
import tn.digitworks.naanayamthavanai.preclosure.PreclosureCalculator.Settlement;
import tn.digitworks.naanayamthavanai.preclosure.PreclosureDtos.PreclosureConfirm;
import tn.digitworks.naanayamthavanai.preclosure.PreclosureDtos.PreclosureResult;
import tn.digitworks.naanayamthavanai.preclosure.PreclosureDtos.QuoteResponse;
import tn.digitworks.naanayamthavanai.security.CurrentUser;
import tn.digitworks.naanayamthavanai.tenant.SettingsService;
import tn.digitworks.naanayamthavanai.tenant.TenantSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PreclosureService {

    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(10);

    private final LoanService loanService;
    private final InstallmentRepository installments;
    private final PaymentRepository payments;
    private final PaymentAllocationRepository allocations;
    private final PaymentService paymentService;
    private final LoanPreclosureRepository preclosures;
    private final SettingsService settingsService;
    private final UpiRefCheck upiRefCheck;
    private final Clock clock;
    private final PreclosureCalculator calculator = new PreclosureCalculator();

    public PreclosureService(LoanService loanService, InstallmentRepository installments, PaymentRepository payments,
                             PaymentAllocationRepository allocations, PaymentService paymentService,
                             LoanPreclosureRepository preclosures, SettingsService settingsService,
                             UpiRefCheck upiRefCheck, Clock clock) {
        this.upiRefCheck = upiRefCheck;
        this.loanService = loanService;
        this.installments = installments;
        this.payments = payments;
        this.allocations = allocations;
        this.paymentService = paymentService;
        this.preclosures = preclosures;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public QuoteResponse quote(long loanId, LocalDate asOf) {
        long tenantId = CurrentUser.get().requireTenant();
        Loan loan = loanService.requireAccessible(tenantId, loanId);
        TenantSettings settings = checkAllowed(tenantId, loan);
        LocalDate date = asOf == null ? LocalDate.now(clock) : asOf;
        return QuoteResponse.of(loanId, calculate(installments.findByLoanIdOrderBySeqNo(loanId), date, settings));
    }

    @Transactional
    public PreclosureResult confirm(long loanId, PreclosureConfirm in) {
        CurrentUser me = CurrentUser.get();
        long tenantId = me.requireTenant();

        Optional<Payment> existing = payments.findByTenantIdAndClientTxnId(tenantId, in.clientTxnId());
        if (existing.isPresent()) {
            Payment p = existing.get();
            if (!p.getLoanId().equals(loanId) || p.getPurpose() != Payment.Purpose.PRECLOSURE) {
                throw ApiException.conflict("CLIENT_TXN_REUSED", "clientTxnId was already used for a different payment");
            }
            return result(loanId, p, null);
        }
        LocalDateTime paidOn = in.paidOn().atZoneSameInstant(AppConfig.BUSINESS_ZONE).toLocalDateTime();
        if (paidOn.isAfter(LocalDateTime.now(clock).plus(MAX_CLOCK_SKEW))) {
            throw ApiException.rule("PAID_ON_IN_FUTURE", "Payment time is in the future; check the phone's clock");
        }
        String upiRef = upiRefCheck.resolve(tenantId, in.mode(), in.upiRefNo());

        Loan loan = loanService.lockAccessible(tenantId, loanId);
        TenantSettings settings = checkAllowed(tenantId, loan);
        List<Installment> rows = installments.lockByLoanId(loanId);
        Quote q = calculate(rows, paidOn.toLocalDate(), settings);
        if (q.settlementAmount().compareTo(in.settlementAmount()) != 0) {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "QUOTE_OUTDATED",
                    "Settlement amount is now " + q.settlementAmount() + "; fetch a new quote",
                    Map.of("settlementAmount", q.settlementAmount().toPlainString()));
        }

        Payment payment = new Payment(tenantId, loanId, q.settlementAmount(), in.mode(), upiRef,
                paidOn, me.userId(), in.clientTxnId(), "Pre-closure");
        payment.setPurpose(Payment.Purpose.PRECLOSURE);
        payment = payments.save(payment);

        Map<Long, Settlement> settlementByInstallment = q.settlements().stream()
                .collect(Collectors.toMap(Settlement::installmentId, Function.identity()));
        for (Settlement s : q.settlements()) {
            allocations.save(new PaymentAllocation(tenantId, payment.getId(), s.installmentId(), s.amount(),
                    s.interestPart(), s.penaltyPart()));
        }
        // Close every installment that still had something collectible
        for (Installment i : rows) {
            Settlement s = settlementByInstallment.get(i.getId());
            if (s != null) {
                i.settle(s.amount(), s.penaltyPart());
            } else if (i.getStatus().isOpen()) {
                i.settle(BigDecimal.ZERO, BigDecimal.ZERO);
            }
        }

        preclosures.save(new LoanPreclosure(tenantId, loanId, payment.getId(), q, me.userId(), LocalDateTime.now(clock)));
        loan.close(Loan.Status.PRECLOSED, LocalDateTime.now(clock));
        return result(loanId, payment, q);
    }

    private PreclosureResult result(long loanId, Payment payment, Quote q) {
        return new PreclosureResult(loanService.get(loanId), paymentService.toResponse(payment),
                q == null ? null : QuoteResponse.of(loanId, q));
    }

    private TenantSettings checkAllowed(long tenantId, Loan loan) {
        TenantSettings settings = settingsService.forTenant(tenantId);
        if (!settings.isPreclosureEnabled()) {
            throw ApiException.rule("PRECLOSURE_DISABLED", "Pre-closure is turned off in settings");
        }
        if (loan.getStatus() != Loan.Status.ACTIVE) {
            throw ApiException.rule("LOAN_NOT_ACTIVE", "Only active loans can be pre-closed");
        }
        return settings;
    }

    private Quote calculate(List<Installment> rows, LocalDate asOf, TenantSettings s) {
        Quote q = calculator.quote(rows.stream().map(Installment::toRow).toList(), asOf,
                s.getPreclosureRebate(), s.getPreclosureChargeType(), s.getPreclosureChargeValue());
        if (q.settlementAmount().signum() <= 0) {
            throw ApiException.rule("NOTHING_OUTSTANDING", "Nothing is outstanding on this loan");
        }
        return q;
    }
}
