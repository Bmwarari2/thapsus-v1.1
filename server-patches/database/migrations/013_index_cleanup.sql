-- ============================================================
-- Migration 013 — Drop duplicate indexes.
--
-- Linter finding: 11 places where two (or three) indexes covered
-- the same expression. In every case one index was the
-- auto-generated UNIQUE constraint index (`<table>_<col>_key`),
-- which we KEEP because dropping it removes the uniqueness
-- guarantee. The redundant manual `idx_*` is dropped instead.
-- Where neither index is a uniqueness constraint, we keep the
-- more descriptive name.
--
-- Idempotent: `DROP INDEX IF EXISTS`.
--
-- Note: the linter also flagged a number of "unused indexes"
-- (never hit by `pg_stat_user_indexes`). Those are NOT touched
-- by this migration. Reason: an unused index today may be the
-- index a planned-but-unshipped query needs tomorrow. They cost
-- write throughput, not correctness — handle in a follow-up
-- after the index strategy stabilises.
-- ============================================================

-- consolidations: drop manual duplicate of unique constraint
DROP INDEX IF EXISTS public.idx_consolidations_week_start;

-- insurance_policies: drop manual duplicate of unique constraint
DROP INDEX IF EXISTS public.idx_insurance_parcel;

-- orders: drop both manual duplicates of unique constraint
DROP INDEX IF EXISTS public.idx_orders_tracking;
DROP INDEX IF EXISTS public.idx_orders_tracking_number;

-- packages: drop manual duplicate of unique constraint
DROP INDEX IF EXISTS public.idx_packages_barcode;

-- password_reset_tokens: drop both manual duplicates of unique constraint
DROP INDEX IF EXISTS public.idx_password_reset_token;
DROP INDEX IF EXISTS public.idx_reset_token;

-- pod_events: keep the more descriptive `idx_pod_events_parcel`
DROP INDEX IF EXISTS public.idx_pod_parcel;

-- promotions: drop manual duplicate of unique constraint
DROP INDEX IF EXISTS public.idx_promotions_code;

-- referrals: keep the more explicit `_id`-suffixed names
DROP INDEX IF EXISTS public.idx_referrals_referee;
DROP INDEX IF EXISTS public.idx_referrals_referrer;

-- users: drop manual duplicates of unique constraints
DROP INDEX IF EXISTS public.idx_users_referral_code;
DROP INDEX IF EXISTS public.idx_users_warehouse_id;

-- ── Verify ───────────────────────────────────────────────────
DO $$
DECLARE
  remaining INT;
BEGIN
  WITH normalised AS (
    SELECT
      tablename,
      indexname,
      regexp_replace(indexdef, '^CREATE [A-Z ]*INDEX [^ ]+', 'IDX', 'i') AS shape
    FROM pg_indexes
    WHERE schemaname='public'
  )
  SELECT COUNT(*) INTO remaining
    FROM (
      SELECT tablename, shape
        FROM normalised
       GROUP BY tablename, shape
      HAVING count(*) > 1
    ) dupes;
  RAISE NOTICE 'Remaining duplicate-index pairs: %', remaining;
END $$;
