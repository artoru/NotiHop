#!/usr/bin/env bash
# Build a deterministic release APK and sign it with apksigner (v2 scheme only).
#
# Gradle always produces the *unsigned* APK (see notihop/build.gradle.kts); we sign
# here so the bytes are byte-for-byte reproducible, which is what F-Droid verifies.
#
# Reads credentials from the git-ignored secrets/keystore.properties (not committed).
#
# Output: notihop/build/outputs/apk/release/notihop-release.apk  (signed)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
APKSIGNER="$SDK/build-tools/37.0.0/apksigner"
KS="$ROOT/secrets/notihop-release.jks"
PROPS="$ROOT/secrets/keystore.properties"
OUT_DIR="$ROOT/notihop/build/outputs/apk/release"
UNSIGNED="$OUT_DIR/notihop-release-unsigned.apk"
SIGNED="$OUT_DIR/notihop-release.apk"

[[ -f "$PROPS" ]] || { echo "Missing $PROPS"; exit 1; }
ALIAS=$(sed -n 's/^keyAlias=//p' "$PROPS")
STOREPASS=$(sed -n 's/^storePassword=//p' "$PROPS")
KEYPASS=$(sed -n 's/^keyPassword=//p' "$PROPS")

export JAVA_HOME="${JAVA_HOME:-$HOME/android-studio/jbr}"
export ANDROID_HOME="${ANDROID_HOME:-$SDK}"
echo "Building unsigned release APK..."
./gradlew -q clean assembleRelease

echo "Signing with apksigner (v1 not needed for minSdk 26, v2 only)..."
"$APKSIGNER" sign \
  --ks "$KS" --ks-key-alias "$ALIAS" \
  --ks-pass pass:"$STOREPASS" --key-pass pass:"$KEYPASS" \
  --min-sdk-version 26 --max-sdk-version 37 \
  --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled false \
  --out "$SIGNED" "$UNSIGNED"

echo "Verifying..."
"$APKSIGNER" verify --print-certs "$SIGNED" | grep -i 'SHA-256'
echo "Signed APK: $SIGNED"
