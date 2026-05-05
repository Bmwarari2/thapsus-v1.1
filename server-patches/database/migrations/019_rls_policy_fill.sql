-- Migration 019 — RLS policy fill + password column re-revoke
--
-- Post-018 state on project zzdfxsfuhosuqvsugtfd:
--   - RLS is ENABLED + FORCED on all 40 public tables. ✅
--   - 25 tables have a `self_or_staff` SELECT policy. ✅
--   - 15 tables have RLS on but ZERO policies, so authenticated reads return
--     empty: `_migrations, admin_logs, agent_invoices, backups, customs_entries,
--     email_logs, error_logs, exchange_rates, fees, password_reset_tokens,
--     pricing_tiers, prohibited_items, promotions, shipping_rates,
--     tudor_invoices`.
--   - `users.password` is still SELECTable by anon + authenticated despite 018's
--     REVOKE — re-issue here (idempotent).
--
-- This migration ONLY adds SELECT policies and re-revokes the password column.
-- Writes continue to flow through the Express service_role pool, which bypasses
-- RLS by default. `password_reset_tokens` intentionally stays policy-less:
-- nothing on the customer surface should ever read it; the password-reset flow
-- is server-side via service_role.
--
-- Idempotent: every CREATE POLICY is preceded by DROP POLICY IF EXISTS.

-- ── Shared catalogues — any authenticated user can read ────────────────────
DROP POLICY IF EXISTS "exchange_rates authed read" ON public.exchange_rates;
CREATE POLICY "exchange_rates authed read" ON public.exchange_rates
  FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS "fees authed read" ON public.fees;
CREATE POLICY "fees authed read" ON public.fees
  FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS "pricing_tiers authed read" ON public.pricing_tiers;
CREATE POLICY "pricing_tiers authed read" ON public.pricing_tiers
  FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS "prohibited_items authed read" ON public.prohibited_items;
CREATE POLICY "prohibited_items authed read" ON public.prohibited_items
  FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS "promotions authed read" ON public.promotions;
CREATE POLICY "promotions authed read" ON public.promotions
  FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS "shipping_rates authed read" ON public.shipping_rates;
CREATE POLICY "shipping_rates authed read" ON public.shipping_rates
  FOR SELECT TO authenticated USING (true);

-- ── Staff-only operator/clearing-agent surfaces ────────────────────────────
DROP POLICY IF EXISTS "agent_invoices staff_only" ON public.agent_invoices;
CREATE POLICY "agent_invoices staff_only" ON public.agent_invoices
  FOR SELECT TO authenticated USING (public.is_thapsus_staff());

DROP POLICY IF EXISTS "customs_entries staff_only" ON public.customs_entries;
CREATE POLICY "customs_entries staff_only" ON public.customs_entries
  FOR SELECT TO authenticated USING (public.is_thapsus_staff());

DROP POLICY IF EXISTS "tudor_invoices staff_only" ON public.tudor_invoices;
CREATE POLICY "tudor_invoices staff_only" ON public.tudor_invoices
  FOR SELECT TO authenticated USING (public.is_thapsus_staff());

-- ── Admin-only operational tables ──────────────────────────────────────────
DROP POLICY IF EXISTS "admin_logs admin_only" ON public.admin_logs;
CREATE POLICY "admin_logs admin_only" ON public.admin_logs
  FOR SELECT TO authenticated USING (public.is_thapsus_admin());

DROP POLICY IF EXISTS "email_logs admin_only" ON public.email_logs;
CREATE POLICY "email_logs admin_only" ON public.email_logs
  FOR SELECT TO authenticated USING (public.is_thapsus_admin());

DROP POLICY IF EXISTS "error_logs admin_only" ON public.error_logs;
CREATE POLICY "error_logs admin_only" ON public.error_logs
  FOR SELECT TO authenticated USING (public.is_thapsus_admin());

DROP POLICY IF EXISTS "backups admin_only" ON public.backups;
CREATE POLICY "backups admin_only" ON public.backups
  FOR SELECT TO authenticated USING (public.is_thapsus_admin());

DROP POLICY IF EXISTS "_migrations admin_only" ON public._migrations;
CREATE POLICY "_migrations admin_only" ON public._migrations
  FOR SELECT TO authenticated USING (public.is_thapsus_admin());

-- ── password_reset_tokens stays intentionally policy-less ──────────────────
-- Server uses service_role to insert/select; no client should ever touch it.

-- ── Re-revoke users.password from API roles ────────────────────────────────
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_schema='public' AND table_name='users' AND column_name='password') THEN
    REVOKE SELECT (password) ON public.users FROM anon;
    REVOKE SELECT (password) ON public.users FROM authenticated;
  END IF;
END $$;

-- ── Verify ─────────────────────────────────────────────────────────────────
DO $$
DECLARE
  unpolicied_count int;
  pw_anon boolean;
  pw_auth boolean;
BEGIN
  SELECT count(*) INTO unpolicied_count
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    LEFT JOIN pg_policies p
      ON p.schemaname = n.nspname AND p.tablename = c.relname
   WHERE n.nspname = 'public'
     AND c.relkind = 'r'
     AND c.relrowsecurity = true
     AND p.policyname IS NULL
     -- expected to remain policy-less:
     AND c.relname NOT IN ('password_reset_tokens');

  IF unpolicied_count > 0 THEN
    RAISE WARNING 'still % public table(s) with RLS on but no policy', unpolicied_count;
  END IF;

  SELECT has_column_privilege('anon','public.users','password','SELECT') INTO pw_anon;
  SELECT has_column_privilege('authenticated','public.users','password','SELECT') INTO pw_auth;
  IF pw_anon OR pw_auth THEN
    RAISE WARNING 'users.password still readable by anon=% authenticated=%', pw_anon, pw_auth;
  ELSE
    RAISE NOTICE 'users.password locked from anon + authenticated.';
  END IF;
END $$;
