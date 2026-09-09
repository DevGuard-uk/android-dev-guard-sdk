# Native Android DevGuard SDK

<p align="center">
  <a href="https://devguard.uk">
    <img src="assets/logo.png" alt="DevGuard" width="120" height="120" />
  </a>
</p>

<p align="center">
  <a href="https://devguard.uk"><img src="https://img.shields.io/badge/website-devguard.uk-6C47FF?style=flat-square" alt="Website" /></a>
  <a href="https://github.com/DevGuard-uk/android-dev-guard-sdk"><img src="https://img.shields.io/badge/github-DevGuard--uk-181717?style=flat-square" alt="GitHub" /></a>
  <img src="https://img.shields.io/badge/platform-Android-3DDC84?style=flat-square" alt="Android" />
  <img src="https://img.shields.io/badge/maven-uk.devguard%3Aandroid--sdk-2D7DD2?style=flat-square" alt="Maven" />
  <img src="https://img.shields.io/badge/license-MIT-3CB371?style=flat-square" alt="License" />
</p>

Kotlin SDK for native Android applications. Artifact: **`uk.devguard:android-sdk`**.

<table>
  <tr>
    <td align="center" width="33%"><img src="screenshots/welcome-screen.png" width="240" alt="Active and protected app" /></td>
    <td align="center" width="33%"><img src="screenshots/payment-reminder.png" width="240" alt="In-app warning banner" /></td>
    <td align="center" width="33%"><img src="screenshots/access-suspended.png" width="240" alt="Access suspended lock screen" /></td>
  </tr>
  <tr>
    <td align="center"><b>Active &amp; Protected</b><br/><code>ACTIVE</code></td>
    <td align="center"><b>In-App Warning</b><br/><code>WARNING</code></td>
    <td align="center"><b>Access Suspended</b><br/><code>LOCKED</code></td>
  </tr>
</table>

## Features

- HMAC-signed verify against the DevGuard API
- GZip secure tunnel (`X-DevGuard-Tunnel: v1-gzip`)
- Premium lock screen (LOCKED / EXPIRED / PENDING / WARNING banner)
- Heartbeat + lifecycle sync (pauses in background)
- Device registration token persistence
- Remote wipe (`wipeNonce` / beta features)
- `setDeviceUser` for Developer Portal → Users
- `setUserLog` for Developer Portal → User Journey (plan-gated upload)
- Compromised-device and emulator policy enforcement

## Install

Maven Central:

```kotlin
repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("uk.devguard:android-sdk:1.0.2")
}
```

Maven groupId, Sonatype namespace, and Kotlin imports all use **`uk.devguard.*`**.

Public repository: [github.com/DevGuard-uk/android-dev-guard-sdk](https://github.com/DevGuard-uk/android-dev-guard-sdk)

## Quick start

```kotlin
DevGuard.initialize(
    context = applicationContext,
    projectId = "your_project_id",
    secret = "YOUR_MASTER_SECRET",
)
```

Sign up at [devguard.uk](https://devguard.uk) for a **Project ID** and **Master Secret**.

## Support

- **Issues:** [GitHub Issues](https://github.com/DevGuard-uk/android-dev-guard-sdk/issues)
- **Docs:** [devguard.uk/docs](https://devguard.uk/docs)
