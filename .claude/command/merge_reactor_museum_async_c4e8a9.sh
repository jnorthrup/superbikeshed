#!/bin/bash
# Async REACTOR museum merger - restore reactor pattern implementation
BRANCH_NAME="merge-reactor-museum-$(date +%s)-$(shuf -i 1000-9999 -n 1)"
cd /Users/jim/work/superbikeshed

git checkout -b "$BRANCH_NAME" 2>/dev/null

echo "=== REACTOR Museum MERGE ===" > .claude/memo/reactor_merge_complete.txt
echo "Branch: $BRANCH_NAME" >> .claude/memo/reactor_merge_complete.txt

# Restore reactor pattern implementations
find museum/reactor -name "*.kt" | while read reactor_file; do
    target_dir="Trikeshed/src/commonMain/kotlin/borg/trikeshed/reactor/"
    mkdir -p "$target_dir"
    cp "$reactor_file" "$target_dir/"
    echo "Restored: $(basename $reactor_file)" >> .claude/memo/reactor_merge_complete.txt
done

echo "Status: COMPLETE" >> .claude/memo/reactor_merge_complete.txt