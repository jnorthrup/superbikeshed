#!/bin/bash

# Zero-Error Build Check Script
# Ensures codebase is error-free before sandbox submission

set -e  # Exit on any error

echo "=== Zero-Error Build Check ==="
echo "Starting at: $(date)"
echo

# Step 1: Clean everything
echo "1. Cleaning workspace..."
./gradlew clean --console=plain --no-daemon

# Step 2: Check each module individually
echo -e "\n2. Building modules individually..."
MODULES=(
    "trikeshed-lib"
    "trikeshed-common"
    "trikeshed-io"
    "trikeshed-reactor"
    "trikeshed-net"
    "trikeshed-torrent"
    "trikeshed-dht"
    "trikeshed-ipc"
    "trikeshed-ccek"
    "trikeshed-ljson"
    "trikeshed-strace"
    "trikeshed-couchdb"
    "trikeshed-ipfs"
    "trikeshed-isam"
    "trikeshed-json"
    "trikeshed-cursor"
    "trikeshed-lsmr"
)

FAILED_MODULES=()

for module in "${MODULES[@]}"; do
    echo -e "\n--- Building $module ---"
    if ./gradlew ":$module:build" --console=plain --no-daemon; then
        echo "✓ $module built successfully"
    else
        echo "✗ $module FAILED"
        FAILED_MODULES+=("$module")
    fi
done

# Step 3: Run full build
echo -e "\n3. Running full build..."
if ./gradlew build --console=plain --no-daemon; then
    echo "✓ Full build successful"
else
    echo "✗ Full build FAILED"
    exit 1
fi

# Step 4: Run tests
echo -e "\n4. Running tests..."
if ./gradlew test --console=plain --no-daemon; then
    echo "✓ All tests passed"
else
    echo "✗ Tests FAILED"
    exit 1
fi

# Step 5: Summary
echo -e "\n=== Build Summary ==="
if [ ${#FAILED_MODULES[@]} -eq 0 ]; then
    echo "✓ All modules built successfully!"
    echo "✓ Ready for sandbox submission"
    exit 0
else
    echo "✗ Failed modules:"
    for module in "${FAILED_MODULES[@]}"; do
        echo "  - $module"
    done
    echo
    echo "Fix these modules before submitting to sandbox"
    exit 1
fi