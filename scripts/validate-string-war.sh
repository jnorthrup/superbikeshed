#!/bin/bash

# String War Validation Script
# Detects String usage violations that cause performance issues

set -e

echo "🔍 Validating String War constraints..."

VIOLATIONS_FOUND=0

# Check 1: String literals in loops (HIGHEST PRIORITY)
echo "  Checking for String literals in loops..."

STRING_IN_LOOPS=$(find . -name "*.kt" -exec grep -l "for.*in.*{" {} \; | xargs grep -l "val.*=.*\".*\"" 2>/dev/null || true)

if [ ! -z "$STRING_IN_LOOPS" ]; then
    echo "❌ VIOLATION: String literals found in loops:"
    echo "$STRING_IN_LOOPS" | while read file; do
        echo "  $file:"
        grep -n "val.*=.*\".*\"" "$file" | head -3
    done
    VIOLATIONS_FOUND=$((VIOLATIONS_FOUND + 1))
fi

# Check 2: String concatenation in hot paths
echo "  Checking for String concatenation in hot paths..."

STRING_CONCAT=$(find . -name "*.kt" -exec grep -l "for.*in.*{" {} \; | xargs grep -l "\${.*}" 2>/dev/null || true)

if [ ! -z "$STRING_CONCAT" ]; then
    echo "❌ VIOLATION: String concatenation found in loops:"
    echo "$STRING_CONCAT" | while read file; do
        echo "  $file:"
        grep -n "\${.*}" "$file" | head -3
    done
    VIOLATIONS_FOUND=$((VIOLATIONS_FOUND + 1))
fi

# Check 3: String comparisons in performance-critical code
echo "  Checking for String comparisons in performance-critical code..."

STRING_COMPARE=$(find . -name "*.kt" -exec grep -l "for.*in.*{" {} \; | xargs grep -l "==.*\".*\"" 2>/dev/null || true)

if [ ! -z "$STRING_COMPARE" ]; then
    echo "❌ VIOLATION: String comparisons found in loops:"
    echo "$STRING_COMPARE" | while read file; do
        echo "  $file:"
        grep -n "==.*\".*\"" "$file" | head -3
    done
    VIOLATIONS_FOUND=$((VIOLATIONS_FOUND + 1))
fi

# Check 4: String allocations in speculative contexts
echo "  Checking for String allocations in speculative contexts..."

STRING_ALLOC=$(find . -name "*.kt" -exec grep -l "String(" {} \; 2>/dev/null || true)

if [ ! -z "$STRING_ALLOC" ]; then
    echo "⚠️  WARNING: String allocations found (review for speculative contexts):"
    echo "$STRING_ALLOC" | head -5
fi

# Check 5: Good patterns - enums and constants
echo "  Checking for good patterns (enums, constants)..."

ENUM_USAGE=$(find . -name "*.kt" -exec grep -l "enum class" {} \; | wc -l)
CONSTANT_USAGE=$(find . -name "*.kt" -exec grep -l "const val" {} \; | wc -l)

echo "  ✅ Found $ENUM_USAGE enum classes"
echo "  ✅ Found $CONSTANT_USAGE const values"

# Summary
if [ $VIOLATIONS_FOUND -eq 0 ]; then
    echo "✅ String War validation passed!"
    echo ""
    echo "No String violations detected:"
    echo "  ✓ No String literals in loops"
    echo "  ✓ No String concatenation in hot paths"
    echo "  ✓ No String comparisons in performance-critical code"
    echo ""
    echo "Remember: 'Strings in speculative loops are the enemy of performance!'"
else
    echo ""
    echo "❌ String War validation failed with $VIOLATIONS_FOUND violations!"
    echo ""
    echo "Fix these violations to improve performance:"
    echo "  - Move String literals outside loops"
    echo "  - Use enums/constants instead of String identifiers"
    echo "  - Use structured logging instead of String concatenation"
    echo "  - Use primitive comparisons instead of String comparisons"
    echo ""
    echo "See .cursor/anchors/string-war-anchor.md for detailed patterns."
    exit 1
fi 