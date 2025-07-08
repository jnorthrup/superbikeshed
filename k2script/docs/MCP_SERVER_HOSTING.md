# MCP Server Hosting System

## Overview

The MCP (Model Context Protocol) Server Hosting System provides a complete infrastructure for deploying, managing, and scaling MCP servers. It includes service discovery, load balancing, health monitoring, and containerized hosting.

## Architecture

### Core Components

1. **MCPServer** - Core MCP protocol implementation
2. **MCPRegistry** - Service discovery and registration
3. **MCPGateway** - REST API gateway for MCP operations
4. **MCPHostingService** - Containerized hosting management
5. **MCPLoadBalancer** - Request distribution and health checks
6. **MCPCircuitBreaker** - Fault tolerance pattern
7. **MCPResponseAggregator** - Response aggregation from multiple servers
8. **MCPScalingManager** - Auto-scaling capabilities

### Features

- **Service Discovery**: Automatic registration and discovery of MCP servers
- **Load Balancing**: Round-robin distribution of requests
- **Health Monitoring**: Continuous health checks with response time tracking
- **Circuit Breaker**: Fault tolerance with automatic recovery
- **Response Aggregation**: Combine responses from multiple servers
- **Auto-scaling**: Dynamic scaling based on CPU/memory usage
- **Containerized Deployment**: Docker and Kubernetes support
- **Monitoring**: Prometheus metrics and Grafana dashboards

## Quick Start

### 1. Basic Usage

```bash
# Register an MCP server
k2script --mcp register my-server 1.0.0 tools resources

# Start the server
k2script --mcp start my-server

# Check health
k2script --mcp health

# List all servers
k2script --mcp list

# Route a request
k2script --mcp route tools/list filter=active
```

### 2. Docker Deployment

```bash
# Build and run with Docker Compose
cd k2script/docker
docker-compose up -d

# Check status
docker-compose ps

# View logs
docker-compose logs mcp-gateway
```

### 3. Kubernetes Deployment

```bash
# Deploy to Kubernetes
kubectl apply -f k2script/k8s/mcp-deployment.yaml

# Check deployment status
kubectl get pods -n mcp-system

# Access the gateway
kubectl port-forward svc/mcp-gateway-service 8080:80 -n mcp-system
```

## API Reference

### MCPServer

```kotlin
class MCPServer(
    val name: String,
    val version: String,
    val capabilities: Set<String>,
    private val port: Int = 8080
)
```

**Methods:**
- `getServerInfo()`: Get server information
- `registerHandler(method, handler)`: Register request handler
- `handleRequest(request)`: Handle MCP request
- `start()`: Start the server
- `stop()`: Stop the server
- `isRunning()`: Check if server is running

### MCPRegistry

```kotlin
class MCPRegistry
```

**Methods:**
- `register(server)`: Register an MCP server
- `unregister(serverName)`: Unregister a server
- `discover()`: Get all registered servers
- `findByName(name)`: Find server by name
- `findByCapability(capability)`: Find servers by capability

### MCPGateway

```kotlin
class MCPGateway
```

**Methods:**
- `registerServer(server)`: Register server with gateway
- `route(request)`: Route request through load balancer
- `getRegisteredServers()`: Get all registered servers

### MCPLoadBalancer

```kotlin
class MCPLoadBalancer
```

**Methods:**
- `addServer(server)`: Add server to load balancer
- `removeServer(server)`: Remove server from load balancer
- `selectServer(request)`: Select server for request (round-robin)
- `getHealthyServers()`: Get healthy servers

### MCPHealthChecker

```kotlin
class MCPHealthChecker
```

**Methods:**
- `checkHealth(server)`: Check server health and response time

### MCPHostingService

```kotlin
class MCPHostingService
```

**Methods:**
- `deploy(config)`: Deploy MCP server container
- `getContainer(containerId)`: Get container information
- `listContainers()`: List all containers
- `stopContainer(containerId)`: Stop container

### MCPCircuitBreaker

```kotlin
class MCPCircuitBreaker(
    private val failureThreshold: Int,
    private val timeout: Long
)
```

**Methods:**
- `execute(server, block)`: Execute with circuit breaker protection
- `isOpen()`: Check if circuit is open
- `isHalfOpen()`: Check if circuit is half-open
- `isClosed()`: Check if circuit is closed

## Configuration

### Environment Variables

```bash
# Server configuration
MCP_SERVER_PORT=8080
MCP_LOG_LEVEL=INFO
MCP_CACHE_DIR=/app/cache
MCP_DATA_DIR=/app/data

# Capabilities
MCP_CAPABILITIES=tools,resources,prompts

# Health check
MCP_HEALTH_CHECK_INTERVAL=30s
MCP_HEALTH_CHECK_TIMEOUT=10s

# Circuit breaker
MCP_CIRCUIT_BREAKER_FAILURE_THRESHOLD=3
MCP_CIRCUIT_BREAKER_TIMEOUT=5000ms
```

### Docker Configuration

```yaml
# docker-compose.yml
services:
  mcp-gateway:
    image: mcp-server:latest
    environment:
      - MCP_SERVER_TYPE=gateway
      - MCP_CAPABILITIES=gateway,loadbalancer
    ports:
      - "8080:8080"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 30s
      timeout: 10s
      retries: 3
```

### Kubernetes Configuration

```yaml
# mcp-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mcp-gateway
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: mcp-gateway
        image: mcp-server:latest
        env:
        - name: MCP_SERVER_TYPE
          value: "gateway"
        - name: MCP_CAPABILITIES
          value: "gateway,loadbalancer"
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
```

