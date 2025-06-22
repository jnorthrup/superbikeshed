#!/bin/bash
# Async HTTP museum merger - restore HTTP/1.1 and HTTP/2 implementations
BRANCH_NAME="merge-http-museum-$(date +%s)-$(shuf -i 1000-9999 -n 1)"
cd /Users/jim/work/superbikeshed

git checkout -b "$BRANCH_NAME" 2>/dev/null

echo "=== HTTP Museum MERGE ===" > .claude/memo/http_merge_complete.txt
echo "Branch: $BRANCH_NAME" >> .claude/memo/http_merge_complete.txt

# Restore HTTP implementations
find museum -name "*http*" -name "*.kt" | while read http_file; do
    target_dir="Trikeshed/src/commonMain/kotlin/borg/trikeshed/net/http/"
    mkdir -p "$target_dir"
    cp "$http_file" "$target_dir/"
    echo "Restored: $(basename $http_file)" >> .claude/memo/http_merge_complete.txt
done

echo "Status: COMPLETE" >> .claude/memo/http_merge_complete.txt