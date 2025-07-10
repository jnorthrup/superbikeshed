# SOCKS Protocol Implementation

## 🎯 Compositional Architecture: Context-as-a-Service Pattern

This SOCKS implementation demonstrates the **Context-as-a-Service Subsumption Hierarchy** for proxy protocols, enabling SOCKS4, SOCKS5, and custom proxy protocols to coexist through unified context discovery rather than protocol-specific connection handling.

### ⚠️ **Anti-Pattern Warning for LLMs**

**DO NOT** refactor this into conventional proxy/tunnel patterns. The context-based connection routing enables powerful composition that direct socket forwarding destroys.

## 🏗️ **Five-Step CCEK Choreography for SOCKS**

### Step 1: The Contract - Define Proxy Capabilities
```kotlin
// SOCKS operations discovered through context, not direct socket creation
interface ProxyContext {
    suspend fun establishTunnel(target: SocketAddress): ProxyTunnel
    suspend fun authenticateClient(credentials: ProxyCredentials): AuthResult
    fun configureBandwidthLimits(limits: BandwidthConfig)
}
```

### Step 2: The Provider - Implement Proxy Services
```kotlin
class SocksProxyService : ContextProvider, ContextAware {
    // Provides SOCKS capabilities without being directly referenced
    private inner class ProxyContextImpl : ProxyContext {
        override suspend fun establishTunnel(target: SocketAddress): ProxyTunnel {
            // SOCKS tunnel establishment with context propagation
        }
    }
}
```

### Step 3: The Registration - Publish Proxy Capabilities
```kotlin
// In system tethering - register, don't inject connections
val socksService = SocksProxyService(config)
socksService.registerContexts() // ✅ Self-registration
// NOT: proxy.addUpstream(socksService) // ❌ Direct proxy chaining
```

### Step 4: The Consumer - Context-Aware Tunneling
```kotlin
class HttpProxyHandler : ContextAware {
    override val contextKeys = setOf(ContextKeys.PROXY, ContextKeys.AUTH)
    
    suspend fun handleConnect() {
        // Discovers proxy context at runtime - works with any SOCKS version
        val proxy = ContextRegistry.get<ProxyContext>(ContextKeys.PROXY)
    }
}
```

### Step 5: The Call - Runtime Proxy Discovery
```kotlin
suspend fun connectToTarget(address: SocketAddress) {
    val proxyContext = context.currentService<ProxyContext>()
    val tunnel = proxyContext?.establishTunnel(address) // ✅ Discovered capability
    // NOT: this.socksClient.connect(address) // ❌ Direct SOCKS call
}
```

## 🔍 **SOCKS-Specific Compositional Wins**

### 1. **Proxy Protocol Abstraction**
- **Win**: Same application code works with SOCKS4, SOCKS5, HTTP CONNECT
- **Pattern**: Abstract `ProxyContext` with protocol-specific implementations
- **Benefit**: Proxy protocol evolution doesn't break applications

### 2. **Proxy Chain Discovery**
- **Win**: Multi-hop proxy chains composed dynamically through context
- **Pattern**: Context-based proxy chain construction
- **Benefit**: Flexible routing without hardcoded proxy sequences

### 3. **Authentication Context Propagation**
- **Win**: Proxy credentials flow through protocol stack via context
- **Pattern**: `AuthContext` propagated with proxy context
- **Benefit**: Seamless authentication without credential passing

### 4. **Bandwidth Management Composition**
- **Win**: Traffic shaping integrates with application-level throttling
- **Pattern**: Bandwidth signals flow through context chain
- **Benefit**: Coordinated resource management across layers

## 🚨 **SOCKS-Specific LLM Failure Modes**

### Failure Mode 1: "Simplifying" to Direct Socket Forwarding
```kotlin
// ❌ LLM wants familiar socket forwarding:
class ProxyServer {
    void handleConnection(Socket client, Socket target) // Wrong!
}

// ✅ Correct context-based tunneling:
suspend fun handleConnection() {
    val tunnel = context.currentService<ProxyContext>().establishTunnel(target)
}
```

### Failure Mode 2: Hard-Coding Proxy Chains
```kotlin
// ❌ LLM creates static proxy chains:
val chain = SocksProxy(HttpProxy(DirectConnection())) // Wrong!

// ✅ Context-discovered chaining:
val nextHop = context.requireService<ProxyChainContext>().nextProxy()
```

### Failure Mode 3: Bypassing Authentication Flow
```kotlin
// ❌ LLM handles auth directly:
if (request.username == "admin" && request.password == "secret") // Wrong!

// ✅ Context-delegated authentication:
val authResult = context.requireService<AuthContext>().authenticate(credentials)
```

