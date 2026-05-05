-- Migration 014 — orders.hs_tier
-- Adds the HS-tier column the customs estimator now keys on. Default
-- 'general' so historic rows behave as before. Idempotent.

ALTER TABLE orders
  ADD COLUMN IF NOT EXISTS hs_tier TEXT NOT NULL DEFAULT 'general';

-- CHECK constraint guards against typos. Mirrors HS_TIERS in utils/pricing.js.
DO $$
BEGIN
  ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_hs_tier_check;
EXCEPTION WHEN undefined_object THEN
  NULL;
END $$;

ALTER TABLE orders
  ADD CONSTRAINT orders_hs_tier_check
  CHECK (hs_tier IN (
    'general',
    'electronics',
    'clothing_textiles',
    'food_processed',
    'raw_materials',
    'books_media',
    'zero_rated'
  ));

CREATE INDEX IF NOT EXISTS idx_orders_hs_tier ON orders(hs_tier);

DO $$ BEGIN RAISE NOTICE 'orders.hs_tier added (default general).'; END $$;
