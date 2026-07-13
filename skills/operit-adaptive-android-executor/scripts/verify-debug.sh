#!/usr/bin/env bash
# Operit 1.12+ synchronous Android debug build/install helper.
# Do not add `set -e`: Operit warns that errexit can terminate the terminal session.
set -u
set -o pipefail

PROJECT_ROOT="${1:-$(pwd)}"
MODE="${2:-install}" # install | build-only
GRADLE_TASK="${GRADLE_TASK:-assembleDebug}"
STATE_DIR="$PROJECT_ROOT/.operit-executor"
RESULT_FILE="$STATE_DIR/verification-result.txt"
LOG_FILE="$STATE_DIR/last-build.log"

mkdir -p "$STATE_DIR" 2>/dev/null || true
: > "$RESULT_FILE" 2>/dev/null || true

emit() {
  printf '%s\n' "$1"
  printf '%s\n' "$1" >> "$RESULT_FILE" 2>/dev/null || true
}

fail() {
  emit "RESULT=ERROR"
  emit "STEP=$1"
  emit "REASON=$2"
  exit "${3:-1}"
}

cd "$PROJECT_ROOT" 2>/dev/null || fail "PREPARE" "project_root_not_found" 10

[[ -f ./gradlew ]] || fail "PREPARE" "gradlew_not_found" 11
[[ -x ./gradlew ]] || chmod +x ./gradlew 2>/dev/null || true

emit "RESULT=RUNNING"
emit "STEP=BUILD"
emit "TASK=$GRADLE_TASK"

./gradlew --console=plain "$GRADLE_TASK" > "$LOG_FILE" 2>&1
BUILD_STATUS=$?

if [[ $BUILD_STATUS -ne 0 ]]; then
  emit "RESULT=ERROR"
  emit "STEP=BUILD"
  emit "EXIT_CODE=$BUILD_STATUS"
  emit "LOG_LAST_100_BEGIN"
  tail -n 100 "$LOG_FILE" | tee -a "$RESULT_FILE"
  emit "LOG_LAST_100_END"
  exit 20
fi

APK_PATH=""
for candidate in \
  "$PROJECT_ROOT/app/build/outputs/apk/debug/app-debug.apk" \
  "$PROJECT_ROOT/app/build/outputs/apk/debug/app-debug-unsigned.apk"
do
  if [[ -f "$candidate" ]]; then
    APK_PATH="$candidate"
    break
  fi
done

if [[ -z "$APK_PATH" ]]; then
  while IFS= read -r -d '' candidate; do
    if [[ -z "$APK_PATH" || "$candidate" -nt "$APK_PATH" ]]; then
      APK_PATH="$candidate"
    fi
  done < <(find "$PROJECT_ROOT" -type f \
    \( -path '*/build/outputs/apk/debug/*.apk' -o -path '*/build/outputs/apk/*/debug/*.apk' \) \
    -print0 2>/dev/null)
fi

[[ -n "$APK_PATH" && -f "$APK_PATH" ]] || fail "LOCATE_APK" "debug_apk_not_found" 21

if [[ "$MODE" == "build-only" ]]; then
  : > "$RESULT_FILE"
  emit "RESULT=OK"
  emit "BUILD=SUCCESS"
  emit "INSTALL=SKIPPED"
  emit "APK=$APK_PATH"
  exit 0
fi

command -v adb >/dev/null 2>&1 || fail "INSTALL" "adb_not_found" 30

ADB_STATE="$(adb get-state 2>/dev/null || true)"
if [[ "$ADB_STATE" != "device" ]]; then
  emit "RESULT=ERROR"
  emit "STEP=INSTALL"
  emit "BUILD=SUCCESS"
  emit "APK=$APK_PATH"
  emit "REASON=adb_device_not_ready"
  emit "ADB_STATE=${ADB_STATE:-unknown}"
  exit 31
fi

INSTALL_OUTPUT="$(adb install -r "$APK_PATH" 2>&1)"
INSTALL_STATUS=$?
if [[ $INSTALL_STATUS -ne 0 ]] || ! printf '%s' "$INSTALL_OUTPUT" | grep -q "Success"; then
  emit "RESULT=ERROR"
  emit "STEP=INSTALL"
  emit "BUILD=SUCCESS"
  emit "APK=$APK_PATH"
  emit "EXIT_CODE=$INSTALL_STATUS"
  emit "INSTALL_OUTPUT_BEGIN"
  printf '%s\n' "$INSTALL_OUTPUT" | tail -n 100 | tee -a "$RESULT_FILE"
  emit "INSTALL_OUTPUT_END"
  exit 32
fi

: > "$RESULT_FILE"
emit "RESULT=OK"
emit "BUILD=SUCCESS"
emit "INSTALL=SUCCESS"
emit "APK=$APK_PATH"
exit 0
