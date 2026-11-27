-- =====================================================================
-- TimeCraft V103: Curated Women's Watches Collection (most under ₹50,000)
-- Preserves existing ₹1,00,000 - ₹5,00,000 luxury collector watches.
-- Compatible with both MySQL 8 and H2 MySQL-mode.
-- =====================================================================

INSERT INTO products (brand_id, category_id, name, slug, model_name, sku, short_description, description, movement_type, case_diameter_mm, case_material, water_resistance_m, crystal_type, strap_material, dial_color, case_color, gender, warranty_months, regular_price, selling_price, discount_percent, status, featured, new_arrival, best_seller, sold_count, created_at, updated_at) VALUES
((SELECT id FROM brands WHERE slug='titan'), (SELECT id FROM categories WHERE slug='dress'), 'Raga Viva Rose Gold 32', 'titan-raga-viva-rose-gold-32', 'TR-2652', 'TIT-RAG32', 'Sculpted 32 mm rose-gold dress watch with a champagne sunray dial.', 'Crafted for effortless grace from day to evening, the Titan Raga Viva pairs a slender 32 mm rose-gold-plated case with faceted indices and a brushed jewellery bracelet.', 'QUARTZ', 32.00, 'Stainless Steel', 30, 'Mineral Crystal', 'Metal Bracelet', 'Champagne', 'Rose Gold', 'WOMEN', 24, 16995.00, 14445.00, 15.00, 'ACTIVE', TRUE, TRUE, TRUE, 56, TIMESTAMPADD(DAY, -6, CURRENT_TIMESTAMP(6)), CURRENT_TIMESTAMP(6)),
((SELECT id FROM brands WHERE slug='fastrack'), (SELECT id FROM categories WHERE slug='everyday'), 'Glitz Petite Champagne 34', 'fastrack-glitz-petite-champagne-34', 'FT-6290', 'FAS-GLZ34', 'Everyday 34 mm bracelet watch with subtle gold-tone markers.', 'Lightweight and contemporary, the Fastrack Glitz Petite frames a soft ivory-champagne dial with slender hands and an easy-clasp steel bracelet.', 'QUARTZ', 34.00, 'Stainless Steel', 30, 'Mineral Crystal', 'Steel Bracelet', 'Champagne', 'Gold', 'WOMEN', 24, 5495.00, 4395.00, 20.02, 'ACTIVE', FALSE, TRUE, FALSE, 49, TIMESTAMPADD(DAY, -4, CURRENT_TIMESTAMP(6)), CURRENT_TIMESTAMP(6)),
((SELECT id FROM brands WHERE slug='nordhavn'), (SELECT id FROM categories WHERE slug='dress'), 'Fjordline Petite Mesh 32', 'nordhavn-fjordline-petite-mesh-32', 'NF-32', 'NOR-FL32', 'Ultra-slim 32 mm Scandinavian dress watch on Milanese steel mesh.', 'Clean Nordic minimalism proportioned for slender wrists: a 6.4 mm slim profile, sapphire crystal and warm silvered dial.', 'QUARTZ', 32.00, 'Stainless Steel', 50, 'Sapphire', 'Mesh Steel', 'Silver', 'Silver', 'WOMEN', 24, 29500.00, 24900.00, 15.59, 'ACTIVE', TRUE, TRUE, FALSE, 28, TIMESTAMPADD(DAY, -5, CURRENT_TIMESTAMP(6)), CURRENT_TIMESTAMP(6)),
((SELECT id FROM brands WHERE slug='sora-tokei'), (SELECT id FROM categories WHERE slug='everyday'), 'Sakura Blossom Automatic 34', 'sora-tokei-sakura-blossom-34', 'SB-34', 'SOR-SAK34', '34 mm Japanese automatic with a blush sakura-textured dial.', 'Powered by a compact self-winding Nagano calibre with 60 hours of reserve, featuring a soft blush sakura-petal dial and sapphire case back.', 'AUTOMATIC', 34.00, 'Stainless Steel', 100, 'Sapphire', 'Leather', 'Blush Pink', 'Steel', 'WOMEN', 24, 48000.00, 42900.00, 10.63, 'ACTIVE', TRUE, TRUE, TRUE, 33, TIMESTAMPADD(DAY, -2, CURRENT_TIMESTAMP(6)), CURRENT_TIMESTAMP(6));

INSERT INTO product_variants (product_id, sku, name, dial_color, strap_material, display_order, active, deleted, created_at, updated_at)
SELECT id, CONCAT(sku, '-STD'), CONCAT(dial_color, ' dial / ', strap_material), dial_color, strap_material, 0, TRUE, FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM products WHERE sku IN ('TIT-RAG32', 'FAS-GLZ34', 'NOR-FL32', 'SOR-SAK34');

INSERT INTO inventory (variant_id, quantity_on_hand, quantity_reserved, low_stock_threshold, updated_at)
SELECT pv.id, 16, 0, 4, CURRENT_TIMESTAMP(6)
FROM product_variants pv
LEFT JOIN inventory inv ON inv.variant_id = pv.id
WHERE inv.id IS NULL;

INSERT INTO inventory_transactions (variant_id, transaction_type, quantity_change, on_hand_after, reserved_after, reference_type, note, created_at)
SELECT pv.id, 'STOCK_IN', 16, 16, 0, 'SEED', 'Opening stock (Women''s Collection)', CURRENT_TIMESTAMP(6)
FROM product_variants pv
WHERE pv.sku LIKE 'TIT-RAG32%' OR pv.sku LIKE 'FAS-GLZ34%' OR pv.sku LIKE 'NOR-FL32%' OR pv.sku LIKE 'SOR-SAK34%';

INSERT INTO product_images (product_id, url, alt_text, display_order, is_primary, created_at) VALUES
((SELECT id FROM products WHERE sku='TIT-RAG32'), 'https://images.unsplash.com/photo-1524592094714-0f0654e20314?w=1200&q=80', 'Titan Raga Viva Rose Gold 32', 0, TRUE, CURRENT_TIMESTAMP(6)),
((SELECT id FROM products WHERE sku='FAS-GLZ34'), 'https://images.unsplash.com/photo-1508685096489-7aacd43bd3b1?w=1200&q=80', 'Fastrack Glitz Petite Champagne 34', 0, TRUE, CURRENT_TIMESTAMP(6)),
((SELECT id FROM products WHERE sku='NOR-FL32'), 'https://images.unsplash.com/photo-1594534475808-b18fc33b045e?w=1200&q=80', 'Nordhavn Fjordline Petite Mesh 32', 0, TRUE, CURRENT_TIMESTAMP(6)),
((SELECT id FROM products WHERE sku='SOR-SAK34'), 'https://images.unsplash.com/photo-1495856458515-0637185db551?w=1200&q=80', 'Sora Tokei Sakura Blossom Automatic 34', 0, TRUE, CURRENT_TIMESTAMP(6));

INSERT INTO product_attributes (product_id, attr_name, attr_value, display_order)
SELECT id, 'Collection', 'TimeCraft Women''s Edit', 1
FROM products WHERE sku IN ('TIT-RAG32', 'FAS-GLZ34', 'NOR-FL32', 'SOR-SAK34');
