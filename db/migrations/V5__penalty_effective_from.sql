-- =====================================================================
-- Naanayam Thavanai - V5: penalty effective date
-- Set by the service when penalties are turned on, so missed days from
-- before that date are never charged retroactively.
-- =====================================================================

ALTER TABLE tenant_settings
    ADD COLUMN penalty_effective_from DATE NULL AFTER penalty_max_cap;
