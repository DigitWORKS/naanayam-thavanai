package tn.digitworks.naanayamthavanai.loan;

import tn.digitworks.naanayamthavanai.client.Client;
import tn.digitworks.naanayamthavanai.client.ClientRepository;
import tn.digitworks.naanayamthavanai.common.ApiException;
import tn.digitworks.naanayamthavanai.common.PageResponse;
import tn.digitworks.naanayamthavanai.loan.LoanDtos.InstallmentResponse;
import tn.digitworks.naanayamthavanai.loan.LoanDtos.LoanCreate;
import tn.digitworks.naanayamthavanai.loan.LoanDtos.LoanPreview;
import tn.digitworks.naanayamthavanai.loan.LoanDtos.LoanResponse;
import tn.digitworks.naanayamthavanai.loan.LoanDtos.LoanSummary;
import tn.digitworks.naanayamthavanai.loan.schedule.LoanSchedule;
import tn.digitworks.naanayamthavanai.loan.schedule.LoanTerms;
import tn.digitworks.naanayamthavanai.loan.schedule.ScheduleCalculator;
import tn.digitworks.naanayamthavanai.payment.PaymentRepository;
import tn.digitworks.naanayamthavanai.security.AreaScope;
import tn.digitworks.naanayamthavanai.security.CurrentUser;
import tn.digitworks.naanayamthavanai.tenant.SettingsService;
import tn.digitworks.naanayamthavanai.tenant.TenantRepository;
import tn.digitworks.naanayamthavanai.tenant.TenantSettings;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LoanService {

    private static final int MAX_BACKDATE_DAYS = 365;
    private static final int MAX_FUTURE_DAYS = 30;

    private final LoanRepository loans;
    private final InstallmentRepository installments;
    private final ClientRepository clients;
    private final TenantRepository tenants;
    private final PaymentRepository payments;
    private final SettingsService settingsService;
    private final AreaScope areaScope;
    private final Clock clock;
    private final ScheduleCalculator calculator = new ScheduleCalculator();

    public LoanService(LoanRepository loans, InstallmentRepository installments, ClientRepository clients,
                       TenantRepository tenants, PaymentRepository payments, SettingsService settingsService,
                       AreaScope areaScope, Clock clock) {
        this.loans = loans;
        this.installments = installments;
        this.clients = clients;
        this.tenants = tenants;
        this.payments = payments;
        this.settingsService = settingsService;
        this.areaScope = areaScope;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public LoanPreview preview(LoanCreate in) {
        long tenantId = CurrentUser.get().requireTenant();
        validateTerms(in, settingsService.forTenant(tenantId));
        LoanSchedule s = calculator.calculate(terms(in));
        return new LoanPreview(s.interestAmount(), s.disbursedAmount(), s.totalPayable(), s.installmentAmount(),
                s.endDate(), s.installments().stream().map(InstallmentResponse::preview).toList());
    }

    @Transactional(readOnly = true)
    public LoanResponse get(long loanId) {
        long tenantId = CurrentUser.get().requireTenant();
        Loan loan = requireAccessible(tenantId, loanId);
        Client client = clients.findByIdAndTenantId(loan.getClientId(), tenantId).orElseThrow();
        return toResponse(loan, client, installments.findByLoanIdOrderBySeqNo(loanId));
    }

    @Transactional(readOnly = true)
    public PageResponse<LoanSummary> list(Long clientId, Loan.Status status, Long areaId, int page, int size) {
        long tenantId = CurrentUser.get().requireTenant();
        AreaScope.Scope scope = areaScope.current();
        Page<Loan> result = loans.search(tenantId, clientId, status, areaId, scope.all(), scope.areaIds(),
                PageRequest.of(page, size));
        Map<Long, List<Installment>> byLoan = installmentsByLoan(result.getContent());
        LocalDate today = LocalDate.now(clock);
        return PageResponse.of(result, l -> toSummary(l, byLoan.getOrDefault(l.getId(), List.of()), today));
    }

    @Transactional(readOnly = true)
    public List<InstallmentResponse> installments(long loanId) {
        long tenantId = CurrentUser.get().requireTenant();
        requireAccessible(tenantId, loanId);
        return installments.findByLoanIdOrderBySeqNo(loanId).stream().map(InstallmentResponse::of).toList();
    }

    /** Active-loan summaries for a client (used by the client detail screen). */
    @Transactional(readOnly = true)
    public List<LoanSummary> activeSummaries(long tenantId, long clientId) {
        List<Loan> active = loans.findByTenantIdAndClientIdAndStatusOrderByIdDesc(tenantId, clientId, Loan.Status.ACTIVE);
        Map<Long, List<Installment>> byLoan = installmentsByLoan(active);
        LocalDate today = LocalDate.now(clock);
        return active.stream().map(l -> toSummary(l, byLoan.getOrDefault(l.getId(), List.of()), today)).toList();
    }

    // ---------------------------------------------------------------- commands

    @Transactional
    public LoanResponse create(LoanCreate in) {
        CurrentUser me = CurrentUser.get();
        long tenantId = me.requireTenant();
        TenantSettings settings = settingsService.forTenant(tenantId);
        validateTerms(in, settings);

        tenants.lockById(tenantId).orElseThrow();   // serialises loan numbers and the active-loan check
        Client client = clients.findByIdAndTenantId(in.clientId(), tenantId)
                .orElseThrow(() -> ApiException.notFound("Client"));
        areaScope.require(client.getAreaId(), "Client");
        if (!client.isActive()) {
            throw ApiException.rule("CLIENT_INACTIVE", "Client is inactive");
        }
        long active = loans.countByTenantIdAndClientIdAndStatus(tenantId, client.getId(), Loan.Status.ACTIVE);
        if (active >= settings.getMaxActiveLoansPerClient()) {
            throw ApiException.rule("ACTIVE_LOAN_LIMIT",
                    "Client already has " + active + " active loan(s); the limit is " + settings.getMaxActiveLoansPerClient());
        }

        LoanSchedule s = calculator.calculate(terms(in));
        String loanNo = "LN%06d".formatted(loans.countByTenantId(tenantId) + 1);

        Loan loan = new Loan(tenantId, client.getId(), loanNo, me.userId());
        loan.setPrincipal(in.principal());
        loan.setInterestRate(in.interestRate());
        loan.setInterestMethod(in.interestMethod());
        loan.setInterestCollection(in.interestCollection());
        loan.setInterestAmount(s.interestAmount());
        loan.setDisbursedAmount(s.disbursedAmount());
        loan.setTotalPayable(s.totalPayable());
        loan.setFrequency(in.frequency());
        loan.setInstallmentAmount(s.installmentAmount());
        loan.setTenureCount(in.tenureCount());
        loan.setStartDate(in.startDate());
        loan.setOriginalEndDate(s.endDate());
        loan.setCurrentEndDate(s.endDate());
        loan = loans.save(loan);

        Long loanId = loan.getId();
        List<Installment> rows = installments.saveAll(s.installments().stream()
                .map(i -> new Installment(tenantId, loanId, i.seqNo(), i.dueDate(), i.dueAmount(),
                        i.principalComponent(), i.interestComponent(), i.principalBalanceAfter()))
                .toList());
        return toResponse(loan, client, rows);
    }

    @Transactional
    public LoanResponse markDefaulted(long loanId, String legalNote) {
        long tenantId = CurrentUser.get().requireTenant();
        Loan loan = loans.lockByIdAndTenantId(loanId, tenantId).orElseThrow(() -> ApiException.notFound("Loan"));
        if (loan.getStatus() != Loan.Status.ACTIVE) {
            throw ApiException.rule("LOAN_NOT_ACTIVE", "Only active loans can be marked as defaulted");
        }
        loan.setStatus(Loan.Status.DEFAULTED);
        loan.setLegalNote(legalNote.trim());
        Client client = clients.findByIdAndTenantId(loan.getClientId(), tenantId).orElseThrow();
        return toResponse(loan, client, installments.findByLoanIdOrderBySeqNo(loanId));
    }

    // ---------------------------------------------------------------- shared helpers

    /** Loan of the tenant that the caller may see (agents: only their areas). 404 otherwise. */
    public Loan requireAccessible(long tenantId, long loanId) {
        Loan loan = loans.findByIdAndTenantId(loanId, tenantId).orElseThrow(() -> ApiException.notFound("Loan"));
        requireClientArea(tenantId, loan);
        return loan;
    }

    /** Same as {@link #requireAccessible} but locks the loan row for a money-changing operation. */
    public Loan lockAccessible(long tenantId, long loanId) {
        Loan loan = loans.lockByIdAndTenantId(loanId, tenantId).orElseThrow(() -> ApiException.notFound("Loan"));
        requireClientArea(tenantId, loan);
        return loan;
    }

    private void requireClientArea(long tenantId, Loan loan) {
        Client client = clients.findByIdAndTenantId(loan.getClientId(), tenantId)
                .orElseThrow(() -> ApiException.notFound("Loan"));
        areaScope.require(client.getAreaId(), "Loan");
    }

    public LoanSummary toSummary(Loan l, List<Installment> rows, LocalDate today) {
        LoanBalances b = LoanBalances.of(rows, today);
        return new LoanSummary(l.getId(), l.getLoanNo(), l.getPrincipal(), l.getFrequency(), l.getInstallmentAmount(),
                b.outstanding(), b.arrears(), l.getStatus());
    }

    private LoanResponse toResponse(Loan l, Client client, List<Installment> rows) {
        LoanBalances b = LoanBalances.of(rows, LocalDate.now(clock));
        return new LoanResponse(l.getId(), l.getLoanNo(), l.getPrincipal(), l.getFrequency(), l.getInstallmentAmount(),
                b.outstanding(), b.arrears(), l.getStatus(), client.getId(), client.getName(), l.getInterestRate(),
                l.getInterestMethod(), l.getInterestCollection(), l.getInterestAmount(), l.getDisbursedAmount(),
                l.getTotalPayable(), payments.sumByLoanId(l.getId()).setScale(2), b.penaltyDue(), l.getTenureCount(),
                l.getStartDate(), l.getOriginalEndDate(), l.getCurrentEndDate(), l.getLegalNote(), l.getClosedAt());
    }

    private Map<Long, List<Installment>> installmentsByLoan(List<Loan> list) {
        if (list.isEmpty()) {
            return Map.of();
        }
        return installments.findByLoanIdIn(list.stream().map(Loan::getId).toList()).stream()
                .collect(Collectors.groupingBy(Installment::getLoanId, Collectors.toList()));
    }

    private void validateTerms(LoanCreate in, TenantSettings settings) {
        if (in.interestRate().compareTo(settings.getMaxInterestRate()) > 0) {
            throw ApiException.rule("INTEREST_ABOVE_CAP",
                    "Interest rate " + in.interestRate() + "% is above the allowed maximum of "
                            + settings.getMaxInterestRate() + "% per annum");
        }
        if (!settings.getAllowedMethods().contains(in.interestMethod())) {
            throw ApiException.rule("INTEREST_METHOD_NOT_ALLOWED",
                    in.interestMethod() + " interest is not enabled in settings");
        }
        LocalDate today = LocalDate.now(clock);
        if (in.startDate().isBefore(today.minusDays(MAX_BACKDATE_DAYS))
                || in.startDate().isAfter(today.plusDays(MAX_FUTURE_DAYS))) {
            throw ApiException.rule("INVALID_START_DATE",
                    "Start date must be within the last " + MAX_BACKDATE_DAYS + " days or the next "
                            + MAX_FUTURE_DAYS + " days");
        }
    }

    private static LoanTerms terms(LoanCreate in) {
        return new LoanTerms(in.principal(), in.interestRate(), in.interestMethod(), in.interestCollection(),
                in.frequency(), in.tenureCount(), in.startDate());
    }
}
