package tn.digitworks.naanayamthavanai.payment.allocation;

/** Tenant settings that drive allocation (tenant_settings.allocation_order / advance_allocation). */
public record AllocationPolicy(Order order, Advance advance) {

    /** Whether outstanding penalties are cleared before or after installments due now. */
    public enum Order { PENALTY_FIRST, INSTALLMENT_FIRST }

    /** Where money beyond today's dues goes. */
    public enum Advance {
        /** Upcoming installments in order (client can skip the next days). */
        NEXT_INSTALLMENTS,
        /** From the end of the schedule (loan finishes earlier; cancels extensions first). */
        LAST_INSTALLMENTS
    }
}
