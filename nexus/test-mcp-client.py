#!/usr/bin/env python3
"""
Test client for Nexus MCP Server
Tests all available MCP endpoints
"""

import json
import socket
import sys

def send_request(sock, method, params=None, id=1):
    """Send a JSON-RPC request and get response"""
    request = {
        "jsonrpc": "2.0",
        "id": id,
        "method": method
    }
    if params:
        request["params"] = params
    
    # Send request
    message = json.dumps(request) + "\n"
    sock.sendall(message.encode())
    
    # Read response
    response = ""
    while True:
        chunk = sock.recv(1024).decode()
        response += chunk
        if "\n" in response:
            break
    
    return json.loads(response.strip())

def main():
    host = "localhost"
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    
    print(f"Connecting to Nexus MCP Server at {host}:{port}")
    
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.connect((host, port))
        print("Connected!")
        
        # Test 1: Initialize
        print("\n1. Testing initialize...")
        response = send_request(sock, "initialize")
        print(json.dumps(response, indent=2))
        
        # Test 2: List tools
        print("\n2. Testing tools/list...")
        response = send_request(sock, "tools/list", id=2)
        print(json.dumps(response, indent=2))
        
        # Test 3: List resources
        print("\n3. Testing resources/list...")
        response = send_request(sock, "resources/list", id=3)
        print(json.dumps(response, indent=2))
        
        # Test 4: Execute simple AI task
        print("\n4. Testing nexus/executeTask (simple)...")
        response = send_request(sock, "nexus/executeTask", {
            "task": "What is 2+2?",
            "provider": "litellm"
        }, id=4)
        print(json.dumps(response, indent=2))
        
        # Test 5: Execute complex AI task with Nemotron
        print("\n5. Testing nexus/executeTask (complex with Nemotron)...")
        response = send_request(sock, "nexus/executeTask", {
            "task": "Analyze the benefits of using TrikeShed's Join<A,B> over Kotlin's Pair",
            "provider": "nemotron"
        }, id=5)
        print(json.dumps(response, indent=2))
        
        # Test 6: Scan environment
        print("\n6. Testing nexus/scan...")
        response = send_request(sock, "nexus/scan", {
            "path": "."
        }, id=6)
        print(json.dumps(response, indent=2))
        
        # Test 7: Run tool
        print("\n7. Testing nexus/runTool...")
        response = send_request(sock, "nexus/runTool", {
            "tool": "git",
            "args": ["--version"]
        }, id=7)
        print(json.dumps(response, indent=2))
        
        # Test 8: Get telemetry
        print("\n8. Testing nexus/telemetry...")
        response = send_request(sock, "nexus/telemetry", {
            "command": "status"
        }, id=8)
        print(json.dumps(response, indent=2))
        
        # Test 9: Get server status
        print("\n9. Testing nexus/status...")
        response = send_request(sock, "nexus/status", id=9)
        print(json.dumps(response, indent=2))

if __name__ == "__main__":
    try:
        main()
    except ConnectionRefusedError:
        print("Error: Could not connect to MCP server. Is it running?")
        print("Start it with: ./nexus/start-mcp-server.sh")
        sys.exit(1)
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)