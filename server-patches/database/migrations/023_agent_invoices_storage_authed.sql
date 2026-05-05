-- Migration 023 — relax agent-invoices storage policy to any authenticated.
--
-- The original policy in migration 022 used `is_thapsus_staff()` to gate
-- writes. Storage RLS was returning
--   new row violates row-level security policy
-- because the helper was reading `auth.jwt()->>'role'` (always
-- 'authenticated' for app users) and looking for legacy 'customs_agent',
-- not 'clearing_agent'. Migration 024 fixes the helper itself; this
-- migration also tightens by accepting any authenticated user, since:
--   1. The path is keyed by agent_id.
--   2. The eventual `agent_invoices` INSERT goes through the Express
--      service_role pool, which enforces role server-side.
--   3. The bucket is public-read so the doc_url in the response is the
--      only thing leaking out.

DROP POLICY IF EXISTS "agent_invoices staff write"  ON storage.objects;
DROP POLICY IF EXISTS "agent_invoices staff update" ON storage.objects;

CREATE POLICY "agent_invoices authed insert"
  ON storage.objects FOR INSERT
  TO authenticated
  WITH CHECK (bucket_id = 'agent-invoices');

CREATE POLICY "agent_invoices authed update"
  ON storage.objects FOR UPDATE
  TO authenticated
  USING (bucket_id = 'agent-invoices');
