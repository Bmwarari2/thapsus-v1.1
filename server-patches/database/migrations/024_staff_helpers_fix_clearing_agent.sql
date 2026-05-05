-- Migration 024 — restore users-table lookup in staff helpers + recognise
-- 'clearing_agent'.
--
-- After migration 011 (search_path fix) the live versions of
-- is_thapsus_staff / is_thapsus_admin had been rewritten to read
-- `auth.jwt()->>'role'` via jwt_role(). That claim is Supabase's reserved
-- 'authenticated' / 'anon' / 'service_role' tag — never the app role —
-- so every staff-only RLS policy gated on these helpers was silently
-- denying clearing_agents (and any other staff role).
--
-- Restore the public.users lookup + canonical staff list. SECURITY DEFINER
-- + pinned search_path so the helper bypasses RLS on `users` itself.
CREATE OR REPLACE FUNCTION public.is_thapsus_staff()
  RETURNS boolean
  LANGUAGE sql
  STABLE
  SECURITY DEFINER
  SET search_path = public, pg_temp
AS $$
  SELECT COALESCE(
    (SELECT role IN ('admin','operator','clearing_agent','rider')
       FROM public.users
      WHERE id = (auth.jwt()->>'sub')::text),
    false
  );
$$;

CREATE OR REPLACE FUNCTION public.is_thapsus_admin()
  RETURNS boolean
  LANGUAGE sql
  STABLE
  SECURITY DEFINER
  SET search_path = public, pg_temp
AS $$
  SELECT COALESCE(
    (SELECT role = 'admin'
       FROM public.users
      WHERE id = (auth.jwt()->>'sub')::text),
    false
  );
$$;
