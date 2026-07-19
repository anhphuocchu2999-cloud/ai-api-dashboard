#!/usr/bin/env bash
set -euo pipefail

previous_apk=/tmp/ai-api-dashboard-previous-beta.apk
current_apk=app/build/outputs/apk/debug/app-debug.apk
previous_url=https://github.com/anhphuocchu2999-cloud/ai-api-dashboard/releases/download/v0.1.0-beta.17/ai-api-dashboard-v0.1.0-beta.17-debug.apk
main_component=com.java.myapplication.dev/com.java.myapplication.MainActivity

curl --fail --location --retry 3 --output "$previous_apk" "$previous_url"

apksigner_path="$(find "$ANDROID_SDK_ROOT/build-tools" -type f -name apksigner | sort -V | tail -n 1)"
test -x "$apksigner_path"
previous_cert="$($apksigner_path verify --print-certs "$previous_apk" \
  | sed -n 's/.*certificate SHA-256 digest:[[:space:]]*//p' \
  | head -n 1 \
  | tr -d '[:space:]:' \
  | tr '[:lower:]' '[:upper:]')"
test -n "$previous_cert"
test "$previous_cert" = "$EXPECTED_SIGNING_CERT_SHA256"

adb install "$previous_apk"
previous_start="$(adb shell am start -W -n "$main_component")"
printf '%s\n' "$previous_start"
grep -q 'Status: ok' <<<"$previous_start"

adb install -r "$current_apk"
current_start="$(adb shell am start -W -n "$main_component")"
printf '%s\n' "$current_start"
grep -q 'Status: ok' <<<"$current_start"

./gradlew connectedDebugAndroidTest
