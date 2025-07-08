#!/bin/bash

# Nexus MCP Server Startup Script
# Starts the Model Context Protocol server for Nexus

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=== Nexus MCP Server ==="
echo

# Default port
PORT=${1:-8765}

# Build the project first
echo "Building nexus..."
cd "$PROJECT_ROOT"
./gradlew :nexus:jvmJar || {
    echo "Build failed!"
    exit 1
}

# Find the JAR
JAR_PATH="$PROJECT_ROOT/nexus/build/libs/nexus-jvm.jar"
if [ ! -f "$JAR_PATH" ]; then
    echo "JAR not found at: $JAR_PATH"
    echo "Looking for JARs..."
    find "$PROJECT_ROOT/nexus/build" -name "*.jar" -type f
    exit 1
fi

echo
echo "Starting MCP server on port $PORT..."
echo "Press Ctrl+C to stop"
echo

# Run the MCP server
java -cp "$JAR_PATH" nexus.MainKt mcp $PORT