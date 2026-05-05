-- Migration 027 — break the is_thapsus_staff / is_thapsus_admin recursion
-- that's been crashing every authenticated read with `54001 stack depth
-- limit exceeded`.
--
-- Symptom (PostgREST log, 2026-05-02):
--     GET /packages?select=*  →  500
--     {"code":"54001","message":"stack depth limit exceeded",
--      "hint":"Increase the configuration parameter \"max_stack_depth\""}
--
-- Cause:
--   Migration 024 rewrote the helpers as SECURITY DEFINER functions that
--   `SELECT role FROM public.users WHERE id = sub`. SECURITY DEFINER only
--   bypasses RLS when the function-owner role has BYPASSRLS. On this
--   deployment it doesn't, so the inner SELECT against `users` re-fires
--   the `users_select_self_or_admin` policy, which itself calls
--   `is_thapsus_admin()`, which selects from `users` again → infinite
--   recursion → stack overflow → 500 on every read of any self-or-staff
--   table (packages, orders, wallet, transactions, notifications,
--   tickets, dsar_requests, insurance_policies, buy_for_me_orders,
--   ticket_messages, …).
--
-- Fix: restore the JWT-claim-based helpers (the pattern migration 010
-- shipped with originally). The Supabase JWT already carries the app
-- role under `user_metadata.app_role` — minted by
-- `utils/supabaseJwt.js::mintSupabaseToken`. No table read inside the
-- helper → no RLS evaluation → no recursion.
--
-- Migration 011 hardened the search_path; migration 024 added the
-- `clearing_agent` role. Both concerns are preserved here.
--
-- Idempotent via CREATE OR REPLACE.

CREATE OR REPLACE FUNCTION public.is_thapsus_admin()
  RETURNS boolean
  LANGUAGE sql
  STABLE
  SET search_path = public, pg_temp
AS $$
  SELECT COALESCE(
    auth.jwt() -> 'user_metadata' ->> 'app_role',
    ''
  ) = 'admin';
$$;

CREATE OR REPLACE FUNCTION public.is_thapsus_staff()
  RETURNS boolean
  LANGUAGE sql
  STABLE
  SET search_path = public, pg_temp
AS $$
  SELECT COALESCE(
    auth.jwt() -> 'user_metadata' ->> 'app_role',
    ''
  ) IN ('admin','operator','clearing_agent','rider');
$$;

-- Sanity check: confirm the helpers no longer touch `users`.
DO $$
DECLARE
  bad_count int;
BEGIN
  SELECT count(*) INTO bad_count
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
   WHERE n.nspname = 'public'
     AND p.proname IN ('is_thapsus_staff','is_thapsus_admin')
     AND pg_get_functiondef(p.oid) ILIKE '%FROM public.users%';
  IF bad_count > 0 THEN
    RAISE EXCEPTION
      'is_thapsus_staff/is_thapsus_admin still query public.users — recursion not fixed';
  END IF;
  RAISE NOTICE 'helpers no longer query public.users — recursion broken';
END $$;
