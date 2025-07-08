#!/bin/bash

echo "🔧 Disabling WASM targets in all modules..."

# Find all build.gradle.kts files with wasmJs
find . -name "build.gradle.kts" -type f | while read file; do
    if grep -q "wasmJs" "$file"; then
        echo "Processing: $file"
        # Comment out wasmJs lines
        gsed -i 's/^\([[:space:]]*\)wasmJs/\1\/\/ wasmJs/g' "$file"
    fi
done

echo "✅ WASM targets disabled!"
echo "Run ./enable-wasm.sh to re-enable them later"