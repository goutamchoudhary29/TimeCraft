-- V16: Dynamic Delivery Estimation (Warehouses, PIN Code Rules, Holidays, Product Overrides & Global Settings)

CREATE TABLE delivery_warehouses (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    code              VARCHAR(40)  NOT NULL UNIQUE,
    name              VARCHAR(120) NOT NULL,
    city              VARCHAR(80)  NOT NULL,
    state             VARCHAR(80)  NOT NULL,
    pin_code          VARCHAR(12)  NOT NULL,
    pin_prefix_local  VARCHAR(160) NULL,
    pin_prefix_state  VARCHAR(255) NULL,
    default_warehouse BOOLEAN      NOT NULL DEFAULT FALSE,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE delivery_pin_rules (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    pin_pattern       VARCHAR(20)  NOT NULL UNIQUE,
    zone_type         VARCHAR(24)  NOT NULL,
    courier_name      VARCHAR(80)  NULL,
    serviceable       BOOLEAN      NOT NULL DEFAULT TRUE,
    min_days_override INT          NULL,
    max_days_override INT          NULL,
    notes             VARCHAR(255) NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE delivery_holidays (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    holiday_date DATE         NOT NULL UNIQUE,
    name         VARCHAR(120) NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE product_delivery_configs (
    product_id                 BIGINT      PRIMARY KEY,
    warehouse_id               BIGINT      NULL,
    fulfillment_type           VARCHAR(24) NOT NULL DEFAULT 'IN_STOCK',
    processing_days_min        INT         NULL,
    processing_days_max        INT         NULL,
    shipping_days_min_override INT         NULL,
    shipping_days_max_override INT         NULL,
    preorder_release_date      DATE        NULL,
    special_order_note         VARCHAR(255) NULL,
    updated_at                 TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pdc_product   FOREIGN KEY (product_id)   REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_pdc_warehouse FOREIGN KEY (warehouse_id) REFERENCES delivery_warehouses(id) ON DELETE SET NULL
);

-- Seed Primary Indian Horology Vaults / Warehouses
INSERT INTO delivery_warehouses (code, name, city, state, pin_code, pin_prefix_local, pin_prefix_state, default_warehouse, active)
VALUES
    ('BLR-VAULT',   'Bengaluru Horology Vault', 'Bengaluru', 'Karnataka',   '560001', '560,561,562', '56,57,58,59', TRUE,  TRUE),
    ('MUM-ATELIER', 'Mumbai Heritage Atelier',  'Mumbai',    'Maharashtra', '400001', '400,401',     '40,41,42,43,44', FALSE, TRUE);

-- Seed Sample PIN Code Zone & Courier Rules
INSERT INTO delivery_pin_rules (pin_pattern, zone_type, courier_name, serviceable, min_days_override, max_days_override, notes)
VALUES
    ('560001', 'LOCAL',         'BlueDart Armoured Vault',  TRUE,  1, 3,  'Bengaluru Central Vault Express'),
    ('400001', 'OTHER_STATE',   'Sequel Insured Logistics', TRUE,  2, 4,  'Mumbai Priority Air Hub'),
    ('110001', 'OTHER_STATE',   'BlueDart Armoured Vault',  TRUE,  3, 5,  'New Delhi Capital Air Corridor'),
    ('744*',   'REMOTE',        'India Post Insured Air',   TRUE,  6, 10, 'Andaman & Nicobar Island Zone'),
    ('194*',   'REMOTE',        'BlueDart Insured Air',     TRUE,  5, 10, 'Ladakh High-Altitude Zone'),
    ('79*',    'REMOTE',        'BlueDart Insured Air',     TRUE,  5, 9,  'North-East Remote Corridor'),
    ('999999', 'UNSERVICEABLE', NULL,                       FALSE, NULL, NULL, 'Non-serviceable test PIN code');

-- Seed Public Holidays
INSERT INTO delivery_holidays (holiday_date, name)
VALUES
    ('2026-01-26', 'Republic Day'),
    ('2026-08-15', 'Independence Day'),
    ('2026-10-02', 'Gandhi Jayanti'),
    ('2026-12-25', 'Christmas Day'),
    ('2027-01-26', 'Republic Day');

-- Seed Global Configurable Delivery Rules in store_settings
INSERT INTO store_settings (setting_key, setting_value, value_type, setting_group, description, updated_at)
VALUES
    ('delivery.processing_days_min',    '1',                       'INTEGER', 'DELIVERY', 'Minimum business days for bench inspection & dispatch preparation', CURRENT_TIMESTAMP(6)),
    ('delivery.processing_days_max',    '2',                       'INTEGER', 'DELIVERY', 'Maximum business days for bench inspection & dispatch preparation', CURRENT_TIMESTAMP(6)),
    ('delivery.local_min_days',         '1',                       'INTEGER', 'DELIVERY', 'Minimum transit business days for local city delivery', CURRENT_TIMESTAMP(6)),
    ('delivery.local_max_days',         '3',                       'INTEGER', 'DELIVERY', 'Maximum transit business days for local city delivery', CURRENT_TIMESTAMP(6)),
    ('delivery.same_state_min_days',    '2',                       'INTEGER', 'DELIVERY', 'Minimum transit business days for same-state delivery', CURRENT_TIMESTAMP(6)),
    ('delivery.same_state_max_days',    '5',                       'INTEGER', 'DELIVERY', 'Maximum transit business days for same-state delivery', CURRENT_TIMESTAMP(6)),
    ('delivery.other_state_min_days',   '3',                       'INTEGER', 'DELIVERY', 'Minimum transit business days for interstate delivery', CURRENT_TIMESTAMP(6)),
    ('delivery.other_state_max_days',   '7',                       'INTEGER', 'DELIVERY', 'Maximum transit business days for interstate delivery', CURRENT_TIMESTAMP(6)),
    ('delivery.remote_min_days',        '5',                       'INTEGER', 'DELIVERY', 'Minimum transit business days for remote locations', CURRENT_TIMESTAMP(6)),
    ('delivery.remote_max_days',        '10',                      'INTEGER', 'DELIVERY', 'Maximum transit business days for remote locations', CURRENT_TIMESTAMP(6)),
    ('delivery.special_order_min_days', '10',                      'INTEGER', 'DELIVERY', 'Minimum business days for made-to-order / special-order watches', CURRENT_TIMESTAMP(6)),
    ('delivery.special_order_max_days', '18',                      'INTEGER', 'DELIVERY', 'Maximum business days for made-to-order / special-order watches', CURRENT_TIMESTAMP(6)),
    ('delivery.default_courier',        'BlueDart Armoured Vault', 'STRING',  'DELIVERY', 'Default insured horological courier partner', CURRENT_TIMESTAMP(6)),
    ('delivery.exclude_weekends',       'true',                    'BOOLEAN', 'DELIVERY', 'Exclude Saturdays and Sundays from delivery business-day estimates', CURRENT_TIMESTAMP(6)),
    ('delivery.cutoff_hour_ist',        '16',                      'INTEGER', 'DELIVERY', 'Daily IST dispatch cutoff hour (24h format, e.g. 16 = 4:00 PM IST)', CURRENT_TIMESTAMP(6));
