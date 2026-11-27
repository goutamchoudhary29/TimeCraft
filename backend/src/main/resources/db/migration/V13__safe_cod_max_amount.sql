-- =====================================================================
-- TimeCraft V13: Align Cash on Delivery (COD) safe maximum order amount to ₹50,000.00.
-- Compatible with both MySQL 8 and H2 MySQL-mode.
-- =====================================================================

UPDATE store_settings
SET setting_value = '50000.00',
    updated_at = CURRENT_TIMESTAMP(6)
WHERE setting_key = 'cod_max_amount';
