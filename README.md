# Thapsus Cargo — Native iOS Mobile App

UK→Kenya parcel consolidation, native iOS, KMP shared core.

## Architecture

- **`shared/`** — Kotlin Multiplatform module. Owns 100% of business logic, networking,
  caching, and view-model state. Targets `iosX64`, `iosArm64`, `iosSimulatorArm64`.
- **`iosApp/`** — SwiftUI presentation layer (iOS 26 Liquid Glass). Created in Phase 2.

## Phase 1 — Shared Infrastructure (this commit)

What's wired:

| Concern | File(s) |
|---|---|
| Supabase Kotlin client (Auth, Postgrest, Storage, Realtime, Functions) | `data/remote/SupabaseClientFactory.kt` |
| DTOs mirroring the Supabase schema (spec §5) | `data/dto/*.kt` |
| Volumetric weight `max(actual, L·W·H/6000)` | `domain/pricing/VolumetricWeightCalculator.kt` |
| Server-equivalent quote engine + insurance pricing | `domain/pricing/QuoteEngine.kt` |
| Offline-first SQLDelight cache + outbox queue | `commonMain/sqldelight/**`, `data/local/ThapsusLocalCache.kt` |
| Repositories (package, consolidation, customs, last-mile, pricing) | `data/repository/*.kt` |
| StateFlow-based view models (dashboard, intake, rider, quote) | `presentation/*.kt` |
| Koin DI module + iOS-facing SDK façade | `di/ThapsusModule.kt`, `ThapsusSdk.kt` |
| Unit tests for pricing + Money arithmetic | `commonTest/**` |

## Building

Requires JDK 17+ (e.g. via `brew install --cask temurin@17`) and Xcode 26.

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
./gradlew :shared:iosSimulatorArm64Test       # runs commonTest on iOS sim
```

The framework artifact lands at
`shared/build/bin/iosSimulatorArm64/debugFramework/ThapsusShared.framework`.

## Phase 2 (next)

SwiftUI views, `GlassEffectContainer`, `glassEffectID(_:in:)` morphing, AVFoundation
barcode scanner for `/ops/intake`, Contact Picker for the customer address book.

## Phase 3

Bind Swift `@Observable` wrappers to the Kotlin `StateFlow`s exposed via SKIE,
then ship `ITSAppUsesNonExemptEncryption = true` in `Info.plist` for export
compliance (Supabase HTTPS + financial flows).
