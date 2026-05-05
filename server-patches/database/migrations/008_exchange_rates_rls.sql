-- ============================================================
-- Migration 008 — RLS policies for `exchange_rates`.
--
-- Symptom this fixes:
--   `pg_tables.rowsecurity = true` on `exchange_rates` with **zero**
--   policies in `pg_policies`. Effect: any anon/authenticated PostgREST
--   read returns empty, and the iOS OpsSettings exchange-rate editor
--   cannot load the four pairs. (See parity audit F4.)
--
-- Decision: keep RLS enabled (so casual anon reads cannot enumerate
-- admin rate history) but add policies that:
--   1. Allow `authenticated` and `anon` to SELECT current rates — the
--      public webapp `GET /api/exchange/rates` already exposes these
--      and the iOS app should be able to read them directly without
--      bouncing through Express on every render.
--   2. Restrict INSERT/UPDATE/DELETE to `service_role` only. The
--      Express admin endpoints (PUT /api/admin/exchange-rates and the
--      forthcoming PUT /api/exchange/rates/:pair) use the service-role
--      key, so writes still work; the anon key the iOS app carries
--      cannot mutate the table.
--
-- Idempotent — `DROP POLICY IF EXISTS` then `CREATE POLICY` on each.
-- Safe to re-run.
-- ============================================================

-- Make sure RLS is enabled. (The schema dump shows it already is, but
-- a fresh project may not have it — this is a no-op when already on.)
ALTER TABLE public.exchange_rates ENABLE ROW LEVEL SECURITY;

-- 1. Public SELECT — anon + authenticated.
DROP POLICY IF EXISTS exchange_rates_select_public ON public.exchange_rates;
CREATE POLICY exchange_rates_select_public
  ON public.exchange_rates
  FOR SELECT
  TO anon, authenticated
  USING (true);

-- 2. Writes — service_role only. The Express layer holds the
--    service-role key in `SUPABASE_SERVICE_ROLE_KEY` and is the
--    only writer of rate updates.
DROP POLICY IF EXISTS exchange_rates_write_service_role ON public.exchange_rates;
CREATE POLICY exchange_rates_write_service_role
  ON public.exchange_rates
  FOR ALL
  TO service_role
  USING (true)
  WITH CHECK (true);

-- 3. Verify (informational — `RAISE NOTICE` is visible in the SQL
--    editor but not stored).
DO $$
DECLARE
  policy_count INT;
BEGIN
  SELECT COUNT(*) INTO policy_count
    FROM pg_policies
   WHERE schemaname = 'public' AND tablename = 'exchange_rates';
  RAISE NOTICE 'exchange_rates now has % polic%', policy_count,
    CASE WHEN policy_count = 1 THEN 'y' ELSE 'ies' END;
END $$;
