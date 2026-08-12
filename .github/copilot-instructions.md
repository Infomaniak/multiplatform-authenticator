# Copilot Coding Agent Onboarding — multiplatform-authenticator

> **Read `AGENTS.md` first** for architecture, conventions, and the KMP context map. This file covers build, CI, and validation.

## Overview
Kotlin Multiplatform **library** (not an app) holding the shared logic of the Infomaniak Authenticator: OTP (TOTP/HOTP)
engine, API client, local Room database, account/2FA repositories, WebAuthn/passkeys. Single Gradle module
`:AuthenticatorCore`. Consumed by `android-authenticator` (Git submodule + Gradle composite build) and by iOS/macOS as
a static `CoreAuthenticator` XCFramework (via the root `Package.swift`). Published to our self-hosted Reposilite
instance (`https://maven.infomaniak.app`, snapshots + releases). Targets:
`androidLibrary`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`. Key tech: SKIE, Ktor, Room multiplatform,
kotlinx.serialization, okio, kotlin-base32, coroutines. GPL v3.

## One-Time Environment Setup
No setup required: no Git submodule, no `env.properties`, no local.properties secrets. Needs JDK 17 and Android SDK
(`compileSdk`/`minSdk` from `gradle.properties`, currently 36/27).

## Local Verification (CI: `.github/workflows/`)
> **CI only** checks dependent issues, commit messages/PR titles, and Gradle-wrapper validity; snapshot publishing is manual.
> There is no CI build or test step — the commands below are the only validation available.

Runnable in the Copilot cloud agent (Linux) — Kotlin/Native skips Apple targets on Linux automatically:
```bash
./gradlew :AuthenticatorCore:assemble             # Android variant of the library
./gradlew :AuthenticatorCore:testAndroidHostTest  # JVM unit tests (commonTest + androidHostTest), no device
```
First build downloads a large dependency set (~3-4 min on a warm-cache host, longer cold); KSP runs per Apple target
(`compileKotlin<Target>` `dependsOn` `kspKotlin<Target>`, wired at the bottom of `AuthenticatorCore/build.gradle.kts`).

macOS-only — do NOT attempt on Linux, they will fail; say so in the PR description instead of pretending they were verified:
```bash
./gradlew :AuthenticatorCore:build                                       # includes the Apple targets, plus allTests /
                                                                          # iosSimulatorArm64Test / macosArm64Test
./gradlew :AuthenticatorCore:assembleCoreAuthenticatorReleaseXCFramework  # the exact task CI uses
```

## Project Layout
```
AuthenticatorCore/
├── build.gradle.kts   # KMP setup: targets, XCFramework, SKIE, Room
├── schemas/           # Exported Room schemas (commit alongside migrations)
└── src/                              # commonMain/androidMain/appleMain/iosMain/macosMain (public API + internal/)
                                       # commonTest/androidHostTest (no device), androidDeviceTest/appleTest (device)
build-logic/                           # Convention plugins (e.g. infomaniak.publishPlugin)
gradle/kmpAuthenticator.versions.toml  # Version catalog (catalog name: kmpAuthenticator)
Package.swift                          # SPM manifest; url/checksum are CI-managed placeholders
settings.gradle.kts, build.gradle.kts, gradle.properties
```

## PR Review Instructions
- GPLv3 copyright header in every new/modified file; no blank line between the closing `*/` and `package`.
- 130-char line limit; official Kotlin code style; default to `internal`, widen to `public` only for the
  cross-platform API surface (Android app + Swift consume this as a binary contract).
- **No automated public-API guard here** (unlike `android-rich-html-editor`'s Metalava/`api.txt`) — no ABI dump, no
  CI build, so human review is the only safety net. Prefer additive changes; call out signature changes in the PR body.
- Use the `Shared*` prefix for models exported to iOS to avoid Swift name collisions.
- Build all HTTP clients through `internal/network/ApiClientProvider.kt`; keep its `coerceInputValues` /
  `ignoreUnknownKeys` / `decodeEnumsCaseInsensitive` `Json` config intact.
- Room entity changes: commit the regenerated schema under `AuthenticatorCore/schemas/` alongside the migration.
- Do not add `androidx.*` imports to `commonMain`/`appleMain`; keep Android-only deps behind `expect`/`actual`.
- Never hand-edit `url`/`checksum` in `Package.swift` — overwritten by `publish-ios-snapshot.yml`. Keep framework name
  `CoreAuthenticator` and bundleId `com.infomaniak.multiplatform-authenticator.CoreAuthenticator` in sync when
  renaming public symbols consumed by iOS.
- Commit messages and PR title must match the semantic-commit regex enforced by CI.
- When adding/removing a runtime dependency, update `LICENSES.md` at the repo root.
