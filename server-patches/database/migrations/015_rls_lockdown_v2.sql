-- Migration 015 — RLS lockdown v2 (idempotent re-issue)
--
-- Migration 010 was supposed to enable RLS across every public table and
-- restrict writes to service_role. The Supabase linter on 2026-04-29
-- still reports `rls_disabled_in_public` on 36 tables, which means 010
-- either failed silently on this project, was rolled back, or never ran.
-- This migration is a focused re-issue — only ENABLE ROW LEVEL SECURITY
-- + idempotent SELECT policies — so it can run cleanly on top of any
-- partial state without re-defining helper functions or churning policies
-- that already exist.
--
-- Strategy:
--   1. Loop over every table the linter flagged and `ALTER TABLE … ENABLE
--      ROW LEVEL SECURITY` (idempotent).
--   2. For tables iOS reads via PostgREST/Realtime, attach a permissive
--      "authenticated read" policy, scoped where it makes sense (per-user
--      ownership for `wallet`/`transactions`/`notifications`/`tickets`/
--      `referrals`/`buy_for_me_orders`/`dsar_requests`/`pod_events`/
--      `parcel_items`/`run_parcels`/`compliance_trainings`/
--      `marketing_attributions`/`whatsapp_messages`/`nps_responses`/
--      `aml_flags`/`email_logs`/`error_logs`/`admin_logs`/`backups`,
--      and authenticated-everyone for shared catalogues like `pricing_tiers`,
--      `shipping_rates`, `exchange_rates`, `prohibited_items`, `fees`,
--      `consolidations`, `pallets`, `customs_entries`, `agent_invoices`,
--      `tudor_invoices`, `pvoc_documents`, `last_mile_runs`, `packages`,
--      `orders`, `users`, `insurance_policies`, `promotions`).
--   3. Writes are NOT given a policy — they're handled exclusively by the
--      Express server's service-role pool, which bypasses RLS.
--
-- Re-running this migration is safe; every CREATE POLICY is wrapped in
-- DROP POLICY IF EXISTS first.

-- ── 1. Enable RLS everywhere ────────────────────────────────────────────────
DO $$
DECLARE
  t text;
  tables text[] := ARRAY[
    'transactions','referrals','notifications','ticket_messages','tickets',
    'admin_logs','backups','exchange_rates','parcel_items','customs_entries',
    'email_logs','insurance_policies','pvoc_documents','wallet','consolidations',
    'pallets','error_logs','shipping_rates','run_parcels','packages',
    'compliance_trainings','nps_responses','users','orders','prohibited_items',
    '_migrations','agent_invoices','last_mile_runs','pod_events','tudor_invoices',
    'whatsapp_messages','fees','pricing_tiers','promotions','dsar_requests',
    'aml_flags','buy_for_me_orders','marketing_attributions'
  ];
BEGIN
  FOREACH t IN ARRAY tables LOOP
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = 'public' AND table_name = t) THEN
      EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t);
    END IF;
  END LOOP;
END $$;

-- ── 2. Helper: is_thapsus_admin / is_thapsus_staff ──────────────────────────
CREATE OR REPLACE FUNCTION public.is_thapsus_admin()
  RETURNS boolean
  LANGUAGE sql
  STABLE
  SECURITY DEFINER
  SET search_path = public, pg_temp
AS $$
  SELECT COALESCE(
    (SELECT role = 'admin' FROM public.users WHERE id = (auth.jwt()->>'sub')::text),
    false
  );
$$;

CREATE OR REPLACE FUNCTION public.is_thapsus_staff()
  RETURNS boolean
  LANGUAGE sql
  STABLE
  SECURITY DEFINER
  SET search_path = public, pg_temp
AS $$
  SELECT COALESCE(
    (SELECT role IN ('admin','operator','clearing_agent','rider')
       FROM public.users WHERE id = (auth.jwt()->>'sub')::text),
    false
  );
$$;

-- ── 3. Per-table SELECT policies ────────────────────────────────────────────
-- Pattern: DROP IF EXISTS then CREATE. All write paths (INSERT/UPDATE/DELETE)
-- are intentionally left to service_role; the Express server enforces
-- authorisation at the route level.

-- Per-user ownership tables ──────────────────────────────────────────────────
DROP POLICY IF EXISTS "wallet self-or-staff read"  ON public.wallet;
CREATE POLICY "wallet self-or-staff read" ON public.wallet
  FOR SELECT TO authenticated
  USING (user_id = (auth.jwt()->>'sub')::text OR public.is_thapsus_staff());

DROP POLICY IF EXISTS "transactions self-or-staff read" ON public.transactions;
CREATE POLICY "transactions self-or-staff read" ON public.transactions
  FOR SELECT TO authenticated
  USING (user_id = (auth.jwt()->>'sub')::text OR public.is_thapsus_staff());