## Monitoring

### Prometheus Metrics

The MCP servers expose the following metrics:

- `mcp_requests_total`: Total number of requests
- `mcp_request_duration_seconds`: Request duration
- `mcp_server_health`: Server health status
- `mcp_circuit_breaker_state`: Circuit breaker state
- `mcp_load_balancer_requests`: Load balancer request distribution

### Grafana Dashboards

Pre-configured dashboards are available for:

- Server health and performance
- Request throughput and latency
- Circuit breaker status
- Load balancer distribution
- Resource utilization

## Examples

### 1. Custom MCP Server

```kotlin
#!/usr/bin/env k2script

@file:Import("k2script.mcp.*")

// Create custom MCP server
val customServer = MCPServer(
    name = "custom-server",
    version = "1.0.0",
    capabilities = setOf("custom-tools", "data-processing")
)

// Register custom handlers
customServer.registerHandler("custom-tools/process") { params ->
    val data = params["data"] as? String ?: ""
    val result = data.uppercase()
    MCPResponse(200, mapOf("result" to result))
}

// Register with registry
val registry = MCPRegistry()
registry.register(customServer)

println("Custom MCP server registered: ${customServer.name}")
```

### 2. Load Balanced Request

```kotlin
#!/usr/bin/env k2script

@file:Import("k2script.mcp.*")

// Create multiple servers
val server1 = MCPServer("server1", "1.0.0", setOf("tools"))
val server2 = MCPServer("server2", "1.0.0", setOf("tools"))

// Create load balancer
val loadBalancer = MCPLoadBalancer()
loadBalancer.addServer(server1)
loadBalancer.addServer(server2)

// Route requests
val request = MCPRequest("tools/list", emptyMap())
repeat(10) { index ->
    val server = loadBalancer.selectServer(request)
    println("Request $index: ${server.name}")
}
```

### 3. Circuit Breaker Pattern

```kotlin
#!/usr/bin/env k2script

@file:Import("k2script.mcp.*")

val server = MCPServer("test-server", "1.0.0", setOf("tools"))
val circuitBreaker = MCPCircuitBreaker(failureThreshold = 3, timeout = 5000L)

try {
    val result = circuitBreaker.execute(server) {
        // Simulate operation that might fail
        if (Math.random() < 0.5) {
            throw RuntimeException("Random failure")
        }
        "success"
    }
    println("Operation succeeded: $result")
} catch (e: Exception) {
    println("Operation failed: ${e.message}")
}
```

## CLI Commands

### MCP Management

```bash
# Server management
k2script --mcp register <name> <version> <capabilities...>
k2script --mcp start <server-name>
k2script --mcp stop <server-name>
k2script --mcp list

# Health and monitoring
k2script --mcp health
k2script --mcp route <method> <params...>

# Deployment
k2script --mcp deploy <config>
k2script --mcp scale <config>
```

### Examples

```bash
# Register a tools server
k2script --mcp register tools-server 1.0.0 tools filesystem

# Start the server
k2script --mcp start tools-server

# Check health
k2script --mcp health

# Route a request
k2script --mcp route tools/list filter=active

# Deploy container
k2script --mcp deploy docker-config.json
```

## Production Deployment

### 1. Docker Compose (Development)

```bash
cd k2script/docker
docker-compose up -d
```

### 2. Kubernetes (Production)

```bash
# Deploy to Kubernetes
kubectl apply -f k2script/k8s/mcp-deployment.yaml

# Check deployment
kubectl get pods -n mcp-system

# Access services
kubectl port-forward svc/mcp-gateway-service 8080:80 -n mcp-system
```

### 3. Monitoring Setup

```bash
# Access Prometheus
kubectl port-forward svc/prometheus-service 9090:9090 -n monitoring

# Access Grafana
kubectl port-forward svc/grafana-service 3000:3000 -n monitoring
```

## Troubleshooting

### Common Issues

1. **Server not starting**: Check port availability and permissions
2. **Health check failures**: Verify server endpoints and network connectivity
3. **Circuit breaker open**: Check server health and reduce failure threshold
4. **Load balancer issues**: Verify server registration and health status

### Debug Commands

```bash
# Check server logs
k2script --mcp list --verbose

# Test connectivity
k2script --mcp health --detailed

# Check configuration
k2script --mcp config --validate
```

## Performance Tuning

### Resource Limits

```yaml
resources:
  requests:
    memory: "256Mi"
    cpu: "250m"
  limits:
    memory: "512Mi"
    cpu: "500m"
```

### Scaling Configuration

```yaml
autoscaling:
  minReplicas: 2
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70
  targetMemoryUtilizationPercentage: 80
```

### Circuit Breaker Settings

```yaml
circuitBreaker:
  failureThreshold: 3
  timeout: 5000ms
  halfOpenRequests: 1
```

## Security

### Authentication

```yaml
security:
  authentication:
    type: "jwt"
    secret: "${JWT_SECRET}"
  authorization:
    enabled: true
    roles: ["admin", "user"]
```

### Network Security

```yaml
network:
  tls:
    enabled: true
    certFile: "/etc/ssl/certs/mcp.crt"
    keyFile: "/etc/ssl/private/mcp.key"
  firewall:
    allowedIPs: ["10.0.0.0/8", "172.16.0.0/12"]
```

## Contributing

1. Follow TDD approach with comprehensive tests
2. Ensure backward compatibility
3. Add monitoring and logging
4. Update documentation
5. Test with different deployment scenarios

## License

This MCP Server Hosting System is part of the k2script project and follows the same licensing terms. 