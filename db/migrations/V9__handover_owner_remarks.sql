-- =====================================================================
-- Naanayam Thavanai - V9: owner's note on a cash handover
-- remarks stays the agent's note from submit; owner_remarks holds the owner's note on confirm / dispute
-- (before V9 the owner's note overwrote remarks).
-- =====================================================================
ALTER TABLE cash_handover
    ADD COLUMN owner_remarks VARCHAR(255) NULL AFTER remarks;
