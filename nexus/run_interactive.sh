#!/bin/bash

# Run Nexus in interactive mode
echo "Starting Nexus Interactive (Enhanced Edition)..."

# Build if needed
cd "$(dirname "$0")"
cd ..
./gradlew :nexus:build --console=plain --no-daemon

# Run interactive mode
echo ""
echo "Launching Nexus Interactive..."
echo "Set NEXUS_MODEL environment variable to choose your LLM model"
echo "Example: export NEXUS_MODEL=gpt-4"
echo ""
java -cp nexus/build/libs/nexus-jvm-*-SNAPSHOT.jar nexus.MainKt --interactive