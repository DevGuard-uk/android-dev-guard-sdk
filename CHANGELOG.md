# Changelog

## [Unreleased]

### Added
- **User Journey** (`DevGuard.setUserLog`): optional compact UI-step trail for Developer Portal and Console. Uploaded on heartbeat only when the project plan includes User Journey.

## [1.0.2] - 2026-06-26

### Changed
- Kotlin/Java package namespace unified to **`uk.devguard.*`** (matches Maven groupId and Sonatype namespace).
- Android `namespace` in all modules updated to `uk.devguard` / `uk.devguard.core` / `uk.devguard.crash`.
- JNI bridge symbols updated to `Java_uk_devguard_core_NativeBridge_*` (required after package rename).

### Fixed
- `UnsatisfiedLinkError` on app launch when native methods still referenced `io.devguard` JNI names.
- Crash during first API sync when persisting the device token on physical devices.

### Migration from 1.0.1
- Replace `import io.devguard.*` with `import uk.devguard.*` throughout your app.

## [1.0.1] - 2026-06-26

### Fixed
- Native C/C++ linkage for Linux CI (JitPack) — `extern "C"` guards in `devguard_core.h`.

## [1.0.0] - 2026-06-26

### Added

- Initial public release of the native Android licensing SDK (`uk.devguard:android-sdk`).
- HMAC-signed verify, gzip secure tunnel, premium lock screen, heartbeat sync, and remote wipe.
- Built-in plugin crash telemetry and `statusUrl` domain validation for API overrides.
- Native security hardening via `devguard_core` JNI.
