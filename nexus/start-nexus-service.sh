#!/bin/bash

# Nexus Service Loop Startup Script
# This script sets up and starts the integrated Nexus development environment

set -e

echo "🚀 Starting Nexus Service Loop"
echo "================================"

# Check if we're in the right directory
if [ ! -f "nexus-service-loop.kts" ]; then
    echo "❌ Error: nexus-service-loop.kts not found in current directory"
    echo "   Please run this script from the nexus/ directory"
    exit 1
fi

# Check for required environment variables
echo "🔍 Checking environment setup..."

# Check for NVIDIA API key
if [ -z "$NVIDIA_API_KEY" ] && [ -z "$HF_TOKEN" ]; then
    echo "⚠️  Warning: No NVIDIA_API_KEY or HF_TOKEN found"
    echo "   Get a free Nemotron API key at: https://build.nvidia.com"
    echo "   Or set HF_TOKEN for Hugging Face access"
    echo ""
    echo "   You can still run the service, but LLM features will be limited"
    echo ""
else
    echo "✅ LLM API key found"
fi

# Check if IntelliJ is running and API is enabled
echo "🔧 Checking IntelliJ API..."
if curl -s http://localhost:63342/api/status > /dev/null 2>&1; then
    echo "✅ IntelliJ API is accessible"
else
    echo "⚠️  Warning: IntelliJ API not accessible"
    echo "   Make sure IntelliJ is running with REST API enabled:"
    echo "   Help → Edit Custom VM Options → Add:"
    echo "   -Dide.rest.api=true"
    echo "   -Dide.rest.api.port=63342"
    echo "   -Dide.rest.api.cors.enabled=true"
    echo ""
fi

# Set project path
PROJECT_PATH="${PROJECT_PATH:-$(pwd)/..}"
echo "📁 Project path: $PROJECT_PATH"

# Export environment variables
export PROJECT_PATH
export NEXUS_SERVICE_MODE=true

echo ""
echo "🎯 Starting Nexus Service Loop..."
echo "   Press Ctrl+C to stop"
echo ""

# Run the service loop
kotlin nexus-service-loop.kts 