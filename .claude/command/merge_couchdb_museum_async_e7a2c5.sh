#!/bin/bash
# Async COUCHDB museum merger - restore CouchDB integration
BRANCH_NAME="merge-couchdb-museum-$(date +%s)-$(shuf -i 1000-9999 -n 1)"
cd /Users/jim/work/superbikeshed

git checkout -b "$BRANCH_NAME" 2>/dev/null

echo "=== COUCHDB Museum MERGE ===" > .claude/memo/couchdb_merge_complete.txt
echo "Branch: $BRANCH_NAME" >> .claude/memo/couchdb_merge_complete.txt

# Restore CouchDB client implementations
find museum/couchdb -name "*.kt" | while read couchdb_file; do
    target_dir="Trikeshed/src/commonMain/kotlin/borg/trikeshed/couchdb/"
    mkdir -p "$target_dir"
    cp "$couchdb_file" "$target_dir/"
    echo "Restored: $(basename $couchdb_file)" >> .claude/memo/couchdb_merge_complete.txt
done

echo "Status: COMPLETE" >> .claude/memo/couchdb_merge_complete.txt