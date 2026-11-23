-- =====================================================================
-- TimeCraft V101: SVG monogram logos for all 6 luxury watch maisons
-- =====================================================================
UPDATE brands SET logo_url = '/images/brands/aurelian.svg' WHERE slug = 'aurelian' AND logo_url IS NULL;
UPDATE brands SET logo_url = '/images/brands/nordhavn.svg' WHERE slug = 'nordhavn' AND logo_url IS NULL;
UPDATE brands SET logo_url = '/images/brands/maison-verac.svg' WHERE slug = 'maison-verac' AND logo_url IS NULL;
UPDATE brands SET logo_url = '/images/brands/kestrel.svg' WHERE slug = 'kestrel' AND logo_url IS NULL;
UPDATE brands SET logo_url = '/images/brands/halden.svg' WHERE slug = 'halden' AND logo_url IS NULL;
UPDATE brands SET logo_url = '/images/brands/sora-tokei.svg' WHERE slug = 'sora-tokei' AND logo_url IS NULL;
