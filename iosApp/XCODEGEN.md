# XcodeGen for `iosApp/`

Closes audit **W6.2**'s first half. Adds a `project.yml` so adding a
new Swift file no longer requires the four-UUID pbxproj surgery
documented in
`~/.claude/.../memory/reference_ios_pbxproj.md`.

This is the **scaffolding PR**. The committed
`iosApp.xcodeproj/project.pbxproj` remains the canonical project
file for now, and `xcodegen generate` is opt-in for developers
who'd rather not hand-edit pbxproj.

## Install

```sh
brew install xcodegen
```

(Or `mint install yonaskolb/XcodeGen`, or download a binary release
from <https://github.com/yonaskolb/XcodeGen/releases>.)

## Use

When you've added a new Swift file under `iosApp/`:

```sh
cd iosApp
xcodegen generate
```

Then if Xcode is already open, restart it. Sources are auto-
discovered under `iosApp/iosApp/`, so the new file is picked up
without further edits.

## Don't commit the regenerated pbxproj (yet)

The committed `iosApp.xcodeproj/project.pbxproj` is the source of
truth until we flip the convention. Specifically, regenerating
will:

- **Reassign UUIDs** for every PBXFileReference, PBXBuildFile,
  PBXGroup, etc. Diffs are huge and not human-reviewable.
- **Reorder groups** alphabetically rather than by intent.
- **Drop any manually-curated build setting** that isn't reflected
  in `project.yml`. The setting still ends up in the build, just
  via xcconfig overrides — but if there's a setting expressed only
  inline in pbxproj, it'll vanish.

Until everyone on the team is using xcodegen, treat regeneration as
a personal-machine convenience: regenerate, build, validate, then
**revert the pbxproj** (`git checkout -- iosApp.xcodeproj/`)
before committing. The new Swift file is on disk; on the next
team member's machine it lands in the existing pbxproj via Xcode's
"Add Files to project" dialog as before.

## Migration plan (future PR)

To flip the source of truth:

1. Take a snapshot of the current project's settings via
   `xcodebuild -showBuildSettings`. Reconcile every non-default
   setting into `project.yml` until `xcodegen generate` produces a
   pbxproj whose `xcodebuild -showBuildSettings` matches the
   snapshot.
2. Add a CI step that runs `xcodegen generate` and fails if the
   resulting pbxproj differs from the committed one — or, better,
   delete the committed pbxproj and regenerate at CI time.
3. Update
   `~/.claude/.../memory/reference_ios_pbxproj.md`
   to describe the xcodegen flow as the new convention.
4. Document the flip in `CHANGELOG.md` so a developer who hasn't
   pulled in a while knows to install xcodegen.

That's a separate PR — likely a bigger one — and isn't needed
to start using `xcodegen generate` locally today.

## What's in `project.yml`

- App target (`iosApp`, type `application`, iOS 26.0+, Swift 6).
- Auto-discovered sources under `iosApp/iosApp/`.
- The `KMP Build Shared Framework` pre-build script (mirror of the
  existing pbxproj recipe — runs on every build).
- `ThapsusShared.framework` framework dependency from
  `../shared/build/xcode-frameworks/.../`.
- `StripePaymentSheet` SPM package (≥ 25.13.0).
- `Info.plist` and `iosApp.entitlements` paths.
- xcconfig wiring at `Configuration/Config.xcconfig` for the
  `$(APP_BUNDLE_ID)`, `$(APP_VERSION)`, `$(APP_BUILD)`, `$(TEAM_ID)`,
  `$(SUPABASE_URL)`, `$(SUPABASE_ANON_KEY)`, `$(API_BASE_URL)`
  substitutions used in the manifest.

## Validation

After `xcodegen generate` you should be able to:

```sh
xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -destination 'generic/platform=iOS Simulator' \
  build
```

…and see `** BUILD SUCCEEDED **`. If you don't, the `project.yml`
has drifted from reality — file the gap before committing the
regenerated pbxproj.
