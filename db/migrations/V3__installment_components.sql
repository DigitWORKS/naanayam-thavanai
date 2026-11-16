-- =====================================================================
-- Naanayam Thavanai - V3: principal / interest split per installment
-- Needed for pre-closure quotes (outstanding principal) and the
-- financial-year interest income report.
-- =====================================================================

ALTER TABLE installment
    ADD COLUMN principal_component     DECIMAL(12,2) NOT NULL DEFAULT 0.00 AFTER due_amount,
    ADD COLUMN interest_component      DECIMAL(12,2) NOT NULL DEFAULT 0.00 AFTER principal_component,
    ADD COLUMN principal_balance_after DECIMAL(12,2) NOT NULL DEFAULT 0.00 AFTER interest_component;
