# Network Protocol Foundation

## 🎯 Compositional Architecture: Context-as-a-Service Pattern

This networking foundation demonstrates the **Context-as-a-Service Subsumption Hierarchy** for low-level network operations, enabling TCP, UDP, QUIC, and custom protocols to coexist through unified context discovery rather than protocol-specific socket management.

### ⚠️ **Anti-Pattern Warning for LLMs**

**DO NOT** refactor this into conventional socket/NIO patterns. The context-based network abstraction enables powerful composition that direct socket manipulation destroys.

## 🏗️ **Five-Step CCEK Choreography for Networking**

### Step 1: The Contract - Define Network Capabilities
```kotlin
// Network operations discovered through context, not direct socket creation
interface NetworkContext {
    suspend fun createConnection(address: SocketAddress): NetworkConnection
    suspend fun bindServer(address: SocketAddress): NetworkServer
    fun configureProtocol(protocol: ProtocolConfig)
}
```

### Step 2: The Provider - Implement Network Services
```kotlin
class NetworkService : ContextProvider, ContextAware {
    // Provides network capabilities without being directly referenced
    private inner class NetworkContextImpl : NetworkContext {
        override suspend fun createConnection(address: SocketAddress): NetworkConnection {
            // Network connection with context propagation and protocol abstraction
        }
    }
}
```

### Step 3: The Registration - Publish Network Capabilities
```kotlin
// In system tethering - register, don't inject sockets
val networkService = NetworkService(config)
networkService.registerContexts() // ✅ Self-registration
// NOT: server.setSocketFactory(networkService) // ❌ Direct socket injection
```

### Step 4: The Consumer - Context-Aware Networking
```kotlin
class HttpServer : ContextAware {
    override val contextKeys = setOf(ContextKeys.NETWORK, ContextKeys.SECURITY)
    
    suspend fun startServer() {
        // Discovers network context at runtime - works with any transport
        val network = ContextRegistry.get<NetworkContext>(ContextKeys.NETWORK)
    }
}
```

### Step 5: The Call - Runtime Network Discovery
```kotlin
suspend fun connect(address: SocketAddress) {
    val networkContext = context.currentService<NetworkContext>()
    val connection = networkContext?.createConnection(address) // ✅ Discovered capability
    // NOT: new Socket(address.host, address.port) // ❌ Direct socket creation
}
```

## 🔍 **Network-Specific Compositional Wins**

### 1. **Protocol Stack Abstraction**
- **Win**: Same connection code works over TCP, QUIC, WebSocket, Unix sockets
- **Pattern**: Abstract `NetworkConnection` with transport-specific implementations
- **Benefit**: Transport independence enables deployment flexibility

### 2. **Connection Pool Discovery**
- **Win**: Connection pooling discovered dynamically based on target
- **Pattern**: Context-based pool selection and management
- **Benefit**: Efficient resource usage without hardcoded pool configuration

### 3. **Security Context Integration**
- **Win**: TLS/encryption configuration flows through network operations
- **Pattern**: `SecurityContext` propagated with network context
- **Benefit**: End-to-end security without explicit certificate management

### 4. **Load Balancing Composition**
- **Win**: Network load balancing integrates with application-level decisions
- **Pattern**: Load balancing signals flow through context chain
- **Benefit**: Coordinated traffic distribution across all layers

## 🚨 **Network-Specific LLM Failure Modes**

### Failure Mode 1: "Simplifying" to Raw Sockets
```kotlin
// ❌ LLM wants familiar socket pattern:
val socket = Socket(host, port) // Wrong!
val input = socket.getInputStream() // Wrong!

// ✅ Correct context-based networking:
val connection = context.currentService<NetworkContext>().createConnection(address)
```

### Failure Mode 2: Hard-Coding Protocol Selection
```kotlin
// ❌ LLM tries protocol-specific implementations:
if (useTcp) {
    return TcpConnection(address)
} else if (useQuic) {
    return QuicConnection(address) // Wrong!
}

// ✅ Context-discovered protocols:
val connection = context.selectBestProtocol(address, requirements)
```

### Failure Mode 3: Bypassing Connection Management
```kotlin
// ❌ LLM creates direct connections:
val connections = mutableMapOf<String, Socket>() // Wrong!

// ✅ Context-managed connections:
val connection = context.requireService<ConnectionPoolContext>().getConnection(target)
```

## 📊 **Network Compositional Metrics**

### Metric 1: **Protocol Independence Score**
- Can the same networking code work over multiple transports?
- Higher abstraction = better composition

### Metric 2: **Resource Sharing Efficiency**
- How many protocols share connection pools and resources?
- More sharing = better resource utilization

