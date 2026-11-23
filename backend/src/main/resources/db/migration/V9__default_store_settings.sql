-- =====================================================================
-- TimeCraft V9: default store configuration (editable from the admin dashboard)
-- =====================================================================
INSERT INTO store_settings (setting_key, setting_value, value_type, setting_group, description, updated_at) VALUES
('store.name', 'TimeCraft', 'STRING', 'STORE', 'Store display name', CURRENT_TIMESTAMP(6)),
('store.support_email', 'support@timecraft.example', 'STRING', 'STORE', 'Customer support e-mail shown on the website', CURRENT_TIMESTAMP(6)),
('store.support_phone', '+91 80 4000 1234', 'STRING', 'STORE', 'Customer support phone number', CURRENT_TIMESTAMP(6)),
('store.currency', 'INR', 'STRING', 'STORE', 'ISO currency code used for all prices', CURRENT_TIMESTAMP(6)),
('tax.rate_percent', '18.00', 'DECIMAL', 'TAX', 'GST rate applied to watches (percent)', CURRENT_TIMESTAMP(6)),
('tax.inclusive', 'true', 'BOOLEAN', 'TAX', 'true = selling prices already include GST; false = GST added at checkout', CURRENT_TIMESTAMP(6)),
('tax.label', 'GST', 'STRING', 'TAX', 'Tax label shown to customers', CURRENT_TIMESTAMP(6)),
('shipping.flat_rate', '250.00', 'DECIMAL', 'SHIPPING', 'Flat shipping charge per order', CURRENT_TIMESTAMP(6)),
('shipping.free_threshold', '10000.00', 'DECIMAL', 'SHIPPING', 'Orders at or above this amount (after discount) ship free', CURRENT_TIMESTAMP(6)),
('shipping.international_enabled', 'false', 'BOOLEAN', 'SHIPPING', 'Allow delivery addresses outside India', CURRENT_TIMESTAMP(6)),
('checkout.reservation_minutes', '15', 'INTEGER', 'CHECKOUT', 'Minutes stock stays reserved while awaiting payment', CURRENT_TIMESTAMP(6)),
('checkout.max_quantity_per_item', '5', 'INTEGER', 'CHECKOUT', 'Maximum units of a single variant per order', CURRENT_TIMESTAMP(6)),
('returns.window_days', '7', 'INTEGER', 'RETURNS', 'Days after delivery during which returns are accepted', CURRENT_TIMESTAMP(6)),
('inventory.low_stock_threshold', '5', 'INTEGER', 'INVENTORY', 'Default low-stock alert threshold for new variants', CURRENT_TIMESTAMP(6)),
('content.announcement', 'Complimentary insured shipping on orders above ₹10,000 · 7-day returns', 'STRING', 'CONTENT', 'Announcement bar text', CURRENT_TIMESTAMP(6)),
('content.hero_eyebrow', 'The Autumn Edit', 'STRING', 'CONTENT', 'Small text above the hero title', CURRENT_TIMESTAMP(6)),
('content.hero_title', 'Time, crafted with intention.', 'STRING', 'CONTENT', 'Homepage hero title', CURRENT_TIMESTAMP(6)),
('content.hero_subtitle', 'Mechanical icons, refined dress pieces and tool watches built to outlast trends. Curated from independent and heritage maisons.', 'STRING', 'CONTENT', 'Homepage hero subtitle', CURRENT_TIMESTAMP(6)),
('content.hero_image', 'https://images.unsplash.com/photo-1547996160-81dfa63595aa?w=1600&q=80', 'STRING', 'CONTENT', 'Homepage hero image URL', CURRENT_TIMESTAMP(6)),
('content.promo_title', 'The Diver Season', 'STRING', 'CONTENT', 'Promotional banner title', CURRENT_TIMESTAMP(6)),
('content.promo_text', 'Up to 20% off selected dive watches, plus an extra 15% on orders above ₹50,000 with code SEASON15.', 'STRING', 'CONTENT', 'Promotional banner text', CURRENT_TIMESTAMP(6)),
('content.brand_story', 'TimeCraft began with a simple belief: a watch should be chosen as carefully as it was made. Every piece we offer is inspected by our horologists, delivered fully insured, and backed by an authorised warranty.', 'STRING', 'CONTENT', 'Brand story paragraph on the homepage', CURRENT_TIMESTAMP(6));
