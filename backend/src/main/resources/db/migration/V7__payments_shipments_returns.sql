-- =====================================================================
-- TimeCraft V7: payments (Razorpay), webhooks, shipments, returns and refunds
-- =====================================================================

CREATE TABLE payments (
    id                  BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id            BIGINT         NOT NULL,
    provider            VARCHAR(20)    NOT NULL DEFAULT 'RAZORPAY',
    provider_order_id   VARCHAR(64)    NOT NULL,
    provider_payment_id VARCHAR(64),
    amount              DECIMAL(12, 2) NOT NULL,
    currency            CHAR(3)        NOT NULL DEFAULT 'INR',
    status              VARCHAR(30)    NOT NULL,
    method              VARCHAR(30),
    failure_reason      VARCHAR(500),
    refunded_amount     DECIMAL(12, 2) NOT NULL DEFAULT 0,
    paid_at             DATETIME(6),
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    version             BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_payments_provider_order UNIQUE (provider_order_id),
    CONSTRAINT uk_payments_provider_payment UNIQUE (provider_payment_id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id)
);
CREATE INDEX idx_payments_order ON payments (order_id);
CREATE INDEX idx_payments_status_created ON payments (status, created_at);

CREATE TABLE payment_transactions (
    id                 BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payment_id         BIGINT         NOT NULL,
    transaction_type   VARCHAR(40)    NOT NULL,
    amount             DECIMAL(12, 2) NOT NULL,
    status             VARCHAR(30)    NOT NULL,
    provider_reference VARCHAR(100),
    details            VARCHAR(1000),
    created_at         DATETIME(6)    NOT NULL,
    CONSTRAINT fk_payment_transactions_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
);
CREATE INDEX idx_payment_transactions_payment ON payment_transactions (payment_id, created_at);

CREATE TABLE payment_webhook_events (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id          VARCHAR(100) NOT NULL,
    event_type        VARCHAR(60)  NOT NULL,
    payload           TEXT         NOT NULL,
    processing_status VARCHAR(20)  NOT NULL,
    error_message     VARCHAR(500),
    received_at       DATETIME(6)  NOT NULL,
    processed_at      DATETIME(6),
    CONSTRAINT uk_payment_webhook_events_event UNIQUE (event_id)
);
CREATE INDEX idx_payment_webhook_events_type ON payment_webhook_events (event_type, received_at);

CREATE TABLE shipments (
    id                      BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id                BIGINT       NOT NULL,
    courier_name            VARCHAR(80)  NOT NULL,
    tracking_number         VARCHAR(80)  NOT NULL,
    tracking_url            VARCHAR(500),
    status                  VARCHAR(30)  NOT NULL,
    estimated_delivery_date DATE,
    shipped_at              DATETIME(6),
    delivered_at            DATETIME(6),
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    CONSTRAINT uk_shipments_order UNIQUE (order_id),
    CONSTRAINT fk_shipments_order FOREIGN KEY (order_id) REFERENCES orders (id)
);
CREATE INDEX idx_shipments_tracking ON shipments (tracking_number);

CREATE TABLE shipment_tracking_events (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    shipment_id BIGINT       NOT NULL,
    status      VARCHAR(30)  NOT NULL,
    location    VARCHAR(120),
    description VARCHAR(500),
    event_time  DATETIME(6)  NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT fk_shipment_tracking_shipment FOREIGN KEY (shipment_id) REFERENCES shipments (id) ON DELETE CASCADE
);
CREATE INDEX idx_shipment_tracking_shipment ON shipment_tracking_events (shipment_id, event_time);

CREATE TABLE return_requests (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    return_number  VARCHAR(30)  NOT NULL,
    order_id       BIGINT       NOT NULL,
    user_id        BIGINT       NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    reason_code    VARCHAR(40)  NOT NULL,
    reason_details VARCHAR(1000),
    admin_note     VARCHAR(1000),
    reviewed_by    BIGINT,
    reviewed_at    DATETIME(6),
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    CONSTRAINT uk_return_requests_number UNIQUE (return_number),
    CONSTRAINT fk_return_requests_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_return_requests_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_return_requests_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id)
);
CREATE INDEX idx_return_requests_status ON return_requests (status, created_at);
CREATE INDEX idx_return_requests_user ON return_requests (user_id);

CREATE TABLE return_items (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    return_request_id BIGINT       NOT NULL,
    order_item_id     BIGINT       NOT NULL,
    quantity          INT          NOT NULL,
    condition_note    VARCHAR(255),
    CONSTRAINT uk_return_items_item UNIQUE (return_request_id, order_item_id),
    CONSTRAINT fk_return_items_request FOREIGN KEY (return_request_id) REFERENCES return_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_return_items_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id),
    CONSTRAINT ck_return_items_quantity CHECK (quantity > 0)
);

CREATE TABLE refunds (
    id                 BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    refund_number      VARCHAR(30)    NOT NULL,
    order_id           BIGINT         NOT NULL,
    payment_id         BIGINT         NOT NULL,
    return_request_id  BIGINT,
    amount             DECIMAL(12, 2) NOT NULL,
    reason             VARCHAR(255)   NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    provider_refund_id VARCHAR(64),
    failure_reason     VARCHAR(500),
    initiated_by       BIGINT,
    processed_at       DATETIME(6),
    created_at         DATETIME(6)    NOT NULL,
    updated_at         DATETIME(6)    NOT NULL,
    CONSTRAINT uk_refunds_number UNIQUE (refund_number),
    CONSTRAINT uk_refunds_provider UNIQUE (provider_refund_id),
    CONSTRAINT fk_refunds_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_refunds_return FOREIGN KEY (return_request_id) REFERENCES return_requests (id),
    CONSTRAINT fk_refunds_initiator FOREIGN KEY (initiated_by) REFERENCES users (id),
    CONSTRAINT ck_refunds_amount CHECK (amount > 0)
);
CREATE INDEX idx_refunds_status ON refunds (status, created_at);
CREATE INDEX idx_refunds_order ON refunds (order_id);
