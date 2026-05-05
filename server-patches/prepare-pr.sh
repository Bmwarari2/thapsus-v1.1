#!/usr/bin/env bash
# prepare-pr.sh
# Run this from inside your local clone of Swiftcargo-main:
#   cd ~/path/to/Swiftcargo-main
#   bash /Users/mrwanderi/Documents/thapsus-mobile/server-patches/prepare-pr.sh
#
# It creates a branch, copies new files, runs the auth.js patcher, commits,
# and tells you what to run to push.

set -euo pipefail

PATCH_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BRANCH="feat/ios-supabase-jwt-bridge"

# Sanity: are we in the Swiftcargo-main repo?
if [[ ! -f "routes/auth.js" || ! -d ".git" ]]; then
  echo "✘ This doesn't look like the Swiftcargo-main repo root."
  echo "   cd into your local clone first, then re-run:"
  echo "   bash $PATCH_DIR/prepare-pr.sh"
  exit 1
fi

# Sanity: clean working tree
if ! git diff-index --quiet HEAD --; then
  echo "✘ Working tree has uncommitted changes. Commit or stash them, then re-run."
  exit 1
fi

DEFAULT_BRANCH="$(git symbolic-ref refs/remotes/origin/HEAD 2>/dev/null | sed 's@^refs/remotes/origin/@@' || echo "JS1")"
echo "→ Default branch detected: $DEFAULT_BRANCH"

# Make sure we're up to date with the default branch
git fetch origin "$DEFAULT_BRANCH" --quiet
git checkout "$DEFAULT_BRANCH" --quiet
git pull --ff-only origin "$DEFAULT_BRANCH" --quiet

# Create or reset the branch
if git show-ref --verify --quiet "refs/heads/$BRANCH"; then
  echo "→ Branch $BRANCH already exists locally — checking out and resetting"
  git checkout "$BRANCH" --quiet
  git reset --hard "$DEFAULT_BRANCH" --quiet
else
  git checkout -b "$BRANCH" --quiet
fi

# 1. Copy the new utility file
mkdir -p utils
cp "$PATCH_DIR/utils/supabaseJwt.js" utils/supabaseJwt.js
echo "✓ Copied utils/supabaseJwt.js"

# 2. Copy the migration
mkdir -p database/migrations
cp "$PATCH_DIR/database/migrations/002_rls_for_mobile_reads.sql" database/migrations/002_rls_for_mobile_reads.sql
echo "✓ Copied database/migrations/002_rls_for_mobile_reads.sql"

# 3. Patch routes/auth.js
node "$PATCH_DIR/apply-patch.js"

# 4. Update .env.example so future deployers see the new env var
if [[ -f ".env.example" ]] && ! grep -q "SUPABASE_JWT_SECRET" .env.example; then
  cat >> .env.example << 'EOF'

# Supabase JWT mint (issued to mobile clients for PostgREST/Realtime via RLS).
# Get this from Supabase Dashboard → Project Settings → API → JWT Settings.
SUPABASE_JWT_SECRET=
SUPABASE_JWT_TTL_SECONDS=3600
EOF
  echo "✓ Appended SUPABASE_JWT_SECRET stub to .env.example"
fi

# 5. Commit
git add utils/supabaseJwt.js \
        database/migrations/002_rls_for_mobile_reads.sql \
        routes/auth.js \
        .env.example 2>/dev/null || true

if git diff --cached --quiet; then
  echo "· Nothing to commit — branch already in target state"
else
  git commit -m "$(cat <<'EOF'
feat(auth): mint Supabase-shaped JWTs for iOS clients

Adds the server side of the hybrid mobile-sync architecture:

- New util `utils/supabaseJwt.js` — signs HS256 JWTs with
  SUPABASE_JWT_SECRET so PostgREST and Realtime accept them under RLS.
- `/api/auth/login` and `/api/auth/register` now return `supabase_token`
  + `supabase_token_expires_at` alongside the existing `sc_token`.
- New `POST /api/auth/supabase-token` lets mobile clients refresh the
  Supabase JWT independently of the long-lived sc_token.
- Adds an idempotent SQL migration enabling SELECT-only RLS on the
  read tables iOS observes (users, wallet, transactions, orders,
  consolidations, agent_invoices, aml_flags). Run AFTER deploying the
  token-mint changes — Express writes still bypass RLS via the pg
  Pool connection.
- `.env.example` now lists SUPABASE_JWT_SECRET.

Required env on Railway:
  SUPABASE_JWT_SECRET    (from Supabase Dashboard → API → JWT Settings)
  SUPABASE_JWT_TTL_SECONDS optional, default 3600

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
  echo "✓ Committed on branch $BRANCH"
fi

echo ""
echo "─────────────────────────────────────────────────────────────"
echo "Next steps:"
echo "  1. Inspect the diff:    git show --stat"
echo "  2. Push the branch:     git push -u origin $BRANCH"
echo "  3. Open a PR on GitHub against $DEFAULT_BRANCH and merge."
echo "  4. After Railway redeploys, set SUPABASE_JWT_SECRET in Variables."
echo "  5. Then run database/migrations/002_rls_for_mobile_reads.sql"
echo "     in the Supabase SQL editor."
echo "─────────────────────────────────────────────────────────────"
