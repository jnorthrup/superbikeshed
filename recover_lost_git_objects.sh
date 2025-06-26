#!/bin/bash

# RECOVER LOST GIT OBJECTS SCRIPT
# This script recovers the massive upside-down pyramid of Git blobs 
# that were getting destroyed without notice

echo "🚨 RECOVERING LOST GIT OBJECTS - THE UPSIDE-DOWN PYRAMID OF BLOBS 🚨"
echo "=================================================================="

# Create recovery directory
RECOVERY_DIR="git-objects-recovery-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$RECOVERY_DIR"
cd "$RECOVERY_DIR"

echo "📁 Created recovery directory: $RECOVERY_DIR"

# Get all unreachable objects
echo "🔍 Scanning for unreachable objects..."
git fsck --full --unreachable --no-reflogs > unreachable_objects.txt

# Count objects
COMMIT_COUNT=$(grep "unreachable commit" unreachable_objects.txt | wc -l)
TREE_COUNT=$(grep "unreachable tree" unreachable_objects.txt | wc -l)
BLOB_COUNT=$(grep "unreachable blob" unreachable_objects.txt | wc -l)

echo "📊 Found $COMMIT_COUNT unreachable commits"
echo "📊 Found $TREE_COUNT unreachable trees" 
echo "📊 Found $BLOB_COUNT unreachable blobs"
echo "📊 TOTAL: $((COMMIT_COUNT + TREE_COUNT + BLOB_COUNT)) lost objects"

# Create recovery branches for commits
echo "🌿 Creating recovery branches for commits..."
grep "unreachable commit" unreachable_objects.txt | while read -r line; do
    COMMIT_HASH=$(echo "$line" | awk '{print $3}')
    BRANCH_NAME="recovery-commit-${COMMIT_HASH:0:8}"
    echo "Creating branch $BRANCH_NAME for commit $COMMIT_HASH"
    git branch "$BRANCH_NAME" "$COMMIT_HASH" 2>/dev/null || echo "Branch $BRANCH_NAME already exists"
done

# Save blob contents
echo "💾 Saving blob contents..."
mkdir -p blobs
grep "unreachable blob" unreachable_objects.txt | while read -r line; do
    BLOB_HASH=$(echo "$line" | awk '{print $3}')
    echo "Saving blob $BLOB_HASH"
    git show "$BLOB_HASH" > "blobs/$BLOB_HASH" 2>/dev/null || echo "Failed to save blob $BLOB_HASH"
done

# Save tree contents
echo "🌳 Saving tree contents..."
mkdir -p trees
grep "unreachable tree" unreachable_objects.txt | while read -r line; do
    TREE_HASH=$(echo "$line" | awk '{print $3}')
    echo "Saving tree $TREE_HASH"
    git ls-tree "$TREE_HASH" > "trees/$TREE_HASH" 2>/dev/null || echo "Failed to save tree $TREE_HASH"
done

# Create summary report
echo "📝 Creating recovery summary..."
cat > recovery_summary.md << EOF
# Git Objects Recovery Summary

## Recovery Date
$(date)

## Objects Recovered
- **Commits**: $COMMIT_COUNT
- **Trees**: $TREE_COUNT  
- **Blobs**: $BLOB_COUNT
- **Total**: $((COMMIT_COUNT + TREE_COUNT + BLOB_COUNT))

## Recovery Actions Taken
1. Created recovery branches for all unreachable commits
2. Saved blob contents to \`blobs/\` directory
3. Saved tree contents to \`trees/\` directory
4. Generated this summary report

## Next Steps
- Review recovered objects for important content
- Merge relevant commits back to main branches
- Archive or delete recovery branches as needed

## Warning
These objects were unreachable and would have been destroyed by Git garbage collection.
This recovery preserves them from the upside-down pyramid of blobs.
EOF

echo "✅ RECOVERY COMPLETE!"
echo "📁 Recovery directory: $RECOVERY_DIR"
echo "📊 Recovered $((COMMIT_COUNT + TREE_COUNT + BLOB_COUNT)) objects"
echo "🌿 Created recovery branches for $COMMIT_COUNT commits"
echo "💾 Saved $BLOB_COUNT blobs to blobs/ directory"
echo "🌳 Saved $TREE_COUNT trees to trees/ directory"
echo "📝 Summary report: recovery_summary.md"

cd ..
echo "🎉 The upside-down pyramid of Git blobs has been RECOVERED!" 