package tn.digitworks.naanayamthavanai.tenant;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Subscription plan of a financier. The plan decides {@code tenant.plan_expiry}.
 * Prices and payment collection are not part of the backend yet.
 */
public enum Plan {

    /** Free, 30 days. */
    TRIAL,
    MONTHLY,
    YEARLY;

    private static final int TRIAL_DAYS = 30;

    /** Last day of access for a period that starts on {@code start}. */
    public LocalDate expiryFrom(LocalDate start) {
        Objects.requireNonNull(start, "start");
        return switch (this) {
            case TRIAL -> start.plusDays(TRIAL_DAYS);
            case MONTHLY -> start.plusMonths(1);
            case YEARLY -> start.plusYears(1);
        };
    }

    /**
     * New expiry when this plan starts or renews on {@code today}. Unused days are kept: a tenant whose access
     * still runs gets the new period added to its current expiry; an expired (or new) tenant starts from today.
     */
    public LocalDate renewedExpiry(LocalDate currentExpiry, LocalDate today) {
        LocalDate base = currentExpiry != null && !currentExpiry.isBefore(today) ? currentExpiry : today;
        return expiryFrom(base);
    }

    /** Account status that goes with the plan (a suspended tenant stays suspended; see {@link Tenant}). */
    public Tenant.Status status() {
        return this == TRIAL ? Tenant.Status.TRIAL : Tenant.Status.ACTIVE;
    }

    /** Self-registration may only start a trial until payments exist. */
    public boolean isSelfServe() {
        return this == TRIAL;
    }
}