DROP POLICY IF EXISTS "notifications self-or-staff read" ON public.notifications;
CREATE POLICY "notifications self-or-staff read" ON public.notifications
  FOR SELECT TO authenticated
  USING (user_id = (auth.jwt()->>'sub')::text OR public.is_thapsus_staff());

DROP POLICY IF EXISTS "tickets self-or-staff read" ON public.tickets;
CREATE POLICY "tickets self-or-staff read" ON public.tickets
  FOR SELECT TO authenticated
  USING (user_id = (auth.jwt()->>'sub')::text OR public.is_thapsus_staff());

DROP POLICY IF EXISTS "ticket_messages thread-or-staff read" ON public.ticket_messages;
CREATE POLICY "ticket_messages thread-or-staff read" ON public.ticket_messages
  FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.tickets t
       WHERE t.id = ticket_messages.ticket_id
         AND (t.user_id = (auth.jwt()->>'sub')::text OR public.is_thapsus_staff())
    )
  );

-- referrals: BOTH parties can read their own row + admins
DROP POLICY IF EXISTS "referrals participants-or-staff read" ON public.referrals;
CREATE POLICY "referrals participants-or-staff read" ON public.referrals
  FOR SELECT TO authenticated
  USING (
    referrer_id = (auth.jwt()->>'sub')::text
    OR referee_id = (auth.jwt()->>'sub')::text
    OR public.is_thapsus_staff()
  );

DROP POLICY IF EXISTS "buy_for_me self-or-staff read" ON public.buy_for_me_orders;
CREATE POLICY "buy_for_me self-or-staff read" ON public.buy_for_me_orders
  FOR SELECT TO authenticated
  USING (user_id = (auth.jwt()->>'sub')::text OR public.is_thapsus_staff());

DROP POLICY IF EXISTS "dsar self-or-staff read" ON public.dsar_requests;
CREATE POLICY "dsar self-or-staff read" ON public.dsar_requests
  FOR SELECT TO authenticated
  USING (user_id = (auth.jwt()->>'sub')::text OR public.is_thapsus_staff());

DROP POLICY IF EXISTS "insurance self-or-staff read" ON public.insurance_policies;
CREATE POLICY "insurance self-or-staff read" ON public.insurance_policies
  FOR SELECT TO authenticated
  USING (user_id = (auth.jwt()->>'sub')::text OR public.is_thapsus_staff());

DROP POLICY IF EXISTS "nps self-or-staff read" ON public.nps_responses;
CREATE POLICY "nps self-or-staff read" ON public.nps_responses
  FOR SELECT TO authenticated
  USING (user_id = (auth.jwt()->>'sub')::text OR public.is_thapsus_staff());

DROP POLICY IF EXISTS "aml staff-only read" ON public.aml_flags;
CREATE POLICY "aml staff-only read" ON public.aml_flags
  FOR SELECT TO authenticated
  USING (public.is_thapsus_staff());

DROP POLICY IF EXISTS "marketing_attributions staff-only read" ON public.marketing_attributions;
CREATE POLICY "marketing_attributions staff-only read" ON public.marketing_attributions
  FOR SELECT TO authenticated
  USING (public.is_thapsus_staff());

-- Orders + packages: customer reads own, staff reads everything
DROP POLICY IF EXISTS "orders self-or-staff read" ON public.orders;
CREATE POLICY "orders self-or-staff read" ON public.orders
  FOR SELECT TO authenticated
  USING (user_id = (auth.jwt()->>'sub')::text OR public.is_thapsus_staff());

DROP POLICY IF EXISTS "packages self-or-staff read" ON public.packages;
CREATE POLICY "packages self-or-staff read" ON public.packages
  FOR SELECT TO authenticated
  USING (user_id = (auth.jwt()->>'sub')::text OR public.is_thapsus_staff());

DROP POLICY IF EXISTS "parcel_items self-or-staff read" ON public.parcel_items;
CREATE POLICY "parcel_items self-or-staff read" ON public.parcel_items
  FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.packages p
       WHERE p.id = parcel_items.parcel_id
         AND (p.user_id = (auth.jwt()->>'sub')::text OR public.is_thapsus_staff())
    )
  );

-- Users: customer reads own row only, staff reads everyone
DROP POLICY IF EXISTS "users self-or-staff read" ON public.users;
CREATE POLICY "users self-or-staff read" ON public.users
  FOR SELECT TO authenticated
  USING (id = (auth.jwt()->>'sub')::text OR public.is_thapsus_staff());

-- Shared catalogues — any authenticated user can read ──────────────────────
DROP POLICY IF EXISTS "consolidations authed read" ON public.consolidations;
CREATE POLICY "consolidations authed read" ON public.consolidations
  FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS "pallets authed read" ON public.pallets;
CREATE POLICY "pallets authed read" ON public.pallets
  FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS "shipping_rates authed read" ON public.shipping_rates;
