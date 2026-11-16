package tn.digitworks.naanayamthavanai.payment;

import org.springframework.stereotype.Component;
import tn.digitworks.naanayamthavanai.common.ApiException;
import tn.digitworks.naanayamthavanai.tenant.TenantRepository;

import java.util.regex.Pattern;

/**
 * UPI transaction ID (UTR) rules, shared by regular payments and pre-closure:
 * required for UPI, 12 digits (spaces ignored), and not already on an unreversed payment of the tenant.
 * Cash payments never keep a UTR.
 *
 * <p>Must run inside the payment's transaction, BEFORE the loan is locked: it locks the tenant row so two
 * agents cannot record the same UTR at the same moment (lock order: tenant, then loan, then installments).
 */
@Component
public class UpiRefCheck {

    private static final Pattern UTR = Pattern.compile("\\d{12}");

    private final PaymentRepository payments;
    private final TenantRepository tenants;

    public UpiRefCheck(PaymentRepository payments, TenantRepository tenants) {
        this.payments = payments;
        this.tenants = tenants;
    }

    /** Returns the cleaned UTR to store, or null for cash. */
    public String resolve(long tenantId, Payment.Mode mode, String raw) {
        if (mode != Payment.Mode.UPI) {
            return null;
        }
        String utr = raw == null ? "" : raw.replaceAll("\\s+", "");
        if (utr.isEmpty()) {
            throw ApiException.rule("UPI_REF_REQUIRED", "UPI transaction ID (UTR) is required for UPI payments");
        }
        if (!UTR.matcher(utr).matches()) {
            throw ApiException.rule("INVALID_UPI_REF", "UPI transaction ID (UTR) must be 12 digits");
        }
        tenants.lockById(tenantId).orElseThrow();
        if (payments.isUpiRefInUse(tenantId, utr)) {
            throw ApiException.conflict("DUPLICATE_UPI_REF",
                    "UPI transaction ID " + utr + " is already recorded on another payment");
        }
        return utr;
    }
}
