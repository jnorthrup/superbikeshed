# HTTP Protocol Implementation

## 🎯 Compositional Architecture: Context-as-a-Service Pattern

This HTTP implementation demonstrates the **Context-as-a-Service Subsumption Hierarchy** for web protocols, enabling HTTP/1.1, HTTP/2, and HTTP/3 to coexist through unified context discovery rather than protocol-specific implementations.

### ⚠️ **Anti-Pattern Warning for LLMs**

**DO NOT** refactor this into conventional servlet/handler patterns. The context-based request routing enables powerful composition that direct handler registration destroys.

## 🏗️ **Five-Step CCEK Choreography for HTTP**

### Step 1: The Contract - Define HTTP Capabilities
```kotlin
// HTTP operations discovered through context, not servlet registration
interface HttpContext {
    suspend fun handleRequest(request: HttpRequest): HttpResponse
    suspend fun routeRequest(path: String): RouteHandler?
    fun configureMiddleware(middleware: HttpMiddleware)
}
```

### Step 2: The Provider - Implement HTTP Services
```kotlin
class HttpService : ContextProvider, ContextAware {
    // Provides HTTP capabilities without being directly referenced
    private inner class HttpContextImpl : HttpContext {
        override suspend fun handleRequest(request: HttpRequest): HttpResponse {
            // HTTP request handling with context propagation
        }
    }
}
```

### Step 3: The Registration - Publish HTTP Capabilities
```kotlin
// In system tethering - register, don't inject handlers
val httpService = HttpService(config)
httpService.registerContexts() // ✅ Self-registration
// NOT: server.addHandler("/api", apiHandler) // ❌ Direct registration
```

### Step 4: The Consumer - Context-Aware Routing
```kotlin
class ApiHandler : ContextAware {
    override val contextKeys = setOf(ContextKeys.HTTP, ContextKeys.STORAGE)
    
    suspend fun handleApiCall() {
        // Discovers HTTP context at runtime - works with any HTTP version
        val http = ContextRegistry.get<HttpContext>(ContextKeys.HTTP)
    }
}
```

### Step 5: The Call - Runtime HTTP Discovery
```kotlin
suspend fun processRequest(request: HttpRequest) {
    val httpContext = context.currentService<HttpContext>()
    val response = httpContext?.handleRequest(request) // ✅ Discovered capability
    // NOT: this.servlet.service(request, response) // ❌ Direct servlet call
}
```

## 🔍 **HTTP-Specific Compositional Wins**

### 1. **HTTP Version Abstraction**
- **Win**: Same application code works with HTTP/1.1, HTTP/2, and HTTP/3
- **Pattern**: Abstract `HttpContext` with version-specific implementations
- **Benefit**: Protocol evolution doesn't break application logic

### 2. **Middleware Pipeline Discovery**
- **Win**: Middleware components discover each other through context
- **Pattern**: Context-based middleware chain composition
- **Benefit**: Dynamic middleware reconfiguration without restart

### 3. **Request Context Propagation**
- **Win**: User auth, tracing, and metadata flow through entire request
- **Pattern**: CCEK context carries request-scoped data
- **Benefit**: Cross-cutting concerns without explicit parameter passing

### 4. **Transport Independence**
- **Win**: HTTP handlers work over TCP, QUIC, or Unix sockets
- **Pattern**: Transport abstracted behind context interface
- **Benefit**: Deployment flexibility without code changes

## 🚨 **HTTP-Specific LLM Failure Modes**

### Failure Mode 1: "Simplifying" to Servlet Pattern
```kotlin
// ❌ LLM wants familiar servlet pattern:
class ApiServlet extends HttpServlet {
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) // Wrong!
}

// ✅ Correct context-based handling:
suspend fun handleRequest() {
    val request = context.currentService<HttpRequestContext>()
}
```

