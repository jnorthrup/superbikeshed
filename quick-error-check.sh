#!/bin/bash

# Quick Error Check - Minimal bandwidth usage
# Only compiles, doesn't download dependencies

echo "=== Quick Error Check (Low Bandwidth) ==="

# Just compile Kotlin without tests or packaging
echo "Checking compilation only..."

# Use offline mode if dependencies are cached
GRADLE_OPTS="--console=plain --no-daemon --offline"

# Try offline first
if ./gradlew compileKotlinCommon $GRADLE_OPTS 2>/dev/null; then
    echo "✓ Compilation successful (offline)"
else
    echo "⚠ Offline failed, trying with network..."
    # Remove offline flag for one attempt
    ./gradlew compileKotlinCommon --console=plain --no-daemon
fi

# Quick syntax check for specific files
echo -e "\nChecking recently modified files..."
git diff --name-only HEAD^ HEAD | grep "\.kt$" | while read file; do
    if [ -f "$file" ]; then
        echo "Checking: $file"
        # Just parse, don't compile
        kotlinc-native -syntax "$file" 2>/dev/null || echo "  ⚠ Syntax issues in $file"
    fi
done

echo -e "\n✓ Quick check complete"