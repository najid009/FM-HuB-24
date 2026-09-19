#!/usr/bin/env bash
set -euo pipefail
ROOT="${ANDROID_HOME:-$HOME/android-sdk}"
CMD="$ROOT/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$CMD" ]]; then
  mkdir -p "$ROOT/cmdline-tools"
  tmp="$(mktemp -d)"
  curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o "$tmp/tools.zip"
  unzip -q "$tmp/tools.zip" -d "$tmp/unpacked"
  rm -rf "$ROOT/cmdline-tools/latest"
  mv "$tmp/unpacked/cmdline-tools" "$ROOT/cmdline-tools/latest"
  rm -rf "$tmp"
fi
export ANDROID_HOME="$ROOT"
export ANDROID_SDK_ROOT="$ROOT"
yes | "$CMD" --licenses >/dev/null || true
"$CMD" "platform-tools" "platforms;android-36" "build-tools;35.0.0"
cd "$(dirname "$0")/.."
if [[ ! -f local.properties ]]; then printf 'sdk.dir=%s\n' "$ROOT" > local.properties; fi
./gradlew :app:assembleDebug --stacktrace
