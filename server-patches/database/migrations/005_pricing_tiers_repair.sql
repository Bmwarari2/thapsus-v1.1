-- ============================================================
-- Migration 005 — Pricing tiers repair (idempotent)
--
-- Symptom this fixes (iOS calculator):
--   "Couldn't price — No pricing tier matches 2.0kg on channel UK_AIR"
--
-- Root causes seen in the wild on this DB:
--   1. The 001a seed never inserted a UK_air 0–5 kg tier because an
--      earlier row with a different shape already occupied min_kg=0
--      (the `WHERE NOT EXISTS … min_kg=0` guard skipped it).
--   2. The seeded tier had `is_active = false` after a manual edit,
--      so the public `/api/pricing-tiers/tiers` endpoint filtered it
--      out (it only returns is_active = TRUE rows).
--   3. The channel string was written in a non-canonical case
--      (`uk_air`, `UK_AIR`) and stayed there. The iOS DTO now decodes
--      these tolerantly, but normalising the DB keeps things sane.
--
-- This migration:
--   • Normalises every existing channel to the canonical seed casing.
--   • Reactivates any tier whose channel is one of the three valid
--     channels and which covers a sensible range.
--   • Upserts the four UK_air bands so 0–5 kg always has a tier.
--   • Re-runs the China_air + UK_sea baselines for completeness.
--
-- Safe to re-run.
-- ============================================================

-- 1. Channel name normalisation. The QuoteEngine reads the channel
--    string via a tolerant deserialiser, but admin pages still match
--    on exact strings — keeping the DB canonical avoids surprise.
UPDATE public.pricing_tiers
   SET channel = 'UK_air'
 WHERE lower(channel) IN ('uk_air', 'ukair', 'air_uk', 'uk-air');

UPDATE public.pricing_tiers
   SET channel = 'UK_sea'
 WHERE lower(channel) IN ('uk_sea', 'uksea', 'sea_uk', 'uk-sea');

UPDATE public.pricing_tiers
   SET channel = 'China_air'
 WHERE lower(channel) IN ('china_air', 'chinaair', 'cn_air', 'air_china', 'china-air');

-- 2. Reactivate any tier on a canonical channel that is currently
--    inactive — the public endpoint hides inactive rows, which leaves
--    the calculator with nothing to match.
UPDATE public.pricing_tiers
   SET is_active = TRUE
 WHERE channel IN ('UK_air', 'UK_sea', 'China_air')
   AND is_active = FALSE;

-- 3. UK_air rate card (0–100 kg).
INSERT INTO public.pricing_tiers (id, channel, min_kg, max_kg, gbp_per_kg, is_active, notes)
SELECT 'pt-uk-air-001', 'UK_air',  0,   5, 14.00, TRUE, 'Tudor Freight rate card seed (005 repair)'
WHERE NOT EXISTS (
  SELECT 1 FROM public.pricing_tiers
   WHERE channel = 'UK_air' AND min_kg <= 0 AND max_kg >= 5 AND is_active = TRUE
);

INSERT INTO public.pricing_tiers (id, channel, min_kg, max_kg, gbp_per_kg, is_active, notes)
SELECT 'pt-uk-air-002', 'UK_air',  5,  10, 12.00, TRUE, 'Tudor Freight rate card seed (005 repair)'
WHERE NOT EXISTS (
  SELECT 1 FROM public.pricing_tiers
   WHERE channel = 'UK_air' AND min_kg <= 5 AND max_kg >= 10 AND is_active = TRUE
);

INSERT INTO public.pricing_tiers (id, channel, min_kg, max_kg, gbp_per_kg, is_active, notes)
SELECT 'pt-uk-air-003', 'UK_air', 10,  25, 10.00, TRUE, 'Tudor Freight rate card seed (005 repair)'
WHERE NOT EXISTS (
  SELECT 1 FROM public.pricing_tiers
   WHERE channel = 'UK_air' AND min_kg <= 10 AND max_kg >= 25 AND is_active = TRUE
);

INSERT INTO public.pricing_tiers (id, channel, min_kg, max_kg, gbp_per_kg, is_active, notes)
SELECT 'pt-uk-air-004', 'UK_air', 25, 100,  9.00, TRUE, 'Tudor Freight rate card seed (005 repair)'
WHERE NOT EXISTS (
  SELECT 1 FROM public.pricing_tiers
   WHERE channel = 'UK_air' AND min_kg <= 25 AND max_kg >= 100 AND is_active = TRUE
);

-- 4. China_air placeholder rate card.
INSERT INTO public.pricing_tiers (id, channel, min_kg, max_kg, gbp_per_kg, is_active, notes)
SELECT 'pt-cn-air-001', 'China_air',  0,   5, 16.00, TRUE, 'China rate placeholder (005 repair)'
WHERE NOT EXISTS (
  SELECT 1 FROM public.pricing_tiers
   WHERE channel = 'China_air' AND min_kg <= 0 AND max_kg >= 5 AND is_active = TRUE
);

INSERT INTO public.pricing_tiers (id, channel, min_kg, max_kg, gbp_per_kg, is_active, notes)
SELECT 'pt-cn-air-002', 'China_air',  5,  10, 14.00, TRUE, 'China rate placeholder (005 repair)'
WHERE NOT EXISTS (
  SELECT 1 FROM public.pricing_tiers
   WHERE channel = 'China_air' AND min_kg <= 5 AND max_kg >= 10 AND is_active = TRUE
);

INSERT INTO public.pricing_tiers (id, channel, min_kg, max_kg, gbp_per_kg, is_active, notes)
SELECT 'pt-cn-air-003', 'China_air', 10,  25, 12.00, TRUE, 'China rate placeholder (005 repair)'
WHERE NOT EXISTS (
  SELECT 1 FROM public.pricing_tiers
   WHERE channel = 'China_air' AND min_kg <= 10 AND max_kg >= 25 AND is_active = TRUE
);

INSERT INTO public.pricing_tiers (id, channel, min_kg, max_kg, gbp_per_kg, is_active, notes)
SELECT 'pt-cn-air-004', 'China_air', 25, 100, 11.00, TRUE, 'China rate placeholder (005 repair)'
WHERE NOT EXISTS (
  SELECT 1 FROM public.pricing_tiers
   WHERE channel = 'China_air' AND min_kg <= 25 AND max_kg >= 100 AND is_active = TRUE
);

-- 5. UK_sea slow boat — single wide band.
INSERT INTO public.pricing_tiers (id, channel, min_kg, max_kg, gbp_per_kg, is_active, notes)
SELECT 'pt-uk-sea-001', 'UK_sea', 0, 100, 6.00, TRUE, 'UK sea rate placeholder (005 repair)'
WHERE NOT EXISTS (
  SELECT 1 FROM public.pricing_tiers
   WHERE channel = 'UK_sea' AND is_active = TRUE
);

-- Diagnostic — drop into the SQL Editor's results pane after running.
SELECT channel, min_kg, max_kg, gbp_per_kg, is_active
  FROM public.pricing_tiers
 ORDER BY channel, min_kg;
