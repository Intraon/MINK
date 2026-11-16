#!/bin/sh
# Installerer Android SDK'et, der skal til for at bygge MINK (Linux, JDK 17+ og unzip/curl kræves).
# Brug:  sh tools/setup-sdk.sh [mappe]   (standard: ~/android-sdk)   →  derefter:  sh tools/build.sh
set -eu
SDK="${1:-$HOME/android-sdk}"
CLT=13114758
mkdir -p "$SDK/cmdline-tools"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  curl -fsSL -o /tmp/clt.zip "https://dl.google.com/android/repository/commandlinetools-linux-${CLT}_latest.zip"
  unzip -q -o /tmp/clt.zip -d "$SDK/cmdline-tools"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -f /tmp/clt.zip
fi
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses >/dev/null || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" "platform-tools" "platforms;android-36" "build-tools;36.0.0" >/dev/null
echo "SDK klar i $SDK"
