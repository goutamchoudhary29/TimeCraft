-- =====================================================================
-- TimeCraft V4: inventory (per variant), transactions and reservations
-- available = quantity_on_hand - quantity_reserved ; neither may go negative.
-- =====================================================================

CREATE TABLE inventory (
    id                  BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    variant_id          BIGINT      NOT NULL,
    quantity_on_hand    INT         NOT NULL DEFAULT 0,
    quantity_reserved   INT         NOT NULL DEFAULT 0,
    low_stock_threshold INT         NOT NULL DEFAULT 5,
    updated_at          DATETIME(6) NOT NULL,
    version             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_inventory_variant UNIQUE (variant_id),
    CONSTRAINT fk_inventory_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    CONSTRAINT ck_inventory_non_negative CHECK (quantity_on_hand >= 0 AND quantity_reserved >= 0 AND quantity_reserved <= quantity_on_hand)
);

CREATE TABLE inventory_transactions (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    variant_id        BIGINT       NOT NULL,
    transaction_type  VARCHAR(30)  NOT NULL,
    quantity_change   INT          NOT NULL,
    on_hand_after     INT          NOT NULL,
    reserved_after    INT          NOT NULL,
    reference_type    VARCHAR(30),
    reference_id      BIGINT,
    note              VARCHAR(255),
    performed_by      BIGINT,
    created_at        DATETIME(6)  NOT NULL,
    CONSTRAINT fk_inventory_tx_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    CONSTRAINT fk_inventory_tx_user FOREIGN KEY (performed_by) REFERENCES users (id)
);
CREATE INDEX idx_inventory_tx_variant ON inventory_transactions (variant_id, created_at);
CREATE INDEX idx_inventory_tx_reference ON inventory_transactions (reference_type, reference_id);
