#\!/bin/bash

# Nexus MCP Server Startup Script
# Starts the Nexus Model Context Protocol server

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="/Users/jim/work/v2superbikeshed"

# Default configuration
MCP_PORT=${MCP_PORT:-8765}
AI_PROVIDER=${AI_PROVIDER:-litellm}
VERBOSE=${VERBOSE:-false}

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}Nexus MCP Server Startup${NC}"
echo "================================="

# Check if gradlew exists
if [ \! -f "$PROJECT_ROOT/gradlew" ]; then
    echo -e "${RED}Error: gradlew not found in $PROJECT_ROOT${NC}"
    echo "Please run this script from the nexus project directory"
    exit 1
fi

# Check for NVIDIA API key if using Nemotron
if [[ "$AI_PROVIDER" == "nemotron" || "$AI_PROVIDER" == "nemo" ]]; then
    if [ -z "$NVIDIA_API_KEY" ]; then
        echo -e "${YELLOW}Warning: NVIDIA_API_KEY not set. Using default key.${NC}"
        echo "To use your own key: export NVIDIA_API_KEY=your-key-here"
    fi
fi

# Build the project if needed
echo -e "${YELLOW}Building Nexus...${NC}"
cd "$PROJECT_ROOT"
./gradlew :nexus:build -q

if [ $? -ne 0 ]; then
    echo -e "${RED}Build failed\!${NC}"
    exit 1
fi

# Prepare arguments
ARGS="mcp $MCP_PORT"
if [ "$VERBOSE" = "true" ]; then
    ARGS="--verbose $ARGS"
fi
if [ -n "$AI_PROVIDER" ]; then
    ARGS="--ai-provider $AI_PROVIDER $ARGS"
fi

# Start the server
echo -e "${GREEN}Starting MCP server on port $MCP_PORT${NC}"
echo "AI Provider: $AI_PROVIDER"
echo "Verbose: $VERBOSE"
echo ""
echo "To connect: telnet localhost $MCP_PORT"
echo "Or use the test client: python test-mcp-client.py"
echo ""

# Run the server
exec ./gradlew :nexus:run -q --args="$ARGS"
EOF < /dev/null
