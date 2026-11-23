-- =====================================================================
-- TimeCraft V103: Configurable Google OAuth 2.0 Client ID store setting
-- =====================================================================
INSERT INTO store_settings (setting_key, setting_value, value_type, setting_group, description, updated_at)
SELECT 'auth.google_client_id', '', 'STRING', 'AUTH', 'Google OAuth 2.0 Web Client ID (e.g. 123456-abc.apps.googleusercontent.com) for real Google Sign-In popup', CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM store_settings WHERE setting_key = 'auth.google_client_id');
