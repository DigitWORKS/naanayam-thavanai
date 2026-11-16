package tn.digitworks.naanayamthavanai.loan.missed;

import tn.digitworks.naanayamthavanai.loan.Installment;
import tn.digitworks.naanayamthavanai.loan.InstallmentRepository;
import tn.digitworks.naanayamthavanai.loan.Loan;
import tn.digitworks.naanayamthavanai.loan.LoanRepository;
import tn.digitworks.naanayamthavanai.tenant.TenantSettings;
import tn.digitworks.naanayamthavanai.tenant.TenantSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Nightly: marks unpaid past installments MISSED, appends extensions, adds penalties.
 * One transaction per loan, so one bad loan does not stop the rest.
 *
 * <p>Single-instance only for now. With more than one server, add ShedLock so it runs once.
 */
@Service
public class MissedInstallmentService {

    private static final Logger log = LoggerFactory.getLogger(MissedInstallmentService.class);

    public record RunSummary(LocalDate date, int loansChecked, int loansChanged, int failures) {
    }

    private final LoanRepository loans;
    private final InstallmentRepository installments;
    private final TenantSettingsRepository settings;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final MissedInstallmentProcessor processor = new MissedInstallmentProcessor();
    private final boolean enabled;

    public MissedInstallmentService(LoanRepository loans, InstallmentRepository installments,
                                    TenantSettingsRepository settings, PlatformTransactionManager txManager,
                                    Clock clock, @Value("${app.jobs.missed-installments.enabled:true}") boolean enabled) {
        this.loans = loans;
        this.installments = installments;
        this.settings = settings;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${app.jobs.missed-installments.cron:0 15 0 * * *}", zone = "Asia/Kolkata")
    public void nightly() {
        if (enabled) {
            run(LocalDate.now(clock));
        }
    }

    /** Processes every ACTIVE loan as of {@code today}. Safe to run more than once a day. */
    public RunSummary run(LocalDate today) {
        List<Long> ids = loans.findIdsByStatus(Loan.Status.ACTIVE);
        int changed = 0;
        int failures = 0;
        for (Long id : ids) {
            try {
                Boolean c = tx.execute(s -> processLoan(id, today));
                if (Boolean.TRUE.equals(c)) {
                    changed++;
                }
            } catch (RuntimeException e) {
                failures++;
                log.error("Missed-installment processing failed for loan {}", id, e);
            }
        }
        log.info("Missed-installment run for {}: {} loans, {} changed, {} failed", today, ids.size(), changed, failures);
        return new RunSummary(today, ids.size(), changed, failures);
    }

    private boolean processLoan(long loanId, LocalDate today) {
        Loan loan = loans.lockById(loanId).orElse(null);
        if (loan == null || loan.getStatus() != Loan.Status.ACTIVE) {
            return false;
        }
        TenantSettings s = settings.findById(loan.getTenantId()).orElseThrow();
        List<Installment> rows = installments.lockByLoanId(loanId);
        if (rows.isEmpty()) {
            return false;
        }
        MissedProcessingResult result = processor.process(rows.stream().map(Installment::toRow).toList(),
                loan.getFrequency(), today, s.penaltyPolicy());
        if (!result.hasChanges()) {
            return false;
        }

        Map<Long, Installment> byId = rows.stream().collect(Collectors.toMap(Installment::getId, Function.identity()));
        result.updated().forEach(u -> byId.get(u.id()).apply(u));
        installments.saveAll(result.created().stream()
                .map(c -> Installment.extension(loan.getTenantId(), loanId, c)).toList());
        loan.setCurrentEndDate(result.currentEndDate());
        return true;
    }
}
