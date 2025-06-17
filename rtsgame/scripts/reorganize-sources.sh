#!/bin/bash

# Create standardized directory structure
mkdir -p src/commonMain/kotlin
mkdir -p src/commonMain/resources
mkdir -p src/jsMain/kotlin
mkdir -p src/jsMain/resources
mkdir -p src/jvmMain/kotlin
mkdir -p src/jvmMain/resources

# Move existing files to new structure
# Common code
mv src/commonMain/*.kt src/commonMain/kotlin/ 2>/dev/null || true
mv src/commonMain/resources/* src/commonMain/resources/ 2>/dev/null || true

# JVM code
mv src/jvmMain/*.kt src/jvmMain/kotlin/ 2>/dev/null || true
mv src/jvmMain/resources/* src/jvmMain/resources/ 2>/dev/null || true

# JS/WASM code
mv src/wasmJsMain/*.kt src/jsMain/kotlin/ 2>/dev/null || true
mv src/wasmJsMain/resources/* src/jsMain/resources/ 2>/dev/null || true

# Clean up empty directories
find src -type d -empty -delete

echo "Source reorganization complete!" 