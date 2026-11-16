#!/bin/sh
# Bygger MINK (Linux/Mac). Kræver JDK 17+, curl og unzip. Første gang hentes Android SDK'et (ca. 1 GB).
# Brug:  sh tools/build.sh   →  dist/MINK.apk
set -eu
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-$HOME/android-sdk}"
if [ ! -d "$SDK/platforms/android-36" ] || [ ! -d "$SDK/build-tools/36.0.0" ]; then
  sh tools/setup-sdk.sh "$SDK"
fi
export ANDROID_HOME="$SDK"
sh ./gradlew --no-daemon testReleaseUnitTest assembleRelease
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/MINK.apk
echo "Færdig: dist/MINK.apk"
