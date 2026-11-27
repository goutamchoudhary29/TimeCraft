-- V17: Composite Performance Indexes for Orders, Catalog, Payments, Cart & Delivery Lookups

CREATE INDEX idx_orders_user_placed         ON orders (user_id, placed_at);
CREATE INDEX idx_orders_status_pay_expiry   ON orders (status, payment_status, payment_expires_at);
CREATE INDEX idx_products_cat_status_del    ON products (category_id, status, deleted);
CREATE INDEX idx_products_brand_status_del  ON products (brand_id, status, deleted);
CREATE INDEX idx_payments_order_created     ON payments (order_id, created_at);
CREATE INDEX idx_cart_items_cart            ON cart_items (cart_id);
CREATE INDEX idx_delivery_pin_serviceable   ON delivery_pin_rules (serviceable, pin_pattern);
