#!/bin/bash
set -euo pipefail

INDIR="moneyfan_git_udiffs"
OUTDIR="moneyfan_chronicles"

rm -rf "$OUTDIR"
mkdir -p "$OUTDIR"

find "$INDIR" -type d | while read FILEDIR; do
  # Only process leaf directories (those containing .diff files)
  if ls "$FILEDIR"/*.diff >/dev/null 2>&1; then
    RELDIR="${FILEDIR#$INDIR/}"
    OUTFILE="$OUTDIR/$RELDIR.chronicle"
    mkdir -p "$(dirname "$OUTFILE")"
    # Sort diffs by name (commit hash, which is chronological in this context)
    for DIFF in $(ls "$FILEDIR"/*.diff | sort); do
      COMMIT=$(basename "$DIFF" .diff)
      DATE=$(git show -s --format=%ci "$COMMIT")
      # Extract deleted lines, clean trivial ones
      awk '/^-/ && !/^---/ && !/^@@/ {print substr($0,2)}' "$DIFF" | grep -v '^$' | grep -v '^\s*$' > tmp_deleted.txt
      if [ -s tmp_deleted.txt ]; then
        printf '\n--- Commit: %s | %s ---\n' "$COMMIT" "$DATE" >> "$OUTFILE"
        cat tmp_deleted.txt >> "$OUTFILE"
        printf '\n' >> "$OUTFILE"
      fi
      rm -f tmp_deleted.txt
    done
    # Remove empty chronicles
    [ -s "$OUTFILE" ] || rm "$OUTFILE"
  fi
done
