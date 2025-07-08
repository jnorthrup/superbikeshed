#!/bin/bash

echo "🔧 Re-enabling WASM targets in all modules..."

# Find all build.gradle.kts files with commented wasmJs
grep -l "// wasmJs" */build.gradle.kts | xargs gsed -i 's/^\([[:space:]]*\)\/\/ wasmJs/\1wasmJs/g'

echo "✅ WASM targets re-enabled!"