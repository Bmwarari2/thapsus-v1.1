-- 017_nps_invitations.sql
-- ---------------------------------------------------------------------------
-- NPS auto-trigger backend hook (parity audit followup #6 closeout).
--
-- Until now the iOS NpsAutoPromptModifier deduped survey prompts purely in
-- UserDefaults. That breaks multi-device usage and gives admins no way to
-- measure response rate (we can count nps_responses but not how many were
-- ever asked). This migration adds nps_invitations as the canonical "we
-- offered this customer a survey for parcel X" log.
--
-- Server-side: routes/lastMile.js (POD) and routes/admin.js (bulk update)
-- INSERT a row whenever they flip an order to delivered, ON CONFLICT DO
-- NOTHING so the trigger is idempotent regardless of how the order arrived
-- there. routes/nps.js stamps responded_at on score submission.
-- ---------------------------------------------------------------------------

BEGIN;

CREATE TABLE IF NOT EXISTS public.nps_invitations (
  id            TEXT PRIMARY KEY,
  user_id       TEXT NOT NULL REFERENCES public.users(id)  ON DELETE CASCADE,
  order_id      TEXT NOT NULL REFERENCES public.orders(id) ON DELETE CASCADE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  responded_at  TIMESTAMPTZ,
  CONSTRAINT nps_invitations_order_id_unique UNIQUE (order_id)
);

CREATE INDEX IF NOT EXISTS idx_nps_invitations_user
  ON public.nps_invitations(user_id) WHERE responded_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_nps_invitations_pending
  ON public.nps_invitations(created_at) WHERE responded_at IS NULL;

-- Backfill: every order that's already delivered should have an invitation
-- so existing customers can finish surveys post-deploy without us waiting
-- for the next delivery.
INSERT INTO public.nps_invitations (id, user_id, order_id, created_at)
SELECT
  'NPSI-' || substr(md5(random()::text), 1, 12),
  o.user_id,
  o.id,
  COALESCE(o.updated_at, o.created_at, NOW())
FROM public.orders o
WHERE o.status = 'delivered'
  AND o.user_id IS NOT NULL
ON CONFLICT (order_id) DO NOTHING;

-- Stamp responded_at for any invitation whose customer already left a score
-- (re-running this migration is safe — match by user+parcel).
UPDATE public.nps_invitations inv
   SET responded_at = r.created_at
  FROM public.nps_responses r
 WHERE r.parcel_id = inv.order_id
   AND r.user_id   = inv.user_id
   AND inv.responded_at IS NULL;

-- ── RLS — owner sees their own invites; admins see all ────────────────────
ALTER TABLE public.nps_invitations ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS nps_invitations_self_read ON public.nps_invitations;
CREATE POLICY nps_invitations_self_read
  ON public.nps_invitations
  FOR SELECT
  USING (
    user_id = (auth.jwt() ->> 'sub')
    OR EXISTS (
      SELECT 1 FROM public.users u
      WHERE u.id = (auth.jwt() ->> 'sub') AND u.role = 'admin'
    )
  );

-- All writes go through the service-role Express server, never PostgREST.
REVOKE INSERT, UPDATE, DELETE ON public.nps_invitations FROM anon, authenticated;

DO $$
DECLARE
  total INT;
BEGIN
  SELECT COUNT(*) INTO total FROM public.nps_invitations;
  RAISE NOTICE 'nps_invitations ready, % row(s) (incl. backfill)', total;
END $$;

COMMIT;
