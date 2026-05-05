-- ============================================================
-- Migration 010 — Comprehensive RLS lockdown.
--
-- The Supabase linter (2026-04-29) reported RLS off on every
-- public table. This migration:
--
--   1. Enables RLS on all 39 public tables (idempotent).
--   2. Adds a SELECT policy scoped to the access pattern each
--      table needs (customer-owned, staff-readable, admin-only,
--      or public).
--   3. Restricts all mutations to `service_role`. Express writes
--      via the pg connection pool already bypass RLS, so server
--      flows continue to work; the anon JWT (iOS app) cannot
--      mutate any of these tables directly.
--   4. Locks the `users.password` column so it never leaves the
--      database — the column is legacy (auth uses
--      `password_hash`).
--
-- Helpers `is_thapsus_admin()` / `is_thapsus_staff()` from
-- migration 009 are reused. Migration 011 hardens their
-- `search_path`.
--
-- Run order:
--   009_streaming_rls_policies.sql   (already applied)
--   010_rls_lockdown.sql             ←  this file
--   011_search_path_fix.sql
--   012_fk_indexes.sql
--   013_index_cleanup.sql
--
-- This file is idempotent — every CREATE POLICY is preceded by
-- DROP POLICY IF EXISTS, every ALTER TABLE … ENABLE RLS is
-- a no-op when already on.
-- ============================================================

-- ── Re-declare helpers in case 009 was rolled back ─────────────
-- Defined as STABLE SQL with a fixed search_path (migration 011
-- normalises this for every helper). Idempotent via OR REPLACE.
CREATE OR REPLACE FUNCTION public.is_thapsus_admin()
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SET search_path = public, pg_temp
AS $$
  SELECT COALESCE(
    auth.jwt() -> 'user_metadata' ->> 'app_role',
    ''
  ) = 'admin';
$$;

CREATE OR REPLACE FUNCTION public.is_thapsus_staff()
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SET search_path = public, pg_temp
AS $$
  SELECT COALESCE(
    auth.jwt() -> 'user_metadata' ->> 'app_role',
    ''
  ) IN ('admin','operator','clearing_agent','rider');
$$;

-- ── Generic write policy template ─────────────────────────────
-- Applied to every table: only `service_role` (the Express
-- service-role key) may INSERT/UPDATE/DELETE. The anon iOS JWT
-- can SELECT subject to per-table policy below; it cannot mutate.
DO $$
DECLARE
  t TEXT;
  -- All public tables. Skip auth-internal `_migrations` + reserved tables.
  tables TEXT[] := ARRAY[
    'admin_logs','agent_invoices','aml_flags','backups','buy_for_me_orders',
    'compliance_trainings','consolidations','customs_entries','dsar_requests',
    'email_logs','error_logs','exchange_rates','fees','insurance_policies',
    'last_mile_runs','marketing_attributions','notifications','nps_responses',
    'orders','packages','pallets','parcel_items','password_reset_tokens',
    'pod_events','pricing_tiers','prohibited_items','promotions','pvoc_documents',
    'referrals','run_parcels','shipping_rates','ticket_messages','tickets',
    'transactions','tudor_invoices','users','wallet','whatsapp_messages',
    '_migrations'
  ];
BEGIN
  FOREACH t IN ARRAY tables LOOP
    IF to_regclass('public.' || t) IS NULL THEN
      RAISE NOTICE 'skip: public.% missing', t;
      CONTINUE;
    END IF;
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'DROP POLICY IF EXISTS %I_write_service_role ON public.%I',
      t, t
    );
    EXECUTE format(
      'CREATE POLICY %I_write_service_role ON public.%I
         FOR ALL TO service_role USING (true) WITH CHECK (true)',
      t, t
    );
  END LOOP;
END $$;

-- ── Per-table SELECT policies ─────────────────────────────────
-- Customer-owned tables that follow the standard `user_id` shape.
-- Pattern: `user_id = auth.uid()::text OR is_thapsus_admin()`
-- `referrals` has its own pair of columns (referrer_id/referee_id)
-- and is handled separately below.
DO $$
DECLARE
  t TEXT;
  customer_owned TEXT[] := ARRAY[
    'orders','packages','wallet','transactions',
    'notifications','tickets','dsar_requests','nps_responses',
    'insurance_policies','buy_for_me_orders','marketing_attributions',
    'compliance_trainings','password_reset_tokens'
  ];
BEGIN
  FOREACH t IN ARRAY customer_owned LOOP
    IF to_regclass('public.' || t) IS NULL THEN CONTINUE; END IF;
    EXECUTE format(
      'DROP POLICY IF EXISTS %I_select_owner_or_admin ON public.%I',
      t, t
    );
    EXECUTE format(
      'CREATE POLICY %I_select_owner_or_admin ON public.%I
         FOR SELECT TO authenticated
         USING (user_id = auth.uid()::text OR public.is_thapsus_admin())',
      t, t
    );
  END LOOP;
