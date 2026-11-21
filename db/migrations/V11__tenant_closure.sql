-- =====================================================================
-- Naanayam Thavanai - V11: owner deletes the business account
-- The owner's request closes the business at once (everyone signed out); a nightly job erases all of
-- its data on erase_on (request + 30 days). Until then a Super Admin can restore it.
-- =====================================================================
ALTER TABLE tenant
    MODIFY COLUMN status ENUM('TRIAL','ACTIVE','SUSPENDED','CLOSED') NOT NULL DEFAULT 'TRIAL',
    ADD COLUMN closed_at DATETIME NULL AFTER terms_accepted_at,
    ADD COLUMN erase_on  DATE     NULL AFTER closed_at;
