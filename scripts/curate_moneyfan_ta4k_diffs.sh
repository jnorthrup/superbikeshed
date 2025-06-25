#!/bin/bash
set -euo pipefail

# Output root directory
OUTDIR="moneyfan_git_udiffs"

# Source roots to process
SOURCES=("moneyfan/src" "ta4k/src")
# File extensions to include
EXTS=("kt" "java" "kts")

# Clean output dir
rm -rf "$OUTDIR"
mkdir -p "$OUTDIR"

for SRCROOT in "${SOURCES[@]}"; do
  # Find all source files with the given extensions
  find "$SRCROOT" \( $(printf -- '-name "*.%s" -o ' "${EXTS[@]}") -false \) | while read -r FILE; do
    # Remove trailing -o -false
    FILE="${FILE#./}"
    # Mirror the directory structure in OUTDIR, but each file gets its own dir
    FILEDIR="$OUTDIR/$(dirname "$FILE")/$(basename "$FILE")"
    mkdir -p "$FILEDIR"
    # Get all commits touching this file, oldest first
    git log --follow --format="%H" -- "$FILE" | tac | while read -r COMMIT; do
      # Get the diff for this commit (if not the first commit, else skip)
      PARENT=$(git rev-list --parents -n 1 "$COMMIT" -- "$FILE" | awk '{print $2}')
      if [ -n "$PARENT" ]; then
        git diff "$PARENT" "$COMMIT" -- "$FILE" > "$FILEDIR/$COMMIT.diff"
      fi
    done
  done
done
