#!/bin/bash
# Quick MCP server test

echo "Testing Nexus MCP Server..."

# Start server in background
./nexus/start-mcp-server.sh &
SERVER_PID=$!
sleep 2

# Run quick test
python3 -c "
import socket, json
s = socket.socket()
s.connect(('localhost', 8765))
s.send(json.dumps({'jsonrpc':'2.0','id':1,'method':'initialize'}).encode() + b'\n')
print(s.recv(1024).decode())
s.close()
"

# Cleanup
kill $SERVER_PID 2>/dev/null
echo "Done"