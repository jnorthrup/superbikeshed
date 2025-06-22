#!/bin/bash
# Async IPFS museum merger - restore IPFS integration
BRANCH_NAME="merge-ipfs-museum-$(date +%s)-$(shuf -i 1000-9999 -n 1)"
cd /Users/jim/work/superbikeshed

git checkout -b "$BRANCH_NAME" 2>/dev/null

echo "=== IPFS Museum MERGE ===" > .claude/memo/ipfs_merge_complete.txt
echo "Branch: $BRANCH_NAME" >> .claude/memo/ipfs_merge_complete.txt

# Restore IPFS client implementations
find museum/ipfs -name "*.kt" | while read ipfs_file; do
    target_dir="Trikeshed/src/commonMain/kotlin/borg/trikeshed/ipfs/"
    mkdir -p "$target_dir"
    cp "$ipfs_file" "$target_dir/"
    echo "Restored: $(basename $ipfs_file)" >> .claude/memo/ipfs_merge_complete.txt
done

echo "Status: COMPLETE" >> .claude/memo/ipfs_merge_complete.txt