### Failure Mode 2: Hard-Coding Route Registration
```kotlin
// ❌ LLM tries direct route mapping:
server.addRoute("/api/users", userHandler) // Wrong!
server.addRoute("/api/posts", postHandler) // Wrong!

// ✅ Context-discovered routing:
val router = context.requireService<RoutingContext>()
router.route(request) // Dynamic discovery
```

### Failure Mode 3: Bypassing Middleware Composition
```kotlin
// ❌ LLM creates explicit middleware chains:
val chain = AuthMiddleware(LoggingMiddleware(CorsMiddleware(handler))) // Wrong!

// ✅ Context-composed middleware:
val response = context.processWithMiddleware(request) // Right!
```

## 📊 **HTTP Compositional Metrics**

### Metric 1: **Version Independence Score**
- Can the same handlers work with HTTP/1.1 and HTTP/3?
- Higher version abstraction = better composition

### Metric 2: **Middleware Dynamism**
- Can middleware be added/removed without code changes?
- More dynamic configuration = better composition

### Metric 3: **Context Flow Integrity**
- Does request context propagate through all layers?
- Complete propagation = full composability

## 🔧 **HTTP Implementation Guidelines**

### For Request Handlers:
1. Never implement servlet interfaces directly
2. Always discover HTTP context at runtime
3. Propagate CCEK context through all operations

### For Middleware:
1. Discover next middleware through context, not explicit chains
2. Add metadata to context, don't modify global state
3. Support dynamic reconfiguration via context

### For Server Infrastructure:
1. Abstract HTTP version differences behind context
2. Enable middleware discovery through registry
3. Support context-based request routing

## 🌊 **Request Context Flow Pattern**

```kotlin
// Request context flows through entire processing pipeline
class AuthMiddleware : ContextAware {
    suspend fun authenticate(request: HttpRequest): HttpRequest {
        val user = validateToken(request.headers["Authorization"])
        
        // Add auth context for downstream handlers
        return request.withContext(
            context + AuthContext(user)
        )
    }
}

class UserHandler : ContextAware {
    suspend fun getUser(): HttpResponse {
        // Auth context automatically available
        val auth = context.requireService<AuthContext>()
        return HttpResponse.ok(auth.user)
    }
}
```

## 🔄 **Dynamic Middleware Composition**

```kotlin
// Middleware discovered and composed at runtime
suspend fun processRequest(request: HttpRequest): HttpResponse {
    val middleware = context.discoverMiddleware(request.path)
    
    return middleware.fold(request) { req, mw ->
        mw.process(req, context)
    }.let { finalRequest ->
        val handler = context.routeHandler(finalRequest.path)
        handler.handle(finalRequest)
    }
}
```

## 🎯 **HTTP Success Patterns**

You've achieved HTTP compositional success when:

- ✅ **Version Agnostic**: Code works across HTTP/1.1, HTTP/2, HTTP/3
- ✅ **Dynamic Middleware**: Can reconfigure processing pipeline at runtime
- ✅ **Context Propagation**: Request metadata flows through all layers
- ✅ **Transport Independence**: Handlers work over any transport
- ✅ **Zero Servlet Coupling**: No direct dependency on servlet APIs
- ✅ **Hot Route Updates**: Can modify routing without restart

## 🚀 **Advanced Composition Examples**

### WebSocket Upgrade via Context
```kotlin
// WebSocket upgrade discovered through HTTP context
suspend fun handleWebSocketRequest(request: HttpRequest) {
    val wsContext = context.currentService<WebSocketContext>()
    if (wsContext?.canUpgrade(request) == true) {
        return wsContext.upgrade(request)
    }
    // Fallback to regular HTTP handling
}
```

### Content Negotiation via Context
```kotlin
// Content negotiation through context-discovered formatters
suspend fun renderResponse(data: Any): HttpResponse {
    val formatter = context.discoverFormatter(
        acceptHeader = request.headers["Accept"],
        dataType = data::class
    )
    return formatter.format(data, context)
}
```

The HTTP implementation demonstrates how protocol-level concerns can be completely abstracted through context discovery, enabling unprecedented flexibility and composability.