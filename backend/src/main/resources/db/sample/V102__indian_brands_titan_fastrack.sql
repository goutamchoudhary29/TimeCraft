-- =====================================================================
-- TimeCraft V102: Indian watch brands (Titan & Fastrack) + watches,
-- variants, opening inventory, images and extended specifications.
-- =====================================================================

INSERT INTO brands (name, slug, description, country_of_origin, founded_year, logo_url, banner_url, featured, active, deleted, created_at, updated_at) VALUES
('Titan', 'titan', 'India''s pioneering horological house since 1984 — renowned for ultra-slim Edge movements, open-heart automatics and timeless dress pieces crafted in Hosur and Bengaluru.', 'India', 1984, '/images/brands/titan.svg', 'https://images.unsplash.com/photo-1522335789203-aabd1fc54bc9?w=1600&q=80', TRUE, TRUE, FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
('Fastrack', 'fastrack', 'Bold, contemporary Indian sport and street-ready timepieces built for active everyday wear.', 'India', 1998, '/images/brands/fastrack.svg', 'https://images.unsplash.com/photo-1524805444758-089113d48a6d?w=1600&q=80', TRUE, TRUE, FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));

INSERT INTO products (brand_id, category_id, name, slug, model_name, sku, short_description, description, movement_type, case_diameter_mm, case_material, water_resistance_m, crystal_type, strap_material, dial_color, case_color, gender, warranty_months, regular_price, selling_price, discount_percent, status, featured, new_arrival, best_seller, sold_count, created_at, updated_at) VALUES
((SELECT id FROM brands WHERE slug='titan'), (SELECT id FROM categories WHERE slug='dress'), 'Edge Ceramic Ultra-Slim 40', 'titan-edge-ceramic-ultra-slim-40', 'TE-9011', 'TIT-EDG40', 'One of the world''s slimmest ceramic dress watches with an in-house wafer-thin movement.', 'Engineered in India, the Titan Edge Ceramic measures just 4.4 mm thin. High-density scratch-resistant ceramic case and bracelet, sapphire crystal and the wafer-thin T-901 calibre.', 'QUARTZ', 40.00, 'High-Tech Ceramic', 30, 'Sapphire', 'Ceramic Bracelet', 'Anthracite Black', 'Black', 'MEN', 24, 34995.00, 29995.00, 14.29, 'ACTIVE', TRUE, FALSE, TRUE, 78, TIMESTAMPADD(DAY, -60, CURRENT_TIMESTAMP(6)), CURRENT_TIMESTAMP(6)),
((SELECT id FROM brands WHERE slug='titan'), (SELECT id FROM categories WHERE slug='dress'), 'Stellar Open-Heart Automatic 42', 'titan-stellar-open-heart-automatic-42', 'TS-1840', 'TIT-STL42', 'Celestial open-heart automatic with a sun-moon 24-hour indicator and sapphire crystal.', 'Inspired by astronomical instruments, the Titan Stellar Automatic showcases a 24-jewel self-winding movement through an open balance window at nine o''clock and an exhibition case back.', 'AUTOMATIC', 42.00, 'Stainless Steel', 50, 'Sapphire', 'Leather', 'Midnight Blue', 'Rose Gold', 'MEN', 24, 24995.00, 19995.00, 20.00, 'ACTIVE', TRUE, TRUE, TRUE, 94, TIMESTAMPADD(DAY, -7, CURRENT_TIMESTAMP(6)), CURRENT_TIMESTAMP(6)),
((SELECT id FROM brands WHERE slug='titan'), (SELECT id FROM categories WHERE slug='chronograph'), 'Maritime Regatta Chronograph 43', 'titan-maritime-regatta-chronograph-43', 'TM-1792', 'TIT-MAR43', 'Nautical chronograph with compass markings, rose-gold bezel and 100 m water resistance.', 'Paying tribute to historic sea voyages, the Maritime Regatta Chronograph pairs a compass-engraved bezel with a deep emerald dial, tachymeter scale and stitched leather strap.', 'CHRONOGRAPH', 43.00, 'Stainless Steel', 100, 'Mineral Hardened', 'Leather', 'Sea Green', 'Steel', 'MEN', 24, 17995.00, 15295.00, 15.00, 'ACTIVE', FALSE, FALSE, TRUE, 52, TIMESTAMPADD(DAY, -95, CURRENT_TIMESTAMP(6)), CURRENT_TIMESTAMP(6)),
((SELECT id FROM brands WHERE slug='titan'), (SELECT id FROM categories WHERE slug='dress'), 'Raga Memoirs Mother-of-Pearl 30', 'titan-raga-memoirs-mop-30', 'TR-2608', 'TIT-RAG30', 'Sculpted rose-gold bracelet watch with a natural mother-of-pearl dial and crystal indices.', 'A signature piece from Titan''s Raga atelier: a iridescent mother-of-pearl dial set in a warm rose-gold case with a jewellery-clasp bracelet.', 'QUARTZ', 30.00, 'Brass / Rose-Gold Plated', 30, 'Mineral Crystal', 'Metal Bracelet', 'Mother of Pearl', 'Rose Gold', 'WOMEN', 24, 12995.00, 11045.00, 15.01, 'ACTIVE', FALSE, TRUE, FALSE, 41, TIMESTAMPADD(DAY, -10, CURRENT_TIMESTAMP(6)), CURRENT_TIMESTAMP(6)),
((SELECT id FROM brands WHERE slug='fastrack'), (SELECT id FROM categories WHERE slug='chronograph'), 'Stunners Sport Chrono 42', 'fastrack-stunners-sport-chrono-42', 'FT-3287', 'FAS-STN42', 'Multi-function sport watch with a bold charcoal dial and knurled bezel.', 'Designed for everyday impact, the Fastrack Stunners Sport Chrono features day-date-24h sub-dials, high-contrast lume hands and an integrated matte steel bracelet.', 'QUARTZ', 42.00, 'Stainless Steel', 50, 'Mineral Crystal', 'Steel Bracelet', 'Charcoal', 'Gunmetal', 'MEN', 24, 5995.00, 4795.00, 20.02, 'ACTIVE', TRUE, FALSE, TRUE, 112, TIMESTAMPADD(DAY, -45, CURRENT_TIMESTAMP(6)), CURRENT_TIMESTAMP(6)),
((SELECT id FROM brands WHERE slug='fastrack'), (SELECT id FROM categories WHERE slug='pilot-field'), 'All Nighters Tactical Field 41', 'fastrack-all-nighters-tactical-field-41', 'FT-3291', 'FAS-NGT41', 'Luminous field watch with an olive dial and quick-release silicone strap.', 'Built for late nights and weekend rides, the All Nighters Tactical Field combines oversized Arabic numerals with a sweat-proof ribbed silicone strap and 50 m water resistance.', 'QUARTZ', 41.00, 'IP Black Steel', 50, 'Mineral Crystal', 'Rubber', 'Olive Green', 'Black', 'UNISEX', 24, 4495.00, 3595.00, 20.02, 'ACTIVE', FALSE, TRUE, FALSE, 68, TIMESTAMPADD(DAY, -4, CURRENT_TIMESTAMP(6)), CURRENT_TIMESTAMP(6)),
((SELECT id FROM brands WHERE slug='fastrack'), (SELECT id FROM categories WHERE slug='everyday'), 'Tick Tock Minimal Mesh 36', 'fastrack-tick-tock-minimal-mesh-36', 'FT-6245', 'FAS-TCK36', 'Clean sunray rose dial on a rose-gold Milanese mesh strap.', 'Effortless daily styling from Fastrack: a slim 36 mm case with baton markers and a breathable stainless-steel Milanese mesh strap.', 'QUARTZ', 36.00, 'Stainless Steel', 30, 'Mineral Crystal', 'Mesh Steel', 'Rose Gold', 'Rose Gold', 'WOMEN', 24, 3995.00, 3395.00, 15.02, 'ACTIVE', FALSE, TRUE, TRUE, 83, TIMESTAMPADD(DAY, -14, CURRENT_TIMESTAMP(6)), CURRENT_TIMESTAMP(6));

