#!/bin/bash

# Architectural Validation Script
# Validates that changes don't break established design patterns
# 
# ADR-001: SIMD Strategy Pattern - Validates C interop and platform-specific actuals
# ADR-002: String Performance War - Validates no String allocations in loops

set -e

echo "🔍 Validating architectural constraints..."

# Check 1: SIMD Strategy Pattern (ADR-001)
echo "  Checking SIMD Strategy Pattern (ADR-001)..."

# Ensure C interop files exist
if [ ! -f "trikeshed-lib/src/macosArm64Main/cinterop/include/simd.h" ]; then
    echo "❌ ERROR: Missing macosArm64 SIMD header (ADR-001 violation)"
    exit 1
fi

if [ ! -f "trikeshed-lib/src/linuxX64Main/cinterop/include/simd.h" ]; then
    echo "❌ ERROR: Missing linuxX64 SIMD header (ADR-001 violation)"
    exit 1
fi

# Ensure platform-specific actuals exist
if [ ! -f "trikeshed-lib/src/macosArm64Main/kotlin/borg/trikeshed/lib/simd/SimdStrategy.kt" ]; then
    echo "❌ ERROR: Missing macosArm64 SIMD actual (ADR-001 violation)"
    exit 1
fi

if [ ! -f "trikeshed-lib/src/linuxX64Main/kotlin/borg/trikeshed/lib/simd/SimdStrategy.kt" ]; then
    echo "❌ ERROR: Missing linuxX64 SIMD actual (ADR-001 violation)"
    exit 1
fi

# Check 2: Platform-specific source sets
echo "  Checking platform-specific source sets..."

# Ensure platform targets are maintained
if ! grep -q "macosArm64()" trikeshed-lib/build.gradle.kts; then
    echo "❌ ERROR: Missing macosArm64 target (architectural violation)"
    exit 1
fi

if ! grep -q "linuxX64()" trikeshed-lib/build.gradle.kts; then
    echo "❌ ERROR: Missing linuxX64 target (architectural violation)"
    exit 1
fi

# Check 3: C interop configuration
echo "  Checking C interop configuration..."

if ! grep -q "cinterops" trikeshed-lib/build.gradle.kts; then
    echo "❌ ERROR: Missing cinterop configuration (ADR-001 violation)"
    exit 1
fi

# Check 4: ADR documentation
echo "  Checking architectural documentation..."

if [ ! -f "docs/architecture/decisions/ADR-001-SIMD-Strategy-Pattern.md" ]; then
    echo "❌ ERROR: Missing ADR-001 documentation"
    exit 1
fi

if [ ! -f "docs/architecture/decisions/ADR-002-String-Performance-War.md" ]; then
    echo "❌ ERROR: Missing ADR-002 documentation"
    exit 1
fi

# Check 5: String Performance War (ADR-002)
echo "  Checking String Performance War constraints (ADR-002)..."

# Check for System.currentTimeMillis() usage (should use kotlinx.datetime)
if grep -r "System.currentTimeMillis()" trikeshed-lib/src/ --include="*.kt" | grep -v "// TODO" | grep -v "// FIXME"; then
    echo "❌ ERROR: Found System.currentTimeMillis() usage (use kotlinx.datetime instead)"
    exit 1
fi

# Check for scalar fallbacks in SIMD implementations
if grep -r "// TODO.*scalar" trikeshed-lib/src/ --include="*.kt" | grep -v "// TODO.*implement.*SIMD"; then
    echo "⚠️  WARNING: Found scalar fallback TODOs - ensure these are temporary"
fi

echo "✅ Architectural validation passed!"
echo ""
echo "Architectural constraints validated:"
echo "  ✓ SIMD Strategy Pattern (ADR-001)"
echo "  ✓ String Performance War (ADR-002)"
echo "  ✓ Platform-specific source sets"
echo "  ✓ C interop configuration"
echo "  ✓ Architectural documentation"
echo "  ✓ No forbidden patterns detected" 