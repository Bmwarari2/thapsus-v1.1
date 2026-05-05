-- ============================================================
-- Migration 002 — Align `packages` with the iOS v2 parcel model
--
-- The legacy `packages` table (database/schema.sql) was modelled as a
-- child of `orders`: every row required `order_id NOT NULL`, the column
-- set was minimal, and `status` used a different taxonomy than the
-- iOS app's PackageStatus enum. Migration 001 added a few columns to
-- `orders` for the v2 framework, but the iOS shared module reads and
-- writes `packages` directly with the parcel-itself shape:
--
--   PackageDto: id, user_id, tracking_number, barcode, retailer,
--               description, declared_value_gbp_pence, actual_kg,
--               volumetric_kg, chargeable_kg, length_cm, width_cm,
--               height_cm, status, hold_reason, hold_resolved_at,
--               photographed_at, photo_url, screening_result,
--               consolidation_id, insurance_policy_id, created_at,
--               updated_at
--
-- This migration brings `packages` into line with that shape.
--
-- Additive + idempotent. Re-running is safe.
--
-- Run order:
--   1. database/schema.sql
--   2. database/migrations/001_framework_v2_additions.sql
--   3. database/migrations/002_packages_v2_alignment.sql   ← this file
-- ============================================================

-- ── 1. Status taxonomy migration ────────────────────────────────────────────
-- Old check: ('pending','received','consolidating','in_transit','customs',
--             'out_for_delivery','delivered','lost')
-- New check: full v2 PackageStatus enum from iOS shared module.

DO $$
BEGIN
  ALTER TABLE packages DROP CONSTRAINT IF EXISTS packages_status_check;
EXCEPTION WHEN undefined_object THEN
  NULL;
END $$;

-- Drop the column default before we rewrite values so we can apply the
-- new default once the new check is in place.
ALTER TABLE packages ALTER COLUMN status DROP DEFAULT;

UPDATE packages SET status = 'pre_registered'        WHERE status = 'pending';
UPDATE packages SET status = 'received_at_warehouse' WHERE status = 'received';
UPDATE packages SET status = 'manifested'            WHERE status = 'consolidating';
UPDATE packages SET status = 'awaiting_duty_payment' WHERE status = 'customs';
UPDATE packages SET status = 'abandoned'             WHERE status = 'lost';
-- 'in_transit', 'out_for_delivery', 'delivered' carry over unchanged.

ALTER TABLE packages
  ADD CONSTRAINT packages_status_check
  CHECK (status IN (
    'pre_registered',
    'received_at_warehouse',
    'photographed',
    'weighed',
    'screened',
    'manifested',
    'in_transit',
    'jkia_arrived',
    'awaiting_duty_payment',
    'released',
    'out_for_delivery',
    'delivered',
    'held',
    'held_at_nairobi_hub',
    'abandoned'
  ));

ALTER TABLE packages ALTER COLUMN status SET DEFAULT 'pre_registered';

-- ── 2. Relax NOT NULL constraints that block standalone parcels ─────────────
-- v2 lets a customer pre-register a parcel before any `orders` row exists,
-- so order_id becomes optional. Description is also nullable in the iOS DTO.
DO $$
BEGIN
  ALTER TABLE packages ALTER COLUMN order_id DROP NOT NULL;
EXCEPTION WHEN others THEN
  NULL;
END $$;

DO $$
BEGIN
  ALTER TABLE packages ALTER COLUMN description DROP NOT NULL;
EXCEPTION WHEN others THEN
  NULL;
END $$;

