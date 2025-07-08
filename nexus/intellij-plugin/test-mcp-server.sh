#!/bin/bash

# Test MCP Server

echo "Testing MCP Server..."

# Test stdio interface
echo '{"jsonrpc":"2.0","id":"1","method":"initialize","params":{}}' | nc localhost 63344

# Test QUIC channels
echo "Testing QUIC channels:"
echo "PSI channel: 63344"
echo "Analysis channel: 63345"
echo "Refactor channel: 63346"
echo "Control channel: 63347"

# Test with curl if available
if command -v curl &> /dev/null; then
    echo "Testing with curl..."
    curl -X POST http://localhost:63344/mcp/rpc \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc":"2.0","id":"test","method":"tools/list"}'
fi