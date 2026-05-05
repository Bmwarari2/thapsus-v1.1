-- ============================================================
-- Migration 006 — Align `exchange_rates` table to the canonical
-- `currency_pair` column shape that routes/admin.js expects.
--
-- Symptom this fixes:
--   GET /api/admin/exchange-rates → 500
--   Railway log: "column \"currency_pair\" does not exist"
--   (PostgreSQL error 42703, file parse_relation.c)
--
-- Some Supabase projects on this codebase have an older
-- `exchange_rates` shape with `pair` / `from_currency` / `to_currency`
-- instead of `currency_pair`. The webapp's exchange-rate handlers
-- only know `currency_pair`, so they fail outright on the legacy
-- shape and the iOS Admin → Settings tab can't load.
--
-- This migration:
--   1. Creates the table from scratch on a fresh project.
--   2. Adds the `currency_pair` column on a legacy project, backfills
--      it from whatever shape exists, then drops the legacy columns.
--   3. Ensures the unique index needed for the
--      `INSERT … ON CONFLICT (currency_pair)` upsert in PUT
--      /api/admin/exchange-rates.
--   4. Seeds the four canonical pairs at sensible defaults if they
--      aren't already present.
--
-- Idempotent — safe to re-run.
-- ============================================================

-- 1. Brand-new project: create the table at the expected shape.
CREATE TABLE IF NOT EXISTS public.exchange_rates (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  currency_pair TEXT NOT NULL,
  rate          NUMERIC(12, 4) NOT NULL CHECK (rate > 0),
  updated_by    UUID,
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Legacy-shape repair. Add the canonical column if it's missing,
--    backfill from whatever the legacy row shape looks like, then
--    drop the older columns.
DO $$
DECLARE
  has_currency_pair  BOOLEAN;
  has_pair           BOOLEAN;
  has_from_currency  BOOLEAN;
  has_to_currency    BOOLEAN;
  has_code           BOOLEAN;
BEGIN
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema='public' AND table_name='exchange_rates' AND column_name='currency_pair'
  ) INTO has_currency_pair;

  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema='public' AND table_name='exchange_rates' AND column_name='pair'
  ) INTO has_pair;

  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema='public' AND table_name='exchange_rates' AND column_name='from_currency'
  ) INTO has_from_currency;

  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema='public' AND table_name='exchange_rates' AND column_name='to_currency'
  ) INTO has_to_currency;

  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema='public' AND table_name='exchange_rates' AND column_name='code'
  ) INTO has_code;

  IF NOT has_currency_pair THEN
    EXECUTE 'ALTER TABLE public.exchange_rates ADD COLUMN currency_pair TEXT';
  END IF;

  -- Backfill from the most likely legacy shapes, in priority order.
  IF has_pair THEN
    EXECUTE 'UPDATE public.exchange_rates SET currency_pair = pair WHERE currency_pair IS NULL';
  END IF;

  IF has_from_currency AND has_to_currency THEN
    EXECUTE 'UPDATE public.exchange_rates
                SET currency_pair = upper(from_currency) || ''_'' || upper(to_currency)
              WHERE currency_pair IS NULL';
  END IF;

  IF has_code THEN
    EXECUTE 'UPDATE public.exchange_rates SET currency_pair = code WHERE currency_pair IS NULL';
  END IF;

  -- Now safe to drop legacy columns. They're no longer referenced.
  IF has_pair          THEN EXECUTE 'ALTER TABLE public.exchange_rates DROP COLUMN pair'; END IF;
  IF has_from_currency THEN EXECUTE 'ALTER TABLE public.exchange_rates DROP COLUMN from_currency'; END IF;
  IF has_to_currency   THEN EXECUTE 'ALTER TABLE public.exchange_rates DROP COLUMN to_currency'; END IF;
  IF has_code          THEN EXECUTE 'ALTER TABLE public.exchange_rates DROP COLUMN code'; END IF;
END $$;

-- 3. Lock canonical column to NOT NULL once everything is backfilled,
--    and add the unique index the upsert depends on.
DO $$
BEGIN
  -- Drop any rows that still have a NULL currency_pair after backfill —
  -- they have no usable identity and would block the NOT NULL.
  DELETE FROM public.exchange_rates WHERE currency_pair IS NULL;

  ALTER TABLE public.exchange_rates ALTER COLUMN currency_pair SET NOT NULL;
EXCEPTION
  WHEN duplicate_object THEN NULL;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS exchange_rates_currency_pair_uidx
  ON public.exchange_rates (currency_pair);

-- 4. Seed canonical pairs at reasonable defaults if absent. Admins can
--    overwrite via PUT /api/admin/exchange-rates.
INSERT INTO public.exchange_rates (currency_pair, rate)
SELECT 'USD_KES', 130.5 WHERE NOT EXISTS (SELECT 1 FROM public.exchange_rates WHERE currency_pair = 'USD_KES');
INSERT INTO public.exchange_rates (currency_pair, rate)
SELECT 'GBP_KES', 164.2 WHERE NOT EXISTS (SELECT 1 FROM public.exchange_rates WHERE currency_pair = 'GBP_KES');
INSERT INTO public.exchange_rates (currency_pair, rate)
SELECT 'EUR_KES', 142.8 WHERE NOT EXISTS (SELECT 1 FROM public.exchange_rates WHERE currency_pair = 'EUR_KES');
INSERT INTO public.exchange_rates (currency_pair, rate)
SELECT 'CNY_KES',  18.2 WHERE NOT EXISTS (SELECT 1 FROM public.exchange_rates WHERE currency_pair = 'CNY_KES');

-- Diagnostic — run after the migration in the SQL Editor.
SELECT currency_pair, rate, updated_at FROM public.exchange_rates ORDER BY currency_pair;
