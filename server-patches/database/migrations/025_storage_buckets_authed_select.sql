-- Migration 025 — let storage clients see public buckets.
--
-- storage.buckets had RLS enabled but no SELECT policy. The Supabase
-- Storage upload flow does `SELECT * FROM storage.buckets WHERE name = $1`
-- before writing the object; with no policy the SELECT returns zero rows
-- and the API surfaces the misleading "new row violates row-level
-- security policy" error against storage.objects, not the underlying
-- bucket lookup that actually failed.
--
-- Re-enable the lookup for any anon/authenticated request, scoped to
-- buckets explicitly marked public. Matches Supabase's default behaviour
-- before the project's RLS lockdown swept this table.
DROP POLICY IF EXISTS "storage public buckets readable" ON storage.buckets;
CREATE POLICY "storage public buckets readable"
  ON storage.buckets FOR SELECT
  TO anon, authenticated
  USING (public = true);