END $$;

-- referrals: a row is visible to BOTH parties (the referrer who
-- shared the code AND the referee who joined under it). Admin
-- still bypasses.
DROP POLICY IF EXISTS referrals_select_party_or_admin ON public.referrals;
CREATE POLICY referrals_select_party_or_admin
  ON public.referrals
  FOR SELECT TO authenticated
  USING (
    referrer_id = auth.uid()::text
    OR referee_id = auth.uid()::text
    OR public.is_thapsus_admin()
  );

-- Tickets thread reuses ticket ownership.
DROP POLICY IF EXISTS ticket_messages_select_thread ON public.ticket_messages;
CREATE POLICY ticket_messages_select_thread
  ON public.ticket_messages
  FOR SELECT TO authenticated
  USING (
    public.is_thapsus_admin()
    OR EXISTS (
      SELECT 1 FROM public.tickets t
       WHERE t.id = ticket_messages.ticket_id
         AND t.user_id = auth.uid()::text
    )
  );

-- Customer self-row on `users` — also blocks the legacy `password`
-- column via a column grant below.
DROP POLICY IF EXISTS users_select_self_or_admin ON public.users;
CREATE POLICY users_select_self_or_admin
  ON public.users
  FOR SELECT TO authenticated
  USING (id = auth.uid()::text OR public.is_thapsus_admin());

-- Public read tables (no auth, but writes still service-role).
DO $$
DECLARE
  t TEXT;
  public_read TEXT[] := ARRAY[
    'exchange_rates','pricing_tiers','fees','prohibited_items',
    'shipping_rates','consolidations','promotions'
  ];
BEGIN
  FOREACH t IN ARRAY public_read LOOP
    IF to_regclass('public.' || t) IS NULL THEN CONTINUE; END IF;
    EXECUTE format(
      'DROP POLICY IF EXISTS %I_select_public ON public.%I',
      t, t
    );
    EXECUTE format(
      'CREATE POLICY %I_select_public ON public.%I
         FOR SELECT TO anon, authenticated USING (true)',
      t, t
    );
  END LOOP;
END $$;

-- Staff-only (operator/clearing_agent/rider/admin can read).
DO $$
DECLARE
  t TEXT;
  staff_only TEXT[] := ARRAY[
    'pallets','parcel_items','pvoc_documents','pod_events',
    'run_parcels','last_mile_runs','customs_entries',
    'agent_invoices','tudor_invoices','whatsapp_messages'
  ];
BEGIN
  FOREACH t IN ARRAY staff_only LOOP
    IF to_regclass('public.' || t) IS NULL THEN CONTINUE; END IF;
    EXECUTE format(
      'DROP POLICY IF EXISTS %I_select_staff ON public.%I',
      t, t
    );
    EXECUTE format(
      'CREATE POLICY %I_select_staff ON public.%I
         FOR SELECT TO authenticated
         USING (public.is_thapsus_staff())',
      t, t
    );
  END LOOP;
END $$;

-- Admin-only (no customer or staff visibility).
DO $$
DECLARE
  t TEXT;
  admin_only TEXT[] := ARRAY[
    'admin_logs','aml_flags','backups','email_logs','error_logs',
    '_migrations'
  ];
BEGIN
  FOREACH t IN ARRAY admin_only LOOP
    IF to_regclass('public.' || t) IS NULL THEN CONTINUE; END IF;
    EXECUTE format(
      'DROP POLICY IF EXISTS %I_select_admin ON public.%I',
      t, t
    );
    EXECUTE format(
      'CREATE POLICY %I_select_admin ON public.%I
         FOR SELECT TO authenticated
         USING (public.is_thapsus_admin())',
      t, t
    );
  END LOOP;
END $$;

-- ── Sensitive column lockdown: users.password ────────────────
-- The `password` column is legacy (current auth path uses
-- `password_hash`). RLS protects rows; column grants protect
-- specific columns even when the row passes the policy.
REVOKE SELECT (password) ON public.users FROM anon, authenticated;

-- ── Verify ───────────────────────────────────────────────────
DO $$
DECLARE
  total_tables INT;
  rls_on_count INT;
  policy_count INT;
BEGIN
  SELECT COUNT(*) INTO total_tables
    FROM pg_tables WHERE schemaname='public';
  SELECT COUNT(*) INTO rls_on_count
    FROM pg_tables WHERE schemaname='public' AND rowsecurity = true;
  SELECT COUNT(*) INTO policy_count
    FROM pg_policies WHERE schemaname='public';
  RAISE NOTICE 'RLS state: % of % public tables have RLS on, % policies installed',
    rls_on_count, total_tables, policy_count;
END $$;
