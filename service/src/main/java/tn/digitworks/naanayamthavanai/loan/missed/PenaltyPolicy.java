package tn.digitworks.naanayamthavanai.loan.missed;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tenant penalty settings (tenant_settings.penalty_*).
 *
 * @param value         rupees for FIXED, % of the missed shortfall for PERCENT
 * @param graceDays     penalty applies only when the shortfall is still unpaid after this many days
 * @param maxCapPerLoan total penalty limit per loan; null = no cap
 * @param effectiveFrom only installments due on or after this date get a penalty, so turning
 *                      penalties on never charges old missed days retroactively
 */
public record PenaltyPolicy(
        boolean enabled,
        Type type,
        BigDecimal value,
        int graceDays,
        BigDecimal maxCapPerLoan,
        LocalDate effectiveFrom) {

    public enum Type { FIXED, PERCENT }

    public static PenaltyPolicy disabled() {
        return new PenaltyPolicy(false, null, null, 0, null, null);
    }
}
