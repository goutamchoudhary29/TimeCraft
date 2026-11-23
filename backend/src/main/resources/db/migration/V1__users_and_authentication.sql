-- =====================================================================
-- TimeCraft V1: users, roles and authentication tokens
-- Only two roles exist in the system: CUSTOMER and ADMIN.
-- =====================================================================

CREATE TABLE roles (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(30)  NOT NULL,
    description VARCHAR(255),
    CONSTRAINT uk_roles_name UNIQUE (name),
    CONSTRAINT ck_roles_name CHECK (name IN ('CUSTOMER', 'ADMIN'))
);

CREATE TABLE users (
    id                    BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email                 VARCHAR(255) NOT NULL,
    password_hash         VARCHAR(100) NOT NULL,
    first_name            VARCHAR(80)  NOT NULL,
    last_name             VARCHAR(80)  NOT NULL,
    phone                 VARCHAR(20),
    email_verified        BOOLEAN      NOT NULL DEFAULT FALSE,
    account_status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          DATETIME(6),
    last_login_at         DATETIME(6),
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_status CHECK (account_status IN ('ACTIVE', 'DEACTIVATED'))
);
CREATE INDEX idx_users_created_at ON users (created_at);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

-- Refresh tokens are stored as SHA-256 hashes only. family_id groups rotated tokens
-- so that re-use of a revoked token revokes the whole family.
CREATE TABLE refresh_tokens (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    token_hash       CHAR(64)     NOT NULL,
    family_id        VARCHAR(36)  NOT NULL,
    expires_at       DATETIME(6)  NOT NULL,
    revoked_at       DATETIME(6),
    replaced_by_hash CHAR(64),
    created_ip       VARCHAR(45),
    user_agent       VARCHAR(255),
    created_at       DATETIME(6)  NOT NULL,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);

CREATE TABLE email_verification_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    token_hash CHAR(64)    NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_email_verification_hash UNIQUE (token_hash),
    CONSTRAINT fk_email_verification_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_email_verification_user ON email_verification_tokens (user_id);

CREATE TABLE password_reset_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    token_hash CHAR(64)    NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_password_reset_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_password_reset_user ON password_reset_tokens (user_id);

INSERT INTO roles (name, description) VALUES ('CUSTOMER', 'Shopper using the storefront');
INSERT INTO roles (name, description) VALUES ('ADMIN', 'Store administrator with full business access');
