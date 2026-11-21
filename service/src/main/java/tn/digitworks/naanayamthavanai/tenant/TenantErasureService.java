package tn.digitworks.naanayamthavanai.tenant;

import jakarta.persistence.EntityManager;
import tn.digitworks.naanayamthavanai.client.DocumentStorage;
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

/**
 * Nightly: erases every business the owner deleted at least {@value Tenant#ERASE_AFTER_DAYS} days ago — all rows
 * of every table, then the uploaded documents. One transaction per business. Backups age out separately
 * (14 days, deploy/backup.sh).
 *
 * <p>Single-instance only for now, like the missed-installment job.
 */
@Service
public class TenantErasureService {

    private static final Logger log = LoggerFactory.getLogger(TenantErasureService.class);

    /**
     * Child tables first. Self-references (installment.extended_from, payment.reversal_of) are cleared before
     * their rows go. TenantErasureTablesTest checks that every table in the migrations is listed here.
     */
    static final List<String> STATEMENTS = List.of(
            "UPDATE installment SET extended_from = NULL WHERE tenant_id = :t",
            "UPDATE payment SET reversal_of = NULL WHERE tenant_id = :t",
            "DELETE FROM loan_preclosure WHERE tenant_id = :t",
            "DELETE FROM payment_allocation WHERE tenant_id = :t",
            "DELETE FROM payment WHERE tenant_id = :t",
            "DELETE FROM cash_handover WHERE tenant_id = :t",
            "DELETE FROM installment WHERE tenant_id = :t",
            "DELETE FROM loan WHERE tenant_id = :t",
            "DELETE FROM client_document WHERE tenant_id = :t",
            "DELETE FROM client WHERE tenant_id = :t",
            "DELETE FROM user_area WHERE tenant_id = :t",
            "DELETE FROM app_user WHERE tenant_id = :t",
            "DELETE FROM area WHERE tenant_id = :t",
            "DELETE FROM tenant_settings WHERE tenant_id = :t",
            "DELETE FROM tenant WHERE id = :t AND status = 'CLOSED'");

    private final TenantRepository tenants;
    private final EntityManager em;
    private final DocumentStorage documents;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final boolean enabled;

    public TenantErasureService(TenantRepository tenants, EntityManager em, DocumentStorage documents,
                                PlatformTransactionManager txManager, Clock clock,
                                @Value("${app.jobs.tenant-erasure.enabled:true}") boolean enabled) {
        this.tenants = tenants;
        this.em = em;
        this.documents = documents;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${app.jobs.tenant-erasure.cron:0 45 0 * * *}", zone = "Asia/Kolkata")
    public void nightly() {
        if (enabled) {
            run(LocalDate.now(clock));
        }
    }

    /** Erases every closed business due on or before {@code today}. Returns how many were erased. */
    public int run(LocalDate today) {
        int erased = 0;
        for (Long id : tenants.findIdsToErase(today)) {
            try {
                tx.executeWithoutResult(s -> erase(id));
                documents.deleteTenant(id);
                erased++;
                log.info("Erased closed business {}", id);
            } catch (RuntimeException e) {
                log.error("Erasing closed business {} failed", id, e);
            }
        }
        return erased;
    }

    private void erase(long tenantId) {
        for (String sql : STATEMENTS) {
            em.createNativeQuery(sql).setParameter("t", tenantId).executeUpdate();
        }
    }
}
