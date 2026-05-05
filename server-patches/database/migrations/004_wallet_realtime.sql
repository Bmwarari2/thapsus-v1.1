-- ============================================================
-- Migration 004 — Enable Supabase Realtime on wallet + transactions
--
-- Symptom this fixes (iOS WalletView):
--   🔴 (Supabase-Realtime) Received message without event:
--   "Unable to subscribe to changes with given parameters.
--    Please check Realtime is enabled for the given connect parameters:
--      [event: *, schema: public, table: wallet,        filters: [{user_id, eq, …}]]
--      [event: *, schema: public, table: transactions, filters: [{user_id, eq, …}]]"
--
-- Same shape as 003_realtime_publication.sql — add the tables to
-- the supabase_realtime publication and set REPLICA IDENTITY FULL
-- so UPDATE events carry the whole row.
--
-- Run order:
--   …
--   3. database/migrations/003_realtime_publication.sql
--   4. database/migrations/004_wallet_realtime.sql       ← this file
-- ============================================================

DO $$
BEGIN
  -- wallet
  IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'wallet' AND relnamespace = 'public'::regnamespace)
     AND NOT EXISTS (
       SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime'
          AND schemaname = 'public'
          AND tablename = 'wallet'
     )
  THEN
    EXECUTE 'ALTER PUBLICATION supabase_realtime ADD TABLE public.wallet';
  END IF;

  -- transactions
  IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'transactions' AND relnamespace = 'public'::regnamespace)
     AND NOT EXISTS (
       SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime'
          AND schemaname = 'public'
          AND tablename = 'transactions'
     )
  THEN
    EXECUTE 'ALTER PUBLICATION supabase_realtime ADD TABLE public.transactions';
  END IF;
END $$;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'wallet' AND relnamespace = 'public'::regnamespace) THEN
    EXECUTE 'ALTER TABLE public.wallet       REPLICA IDENTITY FULL';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'transactions' AND relnamespace = 'public'::regnamespace) THEN
    EXECUTE 'ALTER TABLE public.transactions REPLICA IDENTITY FULL';
  END IF;
END $$;

-- ── RLS reminder ────────────────────────────────────────────────────────────
-- Realtime respects RLS. iOS subscribes with filter user_id=eq.<auth.uid()>.
-- Without a matching SELECT policy the channel JOINs successfully but never
-- emits a row event.
--
--   CREATE POLICY wallet_select_self
--     ON public.wallet FOR SELECT
--     TO authenticated
--     USING (user_id = auth.uid());
--
--   CREATE POLICY transactions_select_self
--     ON public.transactions FOR SELECT
--     TO authenticated
--     USING (user_id = auth.uid());
--
-- ── End of migration ────────────────────────────────────────────────────────
