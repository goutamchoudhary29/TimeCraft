-- =====================================================================
-- TimeCraft V11: Email OTP tracking columns, Order payment_method (RAZORPAY/COD),
-- and Cash-on-Delivery / Checkout store settings.
-- Compatible with both MySQL 8 and H2 MySQL-mode.
-- =====================================================================

ALTER TABLE email_verification_tokens ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE email_verification_tokens ADD COLUMN last_sent_at DATETIME(6);

ALTER TABLE password_reset_tokens ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE password_reset_tokens ADD COLUMN last_sent_at DATETIME(6);

ALTER TABLE orders ADD COLUMN payment_method VARCHAR(20) NOT NULL DEFAULT 'RAZORPAY';
ALTER TABLE orders ADD CONSTRAINT ck_orders_payment_method CHECK (payment_method IN ('RAZORPAY', 'COD'));

INSERT INTO store_settings (setting_key, setting_value, value_type, setting_group, description, updated_at) VALUES
('cod_enabled', 'true', 'BOOLEAN', 'CHECKOUT', 'Allow Cash on Delivery (COD) checkout', CURRENT_TIMESTAMP(6)),
('cod_max_amount', '50000.00', 'DECIMAL', 'CHECKOUT', 'Maximum order total eligible for Cash on Delivery (INR)', CURRENT_TIMESTAMP(6)),
('tax_rate_percent', '18.00', 'DECIMAL', 'TAX', 'Alias for configurable GST rate percent', CURRENT_TIMESTAMP(6)),
('shipping_charge', '250.00', 'DECIMAL', 'SHIPPING', 'Alias for flat shipping charge', CURRENT_TIMESTAMP(6)),
('free_shipping_threshold', '10000.00', 'DECIMAL', 'SHIPPING', 'Alias for free shipping threshold', CURRENT_TIMESTAMP(6)),
('return_window_days', '7', 'INTEGER', 'RETURNS', 'Alias for return window in days', CURRENT_TIMESTAMP(6));