-- ── 3. v2 parcel-level columns ──────────────────────────────────────────────
-- All idempotent. iOS writes these fields directly via Supabase upsert.
ALTER TABLE packages ADD COLUMN IF NOT EXISTS tracking_number          TEXT;
ALTER TABLE packages ADD COLUMN IF NOT EXISTS retailer                 TEXT;
ALTER TABLE packages ADD COLUMN IF NOT EXISTS declared_value_gbp_pence BIGINT NOT NULL DEFAULT 0;
ALTER TABLE packages ADD COLUMN IF NOT EXISTS actual_kg                REAL;
ALTER TABLE packages ADD COLUMN IF NOT EXISTS volumetric_kg            REAL;
ALTER TABLE packages ADD COLUMN IF NOT EXISTS chargeable_kg            REAL;
ALTER TABLE packages ADD COLUMN IF NOT EXISTS length_cm                REAL;
ALTER TABLE packages ADD COLUMN IF NOT EXISTS width_cm                 REAL;
ALTER TABLE packages ADD COLUMN IF NOT EXISTS height_cm                REAL;
ALTER TABLE packages ADD COLUMN IF NOT EXISTS hold_reason              TEXT;
ALTER TABLE packages ADD COLUMN IF NOT EXISTS hold_resolved_at         TIMESTAMPTZ;
ALTER TABLE packages ADD COLUMN IF NOT EXISTS photographed_at          TIMESTAMPTZ;

-- consolidation_id was added to `orders` in migration 001 but parcels-as-packages
-- need their own FK so the iOS consolidation views can join directly.
DO $$
BEGIN
  ALTER TABLE packages
    ADD COLUMN IF NOT EXISTS consolidation_id TEXT
      REFERENCES consolidations(id) ON DELETE SET NULL;
EXCEPTION WHEN undefined_table THEN
  -- consolidations table doesn't exist yet: migration 001 hasn't run.
  -- Add the column without the FK so this migration is still re-runnable
  -- once the dependency lands.
  ALTER TABLE packages ADD COLUMN IF NOT EXISTS consolidation_id TEXT;
END $$;

-- ── 4. Backfill from `orders` where the legacy 1:1 relationship exists ──────
-- For every package row that still has an order_id, copy the parcel-level
-- fields from its parent order so the iOS reads return populated parcels
-- instead of mostly-null skeletons. Only fills nulls — never clobbers values
-- the user may already have edited via the mobile app.
UPDATE packages p SET
  tracking_number = COALESCE(p.tracking_number, o.tracking_number),
  retailer        = COALESCE(p.retailer,        o.retailer),
  declared_value_gbp_pence = CASE
    WHEN p.declared_value_gbp_pence = 0 AND o.declared_value IS NOT NULL
      THEN ROUND(o.declared_value * 100)::BIGINT
    ELSE p.declared_value_gbp_pence
  END,
  description = COALESCE(p.description, o.description)
FROM orders o
WHERE p.order_id = o.id;

-- ── 5. Indexes ──────────────────────────────────────────────────────────────
DO $$
BEGIN
  CREATE UNIQUE INDEX IF NOT EXISTS idx_packages_tracking_number_unique
    ON packages(tracking_number) WHERE tracking_number IS NOT NULL;
EXCEPTION WHEN duplicate_table THEN
  NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_packages_status        ON packages(status);
CREATE INDEX IF NOT EXISTS idx_packages_consolidation ON packages(consolidation_id);

-- ── 6. screening_result alignment ───────────────────────────────────────────
-- Migration 001 added `screening_result` with check ('clean','held','dg_suspect')
-- and no default. The iOS ScreeningResult enum also has 'pending' as the
-- default for unscreened parcels — without it, every iOS upsert fails the
-- check constraint. Widen the constraint and set 'pending' as the default.

DO $$
BEGIN
  ALTER TABLE packages DROP CONSTRAINT IF EXISTS packages_screening_result_check;
EXCEPTION WHEN undefined_object THEN
  NULL;
END $$;

ALTER TABLE packages
  ADD CONSTRAINT packages_screening_result_check
  CHECK (screening_result IS NULL OR screening_result IN ('pending','clean','held','dg_suspect'));

ALTER TABLE packages ALTER COLUMN screening_result SET DEFAULT 'pending';

-- Backfill any NULL screening_result so existing rows match the new default.
UPDATE packages SET screening_result = 'pending' WHERE screening_result IS NULL;

-- ── End of migration ────────────────────────────────────────────────────────
