-- ============================================================
-- Migration 009 — RLS SELECT policies for the streaming tables
-- added by 007.
--
-- Why this matters:
--   Migration 007 added `notifications`, `tickets`, `ticket_messages`,
--   `buy_for_me_orders`, and `aml_flags` to the `supabase_realtime`
--   publication. Realtime respects RLS, so once these tables ship
--   with RLS DISABLED any anon caller can replay every customer's
--   support thread or notification stream. (See parity audit F8.)
--   This migration:
--
--     1. Enables RLS on the five tables.
--     2. Adds per-user SELECT policies for `authenticated` so the
--        iOS Supabase Realtime client only sees rows for the signed-in
--        user (or, for staff roles encoded in the JWT, the queues
--        they need).
--     3. Leaves writes to `service_role`. Express owns every write
--        path on these tables — iOS POSTs through `/api/tickets`,
--        `/api/notifications/:id/read`, etc. The anon JWT cannot
--        mutate.
--
-- ID model:
--   `users.id` is `text` holding a UUID string. JWT `sub` is set
--   by `utils/supabaseJwt.js` to `String(user.id)`. Supabase parses
--   `sub` as UUID for `auth.uid()`, so the policy compares
--   `user_id = auth.uid()::text` — the cast keeps Postgres from
--   complaining about text↔uuid even though both ends are UUID
--   shaped.
--
-- Role detection:
--   `mintSupabaseToken` writes `user_metadata.app_role` into the
--   token. `auth.jwt() -> 'user_metadata' ->> 'app_role'` reads it
--   back at policy evaluation time.
--
-- Idempotent: every CREATE POLICY is preceded by DROP POLICY IF EXISTS.
-- Run only AFTER 007 has put the five tables into the publication —
-- otherwise the channel filters never fire.
-- ============================================================

-- Helper: a SECURITY-INVOKER inline check used by several policies.
-- Returning TRUE for staff roles short-circuits the user_id filter.
CREATE OR REPLACE FUNCTION public.is_thapsus_staff()
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
AS $$
  SELECT COALESCE(
    auth.jwt() -> 'user_metadata' ->> 'app_role',
    ''
  ) IN ('admin','operator','clearing_agent','rider');
$$;

CREATE OR REPLACE FUNCTION public.is_thapsus_admin()
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
AS $$
  SELECT COALESCE(
    auth.jwt() -> 'user_metadata' ->> 'app_role',
    ''
  ) = 'admin';
$$;

-- ── notifications ──────────────────────────────────────────────
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS notifications_select_self ON public.notifications;
CREATE POLICY notifications_select_self
  ON public.notifications
  FOR SELECT
  TO authenticated
  USING (
    user_id = auth.uid()::text
    OR public.is_thapsus_admin()
  );

DROP POLICY IF EXISTS notifications_write_service_role ON public.notifications;
CREATE POLICY notifications_write_service_role
  ON public.notifications
  FOR ALL
  TO service_role
  USING (true)
  WITH CHECK (true);

-- ── tickets ────────────────────────────────────────────────────
ALTER TABLE public.tickets ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tickets_select_own ON public.tickets;
CREATE POLICY tickets_select_own
  ON public.tickets
  FOR SELECT
  TO authenticated
  USING (
    user_id = auth.uid()::text
    OR public.is_thapsus_admin()
  );

DROP POLICY IF EXISTS tickets_write_service_role ON public.tickets;
CREATE POLICY tickets_write_service_role
  ON public.tickets
  FOR ALL
  TO service_role
  USING (true)
  WITH CHECK (true);

-- ── ticket_messages ────────────────────────────────────────────
ALTER TABLE public.ticket_messages ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS ticket_messages_select_own ON public.ticket_messages;
CREATE POLICY ticket_messages_select_own
  ON public.ticket_messages
  FOR SELECT
  TO authenticated
  USING (
    public.is_thapsus_admin()
    OR EXISTS (
      SELECT 1 FROM public.tickets t
       WHERE t.id = ticket_messages.ticket_id
         AND t.user_id = auth.uid()::text
    )
  );

DROP POLICY IF EXISTS ticket_messages_write_service_role ON public.ticket_messages;
CREATE POLICY ticket_messages_write_service_role
  ON public.ticket_messages
  FOR ALL
  TO service_role
  USING (true)
  WITH CHECK (true);

-- ── buy_for_me_orders ──────────────────────────────────────────
ALTER TABLE public.buy_for_me_orders ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS buy_for_me_select_own_or_staff ON public.buy_for_me_orders;
CREATE POLICY buy_for_me_select_own_or_staff
  ON public.buy_for_me_orders
  FOR SELECT
  TO authenticated
  USING (
    user_id = auth.uid()::text
    OR public.is_thapsus_staff()
  );

DROP POLICY IF EXISTS buy_for_me_write_service_role ON public.buy_for_me_orders;
CREATE POLICY buy_for_me_write_service_role
  ON public.buy_for_me_orders
  FOR ALL
  TO service_role
  USING (true)
  WITH CHECK (true);

-- ── aml_flags ──────────────────────────────────────────────────
-- Admin-only queue. Customers must NOT see they were flagged.
ALTER TABLE public.aml_flags ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS aml_flags_select_admin ON public.aml_flags;
CREATE POLICY aml_flags_select_admin
  ON public.aml_flags
  FOR SELECT
  TO authenticated
  USING (public.is_thapsus_admin());

DROP POLICY IF EXISTS aml_flags_write_service_role ON public.aml_flags;
CREATE POLICY aml_flags_write_service_role
  ON public.aml_flags
  FOR ALL
  TO service_role
  USING (true)
  WITH CHECK (true);

-- ── Verify ─────────────────────────────────────────────────────
DO $$
DECLARE
  t TEXT;
  rls_enabled BOOLEAN;
  policy_count INT;
BEGIN
  FOR t IN SELECT unnest(ARRAY[
    'notifications','tickets','ticket_messages',
    'buy_for_me_orders','aml_flags'
  ]) LOOP
    SELECT rowsecurity INTO rls_enabled
      FROM pg_tables WHERE schemaname='public' AND tablename=t;
    SELECT count(*) INTO policy_count
      FROM pg_policies WHERE schemaname='public' AND tablename=t;
    RAISE NOTICE '%: rls=%, policies=%', t, rls_enabled, policy_count;
  END LOOP;
END $$;
