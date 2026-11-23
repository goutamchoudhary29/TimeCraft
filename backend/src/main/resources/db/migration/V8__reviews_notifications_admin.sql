-- =====================================================================
-- TimeCraft V8: reviews, notifications, email logs, audit logs, store settings,
-- support tickets and newsletter subscribers (supporting tables).
-- =====================================================================

-- Review policy: one review per customer per product, only verified purchasers
-- (customer has a DELIVERED order containing the product) may submit reviews.
CREATE TABLE product_reviews (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id        BIGINT        NOT NULL,
    user_id           BIGINT        NOT NULL,
    order_item_id     BIGINT,
    rating            INT           NOT NULL,
    title             VARCHAR(150)  NOT NULL,
    body              VARCHAR(4000) NOT NULL,
    verified_purchase BOOLEAN       NOT NULL DEFAULT FALSE,
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    moderation_note   VARCHAR(500),
    moderated_by      BIGINT,
    moderated_at      DATETIME(6),
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    CONSTRAINT uk_product_reviews_user_product UNIQUE (product_id, user_id),
    CONSTRAINT fk_product_reviews_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_reviews_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_product_reviews_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id),
    CONSTRAINT fk_product_reviews_moderator FOREIGN KEY (moderated_by) REFERENCES users (id),
    CONSTRAINT ck_product_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_product_reviews_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);
CREATE INDEX idx_product_reviews_product_status ON product_reviews (product_id, status, created_at);

CREATE TABLE review_images (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    review_id  BIGINT       NOT NULL,
    url        VARCHAR(500) NOT NULL,
    public_id  VARCHAR(255),
    created_at DATETIME(6)  NOT NULL,
    CONSTRAINT fk_review_images_review FOREIGN KEY (review_id) REFERENCES product_reviews (id) ON DELETE CASCADE
);

CREATE TABLE notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(40)  NOT NULL,
    title      VARCHAR(150) NOT NULL,
    message    VARCHAR(1000) NOT NULL,
    link       VARCHAR(300),
    read_at    DATETIME(6),
    created_at DATETIME(6)  NOT NULL,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_notifications_user ON notifications (user_id, created_at);

CREATE TABLE email_logs (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT,
    recipient     VARCHAR(255) NOT NULL,
    subject       VARCHAR(255) NOT NULL,
    template      VARCHAR(80)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    error_message VARCHAR(500),
    related_type  VARCHAR(40),
    related_id    BIGINT,
    created_at    DATETIME(6)  NOT NULL,
    sent_at       DATETIME(6),
    CONSTRAINT fk_email_logs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX idx_email_logs_status ON email_logs (status, created_at);

CREATE TABLE audit_logs (
    id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_user_id BIGINT,
    actor_email   VARCHAR(255),
    action        VARCHAR(60)   NOT NULL,
    entity_type   VARCHAR(60),
    entity_id     VARCHAR(64),
    details       VARCHAR(4000),
    ip_address    VARCHAR(45),
    created_at    DATETIME(6)   NOT NULL
);
CREATE INDEX idx_audit_logs_created ON audit_logs (created_at);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_actor ON audit_logs (actor_user_id);

CREATE TABLE store_settings (
    id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    setting_key   VARCHAR(80)   NOT NULL,
    setting_value VARCHAR(2000) NOT NULL,
    value_type    VARCHAR(20)   NOT NULL,
    setting_group VARCHAR(40)   NOT NULL,
    description   VARCHAR(255),
    updated_by    BIGINT,
    updated_at    DATETIME(6)   NOT NULL,
    CONSTRAINT uk_store_settings_key UNIQUE (setting_key)
);

CREATE TABLE support_tickets (
    id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_number VARCHAR(30)   NOT NULL,
    user_id       BIGINT,
    name          VARCHAR(120)  NOT NULL,
    email         VARCHAR(255)  NOT NULL,
    subject       VARCHAR(200)  NOT NULL,
    message       VARCHAR(4000) NOT NULL,
    order_number  VARCHAR(30),
    status        VARCHAR(20)   NOT NULL,
    admin_reply   VARCHAR(4000),
    replied_by    BIGINT,
    replied_at    DATETIME(6),
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    CONSTRAINT uk_support_tickets_number UNIQUE (ticket_number),
    CONSTRAINT fk_support_tickets_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX idx_support_tickets_status ON support_tickets (status, created_at);

CREATE TABLE newsletter_subscribers (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME(6)  NOT NULL,
    CONSTRAINT uk_newsletter_email UNIQUE (email)
);