-- Standard variants for Titan & Fastrack watches
INSERT INTO product_variants (product_id, sku, name, dial_color, strap_material, display_order, active, deleted, created_at, updated_at)
SELECT id, CONCAT(sku, '-STD'), CONCAT(dial_color, ' dial / ', strap_material), dial_color, strap_material, 0, TRUE, FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM products WHERE sku IN ('TIT-EDG40','TIT-STL42','TIT-MAR43','TIT-RAG30','FAS-STN42','FAS-NGT41','FAS-TCK36');

-- Additional colourway variants
INSERT INTO product_variants (product_id, sku, name, dial_color, strap_material, strap_color, regular_price_override, selling_price_override, display_order, active, deleted, created_at, updated_at) VALUES
((SELECT id FROM products WHERE sku='TIT-STL42'), 'TIT-STL42-MESH', 'Midnight Blue dial / Rose-Gold Mesh', 'Midnight Blue', 'Mesh Steel', 'Rose Gold', 25995.00, 20995.00, 1, TRUE, FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
((SELECT id FROM products WHERE sku='FAS-STN42'), 'FAS-STN42-BLU', 'Electric Blue dial / Steel Bracelet', 'Blue', 'Steel Bracelet', 'Steel', NULL, NULL, 1, TRUE, FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));

