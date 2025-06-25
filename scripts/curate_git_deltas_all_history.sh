#!/bin/bash
set -euo pipefail

OUTDIR="moneyfan_chronicles_full"
rm -rf "$OUTDIR"
mkdir -p "$OUTDIR"

# Get all files ever tracked in moneyfan and ta4k (including deleted/moved)
git log --pretty=format: --name-only -- moneyfan ta4k | grep -E '\.(kt|java|kts)$' | sort | uniq | while read FILE; do
  # Remove leading ./ if present
  CLEANFILE="${FILE#./}"
  # Replace / with _ for output filename
  OUTFILE="$OUTDIR/${CLEANFILE//\//_}.chronicle"
  # Get all commits for this file, oldest first
  git log --follow --format="%H" -- "$FILE" | tac | while read COMMIT; do
    DATE=$(git show -s --format=%ci "$COMMIT")
    # Get the delta for this commit
    PARENT=$(git rev-list --parents -n 1 "$COMMIT" -- "$FILE" | awk '{print $2}')
    if [ -n "$PARENT" ]; then
      printf '\n--- Commit: %s | %s ---\n' "$COMMIT" "$DATE" >> "$OUTFILE"
      git diff "$PARENT" "$COMMIT" -- "$FILE" >> "$OUTFILE"
    fi
  done
done
