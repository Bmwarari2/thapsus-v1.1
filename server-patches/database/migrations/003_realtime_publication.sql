-- ============================================================
-- Migration 003 — Enable Supabase Realtime on parcel + flight tables
--
-- Without these, the iOS RealtimeSync subscription throws:
--   "Unable to subscribe to changes with given parameters.
--    Please check Realtime is enabled for the given connect
--    parameters: [event: *, schema: public, table: <name>, filters: []]"
--
-- Two settings per table:
--   1. Add to the `supabase_realtime` publication so Postgres
--      streams its WAL changes to Supabase Realtime.
--   2. REPLICA IDENTITY FULL so UPDATE events carry the full
--      old/new row, not just the primary key. Required for the
--      iOS local cache (ThapsusLocalCache) to apply diffs.
--
-- Idempotent: ALTER PUBLICATION ADD TABLE is wrapped in a guard
-- so re-running the migration after the table is already in the
-- publication doesn't error out.
--
-- Run order:
--   1. database/schema.sql
--   2. database/migrations/001_framework_v2_additions.sql
--   3. database/migrations/002_packages_v2_alignment.sql
--   4. database/migrations/003_realtime_publication.sql   ← this file
-- ============================================================

DO $$
BEGIN
  -- packages
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
     WHERE pubname = 'supabase_realtime'
       AND schemaname = 'public'
       AND tablename = 'packages'
  ) THEN
    EXECUTE 'ALTER PUBLICATION supabase_realtime ADD TABLE public.packages';
  END IF;

  -- consolidations
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
     WHERE pubname = 'supabase_realtime'
       AND schemaname = 'public'
       AND tablename = 'consolidations'
  ) THEN
    EXECUTE 'ALTER PUBLICATION supabase_realtime ADD TABLE public.consolidations';
  END IF;
END $$;

ALTER TABLE public.packages       REPLICA IDENTITY FULL;
ALTER TABLE public.consolidations REPLICA IDENTITY FULL;

-- ── RLS reminder ────────────────────────────────────────────────────────────
-- Realtime respects RLS. The iOS customer subscription filters
-- packages by user_id = auth.uid(); the staff subscription is unfiltered.
-- Make sure both tables have a SELECT policy that lets the relevant role
-- see the rows it expects to receive — without one, the channel subscribes
-- successfully but the customer never sees a row event.
--
-- Customer-side policy on `packages` (illustrative, adjust to your model):
--
--   CREATE POLICY packages_select_self
--     ON public.packages FOR SELECT
--     TO authenticated
--     USING (user_id = auth.uid());
--
-- Staff-wide read policy on `packages`:
--
--   CREATE POLICY packages_select_staff
--     ON public.packages FOR SELECT
--     TO authenticated
--     USING (
--       (auth.jwt() -> 'user_metadata' ->> 'app_role')
--         IN ('operator','clearing_agent','rider','admin')
--     );
--
-- ── End of migration ────────────────────────────────────────────────────────
