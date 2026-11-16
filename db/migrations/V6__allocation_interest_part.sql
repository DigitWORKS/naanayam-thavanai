-- =====================================================================
-- Naanayam Thavanai - V6: interest part per payment allocation
-- Interest actually collected, per allocation. The financial-year interest
-- income report sums this column (pre-closure rebates make a ratio-based
-- calculation inaccurate).
-- =====================================================================

ALTER TABLE payment_allocation
    ADD COLUMN interest_part DECIMAL(12,2) NOT NULL DEFAULT 0.00 AFTER amount;
