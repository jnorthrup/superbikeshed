#!/bin/bash

# Run Nexus in interactive mode
echo "Starting Nexus Interactive LLM..."

# Build if needed
cd "$(dirname "$0")"
cd ..
./gradlew :nexus:build --console=plain --no-daemon

# Run interactive mode
echo ""
echo "Launching Nexus Interactive..."
java -cp nexus/build/libs/nexus-jvm-*-SNAPSHOT.jar nexus.MainKt --interactive