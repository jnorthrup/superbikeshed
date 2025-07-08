#!/bin/bash
# Test Nexus functionality

echo "Testing Nexus..."

# Test help
echo "=== Testing help ==="
./gradlew :nexus:run --args="help" --no-daemon

# Test scan
echo -e "\n=== Testing scan ==="
./gradlew :nexus:run --args="scan" --no-daemon

# Test tools discovery
echo -e "\n=== Testing tools ==="
./gradlew :nexus:run --args="tools" --no-daemon

echo -e "\nDone!"