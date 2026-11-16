-- =====================================================================
-- Naanayam Thavanai - V4: track penalty paid per installment
-- paid_amount covers the installment amount only; penalty is tracked separately
-- so payment allocation can tell outstanding penalty from outstanding dues.
-- =====================================================================

ALTER TABLE installment
    ADD COLUMN penalty_paid DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER penalty_amount;
