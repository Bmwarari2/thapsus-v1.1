-- 016_consolidations_uuid_alignment.sql
-- ---------------------------------------------------------------------------
-- Schema-drift migration: align on-disk repo schema with live Supabase.
--
-- Background: `consolidations.id` and `pallets.id` were declared as TEXT in
-- the original framework_v2 migration (001_framework_v2_additions.sql), but
-- the live database has them as `uuid`. The mobile webapp clients call
-- `uuidv4()` client-side (PR #18 patched routes/consolidationsV2.js to stop
-- using `shortId('CON-...')` which produced text-only ids and crashed the
-- live uuid columns). Without this migration, fresh installs from the repo
-- would land back on TEXT and PR #18's writes would fail again.
--
-- Strategy: idempotent. For every column we want to be `uuid`, we only ALTER
-- if `information_schema.columns.data_type = 'text'`. Foreign keys are
-- dropped via pg_constraint introspection before the parent ALTER runs and
-- recreated after, so this script is safe whether the columns are already
-- `uuid` (no-op) or still `text` (full conversion).
--
-- Tables touched:
--   consolidations.id              (PK)
--   pallets.id                     (PK)
--   pallets.consolidation_id       (FK -> consolidations.id, CASCADE)
--   pvoc_documents.consolidation_id(FK -> consolidations.id, CASCADE)
--   orders.consolidation_id        (FK -> consolidations.id, SET NULL)
--   packages.consolidation_id      (FK -> consolidations.id, SET NULL)
--   agent_invoices.consolidation_id(FK -> consolidations.id, SET NULL)
--   tudor_invoices.consolidation_id(FK -> consolidations.id, SET NULL)
-- ---------------------------------------------------------------------------

BEGIN;

-- ── 1. Drop every FK referencing consolidations(id) or pallets(id) ─────────
-- Discovered via pg_constraint so we don't depend on conventional names.
DO $$
DECLARE
  rec RECORD;
BEGIN
  FOR rec IN
    SELECT
      con.conname AS constraint_name,
      n.nspname   AS schema_name,
      c.relname   AS table_name
    FROM pg_constraint con
    JOIN pg_class c ON c.oid = con.conrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    JOIN pg_class p ON p.oid = con.confrelid
    WHERE con.contype = 'f'
      AND n.nspname = 'public'
      AND p.relname IN ('consolidations', 'pallets')
  LOOP
    EXECUTE format(
      'ALTER TABLE %I.%I DROP CONSTRAINT %I',
      rec.schema_name, rec.table_name, rec.constraint_name
    );
    RAISE NOTICE 'dropped FK %.%.%', rec.schema_name, rec.table_name, rec.constraint_name;
  END LOOP;
END $$;

-- ── 2. Convert primary keys (parents) to uuid where currently text ─────────
DO $$
BEGIN
  IF (SELECT data_type FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'consolidations' AND column_name = 'id') = 'text' THEN
    ALTER TABLE public.consolidations ALTER COLUMN id TYPE uuid USING id::uuid;
    RAISE NOTICE 'consolidations.id -> uuid';
  END IF;

  IF (SELECT data_type FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'pallets' AND column_name = 'id') = 'text' THEN
    ALTER TABLE public.pallets ALTER COLUMN id TYPE uuid USING id::uuid;
    RAISE NOTICE 'pallets.id -> uuid';
  END IF;
END $$;

-- ── 3. Convert child consolidation_id columns to uuid ──────────────────────
DO $$
DECLARE
  child RECORD;
BEGIN
  FOR child IN
    SELECT unnest(ARRAY['pallets','pvoc_documents','orders','packages','agent_invoices','tudor_invoices']) AS table_name
  LOOP
    IF EXISTS (
      SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = child.table_name
          AND column_name = 'consolidation_id'
          AND data_type = 'text'
    ) THEN
      EXECUTE format(
        'ALTER TABLE public.%I ALTER COLUMN consolidation_id TYPE uuid USING consolidation_id::uuid',
        child.table_name
      );
      RAISE NOTICE '%.consolidation_id -> uuid', child.table_name;
    END IF;
  END LOOP;
END $$;

-- ── 4. Recreate FK constraints ─────────────────────────────────────────────
-- Each block runs only if the constraint isn't already present. ON DELETE
-- semantics match the original framework_v2 declarations.

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pallets_consolidation_id_fkey') THEN
    ALTER TABLE public.pallets
      ADD CONSTRAINT pallets_consolidation_id_fkey
      FOREIGN KEY (consolidation_id) REFERENCES public.consolidations(id) ON DELETE CASCADE;
  END IF;
END $$;

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pvoc_documents_consolidation_id_fkey') THEN
    ALTER TABLE public.pvoc_documents
      ADD CONSTRAINT pvoc_documents_consolidation_id_fkey
      FOREIGN KEY (consolidation_id) REFERENCES public.consolidations(id) ON DELETE CASCADE;
  END IF;
END $$;

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'orders_consolidation_id_fkey') THEN
    ALTER TABLE public.orders
      ADD CONSTRAINT orders_consolidation_id_fkey
      FOREIGN KEY (consolidation_id) REFERENCES public.consolidations(id) ON DELETE SET NULL;
  END IF;
END $$;

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'packages_consolidation_id_fkey') THEN
    ALTER TABLE public.packages
      ADD CONSTRAINT packages_consolidation_id_fkey
      FOREIGN KEY (consolidation_id) REFERENCES public.consolidations(id) ON DELETE SET NULL;
  END IF;
END $$;

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'agent_invoices_consolidation_id_fkey') THEN
    ALTER TABLE public.agent_invoices
      ADD CONSTRAINT agent_invoices_consolidation_id_fkey
      FOREIGN KEY (consolidation_id) REFERENCES public.consolidations(id) ON DELETE SET NULL;
  END IF;
END $$;

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tudor_invoices_consolidation_id_fkey') THEN
    ALTER TABLE public.tudor_invoices
      ADD CONSTRAINT tudor_invoices_consolidation_id_fkey
      FOREIGN KEY (consolidation_id) REFERENCES public.consolidations(id) ON DELETE SET NULL;
  END IF;
END $$;

-- ── 5. Verification ────────────────────────────────────────────────────────
DO $$
DECLARE
  bad_cols INT;
BEGIN
  SELECT COUNT(*) INTO bad_cols
  FROM information_schema.columns
  WHERE table_schema = 'public'
    AND data_type = 'text'
    AND (
      (table_name IN ('consolidations','pallets') AND column_name = 'id')
      OR (table_name IN ('pallets','pvoc_documents','orders','packages','agent_invoices','tudor_invoices')
          AND column_name = 'consolidation_id')
    );
  IF bad_cols > 0 THEN
    RAISE EXCEPTION 'schema-drift migration left % column(s) as text', bad_cols;
  END IF;
  RAISE NOTICE 'consolidations/pallets uuid alignment OK';
END $$;

COMMIT;
