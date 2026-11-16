package tn.digitworks.naanayamthavanai.loan.schedule;

/** Enums shared by the schedule module. Names match the DB and OpenAPI enums. */
public final class LoanEnums {

    private LoanEnums() {
    }

    public enum InterestMethod { FLAT, REDUCING }

    public enum InterestCollection { UPFRONT_DEDUCTED, ADDED_TO_REPAYMENT }

    /**
     * PENDING/PARTIAL are open (collectible). MISSED means the shortfall moved to an
     * extension installment; only its penalty stays collectible. PAID/SETTLED are closed.
     */
    public enum InstallmentStatus {
        PENDING, PARTIAL, PAID, MISSED, SETTLED;

        public boolean isOpen() {
            return this == PENDING || this == PARTIAL;
        }
    }

    public enum Frequency {
        DAILY(1), WEEKLY(7);

        private final int days;

        Frequency(int days) {
            this.days = days;
        }

        public int days() {
            return days;
        }
    }
}
