-- =====================================================================
-- Naanayam Thavanai - V8: subscription plan + terms acceptance
-- plan decides plan_expiry: TRIAL +30 days, MONTHLY +1 month, YEARLY +1 year.
-- =====================================================================

ALTER TABLE tenant
    ADD COLUMN plan ENUM('TRIAL','MONTHLY','YEARLY') NOT NULL DEFAULT 'TRIAL' AFTER status,
    ADD COLUMN terms_accepted_at DATETIME NULL AFTER plan_expiry;

-- Existing tenants without an expiry get a 30-day trial from when they were created
UPDATE tenant
SET plan_expiry = DATE_ADD(DATE(created_at), INTERVAL 30 DAY)
WHERE plan_expiry IS NULL;
