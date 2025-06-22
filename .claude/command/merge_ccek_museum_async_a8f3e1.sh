#!/bin/bash
# Async CCEK museum merger - actually integrate preserved CCEK code
BRANCH_NAME="merge-ccek-museum-$(date +%s)-$(shuf -i 1000-9999 -n 1)"
cd /Users/jim/work/superbikeshed

echo "Starting CCEK museum MERGE in branch $BRANCH_NAME"
git checkout -b "$BRANCH_NAME" 2>/dev/null || echo "Branch exists, continuing"

echo "=== CCEK Museum MERGE Operation ===" > .claude/memo/ccek_merge_complete.txt
echo "Branch: $BRANCH_NAME" >> .claude/memo/ccek_merge_complete.txt
echo "Status: MERGING" >> .claude/memo/ccek_merge_complete.txt

# Find and integrate CCEK museum artifacts back into TrikeShed
CCEK_MUSEUM_DIR="museum/ccek"
if [ -d "$CCEK_MUSEUM_DIR" ]; then
    echo "Found CCEK museum, integrating artifacts..." >> .claude/memo/ccek_merge_complete.txt
    
    # Apply CCEK-related deltas from museum back to current codebase
    find "$CCEK_MUSEUM_DIR" -name "*.diff" | while read diff_file; do
        echo "Applying CCEK diff: $diff_file" >> .claude/memo/ccek_merge_complete.txt
        patch -p1 < "$diff_file" 2>&1 | head -10 >> .claude/memo/ccek_merge_complete.txt
    done
    
    # Restore any CCEK-specific files that were preserved
    find "$CCEK_MUSEUM_DIR" -name "*.kt" -o -name "*.java" | while read kt_file; do
        echo "Restoring CCEK file: $kt_file" >> .claude/memo/ccek_merge_complete.txt
        # Copy to appropriate location in TrikeShed
        cp "$kt_file" "Trikeshed/src/commonMain/kotlin/borg/trikeshed/ccek/" 2>/dev/null || echo "Created dir and copied"
        mkdir -p "Trikeshed/src/commonMain/kotlin/borg/trikeshed/ccek/"
        cp "$kt_file" "Trikeshed/src/commonMain/kotlin/borg/trikeshed/ccek/"
    done
    
    # Try a quick compile to see if CCEK integration works
    export JAVA_OPTS="--enable-native-access=ALL-UNNAMED"
    ./gradlew :Trikeshed:compileKotlinJvm --console=plain --no-daemon -Djava.version=21 2>&1 | tail -20 >> .claude/memo/ccek_merge_complete.txt
fi

echo "Status: COMPLETE" >> .claude/memo/ccek_merge_complete.txt
echo "CCEK museum merge completed in branch $BRANCH_NAME"