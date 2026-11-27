-- =====================================================================
-- TimeCraft V14: Allow PAYU alongside RAZORPAY and COD in orders.payment_method.
-- Compatible with both MySQL 8 and H2 MySQL-mode.
-- =====================================================================

ALTER TABLE orders DROP CONSTRAINT ck_orders_payment_method;
ALTER TABLE orders ADD CONSTRAINT ck_orders_payment_method CHECK (payment_method IN ('RAZORPAY', 'PAYU', 'COD'));
