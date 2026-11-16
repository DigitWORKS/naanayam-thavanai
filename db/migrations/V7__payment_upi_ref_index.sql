-- =====================================================================
-- Naanayam Thavanai - V7: index for the duplicate UPI transaction ID check
-- Not UNIQUE on purpose: a reversed payment's UTR may be recorded again,
-- and reversal rows keep the original UTR. The service enforces uniqueness
-- among unreversed payments (UpiRefCheck).
-- =====================================================================

CREATE INDEX idx_pay_tenant_upi_ref ON payment (tenant_id, upi_ref_no);