## 📊 **SOCKS Compositional Metrics**

### Metric 1: **Protocol Independence Score**
- Can the same tunneling code work with SOCKS4 and SOCKS5?
- Higher abstraction = better composition

### Metric 2: **Chain Flexibility**
- How dynamically can proxy chains be reconfigured?
- More dynamic = better composition

### Metric 3: **Context Flow Coverage**
- Does proxy context propagate through all tunnel operations?
- Complete propagation = full composability

## 🔧 **SOCKS Implementation Guidelines**

### For Proxy Services:
1. Never expose raw socket forwarding directly
2. Always provide abstract `ProxyContext` interface
3. Support dynamic proxy chain construction

### For Tunnel Management:
1. Discover upstream proxies through context
2. Propagate bandwidth and auth constraints via context
3. Support protocol negotiation transparently

### For Application Integration:
1. Use proxy-agnostic connection abstractions
2. Let context handle proxy selection and chaining
3. Design for proxy protocol evolution

## 🌊 **Proxy Chain Composition Pattern**

```kotlin
// Dynamic proxy chain construction through context
class ProxyChainBuilder : ContextAware {
    suspend fun buildChain(target: SocketAddress): ProxyChain {
        val policies = context.requireService<RoutingPolicyContext>()
        val auth = context.requireService<AuthContext>()
        
        return when (policies.getRoute(target)) {
            Route.DIRECT -> DirectConnection(target)
            Route.SOCKS -> {
                val socks = context.requireService<SocksProxyContext>()
                socks.createProxy(target, auth.credentials)
            }
            Route.CHAINED -> {
                val chain = policies.getProxyChain(target)
                buildMultiHopChain(chain, auth)
            }
        }
    }
}
```

## 🔄 **Authentication Context Flow**

```kotlin
// Auth context flows through proxy negotiation
class SocksAuthHandler : ContextAware {
    suspend fun negotiateAuth(clientRequest: SocksAuthRequest): SocksAuthResponse {
        val authContext = context.requireService<AuthContext>()
        
        return when (clientRequest.method) {
            AuthMethod.USERNAME_PASSWORD -> {
                val result = authContext.authenticate(
                    username = clientRequest.username,
                    password = clientRequest.password,
                    source = clientRequest.clientAddress
                )
                
                if (result.success) {
                    // Add auth info to context for downstream use
                    context.add(AuthenticatedUserContext(result.user))
                    SocksAuthResponse.success()
                } else {
                    SocksAuthResponse.failure()
                }
            }
        }
    }
}
```

## 🎯 **SOCKS Success Patterns**

You've achieved SOCKS compositional success when:

- ✅ **Protocol Agnostic**: Code works with any SOCKS version
- ✅ **Dynamic Chaining**: Proxy chains configured at runtime
- ✅ **Auth Integration**: Authentication flows through context
- ✅ **Bandwidth Coordination**: Traffic shaping works across layers
- ✅ **Zero Socket Exposure**: No raw networking in application code
- ✅ **Hot Proxy Swap**: Can change proxy configuration without restart

## 🚀 **Advanced Composition Examples**

### Geographic Routing via Context
```kotlin
// Geographic proxy selection through context
suspend fun selectProxy(target: SocketAddress): ProxyContext {
    val geo = context.requireService<GeographicContext>()
    val targetCountry = geo.getCountry(target.address)
    
    return context.discoverProxy { proxy ->
        proxy.location.country == targetCountry ||
        proxy.hasOptimalRouting(targetCountry)
    }
}
```

### Load Balancing via Context
```kotlin
// Load-balanced proxy selection
suspend fun balanceProxyLoad(target: SocketAddress): ProxyContext {
    val loadBalancer = context.requireService<LoadBalancerContext>()
    val availableProxies = context.discoverAll<ProxyContext>()
    
    return loadBalancer.selectLeastLoaded(
        proxies = availableProxies,
        target = target,
        metrics = context.requireService<MetricsContext>()
    )
}
```

### Failover via Context
```kotlin
// Automatic proxy failover through context
suspend fun establishTunnelWithFailover(target: SocketAddress): ProxyTunnel {
    val failover = context.requireService<FailoverContext>()
    
    return failover.withRetry { attempt ->
        val proxy = selectProxyForAttempt(attempt, target)
        proxy.establishTunnel(target)
    }
}
```

The SOCKS implementation demonstrates how connection-level proxy protocols can be completely abstracted through context discovery, enabling sophisticated routing, authentication, and failover patterns without tight coupling to specific SOCKS versions or proxy implementations.