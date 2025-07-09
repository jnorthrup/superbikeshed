#!/bin/bash
set -euo pipefail

# 1. Check Gradle targets
TARGETS_FILE=".gradle_targets.stable"
CUR_TARGETS=$(find . -name 'settings.gradle*' -exec grep -h '^\s*include' {} + | sed 's/^[^\'"'"']*\'"'"'//;s/\'"'"'.*//' | sort | uniq)
if [ ! -f "$TARGETS_FILE" ]; then
  echo "$CUR_TARGETS" > "$TARGETS_FILE"
  echo "[INFO] Saved initial Gradle targets snapshot."
else
  DIFF=$(diff <(echo "$CUR_TARGETS") "$TARGETS_FILE" || true)
  if [ -n "$DIFF" ]; then
    echo "[ERROR] Gradle targets have changed!"
    echo "$DIFF"
    exit 1
  fi
fi

# 2. Check for dependency versions in build files
BAD_DEPS=$(grep -rE 'implementation|api|compileOnly|runtimeOnly' --include='build.gradle*' . | grep -E ':[0-9]') || true
if [ -n "$BAD_DEPS" ]; then
  echo "[ERROR] Found dependencies with explicit versions in build files:"
  echo "$BAD_DEPS"
  exit 2
fi

# 3. Check for new/untracked files
FILES_FILE=".gradle_files.stable"
CUR_FILES=$(git ls-files | sort)
if [ ! -f "$FILES_FILE" ]; then
  echo "$CUR_FILES" > "$FILES_FILE"
  echo "[INFO] Saved initial tracked files snapshot."
else
  DIFF=$(diff <(echo "$CUR_FILES") "$FILES_FILE" || true)
  if [ -n "$DIFF" ]; then
    echo "[ERROR] Tracked files have changed! (new/untracked files detected)"
    echo "$DIFF"
    exit 3
  fi
fi

# 4. Integration with Project Armor Stacktrace Fixer
if [ -f "./armor-stacktrace-nvidia-tasker.sh" ]; then
  echo "[INFO] Project Armor integration available"
  echo "[INFO] Run './armor-stacktrace-nvidia-tasker.sh demo' to test TrikeShed stacktrace methodology"
fi

echo "[OK] super-gradle-check passed." 