#!/usr/bin/env bash
# Build the release APK (signed by AGP) and show what F-Droid will verify against.
#
# Gradle does the signing (see notihop/build.gradle.kts): AGP's own signing step is the
# only layout that F-Droid's signature-copying reproducibility check can reproduce, so we
# must not sign with a standalone apksigner call. Credentials come from the git-ignored
# secrets/keystore.properties; without that file the build stays unsigned.
#
# Outputs:
#   notihop/build/outputs/apk/release/notihop-release.apk            (signed, our release)
#   notihop/build/outputs/apk/release/notihop-release-unsigned.apk   (when unsigned build)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
APKSIGNER="$SDK/build-tools/37.0.0/apksigner"
OUT_DIR="$ROOT/notihop/build/outputs/apk/release"

export JAVA_HOME="${JAVA_HOME:-$HOME/android-studio/jbr}"
export ANDROID_HOME="${ANDROID_HOME:-$SDK}"

cd "$ROOT"
echo "Building release APK..."
./gradlew -q clean assembleRelease

SIGNED="$OUT_DIR/notihop-release.apk"
[[ -f "$SIGNED" ]] || { echo "No signed APK - secrets/keystore.properties missing?"; exit 1; }

echo "Signature:"
"$APKSIGNER" verify --verbose "$SIGNED" | grep -E 'Verifies|scheme \(|Signer #1 certificate SHA-256|certificate SHA-256 digest' || true
echo "SHA-256:"
sha256sum "$SIGNED"
echo "Signed APK: $SIGNED"
