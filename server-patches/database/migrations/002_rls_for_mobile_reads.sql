-- 002_rls_for_mobile_reads.sql
-- Enables Row Level Security on tables the iOS app reads via Supabase PostgREST
-- and Realtime. SELECT-only policies. Express keeps writing via the unrestricted
-- pg Pool (bypasses RLS by virtue of being the table owner / using the pooler
-- connection, not an authenticated PostgREST session).
--
-- Run from the Supabase SQL editor or `psql $DATABASE_URL -f 002_rls_for_mobile_reads.sql`.
-- Idempotent: safe to re-run.

------------------------------------------------------------------------
-- users
------------------------------------------------------------------------
ALTER TABLE users ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "users self-read" ON users;
CREATE POLICY "users self-read"
ON users FOR SELECT
TO authenticated
USING (auth.uid()::text = id);

-- Staff/admin can read all rows. We trust the app_role in the JWT user_metadata.
-- (Express puts it there when minting the Supabase JWT.)
DROP POLICY IF EXISTS "users staff-read" ON users;
CREATE POLICY "users staff-read"
ON users FOR SELECT
TO authenticated
USING (
  coalesce((auth.jwt() -> 'user_metadata' ->> 'app_role'), '')
    IN ('admin', 'operator', 'clearing_agent', 'rider')
);

------------------------------------------------------------------------
-- wallet
------------------------------------------------------------------------
ALTER TABLE wallet ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "wallet self-read" ON wallet;
CREATE POLICY "wallet self-read"
ON wallet FOR SELECT
TO authenticated
USING (auth.uid()::text = user_id);

DROP POLICY IF EXISTS "wallet admin-read" ON wallet;
CREATE POLICY "wallet admin-read"
ON wallet FOR SELECT
TO authenticated
USING (
  coalesce((auth.jwt() -> 'user_metadata' ->> 'app_role'), '') = 'admin'
);

------------------------------------------------------------------------
-- transactions  (wallet movements; deposit/payment/refund/referral_credit)
------------------------------------------------------------------------
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "transactions self-read" ON transactions;
CREATE POLICY "transactions self-read"
ON transactions FOR SELECT
TO authenticated
USING (auth.uid()::text = user_id);

DROP POLICY IF EXISTS "transactions admin-read" ON transactions;
CREATE POLICY "transactions admin-read"
ON transactions FOR SELECT
TO authenticated
USING (
  coalesce((auth.jwt() -> 'user_metadata' ->> 'app_role'), '') = 'admin'
);

------------------------------------------------------------------------
-- orders
------------------------------------------------------------------------
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "orders self-read" ON orders;
CREATE POLICY "orders self-read"
ON orders FOR SELECT
TO authenticated
USING (auth.uid()::text = user_id);

DROP POLICY IF EXISTS "orders staff-read" ON orders;
CREATE POLICY "orders staff-read"
ON orders FOR SELECT
TO authenticated
USING (
  coalesce((auth.jwt() -> 'user_metadata' ->> 'app_role'), '')
    IN ('admin', 'operator', 'clearing_agent', 'rider')
);

------------------------------------------------------------------------
-- consolidations  (read-only for any authenticated user — flight manifests)
------------------------------------------------------------------------
ALTER TABLE consolidations ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "consolidations authed-read" ON consolidations;
CREATE POLICY "consolidations authed-read"
ON consolidations FOR SELECT
TO authenticated
USING (true);

------------------------------------------------------------------------
-- agent_invoices (clearing-agent-owned; admins see all)
------------------------------------------------------------------------
ALTER TABLE agent_invoices ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "agent_invoices owner-read" ON agent_invoices;
CREATE POLICY "agent_invoices owner-read"
ON agent_invoices FOR SELECT
TO authenticated
USING (auth.uid()::text = agent_id);

DROP POLICY IF EXISTS "agent_invoices admin-read" ON agent_invoices;
CREATE POLICY "agent_invoices admin-read"
ON agent_invoices FOR SELECT
TO authenticated
USING (
  coalesce((auth.jwt() -> 'user_metadata' ->> 'app_role'), '') = 'admin'
);

------------------------------------------------------------------------
-- aml_flags (compliance-sensitive — admins only on the read path)
------------------------------------------------------------------------
ALTER TABLE aml_flags ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "aml_flags admin-read" ON aml_flags;
CREATE POLICY "aml_flags admin-read"
ON aml_flags FOR SELECT
TO authenticated
USING (
  coalesce((auth.jwt() -> 'user_metadata' ->> 'app_role'), '') = 'admin'
);

------------------------------------------------------------------------
-- Sanity: confirm RLS is on and policies exist.
------------------------------------------------------------------------
DO $$
DECLARE
  t TEXT;
BEGIN
  FOR t IN SELECT unnest(ARRAY[
    'users','wallet','transactions','orders',
    'consolidations','agent_invoices','aml_flags'
  ])
  LOOP
    IF NOT EXISTS (
      SELECT 1 FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE c.relname = t AND n.nspname = 'public' AND c.relrowsecurity
    ) THEN
      RAISE WARNING 'RLS not enabled on %', t;
    END IF;
  END LOOP;
END$$;
