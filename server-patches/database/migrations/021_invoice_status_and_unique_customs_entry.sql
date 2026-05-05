-- Migration 021 — invoice_status enum widening + one-customs-entry-per-parcel
--
-- 1. invoice_status enum currently has {draft, issued, paid, void}, but the
--    server INSERTs 'submitted' on POST /api/agent-invoices and the admin
--    PATCH route accepts {submitted, approved, paid, rejected}. Submission
--    therefore fails with `invalid input value for enum invoice_status`.
--    Add the three missing values; legacy values stay around (no-op).
-- 2. customs_entries(parcel_id) has a non-unique index. Spec is one IDF per
--    parcel — make it UNIQUE so duplicate filings are rejected at the DB
--    level. Server should map 23505 → 409 in a separate webapp PR.
ALTER TYPE public.invoice_status ADD VALUE IF NOT EXISTS 'submitted';
ALTER TYPE public.invoice_status ADD VALUE IF NOT EXISTS 'approved';
ALTER TYPE public.invoice_status ADD VALUE IF NOT EXISTS 'rejected';

DROP INDEX IF EXISTS public.idx_customs_entries_parcel;
CREATE UNIQUE INDEX IF NOT EXISTS uniq_customs_entries_parcel
  ON public.customs_entries(parcel_id);
