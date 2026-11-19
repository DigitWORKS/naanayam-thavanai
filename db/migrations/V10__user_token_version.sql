-- =====================================================================
-- Naanayam Thavanai - V10: sign a user out everywhere
-- Tokens carry token_version (claim "tv"; tokens without it count as 0). Deactivating a user or setting a
-- new PIN increments it, so their existing access and refresh tokens stop working on the next request.
-- =====================================================================
ALTER TABLE app_user
    ADD COLUMN token_version INT NOT NULL DEFAULT 0;
