-- =====================================================================
-- TimeCraft V12: Increase Cash on Delivery (COD) maximum order amount to ₹5,00,000.
-- Compatible with both MySQL 8 and H2 MySQL-mode.
-- =====================================================================

UPDATE store_settings
SET setting_value = '500000.00',
    updated_at = CURRENT_TIMESTAMP(6)
WHERE setting_key = 'cod_max_amount';
