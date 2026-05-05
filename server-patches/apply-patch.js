#!/usr/bin/env node
// apply-patch.js
// Surgically updates routes/auth.js to add the Supabase JWT mint flow.
// Idempotent — safe to re-run; will skip sections that are already applied.
// Run from the Swiftcargo-main repo root: `node ../path/to/apply-patch.js`

const fs = require('fs')
const path = require('path')

const ROUTES_AUTH = path.resolve(process.cwd(), 'routes/auth.js')
if (!fs.existsSync(ROUTES_AUTH)) {
  console.error(`✘ routes/auth.js not found at ${ROUTES_AUTH}`)
  console.error('   Run this from the Swiftcargo-main repo root.')
  process.exit(1)
}

let src = fs.readFileSync(ROUTES_AUTH, 'utf8')
let mutated = false

// 1. Add the import near the top, after the last require() near jsonwebtoken.
if (!src.includes("require('../utils/supabaseJwt')")) {
  const importLine = "const { mintSupabaseToken } = require('../utils/supabaseJwt')\n"
  // Insert right after the last existing require() at the top of the file.
  const reqRegex = /^const\s+.+=\s*require\(['"][^'"]+['"]\)\s*;?\s*$/gm
  let lastIdx = -1
  let match
  while ((match = reqRegex.exec(src)) !== null) {
    // Stop scanning when we hit a non-require / blank line break — i.e. only
    // capture the leading require block.
    if (match.index > 1500) break
    lastIdx = match.index + match[0].length
  }
  if (lastIdx === -1) {
    // Fallback: insert at the very top.
    src = importLine + src
  } else {
    src = src.slice(0, lastIdx) + '\n' + importLine + src.slice(lastIdx)
  }
  mutated = true
  console.log('✓ Added mintSupabaseToken import')
} else {
  console.log('· import already present')
}

// 2. In /login and /register handlers, inject supabase_token + expiry into the
//    success res.json(...) payload.
function injectIntoSuccessJson(src, label) {
  // Find a res.json or res.status(...).json that contains "Login successful"
  // (login) or "User registered" / 201 (register). The marker we look for is
  // the success token field (token: ... or token,) returned alongside user.
  // We do a simpler textual swap: turn `token,\n` into the patched block.
  // The block is idempotent — if "supabase_token:" already exists nearby we
  // skip.
  return src
}

// Login handler — find the success response. We expect a structure like:
//   return res.json({ success: true, message: 'Login successful', token, user: {...} })
// or with res.status(200).json(...). We patch by replacing `token,\n` with the
// supabase block + token line.
const loginPatchPattern = /(\bmessage:\s*['"`]Login successful['"`],\s*\n\s*)token,(\s*\n)/
if (loginPatchPattern.test(src) && !src.includes("supabase_token:")) {
  src = src.replace(
    loginPatchPattern,
    `$1token,\n      supabase_token: (() => { try { return mintSupabaseToken(user).token } catch (e) { console.error('[auth/login] supabase token mint failed:', e.message); return null } })(),\n      supabase_token_expires_at: (() => { try { return mintSupabaseToken(user).expiresAt } catch (e) { return null } })(),$2`
  )
  mutated = true
  console.log('✓ Patched /login response with supabase_token')
} else if (src.includes('supabase_token:')) {
  console.log('· /login already has supabase_token')
} else {
  console.log('⚠ Could not auto-patch /login — please add supabase_token + supabase_token_expires_at fields manually next to the existing `token` field in the success response.')
}

// Register handler — same idea. The message is typically 'User registered successfully' or similar.
const registerPatchPattern = /(\bmessage:\s*['"`](?:User registered|Registration successful|Account created)[^'"`]*['"`],\s*\n\s*)token,(\s*\n)/
if (registerPatchPattern.test(src) && src.split('supabase_token:').length < 3) {
  src = src.replace(
    registerPatchPattern,
    `$1token,\n      supabase_token: (() => { try { return mintSupabaseToken(user).token } catch (e) { console.error('[auth/register] supabase token mint failed:', e.message); return null } })(),\n      supabase_token_expires_at: (() => { try { return mintSupabaseToken(user).expiresAt } catch (e) { return null } })(),$2`
  )
  mutated = true
  console.log('✓ Patched /register response with supabase_token')
} else if (src.split('supabase_token:').length >= 3) {
  console.log('· /register already has supabase_token')
} else {
  console.log('⚠ Could not auto-patch /register — please mirror the same supabase_token field next to the existing `token` field in the register success response. Skip if this server doesn\'t expose /register.')
}

// 3. Add /supabase-token endpoint above module.exports = router.
if (!src.includes("'/supabase-token'") && !src.includes('"/supabase-token"')) {
  const newRoute = `
/**
 * POST /api/auth/supabase-token
 * Exchanges a valid sc_token (Bearer) for a fresh Supabase-shaped JWT.
 * Mobile clients call this before the previous Supabase JWT expires.
 */
router.post('/supabase-token', authMiddleware, async (req, res) => {
  try {
    const { id, email, role } = req.user
    const supabase = mintSupabaseToken({ id, email, role })
    return res.json({
      success: true,
      supabase_token: supabase.token,
      supabase_token_expires_at: supabase.expiresAt
    })
  } catch (e) {
    console.error('[auth/supabase-token]', e)
    return res.status(500).json({ success: false, message: 'Could not mint supabase token' })
  }
})

`
  // Detect the auth-middleware import name actually used in this file.
  const middlewareMatch = src.match(/const\s+\{\s*([A-Za-z0-9_, ]*authMiddleware[A-Za-z0-9_, ]*)\s*\}\s*=\s*require\(['"][^'"]+\/auth['"]\)/)
  let routeBlock = newRoute
  if (!middlewareMatch) {
    // Try alternative names: authenticate, requireAuth, protect.
    const fallbacks = ['authenticate', 'requireAuth', 'protect']
    let chosen = null
    for (const f of fallbacks) {
      if (src.includes(f)) { chosen = f; break }
    }
    if (chosen) {
      routeBlock = newRoute.replace('authMiddleware', chosen)
      console.log(`  (using middleware name "${chosen}")`)
    } else {
      console.log('⚠ Could not find auth middleware import — please review the inserted /supabase-token route and adjust the middleware name to match your file.')
    }
  }
  if (/module\.exports\s*=\s*router/.test(src)) {
    src = src.replace(/(module\.exports\s*=\s*router\s*;?\s*)$/, routeBlock + '$1')
  } else {
    src = src + '\n' + routeBlock
  }
  mutated = true
  console.log('✓ Added /supabase-token endpoint')
} else {
  console.log('· /supabase-token already present')
}

if (mutated) {
  fs.writeFileSync(ROUTES_AUTH, src, 'utf8')
  console.log(`\n✔ Updated ${ROUTES_AUTH}`)
} else {
  console.log('\n· No changes needed (everything already applied)')
}
