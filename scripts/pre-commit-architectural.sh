#!/bin/bash

# Pre-commit hook for architectural validation
# Prevents commits that break established design patterns

echo "🔍 Running pre-commit architectural validation..."

# Run architectural validation
if ! ./scripts/validate-architecture.sh; then
    echo ""
    echo "❌ Pre-commit validation failed!"
    echo "Please fix architectural violations before committing."
    echo ""
    echo "Common fixes:"
    echo "  - Ensure C interop files exist for SIMD (ADR-001)"
    echo "  - Maintain platform-specific actuals"
    echo "  - Use kotlinx.datetime instead of System.currentTimeMillis()"
    echo "  - Reference ADRs in architectural decisions"
    echo ""
    echo "See .cursorrules for architectural constraints."
    exit 1
fi

echo "✅ Pre-commit architectural validation passed!" 