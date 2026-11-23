-- =====================================================================
-- TimeCraft V2: customer profiles and addresses
-- =====================================================================

CREATE TABLE customer_profiles (
    id               BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT      NOT NULL,
    date_of_birth    DATE,
    gender           VARCHAR(20),
    marketing_opt_in BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    CONSTRAINT uk_customer_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_customer_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Orders keep an address snapshot, so addresses can be hard-deleted safely.
CREATE TABLE customer_addresses (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    full_name    VARCHAR(120) NOT NULL,
    phone        VARCHAR(20)  NOT NULL,
    line1        VARCHAR(255) NOT NULL,
    line2        VARCHAR(255),
    landmark     VARCHAR(120),
    city         VARCHAR(100) NOT NULL,
    state        VARCHAR(100) NOT NULL,
    postal_code  VARCHAR(12)  NOT NULL,
    country      CHAR(2)      NOT NULL DEFAULT 'IN',
    address_type VARCHAR(10)  NOT NULL DEFAULT 'HOME',
    is_default   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    CONSTRAINT fk_customer_addresses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_customer_addresses_type CHECK (address_type IN ('HOME', 'WORK', 'OTHER'))
);
CREATE INDEX idx_customer_addresses_user ON customer_addresses (user_id);
