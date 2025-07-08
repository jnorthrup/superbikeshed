# Unified RPC Architecture: TrikeShed RequestFactory + MCP Protocol

This document describes the unified RPC architecture that allows both TrikeShed RequestFactory pattern and MCP (Model Context Protocol) to work together seamlessly.

## Overview

The Nexus IntelliJ Plugin provides a unified RPC system where:
- **TrikeShed Nodes** can be accessed via both RequestFactory RPC and MCP protocol
- **MCP Servers** can be accessed via both MCP protocol and RequestFactory RPC
- All services are registered as nodes in a unified registry

## Architecture Components

### 1. TrikeShed RequestFactory

Based on GWT RequestFactory patterns:
- **Service Interfaces**: Type-safe RPC interfaces
- **Request Contexts**: Batch multiple operations
- **Receivers**: Async callbacks for results
- **Entity Proxies**: Data transfer objects

### 2. MCP Protocol Support

JSON-RPC 2.0 protocol with:
- **Method Routing**: `nodeId.method` format
- **WebSocket Support**: Real-time bidirectional communication
- **Batch Requests**: Multiple operations in single call
- **Error Handling**: Standard JSON-RPC error codes

### 3. TrikeshedMcpBridge

The bridge provides bidirectional compatibility:
- **Node → MCP**: Expose TrikeShed nodes via MCP protocol
- **MCP → Node**: Expose MCP servers as TrikeShed nodes

## Available Nodes

### PSI Node (`psi-node`)
Capabilities:
- `find_symbols` - Search for symbols by pattern
- `rename_symbols` - Rename symbols across project
- `get_ast` - Get AST for a file
- `modify_ast` - Modify AST with edits

### Analysis Node (`analysis-node`)
Capabilities:
- `analyze_code` - Run code inspections
- `find_issues` - Find compilation errors
- `suggest_fixes` - Get quick fix suggestions

### Refactoring Node (`refactoring-node`)
Capabilities:
- `rename` - Rename elements
- `move` - Move elements to different packages
- `extract` - Extract method/variable
- `inline` - Inline method/variable

## API Usage Examples

### Using RequestFactory RPC

```kotlin
// Get the request factory
val factory = bridge.getRequestFactory()

// Create a request context
val context = factory.createRequestContext()

// Add operations to the batch
context.invoke(
    serviceId = "psi-node",
    method = "find_symbols",
    params = mapOf("pattern" to "*Series"),
    receiver = object : Receiver<List<SymbolInfo>> {
        override fun onSuccess(response: List<SymbolInfo>) {
            println("Found ${response.size} symbols")
        }
        
        override fun onFailure(error: ServerFailure) {
            println("Error: ${error.message}")
        }
    }
)

// Fire the batch
context.fire()
```

### Using MCP Protocol

#### REST Endpoint
```bash
curl -X POST http://localhost:63343/api/nexus/mcp/rpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "psi-node.find_symbols",
    "params": {
      "pattern": "*Series"
    }
  }'
```

#### WebSocket Connection
```javascript
const ws = new WebSocket('ws://localhost:63343/api/nexus/mcp/ws');

ws.onopen = () => {
  // Send request
  ws.send(JSON.stringify({
    jsonrpc: "2.0",
    id: "1",
    method: "psi-node.find_symbols",
    params: {
      pattern: "*Series"
    }
  }));
};

ws.onmessage = (event) => {
  const response = JSON.parse(event.data);
  console.log('Response:', response);
};
```

### Using Both Together

```kotlin
class HybridClient(
    private val bridge: TrikeshedMcpBridge,
    private val mcpEndpoint: String
) {
    // Use RequestFactory for type-safe internal calls
    suspend fun findSymbolsInternal(pattern: String): List<SymbolInfo> {
        val context = bridge.getRequestFactory().createRequestContext()
        var result: List<SymbolInfo>? = null
        
        context.invoke(
            serviceId = "psi-node",
            method = "find_symbols",
            params = mapOf("pattern" to pattern),
            receiver = object : Receiver<List<SymbolInfo>> {
                override fun onSuccess(response: List<SymbolInfo>) {
                    result = response
                }
                override fun onFailure(error: ServerFailure) {
                    throw RuntimeException(error.message)
                }
            }
        )
        
        context.fire()
        return result ?: emptyList()
    }
    
    // Use MCP for external/cross-language calls
    suspend fun findSymbolsExternal(pattern: String): JsonElement {
        val request = McpRequest(
            id = JsonPrimitive(UUID.randomUUID().toString()),
            method = "psi-node.find_symbols",
            params = JsonObject(mapOf(
                "pattern" to JsonPrimitive(pattern)
            ))
        )
        
        val response = httpClient.post(mcpEndpoint) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<McpResponse>()
        
        return response.result ?: JsonNull
    }
}
```

## Protocol Comparison

| Feature | RequestFactory RPC | MCP Protocol |
|---------|-------------------|--------------|
| Type Safety | ✓ Compile-time | ✗ Runtime |
| Batching | ✓ Request Context | ✓ Array of requests |
| WebSocket | ✗ HTTP only | ✓ Native support |
| Cross-Language | ✗ JVM only | ✓ Any language |
| Performance | Fast (internal) | Good (JSON overhead) |

## Best Practices

1. **Internal Communication**: Use RequestFactory RPC for type-safe internal calls
2. **External APIs**: Use MCP protocol for cross-language compatibility
3. **Batch Operations**: Group related operations in single request context
4. **Error Handling**: Always implement proper error handling for both protocols
5. **Node Registration**: Register nodes once during initialization

## Advanced Features

### Custom Node Implementation

```kotlin
class CustomNode : TrikeshedNode(
    nodeId = "custom-node",
    capabilities = setOf("custom_operation")
) {
    override suspend fun handleRequest(
        method: String, 
        params: Map<String, Any?>
    ): Any? {
        return when (method) {
            "custom_operation" -> {
                // Your implementation
                mapOf("result" to "success")
            }
            else -> throw IllegalArgumentException("Unknown method")
        }
    }
}

// Register for both protocols
val customNode = CustomNode()
factory.registerNode(customNode.nodeId, customNode)
bridge.exposeNodeAsMcp(customNode)
```

### Protocol Conversion

The bridge automatically handles conversion between:
- RequestFactory typed parameters ↔ MCP JSON parameters
- RequestFactory receivers ↔ MCP response callbacks
- RequestFactory exceptions ↔ MCP error responses

## Monitoring and Debugging

### List Available Nodes
```bash
curl http://localhost:63343/api/nexus/mcp/nodes
```

### Health Check
```bash
curl http://localhost:63343/api/nexus/api/health
```

### WebSocket Debug
```javascript
// Connect with debug logging
const ws = new WebSocket('ws://localhost:63343/api/nexus/mcp/ws');
ws.onopen = () => console.log('Connected');
ws.onclose = () => console.log('Disconnected');
ws.onerror = (error) => console.error('Error:', error);
ws.onmessage = (event) => console.log('Message:', event.data);
```

## Future Enhancements

1. **Service Discovery**: Automatic node discovery across network
2. **Load Balancing**: Distribute requests across multiple nodes
3. **Circuit Breaker**: Fault tolerance for failing nodes
4. **Metrics**: Performance monitoring and analytics
5. **Authentication**: Secure access to sensitive operations