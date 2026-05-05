-- ============================================================
-- Migration 012 — Cover every foreign key with a B-tree index.
--
-- Linter finding (2026-04-29): 16 FK columns had no covering
-- index, which makes every JOIN scan the parent table. Each
-- `CREATE INDEX IF NOT EXISTS` below targets one of those
-- columns. Idempotent — re-running on a project that already
-- has the index is a no-op.
--
-- Naming convention: `idx_<table>_<column>` so the linter's
-- "unused index" sweep can find them by pattern later if any
-- turn out to be dead weight.
-- ============================================================

-- users.referred_by → users(id)
CREATE INDEX IF NOT EXISTS idx_users_referred_by
  ON public.users (referred_by);

-- orders.insurance_policy_id → insurance_policies(id)
CREATE INDEX IF NOT EXISTS idx_orders_insurance_policy_id
  ON public.orders (insurance_policy_id);

-- ticket_messages.sender_id → users(id)
CREATE INDEX IF NOT EXISTS idx_ticket_messages_sender_id
  ON public.ticket_messages (sender_id);

-- ticket_messages.ticket_id → tickets(id)
CREATE INDEX IF NOT EXISTS idx_ticket_messages_ticket_id
  ON public.ticket_messages (ticket_id);

-- admin_logs.admin_id → users(id)
CREATE INDEX IF NOT EXISTS idx_admin_logs_admin_id
  ON public.admin_logs (admin_id);

-- exchange_rates.updated_by → users(id)
CREATE INDEX IF NOT EXISTS idx_exchange_rates_updated_by
  ON public.exchange_rates (updated_by);

-- password_reset_tokens.user_id → users(id)
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user_id
  ON public.password_reset_tokens (user_id);

-- backups.created_by → users(id)
CREATE INDEX IF NOT EXISTS idx_backups_created_by
  ON public.backups (created_by);

-- customs_entries.consolidation_id → consolidations(id)
CREATE INDEX IF NOT EXISTS idx_customs_entries_consolidation_id
  ON public.customs_entries (consolidation_id);

-- pvoc_documents.parcel_id → orders(id)
CREATE INDEX IF NOT EXISTS idx_pvoc_documents_parcel_id
  ON public.pvoc_documents (parcel_id);

-- run_parcels.parcel_id → orders(id)
CREATE INDEX IF NOT EXISTS idx_run_parcels_parcel_id
  ON public.run_parcels (parcel_id);

-- agent_invoices.consolidation_id → consolidations(id)
CREATE INDEX IF NOT EXISTS idx_agent_invoices_consolidation_id
  ON public.agent_invoices (consolidation_id);

-- aml_flags.parcel_id → orders(id)
CREATE INDEX IF NOT EXISTS idx_aml_flags_parcel_id
  ON public.aml_flags (parcel_id);

-- aml_flags.resolved_by → users(id)
CREATE INDEX IF NOT EXISTS idx_aml_flags_resolved_by
  ON public.aml_flags (resolved_by);

-- nps_responses.parcel_id → orders(id)
CREATE INDEX IF NOT EXISTS idx_nps_responses_parcel_id
  ON public.nps_responses (parcel_id);

-- buy_for_me_orders.parcel_id → orders(id)
CREATE INDEX IF NOT EXISTS idx_buy_for_me_orders_parcel_id
  ON public.buy_for_me_orders (parcel_id);

-- ── Verify ───────────────────────────────────────────────────
DO $$
DECLARE
  uncovered INT;
BEGIN
  SELECT COUNT(*) INTO uncovered
  FROM pg_constraint c
  JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY(c.conkey)
  WHERE c.contype = 'f'
    AND c.connamespace = 'public'::regnamespace
    AND NOT EXISTS (
      SELECT 1 FROM pg_index i
       WHERE i.indrelid = c.conrelid
         AND a.attnum = ANY(i.indkey)
         AND i.indkey[0] = a.attnum
    );
  RAISE NOTICE 'FK columns still without a covering index: %', uncovered;
END $$;
