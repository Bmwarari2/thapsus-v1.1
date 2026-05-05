# Push notifications — Setup checklist for after Phase 3

You said you don't yet have an APNs key/cert or a Firebase project. This doc
captures everything you'll need to provision so we can wire push delivery into
the existing Phase 1 notifications inbox without inventing infrastructure.

## 0. What we're building

When the iOS app receives a push, the user sees a banner that opens the
relevant in-app screen — order status update → ParcelDetailView, ticket reply
→ TicketDetailView, etc. The push payload always includes:

```json
{
  "aps": { "alert": { ... }, "sound": "default", "badge": 1 },
  "type": "order_status" | "ticket_reply" | "buy_for_me" | "wallet" | "broadcast",
  "deep_link": "thapsus://...",
  "ref_id": "<orderId | ticketId | …>"
}
```

We persist the same payload as a row in the `notifications` table so the
inbox UI stays consistent regardless of whether the device received the push.

## 1. Apple side — APNs

You need an active **paid Apple Developer Program membership** (£79/yr).

Then in `developer.apple.com`:

1. **Identifiers** → confirm `uk.thapsus.cargo` exists with **Push
   Notifications** capability ticked. If you change the bundle ID later,
   update `iosApp/Configuration/Config.local.xcconfig` to match.
2. **Keys** → "+" → name it `Thapsus APNs`, tick **Apple Push Notifications
   service (APNs)**, Continue → Register → **Download `.p8` file**. You can
   only download once. Store it in 1Password / equivalent.
3. Note three values you'll need on the server:
   - `APNS_KEY_ID` — the 10-char ID shown on the key page
   - `APNS_TEAM_ID` — your Apple Developer team ID (top right of dev portal)
   - `APNS_BUNDLE_ID` — `uk.thapsus.cargo`

In Xcode (`iosApp.xcodeproj` → target → **Signing & Capabilities**):
- Add capability **Push Notifications**.
- Optionally add **Background Modes → Remote notifications** if we later
  want silent pushes for cache invalidation.

This is enough — you don't strictly need Firebase. Sending APNs directly via
`apns2` (Node.js library) is simpler and one fewer vendor.

## 2. Server side — Express

When you're ready, we'll add:

- `npm i apns2` to `package.json`.
- `routes/devices.js`:
  - `POST /api/devices/register` — body `{ token, platform: 'ios', app_version }`,
    upserts into a new `device_tokens` table keyed by `(user_id, token)`.
  - `DELETE /api/devices/:token` — called from iOS when the user signs out.
- `utils/push.js` — wraps `apns2`'s `ApnsClient` with the three env vars,
  exports `sendPush(userId, payload)` that:
  - Looks up active device tokens for `userId`
  - Sends an APNs notification with `topic = APNS_BUNDLE_ID`
  - On `BadDeviceToken` / `Unregistered`, deletes the dead token row
- Hook `sendPush` into the existing notification write paths:
  - `routes/orders.js` `PUT /:id/status` → emits to the order owner
  - `routes/tickets.js` `POST /:id/message` (admin reply) → emits to ticket owner
  - `routes/buyForMe.js` `PATCH /:id` → emits when status changes
  - `routes/wallet.js` payment confirmation → emits on success/failure
- New DB migration `003_device_tokens.sql` with:
  ```sql
  CREATE TABLE IF NOT EXISTS device_tokens (
    user_id     TEXT REFERENCES users(id) ON DELETE CASCADE,
    token       TEXT NOT NULL,
    platform    TEXT CHECK (platform IN ('ios','android','web')) NOT NULL,
    app_version TEXT,
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    last_seen   TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (user_id, token)
  );
  ```
- Required Railway env vars:
  - `APNS_KEY_ID`
  - `APNS_TEAM_ID`
  - `APNS_BUNDLE_ID = uk.thapsus.cargo`
  - `APNS_KEY` — full contents of the `.p8` file, line breaks kept (Railway
    UI handles multi-line values fine; alternatively base64-encode it).
  - `APNS_PRODUCTION = true` (false in staging)

## 3. iOS side — register + handle

1. Add `import UserNotifications` to `iOSApp.swift`.
2. On app launch (post-auth), request authorization:
   ```swift
   UNUserNotificationCenter.current()
     .requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
       if granted {
         DispatchQueue.main.async { UIApplication.shared.registerForRemoteNotifications() }
       }
     }
   ```
3. Implement `application(_:didRegisterForRemoteNotificationsWithDeviceToken:)`
   in a small `AppDelegate` (or via `UIApplicationDelegateAdaptor`). POST the
   token hex string to `/api/devices/register` via `ThapsusApiClient`.
4. Implement `UNUserNotificationCenterDelegate.userNotificationCenter(_:didReceive:withCompletionHandler:)`
   to deep-link based on the `deep_link` field — reuse the URL handling
   already in `RootView`.

We can plumb this end-to-end in a single PR once the credentials in #1 and
the env vars in #2 are in place.

## 4. Estimated effort once credentials are ready

- ~½ day server (devices route + apns2 wiring + 4 hook points + migration).
- ~½ day iOS (capability, delegate, registration call, deep-link routing).
- ~1 day end-to-end: TestFlight build, smoke test, copy edits on banner text.

## 5. Things you do NOT need right now

- Firebase Cloud Messaging — Android-only, we're iOS-first.
- OneSignal / Pushwoosh — extra vendor surface for no benefit at this scale.
- Voice over IP / CallKit pushes — not relevant.
- Live Activities — defer until the consolidation cut-off countdown is more
  prominent in the UX.

When you've completed #1 and have the .p8 file + the three IDs in your
password manager, ping me and I'll execute #2 and #3 in one branch.