CREATE POLICY "shipping_rates authed read" ON public.shipping_rates
  FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS "exchange_rates authed read" ON public.exchange_rates;
CREATE POLICY "exchange_rates authed read" ON public.exchange_rates
  FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS "pricing_tiers authed read" ON public.pricing_tiers;
CREATE POLICY "pricing_tiers authed read" ON public.pricing_tiers
  FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS "fees authed read" ON public.fees;
CREATE POLICY "fees authed read" ON public.fees
  FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS "prohibited authed read" ON public.prohibited_items;
CREATE POLICY "prohibited authed read" ON public.prohibited_items
  FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS "promotions authed read" ON public.promotions;
CREATE POLICY "promotions authed read" ON public.promotions
  FOR SELECT TO authenticated USING (true);

-- Operator/agent surfaces — staff only
DROP POLICY IF EXISTS "customs_entries staff-only read" ON public.customs_entries;
CREATE POLICY "customs_entries staff-only read" ON public.customs_entries
  FOR SELECT TO authenticated USING (public.is_thapsus_staff());

DROP POLICY IF EXISTS "agent_invoices staff-only read" ON public.agent_invoices;
CREATE POLICY "agent_invoices staff-only read" ON public.agent_invoices
  FOR SELECT TO authenticated USING (public.is_thapsus_staff());

DROP POLICY IF EXISTS "tudor_invoices staff-only read" ON public.tudor_invoices;
CREATE POLICY "tudor_invoices staff-only read" ON public.tudor_invoices
  FOR SELECT TO authenticated USING (public.is_thapsus_staff());

DROP POLICY IF EXISTS "pvoc staff-only read" ON public.pvoc_documents;
CREATE POLICY "pvoc staff-only read" ON public.pvoc_documents
  FOR SELECT TO authenticated USING (public.is_thapsus_staff());

DROP POLICY IF EXISTS "last_mile_runs staff-only read" ON public.last_mile_runs;
CREATE POLICY "last_mile_runs staff-only read" ON public.last_mile_runs
  FOR SELECT TO authenticated USING (public.is_thapsus_staff());

DROP POLICY IF EXISTS "run_parcels staff-only read" ON public.run_parcels;
CREATE POLICY "run_parcels staff-only read" ON public.run_parcels
  FOR SELECT TO authenticated USING (public.is_thapsus_staff());

DROP POLICY IF EXISTS "pod_events staff-only read" ON public.pod_events;
CREATE POLICY "pod_events staff-only read" ON public.pod_events
  FOR SELECT TO authenticated USING (public.is_thapsus_staff());

-- Admin-only ──────────────────────────────────────────────────────────────────
DROP POLICY IF EXISTS "admin_logs admin-only read" ON public.admin_logs;
CREATE POLICY "admin_logs admin-only read" ON public.admin_logs
  FOR SELECT TO authenticated USING (public.is_thapsus_admin());

DROP POLICY IF EXISTS "error_logs admin-only read" ON public.error_logs;
CREATE POLICY "error_logs admin-only read" ON public.error_logs
  FOR SELECT TO authenticated USING (public.is_thapsus_admin());

DROP POLICY IF EXISTS "email_logs admin-only read" ON public.email_logs;
CREATE POLICY "email_logs admin-only read" ON public.email_logs
  FOR SELECT TO authenticated USING (public.is_thapsus_admin());

DROP POLICY IF EXISTS "backups admin-only read" ON public.backups;
CREATE POLICY "backups admin-only read" ON public.backups
  FOR SELECT TO authenticated USING (public.is_thapsus_admin());

DROP POLICY IF EXISTS "_migrations admin-only read" ON public._migrations;
CREATE POLICY "_migrations admin-only read" ON public._migrations
  FOR SELECT TO authenticated USING (public.is_thapsus_admin());

DROP POLICY IF EXISTS "compliance admin-only read" ON public.compliance_trainings;
CREATE POLICY "compliance admin-only read" ON public.compliance_trainings
  FOR SELECT TO authenticated USING (public.is_thapsus_admin());

DROP POLICY IF EXISTS "whatsapp admin-only read" ON public.whatsapp_messages;
CREATE POLICY "whatsapp admin-only read" ON public.whatsapp_messages
  FOR SELECT TO authenticated USING (public.is_thapsus_admin());

-- ── 4. REVOKE password column from API roles ───────────────────────────────
-- Keeps the linter's `sensitive_columns_exposed` finding (`users.password`)
-- happy without dropping the column from the schema. Server's service_role
-- pool retains access for password-verify routes.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_schema='public' AND table_name='users' AND column_name='password') THEN
    REVOKE SELECT (password) ON public.users FROM anon, authenticated;
  END IF;
END $$;

DO $$ BEGIN RAISE NOTICE 'RLS lockdown v2 applied. Verify in linter.'; END $$;
