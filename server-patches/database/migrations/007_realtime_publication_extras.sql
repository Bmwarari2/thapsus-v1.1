-- ============================================================
-- Migration 007 — Extend `supabase_realtime` publication for
-- the customer-support + admin queues.
--
-- Phase 1 audit found the publication still only carries
-- `consolidations`, `packages`, `transactions`, `wallet`. The
-- iOS app polls everything else, which means support replies,
-- buy-for-me quote updates and AML escalations show up only on
-- pull-to-refresh.
--
-- This migration adds five tables to the publication and locks
-- `REPLICA IDENTITY FULL` so UPDATE events carry the full row.
-- (The `pod_events` and `last_mile_runs` tables are deliberately
-- left off — riders use the offline outbox; admin dispatch
-- already polls from the operator dashboard.)
--
-- Idempotent: each ADD is guarded by a NOT EXISTS check so the
-- migration is safe to re-run.
--
-- Pre-requisite: migrations 003 (realtime publication groundwork)
-- and 004 (wallet realtime). Apply 008 (exchange_rates RLS) and
-- 009 (RLS SELECT policies for these new streaming tables) at the
-- same time — without 009 the channels subscribe but never emit.
--
-- Run order:
--   003_realtime_publication.sql              (already applied)
--   004_wallet_realtime.sql                   (already applied)
--   005_pricing_tiers_repair.sql              (already applied)
--   006_exchange_rates_alignment.sql          (already applied)
--   008_exchange_rates_rls.sql                (already applied)
--   007_realtime_publication_extras.sql   ←   this file
--   009_streaming_rls_policies.sql            (next)
-- ============================================================

DO $$
DECLARE
  t TEXT;
  tables TEXT[] := ARRAY[
    'notifications',
    'tickets',
    'ticket_messages',
    'buy_for_me_orders',
    'aml_flags'
  ];
BEGIN
  FOREACH t IN ARRAY tables LOOP
    -- 1. Confirm the table exists. If a project was provisioned
    --    from an older snapshot one of these may still be absent;
    --    we skip rather than error so this remains idempotent.
    IF to_regclass('public.' || t) IS NULL THEN
      RAISE NOTICE 'skip: public.% does not exist', t;
      CONTINUE;
    END IF;

    -- 2. Add to the publication (idempotent guard).
    IF NOT EXISTS (
      SELECT 1 FROM pg_publication_tables
       WHERE pubname = 'supabase_realtime'
         AND schemaname = 'public'
         AND tablename  = t
    ) THEN
      EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', t);
      RAISE NOTICE 'added: public.% to supabase_realtime', t;
    ELSE
      RAISE NOTICE 'already in publication: public.%', t;
    END IF;

    -- 3. REPLICA IDENTITY FULL so UPDATE rows carry the old image.
    --    (Idempotent — Postgres no-ops if already FULL.)
    EXECUTE format('ALTER TABLE public.%I REPLICA IDENTITY FULL', t);
  END LOOP;
END $$;

-- ── Verify ────────────────────────────────────────────────────
DO $$
DECLARE
  cnt INT;
BEGIN
  SELECT COUNT(*) INTO cnt
    FROM pg_publication_tables
   WHERE pubname = 'supabase_realtime'
     AND schemaname = 'public'
     AND tablename IN ('notifications','tickets','ticket_messages',
                       'buy_for_me_orders','aml_flags');
  RAISE NOTICE 'supabase_realtime now publishes % of 5 target tables', cnt;
END $$;