-- Opening inventory and ledger entries for the new variants
INSERT INTO inventory (variant_id, quantity_on_hand, quantity_reserved, low_stock_threshold, updated_at)
SELECT pv.id, 18, 0, 5, CURRENT_TIMESTAMP(6)
FROM product_variants pv
LEFT JOIN inventory inv ON inv.variant_id = pv.id
WHERE inv.id IS NULL;

INSERT INTO inventory_transactions (variant_id, transaction_type, quantity_change, on_hand_after, reserved_after, reference_type, note, created_at)
SELECT pv.id, 'STOCK_IN', 18, 18, 0, 'SEED', 'Opening stock (Titan & Fastrack)', CURRENT_TIMESTAMP(6)
FROM product_variants pv
WHERE pv.sku LIKE 'TIT-%' OR pv.sku LIKE 'FAS-%';

-- Product images
INSERT INTO product_images (product_id, url, alt_text, display_order, is_primary, created_at) VALUES
((SELECT id FROM products WHERE sku='TIT-EDG40'), 'https://images.unsplash.com/photo-1522335789203-aabd1fc54bc9?w=1200&q=80', 'Titan Edge Ceramic Ultra-Slim 40', 0, TRUE, CURRENT_TIMESTAMP(6)),
((SELECT id FROM products WHERE sku='TIT-STL42'), 'https://images.unsplash.com/photo-1524805444758-089113d48a6d?w=1200&q=80', 'Titan Stellar Open-Heart Automatic 42', 0, TRUE, CURRENT_TIMESTAMP(6)),
((SELECT id FROM products WHERE sku='TIT-STL42'), 'https://images.unsplash.com/photo-1533139502658-0198f920d8e8?w=1200&q=80', 'Titan Stellar exhibition movement', 1, FALSE, CURRENT_TIMESTAMP(6)),
((SELECT id FROM products WHERE sku='TIT-MAR43'), 'https://images.unsplash.com/photo-1539874754764-5a96559165b0?w=1200&q=80', 'Titan Maritime Regatta Chronograph 43', 0, TRUE, CURRENT_TIMESTAMP(6)),
((SELECT id FROM products WHERE sku='TIT-RAG30'), 'https://images.unsplash.com/photo-1524592094714-0f0654e20314?w=1200&q=80', 'Titan Raga Memoirs Mother-of-Pearl 30', 0, TRUE, CURRENT_TIMESTAMP(6)),
((SELECT id FROM products WHERE sku='FAS-STN42'), 'https://images.unsplash.com/photo-1526045431048-f857369baa09?w=1200&q=80', 'Fastrack Stunners Sport Chrono 42', 0, TRUE, CURRENT_TIMESTAMP(6)),
((SELECT id FROM products WHERE sku='FAS-NGT41'), 'https://images.unsplash.com/photo-1542496658-e33a6d0d50f6?w=1200&q=80', 'Fastrack All Nighters Tactical Field 41', 0, TRUE, CURRENT_TIMESTAMP(6)),
((SELECT id FROM products WHERE sku='FAS-TCK36'), 'https://images.unsplash.com/photo-1508685096489-7aacd43bd3b1?w=1200&q=80', 'Fastrack Tick Tock Minimal Mesh 36', 0, TRUE, CURRENT_TIMESTAMP(6));

-- Extended specifications
INSERT INTO product_attributes (product_id, attr_name, attr_value, display_order)
SELECT id, 'Origin', 'Crafted in India (Hosur Horological Atelier)', 1
FROM products WHERE sku LIKE 'TIT-%' OR sku LIKE 'FAS-%';
