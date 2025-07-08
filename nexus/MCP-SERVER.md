# Nexus MCP Server

The Nexus MCP (Model Context Protocol) server provides a standardized interface for AI assistants to interact with the Nexus development agent capabilities.

## Features

- **AI Task Execution**: Execute tasks using multiple AI providers (LiteLLM/OpenAI, Nemotron)
- **Environment Scanning**: Analyze project structure, languages, and tools
- **Tool Orchestration**: Execute development tools (gradle, git, etc.)
- **Telemetry Collection**: Monitor IDE activity across IntelliJ, VS Code, and Eclipse
- **Real-time Updates**: Subscribe to resource changes

## Starting the Server

### Method 1: Using the startup script
```bash
./nexus/start-mcp-server.sh [port]
# Default port is 8765
```

### Method 2: Using nexus directly
```bash
# Build first
./gradlew :nexus:jvmJar

# Run
java -cp nexus/build/libs/nexus-jvm.jar nexus.MainKt mcp 8765
```

### Method 3: Using gradlew run
```bash
./gradlew :nexus:run --args="mcp 8765"
```

## Available Tools

### nexus/executeTask
Execute an AI-powered task with optional thinking mode.

**Parameters:**
- `task` (required): Task description
- `provider` (optional): AI provider - "litellm", "nemotron", "nemo" (default: "litellm")
- `context` (optional): Additional context for the task

**Nemotron Thinking Mode**: Automatically activates for complex tasks containing keywords like "analyze", "design", "architect", "refactor".

### nexus/scan
Scan environment for languages, tools, and project structure.

**Parameters:**
- `path` (optional): Path to scan (default: current directory)

### nexus/runTool
Execute a development tool.

**Parameters:**
- `tool` (required): Tool name (e.g., gradle, maven, git)
- `args` (optional): Array of arguments to pass to the tool

### nexus/telemetry
Get telemetry metrics and reports.

**Parameters:**
- `command` (optional): "status", "metrics", or "report"

### nexus/status
Get server status and capabilities.

## Testing the Server

Use the provided test client:
```bash
python3 nexus/test-mcp-client.py [port]
```

Or use any JSON-RPC client:
```bash
# Using netcat
echo '{"jsonrpc":"2.0","id":1,"method":"initialize"}' | nc localhost 8765

# Using curl (if HTTP mode is enabled)
curl -X POST http://localhost:8765 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

## Integration with AI Assistants

### Claude Desktop
Add to your Claude configuration:
```json
{
  "servers": {
    "nexus": {
      "command": "/path/to/nexus/start-mcp-server.sh",
      "args": ["8765"]
    }
  }
}
```

### Custom Integration
Connect via TCP socket to port 8765 and communicate using JSON-RPC 2.0 protocol.

## Architecture

The MCP server integrates with:
- **AI Providers**: LiteLLM (OpenAI/Anthropic) and Nemotron (NVIDIA)
- **Environment Scanner**: Detects languages, frameworks, and project structure
- **Tool Orchestrator**: Discovers and executes development tools
- **Telemetry System**: Collects IDE activity metrics
- **LSP Server**: Code intelligence features (separate service on port 7777)

## Troubleshooting

1. **Port already in use**: Change the port number or kill the existing process
2. **Build failures**: Ensure you have JDK 11+ and run `./gradlew clean build`
3. **AI provider errors**: Check your API keys (OPENAI_API_KEY, NVIDIA_API_KEY)
4. **Connection refused**: Ensure the server is running and firewall allows the port