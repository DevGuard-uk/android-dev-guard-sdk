# Native Android SDK — manual deployment guide

End-to-end checklist for shipping **`deployment/native-android-dev-guard/`** to **GitHub** and **Maven Central**. Read this when you deploy without an agent.

**Related docs**

| Doc | Purpose |
|-----|---------|
| [MAVEN_CENTRAL_SETUP.md](./MAVEN_CENTRAL_SETUP.md) | One-time Sonatype account, namespace, GPG, credentials |
| [../../deployment/README.md](../../deployment/README.md) | All SDK deployment folders + rsync commands |
| [README.md](./README.md) | Integrator install snippet |

**Public remotes**

- GitHub: [github.com/DevGuard-uk/android-dev-guard-sdk](https://github.com/DevGuard-uk/android-dev-guard-sdk)
- Maven: `uk.devguard:android-sdk` on [central.sonatype.com](https://central.sonatype.com/artifact/uk.devguard/android-sdk)

---

## Quick reference (repeat releases)

After one-time setup in [MAVEN_CENTRAL_SETUP.md](./MAVEN_CENTRAL_SETUP.md):

```bash
# 1. Sync private → deployment (from monorepo root)
rsync -a --delete \
  --exclude='example/' --exclude='.gradle/' --exclude='build/' \
  native-android-dev-guard/ deployment/native-android-dev-guard/

# 2. Re-sanitize customer docs (LICENSE, README, CHANGELOG — no internal IDs)
# 3. Bump VERSION_NAME in deployment/native-android-dev-guard/gradle.properties

cd deployment/native-android-dev-guard

# 4. Build + smoke test
./gradlew :sdk:assembleRelease :core:assembleRelease :crash-reporter:assembleRelease
node ../../testing_suite/scripts/test_native_sdk_smoke.js --android-only

# 5. Publish to Maven Central (credentials in ~/.gradle/gradle.properties)
./gradlew publishAllToMavenCentral

# 6. Push public GitHub + tag (see § GitHub below)
# 7. Monorepo PR: feature/deployment-native-android-vX.Y.Z → staging
```

---

## Phase A — One-time setup (~30 min)

Do once per machine / org. Details: [MAVEN_CENTRAL_SETUP.md](./MAVEN_CENTRAL_SETUP.md).

| Step | Action |
|------|--------|
| A1 | Sign in at [central.sonatype.com](https://central.sonatype.com) (GitHub) |
| A2 | Register namespace **`uk.devguard`**, verify **`devguard.uk`** DNS TXT |
| A3 | Generate GPG key (`DevGuard UK`, `app@devguard.uk`, RSA 4096) |
| A4 | Upload public key to [keys.openpgp.org](https://keys.openpgp.org) |
| A5 | Generate Sonatype user token (profile menu → **View User Tokens**) |
| A6 | Run `./scripts/setup-gradle-publish-props.sh` (writes `~/.gradle/gradle.properties`) |

**Current org key (generated 2026-06-26)**

| Field | Value |
|-------|--------|
| Key ID | `54BDC33F719C0412` |
| Gradle `signingInMemoryKeyId` | `719C0412` |
| Fingerprint | `1DEA B589 5D73 09FA CAD5 67D2 54BD C33F 719C 0412` |
| Email | `app@devguard.uk` |

Passphrase is stored in your password manager and in `~/.gradle/gradle.properties` (local only — never commit).

---

## Phase B — Prepare a release

### B1 — Sync from private monorepo

Develop in `native-android-dev-guard/`; publish from `deployment/native-android-dev-guard/`.

```bash
rsync -a --delete \
  --exclude='example/' \
  --exclude='.gradle/' \
  --exclude='build/' \
  --exclude='.cxx/' \
  native-android-dev-guard/ deployment/native-android-dev-guard/
```

Also sync crash standalone if shipping it:

```bash
rsync -a --delete --exclude='.gradle/' --exclude='build/' --exclude='.cxx/' \
  native-android-dev-guard/core/ deployment/native-android-dev-guard-crash/core/
rsync -a --delete --exclude='build/' \
  native-android-dev-guard/crash-reporter/ deployment/native-android-dev-guard-crash/crash-reporter/
```

### B2 — Sanitize public copy

- **LICENSE** → `Copyright (c) {year} DevGuard UK`
- **README.md / CHANGELOG.md** → integrator audience only (no monorepo paths, internal project IDs)
- No `kRBznGhcjQttRL7SXHGK`, service-account JSON, or `.env`
- **Never commit** real tokens — `gradle-publish.properties.example` uses placeholders only

### B3 — Version bump

Edit `deployment/native-android-dev-guard/gradle.properties`:

```properties
VERSION_NAME=1.0.2
```

Published coordinates (same version for all three):

| Module | Maven coordinates |
|--------|-------------------|
| Main SDK | `uk.devguard:android-sdk:{version}` |
| JNI core | `uk.devguard:android-core:{version}` |
| Crash reporter | `uk.devguard:android-crash-reporter:{version}` |

Update `CHANGELOG.md`, README install snippet, and website `sdkResources.ts` if the version changed.

---

## Phase C — Build and verify

```bash
cd deployment/native-android-dev-guard

./gradlew :sdk:assembleRelease :core:assembleRelease :crash-reporter:assembleRelease

node ../../testing_suite/scripts/test_native_sdk_smoke.js --android-only
```

Fix any Gradle/NDK errors before publishing.

---

## Phase D — Publish to Maven Central

### Credentials

Secrets file (gitignored): `gradle-publish.local.properties`  
Public key IDs: `signing-keys.properties`

```bash
# First time only:
cp gradle-publish.local.properties.example gradle-publish.local.properties
# Edit: Sonatype token + GPG passphrase

chmod +x scripts/setup-gradle-publish-props.sh
./scripts/setup-gradle-publish-props.sh
```

Or pass env vars: `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `GPG_PASSPHRASE` (key ID defaults from `signing-keys.properties`).

### Publish command

```bash
./gradlew publishAllToMavenCentral
```

This command does **two things**:

1. Uploads + GPG-signs all three modules to Sonatype’s OSSRH compatibility staging API.
2. Runs **`transferMavenCentralStagingToPortal`** automatically — moves the staging upload into the Central Portal **Deployments** tab.

> **Why Deployments looked empty:** Gradle’s `maven-publish` only PUTs files to a temporary staging area. Without the transfer step, nothing appears at [central.sonatype.com/publishing](https://central.sonatype.com/publishing). See [Sonatype OSSRH Staging API docs](https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/).

If you published from an old session / different IP and Deployments is still empty, run manually:

```bash
./gradlew transferMavenCentralStagingToPortal
```

### After upload

1. Open [central.sonatype.com/publishing](https://central.sonatype.com/publishing) → **Deployments**
2. Wait for validation (~5–30 min; sometimes longer)
3. If state is **VALIDATED**, click **Publish** (unless you configured automatic publish)
4. Confirm artifacts: [uk.devguard/android-sdk](https://central.sonatype.com/artifact/uk.devguard/android-sdk)

Integrators add:

```kotlin
repositories { mavenCentral() }
dependencies {
    implementation("uk.devguard:android-sdk:1.0.1")
}
```

Kotlin imports stay `io.devguard.*` — only Maven `groupId` is `uk.devguard`.

---

## Phase E — Push public GitHub

Deployment folder is **not** its own git repo. Clone the public remote, rsync, commit, tag:

```bash
VERSION=1.0.2
TMP=$(mktemp -d)/android-dev-guard-sdk

git clone --depth 1 https://github.com/DevGuard-uk/android-dev-guard-sdk.git "$TMP"
rsync -a --delete --exclude='.git' \
  deployment/native-android-dev-guard/ "$TMP/"

cd "$TMP"
git add -A
git commit -m "Release v${VERSION} — native Android SDK"
git tag -a "v${VERSION}" -m "v${VERSION}"
git push origin main
git push origin "v${VERSION}"
```

---

## Phase F — Monorepo commit

```bash
git checkout staging && git pull origin staging
git checkout -b feature/deployment-native-android-v${VERSION}
git add deployment/native-android-dev-guard/
git commit -m "Deploy native Android SDK v${VERSION} to Maven Central"
git push -u origin feature/deployment-native-android-v${VERSION}
gh pr create --base staging --head feature/deployment-native-android-v${VERSION} \
  --title "Deploy native Android SDK v${VERSION}" \
  --body "## Summary
- Synced deployment/native-android-dev-guard
- Published uk.devguard:android-sdk:${VERSION} to Maven Central

## Test plan
- [x] ./gradlew publishAllToMavenCentral
- [x] test_native_sdk_smoke.js --android-only"
```

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Deployments tab empty after publish | Run `./gradlew transferMavenCentralStagingToPortal` (now runs automatically after `publishAllToMavenCentral`) |
| `404` on publish | Repository URL must be `https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/` |
| `401 Unauthorized` | Regenerate token (View User Tokens); re-run `setup-gradle-publish-props.sh` |
| Signing failed | Re-run setup script; confirm key on [keys.openpgp.org](https://keys.openpgp.org) |
| No GPG page in portal | Expected — upload public key to keyserver only |
| Deployment stuck VALIDATING | Wait 1–2 h; email central-support@sonatype.com if longer |
| Wrong namespace | `GROUP=uk.devguard` in project `gradle.properties` |

---

## Checklist (copy per release)

- [ ] Rsync `native-android-dev-guard/` → `deployment/native-android-dev-guard/`
- [ ] Sanitize LICENSE / README / CHANGELOG
- [ ] Bump `VERSION_NAME` in `gradle.properties`
- [ ] `./gradlew` release assemble + smoke test
- [ ] `./gradlew publishAllToMavenCentral`
- [ ] Publish deployment in Sonatype portal when **VALIDATED**
- [ ] Verify artifact on central.sonatype.com
- [ ] Push public GitHub + tag `vX.Y.Z`
- [ ] Monorepo PR to `staging`
