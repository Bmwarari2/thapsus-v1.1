-- ============================================================
-- 029_pricing_settings.sql — converge thapsus on the six-knob
-- pricing model already landed in swiftcargo-main as migration
-- 051. Same schema, same seed values, so an identical parcel +
-- identical settings produce identical totals in both repos.
--
-- Tables created
--   pricing_settings  — the four numeric tunables
--                       (base_shipping_per_kg, base_handling_fee,
--                        card_processing_pct, dim_divisor)
--   customs_tiers     — per-tier duty/VAT/IDF/RDL bands
--   hs_code_tiers     — HS-code prefix → tier (longest prefix wins)
--   electronics_fees  — per-item-type flat handling £
--
-- Existing data treatment
--   • `pricing_tiers` rows are flipped to is_active=FALSE — the new
--     model uses a single base £/kg, but the rows stay for history /
--     rollback. The QuoteEngine no longer reads them.
--   • `fees` is left as-is. The QuoteEngine no longer reads
--     `uk_handling` / `card_processing` from it — those now live in
--     pricing_settings — but the table itself stays so older operator
--     screens that show fee history keep working.
-- ============================================================

-- ── 1. pricing_settings (the four tunables) ─────────────────────────────────
CREATE TABLE IF NOT EXISTS pricing_settings (
  key         TEXT PRIMARY KEY,
  value       NUMERIC(12,4) NOT NULL,
  currency    VARCHAR(3),                      -- 'GBP' or NULL for % / unitless
  is_percent  BOOLEAN      NOT NULL DEFAULT FALSE,
  label       TEXT,
  notes       TEXT,
  updated_by  TEXT,                            -- users.id is TEXT in this repo
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO pricing_settings (key, value, currency, is_percent, label, notes)
SELECT 'base_shipping_per_kg', 8.0000, 'GBP', FALSE,
       'Base shipping (£ per kg)',
       'Multiplied by chargeable weight = max(actual_kg, vol_kg). Replaces the per-channel pricing_tiers ladder.'
WHERE NOT EXISTS (SELECT 1 FROM pricing_settings WHERE key = 'base_shipping_per_kg');

INSERT INTO pricing_settings (key, value, currency, is_percent, label, notes)
SELECT 'base_handling_fee', 3.0000, 'GBP', FALSE,
       'Base handling fee (flat £)',
       'Single flat handling fee per order. Replaces the per-channel fees.uk_handling.'
WHERE NOT EXISTS (SELECT 1 FROM pricing_settings WHERE key = 'base_handling_fee');

INSERT INTO pricing_settings (key, value, currency, is_percent, label, notes)
SELECT 'card_processing_pct', 0.0000, NULL, TRUE,
       'Card processing surcharge (%)',
       'Multiplied by the FULL subtotal (shipping + handling + special + customs_est). Stored as a fraction, e.g. 0.03 for 3%.'
WHERE NOT EXISTS (SELECT 1 FROM pricing_settings WHERE key = 'card_processing_pct');

INSERT INTO pricing_settings (key, value, currency, is_percent, label, notes)
SELECT 'dim_divisor', 5000.0000, NULL, FALSE,
       'Volumetric divisor',
       'vol_kg = (L × W × H) / divisor. 5000 reconciles with swiftcargo-main; 6000 was the previous IATA value used by VolumetricWeightCalculator.'
WHERE NOT EXISTS (SELECT 1 FROM pricing_settings WHERE key = 'dim_divisor');


-- ── 2. customs_tiers ────────────────────────────────────────────────────────
-- Mirrors the seed in swiftcargo-main/051. Sourced from KRA 2024 EAC CET
-- bands as documented in the swiftcargo HS_TIERS constant.
CREATE TABLE IF NOT EXISTS customs_tiers (
  tier_key    VARCHAR(50) PRIMARY KEY,
  label       TEXT        NOT NULL,
  duty_pct    NUMERIC(6,4) NOT NULL,
  vat_pct     NUMERIC(6,4) NOT NULL DEFAULT 0.16,
  idf_pct     NUMERIC(6,4) NOT NULL DEFAULT 0.035,
  rdl_pct     NUMERIC(6,4) NOT NULL DEFAULT 0.02,
  notes       TEXT,
  is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
  updated_by  TEXT,
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO customs_tiers (tier_key, label, duty_pct, vat_pct, notes)
SELECT 'general', 'General goods (default)', 0.2500, 0.16,
       'Default 25% duty band — most clothing, footwear, toys, household goods.'
WHERE NOT EXISTS (SELECT 1 FROM customs_tiers WHERE tier_key = 'general');

INSERT INTO customs_tiers (tier_key, label, duty_pct, vat_pct, notes)
SELECT 'electronics', 'Consumer electronics', 0.0000, 0.16,
       'Phones, laptops, screens — duty-free at 0% but 16% VAT still applies.'
WHERE NOT EXISTS (SELECT 1 FROM customs_tiers WHERE tier_key = 'electronics');

INSERT INTO customs_tiers (tier_key, label, duty_pct, vat_pct, notes)
SELECT 'clothing_textiles', 'Clothing & textiles', 0.2500, 0.16,
       'Apparel, footwear, fabric — 25% duty band.'
WHERE NOT EXISTS (SELECT 1 FROM customs_tiers WHERE tier_key = 'clothing_textiles');

INSERT INTO customs_tiers (tier_key, label, duty_pct, vat_pct, notes)
SELECT 'food_processed', 'Processed food / supplements', 0.3500, 0.16,
       'Sensitive list — 35% duty band.'
WHERE NOT EXISTS (SELECT 1 FROM customs_tiers WHERE tier_key = 'food_processed');

INSERT INTO customs_tiers (tier_key, label, duty_pct, vat_pct, notes)
SELECT 'raw_materials', 'Raw materials / inputs', 0.0000, 0.16,
       'Industrial inputs and raw materials — 0% duty.'
WHERE NOT EXISTS (SELECT 1 FROM customs_tiers WHERE tier_key = 'raw_materials');

INSERT INTO customs_tiers (tier_key, label, duty_pct, vat_pct, notes)
SELECT 'books_media', 'Books / printed media', 0.0000, 0.0000,
       'Books, journals — 0% duty AND zero-rated for VAT.'
WHERE NOT EXISTS (SELECT 1 FROM customs_tiers WHERE tier_key = 'books_media');

INSERT INTO customs_tiers (tier_key, label, duty_pct, vat_pct, notes)
SELECT 'zero_rated', 'Zero-rated (medical / exempt)', 0.0000, 0.0000,
       'Medical supplies and other gazetted exemptions — 0% duty, 0% VAT.'
WHERE NOT EXISTS (SELECT 1 FROM customs_tiers WHERE tier_key = 'zero_rated');


-- ── 3. hs_code_tiers (prefix → tier; longest prefix wins) ──────────────────
CREATE TABLE IF NOT EXISTS hs_code_tiers (
  hs_prefix   VARCHAR(10) PRIMARY KEY,
  tier_key    VARCHAR(50) NOT NULL REFERENCES customs_tiers(tier_key) ON DELETE RESTRICT,
  notes       TEXT,
  updated_by  TEXT,
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO hs_code_tiers (hs_prefix, tier_key, notes)
SELECT '8517', 'electronics', 'Phones, smartphones, networking gear (HS Chapter 85.17)'
WHERE NOT EXISTS (SELECT 1 FROM hs_code_tiers WHERE hs_prefix = '8517');

INSERT INTO hs_code_tiers (hs_prefix, tier_key, notes)
SELECT '8471', 'electronics', 'Automatic data-processing machines (laptops, desktops)'
WHERE NOT EXISTS (SELECT 1 FROM hs_code_tiers WHERE hs_prefix = '8471');

INSERT INTO hs_code_tiers (hs_prefix, tier_key, notes)
SELECT '8528', 'electronics', 'Monitors, projectors, television receivers'
WHERE NOT EXISTS (SELECT 1 FROM hs_code_tiers WHERE hs_prefix = '8528');

INSERT INTO hs_code_tiers (hs_prefix, tier_key, notes)
SELECT '6109', 'clothing_textiles', 'T-shirts, singlets, vests (HS 61.09)'
WHERE NOT EXISTS (SELECT 1 FROM hs_code_tiers WHERE hs_prefix = '6109');

INSERT INTO hs_code_tiers (hs_prefix, tier_key, notes)
SELECT '6203', 'clothing_textiles', 'Men''s suits, trousers (HS 62.03)'
WHERE NOT EXISTS (SELECT 1 FROM hs_code_tiers WHERE hs_prefix = '6203');

INSERT INTO hs_code_tiers (hs_prefix, tier_key, notes)
SELECT '6204', 'clothing_textiles', 'Women''s suits, dresses (HS 62.04)'
WHERE NOT EXISTS (SELECT 1 FROM hs_code_tiers WHERE hs_prefix = '6204');

INSERT INTO hs_code_tiers (hs_prefix, tier_key, notes)
SELECT '6403', 'clothing_textiles', 'Footwear with leather uppers (HS 64.03)'
WHERE NOT EXISTS (SELECT 1 FROM hs_code_tiers WHERE hs_prefix = '6403');

INSERT INTO hs_code_tiers (hs_prefix, tier_key, notes)
SELECT '4901', 'books_media', 'Printed books, brochures (HS 49.01)'
WHERE NOT EXISTS (SELECT 1 FROM hs_code_tiers WHERE hs_prefix = '4901');

INSERT INTO hs_code_tiers (hs_prefix, tier_key, notes)
SELECT '3004', 'zero_rated', 'Medicaments packaged for retail sale (HS 30.04)'
WHERE NOT EXISTS (SELECT 1 FROM hs_code_tiers WHERE hs_prefix = '3004');


-- ── 4. electronics_fees ─────────────────────────────────────────────────────
-- Brand-new in thapsus (swiftcargo had a lazily-created equivalent). Seeded
-- with phone/laptop/tv_monitor matching swiftcargo's defaults so cross-repo
-- parity holds for any electronics-flagged order.
CREATE TABLE IF NOT EXISTS electronics_fees (
  item_key      VARCHAR(50) PRIMARY KEY,
  label         TEXT NOT NULL,
  fee_gbp       NUMERIC(10,2) NOT NULL,
  min_weight_kg NUMERIC(6,2) NOT NULL DEFAULT 0,
  is_active     BOOLEAN NOT NULL DEFAULT TRUE,
  updated_by    TEXT,
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO electronics_fees (item_key, label, fee_gbp, min_weight_kg)
SELECT 'phone', 'Phone', 75.00, 1
WHERE NOT EXISTS (SELECT 1 FROM electronics_fees WHERE item_key = 'phone');

INSERT INTO electronics_fees (item_key, label, fee_gbp, min_weight_kg)
SELECT 'laptop', 'Laptop / Accessories', 65.00, 1
WHERE NOT EXISTS (SELECT 1 FROM electronics_fees WHERE item_key = 'laptop');

INSERT INTO electronics_fees (item_key, label, fee_gbp, min_weight_kg)
SELECT 'tv_monitor', 'TV / Screen / Monitor', 65.00, 1
WHERE NOT EXISTS (SELECT 1 FROM electronics_fees WHERE item_key = 'tv_monitor');


-- ── 5. Archive existing pricing_tiers rows ─────────────────────────────────
-- The QuoteEngine no longer reads them — single base £/kg replaces the
-- weight-band ladder. Set is_active=FALSE so they vanish from any UI that
-- filters on `is_active = TRUE`, but keep the rows for rollback and history.
-- Wrap in a DO block so this is a no-op when the table doesn't exist (test
-- environments, fresh installs that haven't run 001_framework_v2_additions).
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables
              WHERE table_schema = 'public' AND table_name = 'pricing_tiers') THEN
    UPDATE public.pricing_tiers
       SET is_active = FALSE
     WHERE is_active = TRUE;
  END IF;
END $$;
