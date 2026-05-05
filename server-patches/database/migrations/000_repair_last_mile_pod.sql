-- ============================================================
-- Repair migration — run BEFORE re-attempting 001 if the previous
-- attempt left `last_mile_runs` or `pod_events` half-built (the
-- common symptom is "ERROR: 42703: column rider_id does not exist"
-- on the CREATE INDEX line).
--
-- CREATE TABLE IF NOT EXISTS does nothing once the table is in
-- place, so a partial-shape table never gets its missing columns
-- back. ALTER TABLE … ADD COLUMN IF NOT EXISTS fills the gap.
--
-- Idempotent: re-runnable as many times as you like.
-- ============================================================

ALTER TABLE IF EXISTS public.last_mile_runs
  ADD COLUMN IF NOT EXISTS rider_id        TEXT REFERENCES public.users(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS zone            TEXT,
  ADD COLUMN IF NOT EXISTS run_date        DATE,
  ADD COLUMN IF NOT EXISTS status          TEXT,
  ADD COLUMN IF NOT EXISTS total_stops     INTEGER DEFAULT 0,
  ADD COLUMN IF NOT EXISTS completed_stops INTEGER DEFAULT 0,
  ADD COLUMN IF NOT EXISTS notes           TEXT,
  ADD COLUMN IF NOT EXISTS created_at      TIMESTAMPTZ DEFAULT NOW(),
  ADD COLUMN IF NOT EXISTS updated_at      TIMESTAMPTZ DEFAULT NOW();

ALTER TABLE IF EXISTS public.pod_events
  ADD COLUMN IF NOT EXISTS parcel_id       TEXT,
  ADD COLUMN IF NOT EXISTS run_id          TEXT REFERENCES public.last_mile_runs(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS rider_id        TEXT REFERENCES public.users(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS result          TEXT,
  ADD COLUMN IF NOT EXISTS captured_at     TIMESTAMPTZ DEFAULT NOW(),
  ADD COLUMN IF NOT EXISTS photo_url       TEXT,
  ADD COLUMN IF NOT EXISTS signature_url   TEXT,
  ADD COLUMN IF NOT EXISTS otp_used        TEXT,
  ADD COLUMN IF NOT EXISTS recipient_name  TEXT,
  ADD COLUMN IF NOT EXISTS recipient_phone TEXT,
  ADD COLUMN IF NOT EXISTS notes           TEXT,
  ADD COLUMN IF NOT EXISTS created_at      TIMESTAMPTZ DEFAULT NOW();

-- After this runs cleanly, re-attempt 001_framework_v2_additions.sql.
