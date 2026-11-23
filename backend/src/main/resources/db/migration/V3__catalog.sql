-- =====================================================================
-- TimeCraft V3: catalog (brands, categories, products, variants, images, attributes)
-- Images are stored in Cloudinary; only URL + public_id are persisted.
-- =====================================================================

CREATE TABLE brands (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name              VARCHAR(100) NOT NULL,
    slug              VARCHAR(120) NOT NULL,
    description       TEXT,
    country_of_origin VARCHAR(80),
    founded_year      INT,
    logo_url          VARCHAR(500),
    logo_public_id    VARCHAR(255),
    banner_url        VARCHAR(500),
    featured          BOOLEAN      NOT NULL DEFAULT FALSE,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    CONSTRAINT uk_brands_name UNIQUE (name),
    CONSTRAINT uk_brands_slug UNIQUE (slug)
);

CREATE TABLE categories (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    parent_id     BIGINT,
    name          VARCHAR(100) NOT NULL,
    slug          VARCHAR(120) NOT NULL,
    description   TEXT,
    image_url     VARCHAR(500),
    display_order INT          NOT NULL DEFAULT 0,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uk_categories_slug UNIQUE (slug),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id)
);

CREATE TABLE products (
    id                 BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    brand_id           BIGINT        NOT NULL,
    category_id        BIGINT        NOT NULL,
    name               VARCHAR(160)  NOT NULL,
    slug               VARCHAR(180)  NOT NULL,
    model_name         VARCHAR(120)  NOT NULL,
    sku                VARCHAR(64)   NOT NULL,
    short_description  VARCHAR(300),
    description        TEXT,
    movement_type      VARCHAR(30)   NOT NULL,
    case_diameter_mm   DECIMAL(5, 2) NOT NULL,
    case_material      VARCHAR(60)   NOT NULL,
    water_resistance_m INT           NOT NULL DEFAULT 0,
    crystal_type       VARCHAR(60)   NOT NULL,
    strap_material     VARCHAR(60)   NOT NULL,
    dial_color         VARCHAR(40)   NOT NULL,
    case_color         VARCHAR(40),
    gender             VARCHAR(20)   NOT NULL DEFAULT 'UNISEX',
    warranty_months    INT           NOT NULL DEFAULT 24,
    regular_price      DECIMAL(12, 2) NOT NULL,
    selling_price      DECIMAL(12, 2) NOT NULL,
    discount_percent   DECIMAL(5, 2) NOT NULL DEFAULT 0,
    status             VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    featured           BOOLEAN       NOT NULL DEFAULT FALSE,
    new_arrival        BOOLEAN       NOT NULL DEFAULT FALSE,
    best_seller        BOOLEAN       NOT NULL DEFAULT FALSE,
    avg_rating         DECIMAL(3, 2) NOT NULL DEFAULT 0,
    review_count       INT           NOT NULL DEFAULT 0,
    sold_count         INT           NOT NULL DEFAULT 0,
    deleted            BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_products_slug UNIQUE (slug),
    CONSTRAINT uk_products_sku UNIQUE (sku),
    CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES brands (id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT ck_products_prices CHECK (regular_price >= 0 AND selling_price >= 0 AND selling_price <= regular_price),
    CONSTRAINT ck_products_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED'))
);
CREATE INDEX idx_products_brand ON products (brand_id);
CREATE INDEX idx_products_category ON products (category_id);
CREATE INDEX idx_products_status_deleted ON products (status, deleted);
CREATE INDEX idx_products_selling_price ON products (selling_price);
CREATE INDEX idx_products_movement ON products (movement_type);
CREATE INDEX idx_products_created_at ON products (created_at);
CREATE INDEX idx_products_sold_count ON products (sold_count);
CREATE INDEX idx_products_avg_rating ON products (avg_rating);
CREATE INDEX idx_products_discount ON products (discount_percent);
CREATE INDEX idx_products_name ON products (name);

-- Each product has one or more purchasable variants; inventory lives at variant level.
CREATE TABLE product_variants (
    id                     BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id             BIGINT         NOT NULL,
    sku                    VARCHAR(64)    NOT NULL,
    name                   VARCHAR(120)   NOT NULL,
    dial_color             VARCHAR(40),
    strap_material         VARCHAR(60),
    strap_color            VARCHAR(40),
    regular_price_override DECIMAL(12, 2),
    selling_price_override DECIMAL(12, 2),
    display_order          INT            NOT NULL DEFAULT 0,
    active                 BOOLEAN        NOT NULL DEFAULT TRUE,
    deleted                BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at             DATETIME(6)    NOT NULL,
    updated_at             DATETIME(6)    NOT NULL,
    CONSTRAINT uk_product_variants_sku UNIQUE (sku),
    CONSTRAINT fk_product_variants_product FOREIGN KEY (product_id) REFERENCES products (id)
);
CREATE INDEX idx_product_variants_product ON product_variants (product_id);

CREATE TABLE product_images (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id    BIGINT       NOT NULL,
    variant_id    BIGINT,
    url           VARCHAR(500) NOT NULL,
    public_id     VARCHAR(255),
    alt_text      VARCHAR(200),
    display_order INT          NOT NULL DEFAULT 0,
    is_primary    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_images_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE SET NULL
);
CREATE INDEX idx_product_images_product ON product_images (product_id, display_order);

CREATE TABLE product_attributes (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id    BIGINT       NOT NULL,
    attr_name     VARCHAR(80)  NOT NULL,
    attr_value    VARCHAR(255) NOT NULL,
    display_order INT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_product_attributes_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);
CREATE INDEX idx_product_attributes_product ON product_attributes (product_id);
