-- ============================================================
-- Migration 011 — Pin `search_path` on every helper function.
--
-- Linter finding: 6 public SQL functions (`is_thapsus_staff`,
-- `is_thapsus_admin`, `jwt_role`, `is_staff`, `is_admin`,
-- `current_user_id`) ran with a mutable `search_path`. Per
-- Supabase guidance that's a role-safety risk — the caller's
-- environment can swap in an unexpected schema during object
-- resolution.
--
-- Fix: re-declare each function with a fixed
-- `SET search_path = public, pg_temp`. Bodies stay identical to
-- what we found in `pg_proc` on 2026-04-29, just hardened.
--
-- Idempotent — `CREATE OR REPLACE FUNCTION` is the standard
-- swap-in-place. Drop + recreate avoided to preserve any RLS
-- policies that already reference these helpers.
-- ============================================================

-- 1. JWT role extractor (reads PostgREST claim header)
CREATE OR REPLACE FUNCTION public.jwt_role()
RETURNS TEXT
LANGUAGE SQL
STABLE
SET search_path = public, pg_temp
AS $$
  SELECT COALESCE(
    (current_setting('request.jwt.claims', true)::jsonb ->> 'role'),
    'anon'
  );
$$;

-- 2. JWT subject (current user id from `sub` claim)
CREATE OR REPLACE FUNCTION public.current_user_id()
RETURNS TEXT
LANGUAGE SQL
STABLE
SET search_path = public, pg_temp
AS $$
  SELECT NULLIF(
    current_setting('request.jwt.claims', true)::jsonb ->> 'sub',
    ''
  );
$$;

-- 3. Generic admin / staff predicates (legacy — uses `jwt_role()`)
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SET search_path = public, pg_temp
AS $$
  SELECT public.jwt_role() = 'admin';
$$;

CREATE OR REPLACE FUNCTION public.is_staff()
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SET search_path = public, pg_temp
AS $$
  SELECT public.jwt_role() IN ('operator', 'admin');
$$;

-- 4. Thapsus-specific admin / staff predicates (read
-- `user_metadata.app_role` from the Supabase JWT — minted by
-- `utils/supabaseJwt.js`)
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

-- ── Verify ───────────────────────────────────────────────────
DO $$
DECLARE
  unsafe INT;
BEGIN
  SELECT COUNT(*) INTO unsafe
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
   WHERE n.nspname = 'public'
     AND p.proname IN ('is_thapsus_staff','is_thapsus_admin','jwt_role',
                       'is_staff','is_admin','current_user_id')
     AND p.proconfig IS NULL;
  IF unsafe > 0 THEN
    RAISE WARNING 'search_path still mutable on % helper functions', unsafe;
  ELSE
    RAISE NOTICE 'search_path pinned on all 6 helpers ✓';
  END IF;
END $$;
