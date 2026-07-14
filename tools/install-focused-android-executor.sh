#!/usr/bin/env bash
# Install Focused Android Executor V2 from the ZIP stored beside this script.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ZIP_FILE="$SCRIPT_DIR/focused-android-executor-v2.zip"
PROJECT_ROOT="${1:-$(pwd)}"
TARGET_DIR="$PROJECT_ROOT/.agent/skills/focused-android-executor"
TEMP_DIR="$(mktemp -d)"
cleanup() { rm -rf "$TEMP_DIR"; }
trap cleanup EXIT

if [[ ! -f "$ZIP_FILE" ]]; then
  echo "RESULT=ERROR"
  echo "REASON=skill_zip_not_found"
  echo "EXPECTED=$ZIP_FILE"
  exit 10
fi

if command -v unzip >/dev/null 2>&1; then
  unzip -q "$ZIP_FILE" -d "$TEMP_DIR"
elif command -v python3 >/dev/null 2>&1; then
  python3 -m zipfile -e "$ZIP_FILE" "$TEMP_DIR"
else
  echo "RESULT=ERROR"
  echo "REASON=no_zip_extractor"
  exit 11
fi

SOURCE_DIR="$TEMP_DIR/focused-android-executor"
if [[ ! -f "$SOURCE_DIR/SKILL.md" ]]; then
  echo "RESULT=ERROR"
  echo "REASON=invalid_skill_archive"
  exit 12
fi

mkdir -p "$(dirname "$TARGET_DIR")"
rm -rf "$TARGET_DIR"
mkdir -p "$TARGET_DIR"
cp -R "$SOURCE_DIR"/. "$TARGET_DIR"/
chmod +x "$TARGET_DIR/scripts/verify-debug.sh" 2>/dev/null || true

if [[ ! -f "$PROJECT_ROOT/CURRENT_TASK.md" ]]; then
  cp "$TARGET_DIR/templates/CURRENT_TASK.md" "$PROJECT_ROOT/CURRENT_TASK.md"
  TASK_STATUS=created
else
  TASK_STATUS=preserved
fi

echo "RESULT=OK"
echo "INSTALLED=$TARGET_DIR"
echo "CURRENT_TASK=$TASK_STATUS"
echo "NEXT=Fill CURRENT_TASK.md, then invoke focused-android-executor."