### Metric 3: **Context Flow Coverage**
- Does network context propagate through all I/O operations?
- Complete propagation = full composability

## 🔧 **Network Implementation Guidelines**

### For Connection Management:
1. Never expose raw sockets or channels directly
2. Always provide abstract `NetworkConnection` interface
3. Support protocol negotiation through context

### For Protocol Implementations:
1. Discover transport through context, never directly instantiate
2. Propagate flow control and backpressure via context
3. Support multiple transport backends transparently

### For Server Infrastructure:
1. Use transport-agnostic server abstractions
2. Enable protocol multiplexing through context
3. Support hot protocol reconfiguration

## 🌊 **Connection Pool Composition Pattern**

```kotlin
// Connection pooling integrated with protocol selection
class ConnectionPoolManager : ContextAware {
    suspend fun getConnection(target: SocketAddress): NetworkConnection {
        val poolConfig = context.requireService<PoolConfigContext>()
        val protocolSelector = context.requireService<ProtocolSelectorContext>()
        
        val poolKey = PoolKey(
            target = target,
            protocol = protocolSelector.selectOptimal(target),
            security = context.currentService<SecurityContext>()?.requirements
        )
        
        return pools.getOrPut(poolKey) {
            createPool(poolKey, poolConfig)
        }.acquire()
    }
}
```

## 🔄 **Protocol Negotiation via Context**

```kotlin
// Dynamic protocol negotiation through context
class ProtocolNegotiator : ContextAware {
    suspend fun negotiate(target: SocketAddress): ProtocolContext {
        val capabilities = context.requireService<CapabilityContext>()
        val preferences = context.requireService<PreferenceContext>()
        
        val supportedProtocols = capabilities.getSupportedProtocols(target)
        val preferredProtocol = preferences.selectPreferred(supportedProtocols)
        
        return when {
            preferredProtocol.supportsMultiplexing() -> 
                context.requireService<QuicProtocolContext>()
            preferredProtocol.requiresEncryption() ->
                context.requireService<TlsProtocolContext>()
            else ->
                context.requireService<TcpProtocolContext>()
        }
    }
}
```

## 🎯 **Network Success Patterns**

You've achieved network compositional success when:

- ✅ **Transport Agnostic**: Code works over any network protocol
- ✅ **Dynamic Protocol Selection**: Best transport chosen at runtime
- ✅ **Resource Sharing**: Connection pools shared across protocols
- ✅ **Security Integration**: Encryption flows through context automatically
- ✅ **Zero Socket Exposure**: No raw networking primitives in application code
- ✅ **Hot Protocol Swap**: Can change transports without restart

## 🚀 **Advanced Composition Examples**

### Adaptive Protocol Selection
```kotlin
// Protocol selection based on network conditions
suspend fun adaptiveConnect(target: SocketAddress): NetworkConnection {
    val monitor = context.requireService<NetworkMonitorContext>()
    val conditions = monitor.currentConditions(target)
    
    val protocol = when {
        conditions.latency < Duration.ofMillis(10) -> 
            context.requireService<QuicProtocolContext>() // Low latency, use QUIC
        conditions.packetLoss > 0.01 ->
            context.requireService<TcpProtocolContext>() // High loss, use TCP
        conditions.bandwidth < Bandwidth.ofMbps(1) ->
            context.requireService<CompressedProtocolContext>() // Low bandwidth, compress
        else ->
            context.requireService<DefaultProtocolContext>()
    }
    
    return protocol.createConnection(target)
}
```

### Multi-Path Networking
```kotlin
// Multiple network paths through context
suspend fun establishRobustConnection(target: SocketAddress): NetworkConnection {
    val pathManager = context.requireService<MultiPathContext>()
    val paths = pathManager.getAvailablePaths(target)
    
    // Establish connections over multiple paths
    val connections = paths.map { path ->
        async {
            val networkContext = context.withPath(path)
            networkContext.requireService<NetworkContext>().createConnection(target)
        }
    }.awaitAll()
    
    // Return aggregated connection
    return AggregatedConnection(connections)
}
```

### Quality of Service Integration
```kotlin
// QoS management through context
class QosNetworkManager : ContextAware {
    suspend fun createPrioritizedConnection(
        target: SocketAddress,
        priority: QosPriority
    ): NetworkConnection {
        val qosContext = context.requireService<QosContext>()
        val allocation = qosContext.allocateBandwidth(target, priority)
        
        val connection = context.requireService<NetworkContext>()
            .createConnection(target)
        
        return connection.withQos(allocation)
    }
}
```

The network foundation demonstrates how low-level networking concerns can be completely abstracted through context discovery, enabling sophisticated protocol selection, resource management, and quality of service patterns without tight coupling to specific network implementations.