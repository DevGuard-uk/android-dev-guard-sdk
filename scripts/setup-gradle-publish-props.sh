#!/usr/bin/env bash
# Writes Maven Central + GPG signing props to ~/.gradle/gradle.properties.
# Reads org key IDs from signing-keys.properties and secrets from gradle-publish.local.properties.
# Safe to re-run — replaces only the DevGuard publish block.
#
# Usage:
#   cp gradle-publish.local.properties.example gradle-publish.local.properties  # first time
#   ./scripts/setup-gradle-publish-props.sh
#
# Override via env: GPG_KEY_ID, GPG_PASSPHRASE, MAVEN_CENTRAL_USERNAME, MAVEN_CENTRAL_PASSWORD
# See ../DEPLOYMENT.md and ../MAVEN_CENTRAL_SETUP.md

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SIGNING_KEYS="${ROOT_DIR}/signing-keys.properties"
LOCAL_PROPS="${ROOT_DIR}/gradle-publish.local.properties"
GRADLE_PROPS="${GRADLE_PROPS:-${HOME}/.gradle/gradle.properties}"

load_props() {
  local file="$1"
  [[ -f "$file" ]] || return 0
  while IFS='=' read -r key value; do
    [[ -z "${key}" || "${key}" =~ ^# ]] && continue
    key="$(echo "$key" | xargs)"
    value="$(echo "$value" | xargs)"
    case "$key" in
      signingGpgKeyIdFull)    : "${GPG_KEY_ID:=${value}}" ;;
      signingGpgKeyIdShort)   : "${GPG_KEY_ID_SHORT:=${value}}" ;;
      mavenCentralNamespace)  : "${MAVEN_NAMESPACE:=${value}}" ;;
      mavenCentralUsername)   : "${MAVEN_CENTRAL_USERNAME:=${value}}" ;;
      mavenCentralPassword)   : "${MAVEN_CENTRAL_PASSWORD:=${value}}" ;;
      signingGpgPassphrase)   : "${GPG_PASSPHRASE:=${value}}" ;;
    esac
  done < "$file"
}

load_props "${SIGNING_KEYS}"
load_props "${LOCAL_PROPS}"

GPG_KEY_ID="${GPG_KEY_ID:-54BDC33F719C0412}"
GPG_KEY_ID_SHORT="${GPG_KEY_ID_SHORT:-719C0412}"
GPG_PASSPHRASE="${GPG_PASSPHRASE:-}"
MAVEN_CENTRAL_USERNAME="${MAVEN_CENTRAL_USERNAME:-}"
MAVEN_CENTRAL_PASSWORD="${MAVEN_CENTRAL_PASSWORD:-}"
MAVEN_NAMESPACE="${MAVEN_NAMESPACE:-uk.devguard}"

if ! command -v gpg >/dev/null 2>&1; then
  echo "gpg not found. Install: brew install gnupg" >&2
  exit 1
fi

if [[ ! -f "${LOCAL_PROPS}" ]]; then
  echo "Missing ${LOCAL_PROPS}" >&2
  echo "Copy gradle-publish.local.properties.example → gradle-publish.local.properties" >&2
  exit 1
fi

if [[ -z "${MAVEN_CENTRAL_USERNAME}" || -z "${MAVEN_CENTRAL_PASSWORD}" || -z "${GPG_PASSPHRASE}" ]]; then
  echo "Set mavenCentralUsername, mavenCentralPassword, signingGpgPassphrase in ${LOCAL_PROPS}" >&2
  exit 1
fi

SHORT_KEY_ID="${GPG_KEY_ID: -8}"
if [[ "${SHORT_KEY_ID}" != "${GPG_KEY_ID_SHORT}" ]]; then
  echo "Warning: signingGpgKeyIdShort (${GPG_KEY_ID_SHORT}) != last 8 of key (${SHORT_KEY_ID})" >&2
fi

echo "Exporting GPG secret key ${GPG_KEY_ID} (signingInMemoryKeyId=${SHORT_KEY_ID})..."
IN_MEMORY_KEY="$(
  gpg --batch --pinentry-mode loopback --passphrase "${GPG_PASSPHRASE}" \
    --armor --export-secret-keys "${GPG_KEY_ID}" \
  | awk '{gsub(/\\/,"\\\\"); printf "%s\\n", $0}' | tr -d '\n'
)"

mkdir -p "$(dirname "${GRADLE_PROPS}")"
touch "${GRADLE_PROPS}"

python3 - "${GRADLE_PROPS}" <<'PY'
import pathlib
import re
import sys

path = pathlib.Path(sys.argv[1])
text = path.read_text() if path.exists() else ""
text = re.sub(
    r"\n# --- DevGuard Maven Central publish.*?# --- /DevGuard Maven Central publish ---\n?",
    "\n",
    text,
    flags=re.DOTALL,
)
path.write_text(text.rstrip() + "\n")
PY

cat >> "${GRADLE_PROPS}" <<EOF

# --- DevGuard Maven Central publish (generated $(date -u +%Y-%m-%dT%H:%MZ)) ---
# namespace=${MAVEN_NAMESPACE} key=${GPG_KEY_ID}
mavenCentralUsername=${MAVEN_CENTRAL_USERNAME}
mavenCentralPassword=${MAVEN_CENTRAL_PASSWORD}
signingInMemoryKeyId=${SHORT_KEY_ID}
signingInMemoryPassword=${GPG_PASSPHRASE}
signingInMemoryKey=${IN_MEMORY_KEY}
# --- /DevGuard Maven Central publish ---
EOF

chmod 600 "${GRADLE_PROPS}" 2>/dev/null || true

echo "Wrote Maven Central credentials to ${GRADLE_PROPS}"
echo "  namespace=${MAVEN_NAMESPACE}"
echo "  mavenCentralUsername=${MAVEN_CENTRAL_USERNAME}"
echo "  signingInMemoryKeyId=${SHORT_KEY_ID}"
echo ""
echo "Next: cd deployment/native-android-dev-guard && ./gradlew publishAllToMavenCentral"
