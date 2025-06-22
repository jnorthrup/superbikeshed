#!/bin/bash
# Async LJSON/ZRAN museum merger - restore compression and parsing
BRANCH_NAME="merge-ljson-zran-museum-$(date +%s)-$(shuf -i 1000-9999 -n 1)"
cd /Users/jim/work/superbikeshed

git checkout -b "$BRANCH_NAME" 2>/dev/null

echo "=== LJSON/ZRAN Museum MERGE ===" > .claude/memo/ljson_zran_merge_complete.txt
echo "Branch: $BRANCH_NAME" >> .claude/memo/ljson_zran_merge_complete.txt

# Restore LJSON parsing
find museum/ljson -name "*.kt" | while read ljson_file; do
    target_dir="Trikeshed/src/commonMain/kotlin/borg/trikeshed/parse/ljson/"
    mkdir -p "$target_dir"
    cp "$ljson_file" "$target_dir/"
    echo "Restored LJSON: $(basename $ljson_file)" >> .claude/memo/ljson_zran_merge_complete.txt
done

# Restore ZRAN compression
find museum/zran -name "*.kt" | while read zran_file; do
    target_dir="Trikeshed/src/commonMain/kotlin/borg/trikeshed/tilting/zran/"
    mkdir -p "$target_dir"
    cp "$zran_file" "$target_dir/"
    echo "Restored ZRAN: $(basename $zran_file)" >> .claude/memo/ljson_zran_merge_complete.txt
done

echo "Status: COMPLETE" >> .claude/memo/ljson_zran_merge_complete.txt