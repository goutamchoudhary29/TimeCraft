-- =====================================================================
-- TimeCraft V5: carts, wishlists and coupons
-- A cart belongs either to a customer (user_id) or to a guest (guest_token hash).
-- =====================================================================

CREATE TABLE coupons (
    id                 BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code               VARCHAR(40)    NOT NULL,
    description        VARCHAR(255),
    discount_type      VARCHAR(20)    NOT NULL,
    discount_value     DECIMAL(12, 2) NOT NULL,
    min_order_value    DECIMAL(12, 2) NOT NULL DEFAULT 0,
    max_discount       DECIMAL(12, 2),
    starts_at          DATETIME(6)    NOT NULL,
    expires_at         DATETIME(6)    NOT NULL,
    usage_limit        INT,
    per_customer_limit INT,
    used_count         INT            NOT NULL DEFAULT 0,
    active             BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at         DATETIME(6)    NOT NULL,
    updated_at         DATETIME(6)    NOT NULL,
    version            BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_coupons_code UNIQUE (code),
    CONSTRAINT ck_coupons_type CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    CONSTRAINT ck_coupons_value CHECK (discount_value > 0)
);
CREATE INDEX idx_coupons_active_dates ON coupons (active, starts_at, expires_at);

CREATE TABLE carts (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT,
    guest_token CHAR(64),
    coupon_id   BIGINT,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    CONSTRAINT uk_carts_user UNIQUE (user_id),
    CONSTRAINT uk_carts_guest UNIQUE (guest_token),
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_carts_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id) ON DELETE SET NULL
);
CREATE INDEX idx_carts_updated_at ON carts (updated_at);

CREATE TABLE cart_items (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    cart_id    BIGINT      NOT NULL,
    variant_id BIGINT      NOT NULL,
    quantity   INT         NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_cart_items_cart_variant UNIQUE (cart_id, variant_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    CONSTRAINT ck_cart_items_quantity CHECK (quantity > 0)
);

CREATE TABLE wishlists (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_wishlists_user UNIQUE (user_id),
    CONSTRAINT fk_wishlists_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE wishlist_items (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    wishlist_id BIGINT      NOT NULL,
    product_id  BIGINT      NOT NULL,
    variant_id  BIGINT,
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT uk_wishlist_items_product UNIQUE (wishlist_id, product_id),
    CONSTRAINT fk_wishlist_items_wishlist FOREIGN KEY (wishlist_id) REFERENCES wishlists (id) ON DELETE CASCADE,
    CONSTRAINT fk_wishlist_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_wishlist_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE SET NULL
);
