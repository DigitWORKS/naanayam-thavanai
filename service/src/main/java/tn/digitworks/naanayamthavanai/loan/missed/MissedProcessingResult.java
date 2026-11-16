package tn.digitworks.naanayamthavanai.loan.missed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * @param updated        existing rows that changed (marked MISSED and/or penalty added)
 * @param created        new extension rows to insert (id = null)
 * @param currentEndDate new loan.current_end_date (unchanged if no extension was added)
 */
public record MissedProcessingResult(
        List<InstallmentRow> updated,
        List<InstallmentRow> created,
        LocalDate currentEndDate,
        BigDecimal penaltyAdded) {

    public boolean hasChanges() {
        return !updated.isEmpty() || !created.isEmpty();
    }
}
