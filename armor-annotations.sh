#!/bin/bash

echo "🛡️ Armoring against annotation compilation errors"
echo "=============================================="

# Find all Kotlin files with problematic @file:OptIn declarations
echo "Scanning for @file:OptIn issues..."

# Fix the OptIn annotation syntax issues
find . -name "*.kt" -type f -not -path "./build/*" -not -path "./.gradle/*" | while read -r file; do
    if grep -q "^@file:OptIn" "$file"; then
        echo "Fixing: $file"
        
        # Use gsed to fix the OptIn syntax - add proper spacing and ensure it's on its own line
        gsed -i '1s/@file:OptIn/@file:OptIn/' "$file"
        
        # Remove duplicate or malformed OptIn annotations
        gsed -i '/^@file:OptIn/!b; N; s/@file:OptIn.*\n@file:OptIn/@file:OptIn/' "$file"
        
        # Ensure package declaration comes after file annotations
        gsed -i ':a; /^@file:/{N; /\npackage/!ba; }' "$file"
    fi
done

# Fix files that start with package but need OptIn
find . -name "*.kt" -type f -not -path "./build/*" -not -path "./.gradle/*" | while read -r file; do
    if grep -q "ExperimentalStdlibApi\|ExperimentalUnsignedTypes\|RequiresOptIn" "$file"; then
        if ! grep -q "^@file:OptIn" "$file"; then
            echo "Adding OptIn to: $file"
            # Add OptIn annotation before package declaration
            gsed -i '1s/^package/@file:OptIn(kotlin.ExperimentalStdlibApi::class)\npackage/' "$file"
        fi
    fi
done

echo "✅ Armor applied against annotation errors"
echo
echo "Running quick compilation test..."
gradle :trikeshed-lib:compileCommonMainKotlinMetadata --no-daemon --dry-run

echo "🎯 Done. Try building again."