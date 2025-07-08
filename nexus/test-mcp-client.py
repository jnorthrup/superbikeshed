#\!/usr/bin/env python3
"""
Nexus MCP Test Client

A simple Python client to test the Nexus MCP server functionality.
Tests all major endpoints and demonstrates usage patterns.
"""

import json
import socket
import sys
import time
from typing import Dict, Any, Optional
import argparse

class MCPClient:
    """Simple MCP client for testing"""
    
    def __init__(self, host: str = "localhost", port: int = 8765):
        self.host = host
        self.port = port
        self.socket = None
        self.request_id = 0
        
    def connect(self):
        """Connect to MCP server"""
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.connect((self.host, self.port))
            print(f"Connected to {self.host}:{self.port}")
            return True
        except Exception as e:
            print(f"Connection failed: {e}")
            return False
    
    def disconnect(self):
        """Disconnect from server"""
        if self.socket:
            self.socket.close()
            self.socket = None
            print("Disconnected")
    
    def send_request(self, method: str, params: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """Send a JSON-RPC request and get response"""
        self.request_id += 1
        
        request = {
            "jsonrpc": "2.0",
            "id": self.request_id,
            "method": method
        }
        
        if params:
            request["params"] = params
        
        # Send request
        request_str = json.dumps(request) + "\n"
        self.socket.send(request_str.encode())
        
        # Receive response
        response_data = b""
        while True:
            chunk = self.socket.recv(4096)
            response_data += chunk
            if b"\n" in response_data:
                break
        
        response_str = response_data.decode().strip()
        return json.loads(response_str)
    
    def initialize(self):
        """Initialize MCP session"""
        print("\n=== Initializing MCP Session ===")
        response = self.send_request("initialize", {
            "protocolVersion": "2024-11-05",
            "capabilities": {}
        })
        
        if "result" in response:
            print(f"Protocol version: {response['result']['protocolVersion']}")
            print(f"Server: {response['result']['serverInfo']['name']} v{response['result']['serverInfo']['version']}")
            print("Capabilities:", json.dumps(response['result']['capabilities'], indent=2))
        else:
            print("Error:", response.get("error"))
        
        return response
    
    def list_tools(self):
        """List available tools"""
        print("\n=== Available Tools ===")
        response = self.send_request("tools/list")
        
        if "result" in response:
            tools = response['result']['tools']
            for tool in tools:
                print(f"\n- {tool['name']}")
                print(f"  Description: {tool['description']}")
                if 'inputSchema' in tool:
                    print(f"  Parameters: {json.dumps(tool['inputSchema']['properties'], indent=4)}")
        else:
            print("Error:", response.get("error"))
        
        return response
    
    def list_resources(self):
        """List available resources"""
        print("\n=== Available Resources ===")
        response = self.send_request("resources/list")
        
        if "result" in response:
            resources = response['result']['resources']
            for resource in resources:
                print(f"\n- {resource['uri']}")
                print(f"  Name: {resource['name']}")
                print(f"  Description: {resource['description']}")
                print(f"  Type: {resource['mimeType']}")
        else:
            print("Error:", response.get("error"))
        
        return response
    
    def execute_task(self, task: str, provider: str = "litellm", context: Optional[str] = None):
        """Execute an AI task"""
        print(f"\n=== Executing Task ===")
        print(f"Task: {task}")
        print(f"Provider: {provider}")
        
        params = {
            "task": task,
            "provider": provider
        }
        if context:
            params["context"] = context
        
        start_time = time.time()
        response = self.send_request("nexus/executeTask", params)
        elapsed = time.time() - start_time
        
        if "result" in response:
            print(f"\nResult (in {elapsed:.2f}s):")
            print(response['result']['result'])
        else:
            print("Error:", response.get("error"))
        
        return response
    
    def scan_environment(self, path: str = "."):
        """Scan environment"""
        print(f"\n=== Scanning Environment ===")
        print(f"Path: {path}")
        
        response = self.send_request("nexus/scan", {"path": path})
        
        if "result" in response:
            result = response['result']
            print("\nScan Results:")
            print(f"- Languages: {', '.join(result['languages'])}")
            print(f"- Build Tools: {', '.join(result['buildTools'])}")
            print(f"- Project Type: {result['projectType']}")
            print(f"- Frameworks: {', '.join(result['frameworks'])}")
            print(f"- Total Files: {result['structure']['totalFiles']}")
            print(f"- Total Size: {result['structure']['totalSize']} bytes")
        else:
            print("Error:", response.get("error"))
        
        return response
    
    def run_tool(self, tool: str, args: list):
        """Run a development tool"""
        print(f"\n=== Running Tool ===")
        print(f"Tool: {tool}")
        print(f"Arguments: {args}")
        
        response = self.send_request("nexus/runTool", {
            "tool": tool,
            "args": args
        })
        
        if "result" in response:
            result = response['result']
            print(f"\nExit Code: {result['exitCode']}")
            if result.get('output'):
                print("Output:")
                print(result['output'])
            if result.get('error'):
                print("Error:")
                print(result['error'])
        else:
            print("Error:", response.get("error"))
        
        return response
    
    def get_telemetry(self, command: str = "status"):
        """Get telemetry information"""
        print(f"\n=== Telemetry: {command} ===")
        
        response = self.send_request("nexus/telemetry", {"command": command})
        
        if "result" in response:
            print(json.dumps(response['result'], indent=2))
        else:
            print("Error:", response.get("error"))
        
        return response
    
    def get_status(self):
        """Get server status"""
        print("\n=== Server Status ===")
        
        response = self.send_request("nexus/status")
        
        if "result" in response:
            status = response['result']
            print(f"Server: {status['server']} v{status['version']}")
            print(f"Status: {status['status']}")
            print(f"Active Connections: {status['connections']}")
            print(f"Capabilities: {json.dumps(status['capabilities'], indent=2)}")
        else:
            print("Error:", response.get("error"))
        
        return response

def run_interactive_tests(client: MCPClient):
    """Run interactive test menu"""
    while True:
        print("\n=== Nexus MCP Test Menu ===")
        print("1. Initialize session")
        print("2. List tools")
        print("3. List resources")
        print("4. Execute AI task")
        print("5. Scan environment")
        print("6. Run tool")
        print("7. Get telemetry")
        print("8. Get server status")
        print("9. Run all tests")
        print("0. Exit")
        
        choice = input("\nSelect option: ").strip()
        
        if choice == "1":
            client.initialize()
        elif choice == "2":
            client.list_tools()
        elif choice == "3":
            client.list_resources()
        elif choice == "4":
            task = input("Enter task description: ").strip()
            provider = input("Enter AI provider (litellm/nemotron/nemo) [litellm]: ").strip() or "litellm"
            context = input("Enter context (optional): ").strip() or None
            client.execute_task(task, provider, context)
        elif choice == "5":
            path = input("Enter path to scan [.]: ").strip() or "."
            client.scan_environment(path)
        elif choice == "6":
            tool = input("Enter tool name: ").strip()
            args_str = input("Enter arguments (space-separated): ").strip()
            args = args_str.split() if args_str else []
            client.run_tool(tool, args)
        elif choice == "7":
            command = input("Enter telemetry command (status/metrics/report) [status]: ").strip() or "status"
            client.get_telemetry(command)
        elif choice == "8":
            client.get_status()
        elif choice == "9":
            run_all_tests(client)
        elif choice == "0":
            break
        else:
            print("Invalid option")

def run_all_tests(client: MCPClient):
    """Run all tests in sequence"""
    print("\n=== Running All Tests ===")
    
    # Initialize
    client.initialize()
    time.sleep(0.5)
    
    # Get status
    client.get_status()
    time.sleep(0.5)
    
    # List tools and resources
    client.list_tools()
    time.sleep(0.5)
    client.list_resources()
    time.sleep(0.5)
    
    # Test AI tasks
    print("\n--- Testing AI Providers ---")
    
    # Test LiteLLM (quick)
    client.execute_task("What is 2+2?", "litellm")
    time.sleep(1)
    
    # Test Nemotron non-thinking
    client.execute_task("List 3 programming languages", "nemotron")
    time.sleep(1)
    
    # Test Nemotron thinking mode
    client.execute_task("Analyze the pros and cons of microservices architecture", "nemotron")
    time.sleep(1)
    
    # Scan current directory
    client.scan_environment(".")
    time.sleep(0.5)
    
    # Try to run a simple tool
    client.run_tool("echo", ["Hello", "from", "Nexus"])
    time.sleep(0.5)
    
    # Get telemetry
    client.get_telemetry("status")
    time.sleep(0.5)
    
    print("\n=== All Tests Completed ===")

def main():
    parser = argparse.ArgumentParser(description="Nexus MCP Test Client")
    parser.add_argument("--host", default="localhost", help="Server host")
    parser.add_argument("--port", type=int, default=8765, help="Server port")
    parser.add_argument("--all", action="store_true", help="Run all tests automatically")
    
    args = parser.parse_args()
    
    client = MCPClient(args.host, args.port)
    
    if not client.connect():
        sys.exit(1)
    
    try:
        if args.all:
            run_all_tests(client)
        else:
            run_interactive_tests(client)
    except KeyboardInterrupt:
        print("\nInterrupted by user")
    except Exception as e:
        print(f"\nError: {e}")
    finally:
        client.disconnect()

if __name__ == "__main__":
    main()
EOF < /dev/null
