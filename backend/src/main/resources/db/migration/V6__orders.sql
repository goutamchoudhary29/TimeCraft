-- =====================================================================
-- TimeCraft V6: orders, immutable order item snapshots, status history,
-- stock reservations and coupon usages (both reference orders).
-- =====================================================================

CREATE TABLE orders (
    id                BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_number      VARCHAR(30)    NOT NULL,
    user_id           BIGINT         NOT NULL,
    status            VARCHAR(30)    NOT NULL,
    payment_status    VARCHAR(30)    NOT NULL,
    currency          CHAR(3)        NOT NULL DEFAULT 'INR',
    subtotal          DECIMAL(12, 2) NOT NULL,
    discount_total    DECIMAL(12, 2) NOT NULL DEFAULT 0,
    tax_total         DECIMAL(12, 2) NOT NULL DEFAULT 0,
    shipping_total    DECIMAL(12, 2) NOT NULL DEFAULT 0,
    grand_total       DECIMAL(12, 2) NOT NULL,
    tax_inclusive     BOOLEAN        NOT NULL DEFAULT FALSE,
    coupon_id         BIGINT,
    coupon_code       VARCHAR(40),
    customer_email    VARCHAR(255)   NOT NULL,
    customer_name     VARCHAR(160)   NOT NULL,
    ship_full_name    VARCHAR(120)   NOT NULL,
    ship_phone        VARCHAR(20)    NOT NULL,
    ship_line1        VARCHAR(255)   NOT NULL,
    ship_line2        VARCHAR(255),
    ship_landmark     VARCHAR(120),
    ship_city         VARCHAR(100)   NOT NULL,
    ship_state        VARCHAR(100)   NOT NULL,
    ship_postal_code  VARCHAR(12)    NOT NULL,
    ship_country      CHAR(2)        NOT NULL,
    customer_note     VARCHAR(500),
    idempotency_key   VARCHAR(64),
    payment_expires_at DATETIME(6),
    placed_at         DATETIME(6)    NOT NULL,
    confirmed_at      DATETIME(6),
    delivered_at      DATETIME(6),
    cancelled_at      DATETIME(6),
    cancel_reason     VARCHAR(255),
    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    version           BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_orders_number UNIQUE (order_number),
    CONSTRAINT uk_orders_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_orders_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id)
);
CREATE INDEX idx_orders_user_created ON orders (user_id, created_at);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_placed_at ON orders (placed_at);
CREATE INDEX idx_orders_payment_expiry ON orders (payment_status, payment_expires_at);

CREATE TABLE order_items (
    id                BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id          BIGINT         NOT NULL,
    product_id        BIGINT,
    variant_id        BIGINT,
    product_name      VARCHAR(160)   NOT NULL,
    brand_name        VARCHAR(100)   NOT NULL,
    sku               VARCHAR(64)    NOT NULL,
    variant_name      VARCHAR(120)   NOT NULL,
    image_url         VARCHAR(500),
    unit_price        DECIMAL(12, 2) NOT NULL,
    quantity          INT            NOT NULL,
    line_subtotal     DECIMAL(12, 2) NOT NULL,
    discount_amount   DECIMAL(12, 2) NOT NULL DEFAULT 0,
    tax_rate          DECIMAL(5, 2)  NOT NULL DEFAULT 0,
    tax_amount        DECIMAL(12, 2) NOT NULL DEFAULT 0,
    line_total        DECIMAL(12, 2) NOT NULL,
    returned_quantity INT            NOT NULL DEFAULT 0,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE SET NULL,
    CONSTRAINT fk_order_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE SET NULL,
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0 AND returned_quantity >= 0 AND returned_quantity <= quantity)
);
CREATE INDEX idx_order_items_order ON order_items (order_id);
CREATE INDEX idx_order_items_product ON order_items (product_id);

CREATE TABLE order_status_history (
    id                 BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id           BIGINT       NOT NULL,
    from_status        VARCHAR(30),
    to_status          VARCHAR(30)  NOT NULL,
    note               VARCHAR(500),
    changed_by_type    VARCHAR(20)  NOT NULL,
    changed_by_user_id BIGINT,
    created_at         DATETIME(6)  NOT NULL,
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES orders (id)
);
CREATE INDEX idx_order_status_history_order ON order_status_history (order_id, created_at);

CREATE TABLE stock_reservations (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    variant_id BIGINT      NOT NULL,
    order_id   BIGINT      NOT NULL,
    quantity   INT         NOT NULL,
    status     VARCHAR(20) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_stock_reservations_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    CONSTRAINT fk_stock_reservations_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_stock_reservations_quantity CHECK (quantity > 0),
    CONSTRAINT ck_stock_reservations_status CHECK (status IN ('ACTIVE', 'COMMITTED', 'RELEASED', 'EXPIRED'))
);
CREATE INDEX idx_stock_reservations_status_expiry ON stock_reservations (status, expires_at);
CREATE INDEX idx_stock_reservations_order ON stock_reservations (order_id);

CREATE TABLE coupon_usages (
    id              BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    coupon_id       BIGINT         NOT NULL,
    user_id         BIGINT         NOT NULL,
    order_id        BIGINT         NOT NULL,
    discount_amount DECIMAL(12, 2) NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    CONSTRAINT uk_coupon_usages_order UNIQUE (order_id),
    CONSTRAINT fk_coupon_usages_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id),
    CONSTRAINT fk_coupon_usages_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_coupon_usages_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_coupon_usages_status CHECK (status IN ('RESERVED', 'REDEEMED', 'RELEASED'))
);
CREATE INDEX idx_coupon_usages_coupon_user ON coupon_usages (coupon_id, user_id, status);
