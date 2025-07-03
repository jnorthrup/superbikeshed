#!/bin/bash

# Fix all references to old :Trikeshed project
echo "Fixing Trikeshed dependencies in all build.gradle.kts files..."

# List of files that need fixing (excluding settings.gradle.kts files)
files=(
    "SSH/build.gradle.kts"
    "spacegraph/build.gradle.kts"
    "moneyfan/build.gradle.kts"
    "kotlinx-serialization-wireproto/build.gradle.kts"
    "kotlinx-serialization-scanner/build.gradle.kts"
    "kotlin-entity-scanner/build.gradle.kts"
    "k2script/build.gradle.kts"
    "flatton/build.gradle.kts"
    "nexus/build.gradle.kts"
    "fiduciary/build.gradle.kts"
    "rtsgame/build.gradle.kts"
    "zlib-kmp-reference/build.gradle.kts"
    "ta4k/build.gradle.kts"
)

for file in "${files[@]}"; do
    if [ -f "$file" ]; then
        echo "Processing $file..."
        # Replace implementation(project(":Trikeshed")) with the new modules
        sed -i '' 's/implementation(project(":Trikeshed"))/implementation(project(":trikeshed-lib"))\n                implementation(project(":trikeshed-common"))/' "$file"
        
        # Also handle api(project(":Trikeshed")) if it exists
        sed -i '' 's/api(project(":Trikeshed"))/api(project(":trikeshed-lib"))\n                api(project(":trikeshed-common"))/' "$file"
    else
        echo "Warning: $file not found"
    fi
done

echo "Done! All Trikeshed dependencies have been updated."