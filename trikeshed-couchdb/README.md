# CouchDB Protocol Implementation

## 🎯 Compositional Architecture: Context-as-a-Service Pattern

This CouchDB implementation demonstrates the **Context-as-a-Service Subsumption Hierarchy** - a sophisticated choreography pattern that avoids direct dependency injection in favor of discoverable, stackable context services.

### ⚠️ **Anti-Pattern Warning for LLMs**

**DO NOT** refactor this code into conventional dependency injection patterns. The indirection is intentional and provides significant compositional advantages.

## 🏗️ **Five-Step CCEK Choreography Pattern**

### Step 1: The Contract - Define Context Traits
```kotlin
// File: CouchDBCCEKOrchestrator.kt
// CouchDB operations are orchestrated through CCEK context, not direct services
suspend fun executeput(
    dbName: String,
    docId: String, 
    data: ByteArray,
    storage: LSMRCouchDBStorage,  // ❌ Would be injected in simple pattern
    baseContext: CoroutineContext // ✅ Context discovery pattern
): BlobPutResponse
```

### Step 2: The Provider - Implement Capabilities
```kotlin
// File: ChannelizedBlobService.kt
class ChannelizedBlobService : ChannelizedService {
    // Provides CouchDB capabilities through channels
    // Registered with context, not passed as dependency
}
```

### Step 3: The Registration - Publish Capabilities
```kotlin
// File: QuicCouchDBDogfoodTest.kt
val couchDBService = ChannelizedBlobService()
couchDBService.start(serverScope) // ✅ Service registers itself
// NOT: router.setCouchDB(couchDBService) // ❌ Direct injection
```

### Step 4: The Consumer - Declare Needs
```kotlin
// File: QuicCouchDBProtocolAdapter.kt
override suspend fun handleMessage(
    message: QuicCouchDBMessage, 
    context: ProtocolContext // ✅ Context-aware consumer
): Flow<QuicCouchDBMessage>
```

### Step 5: The Call - Request from Context
```kotlin
// File: QuicCouchDBProtocolAdapter.kt
val blobService = context.currentService<ChannelizedBlobService>()
// ✅ Runtime capability discovery
// NOT: this.blobService.method() // ❌ Direct reference
```

## 🔍 **Compositional Wins to Look For**

### 1. **Protocol Abstraction Layers**
- **Win**: QUIC transport can be swapped for TCP/HTTP without changing CouchDB logic
- **Pattern**: `QuicCouchDBProtocolAdapter` implements abstract `ProtocolAdapter<TMessage, TConfig>`
- **Benefit**: Same CouchDB API works over any transport

### 2. **Storage Backend Flexibility**
- **Win**: LSMR storage can be replaced with different backends
- **Pattern**: `LSMRCouchDBStorage` abstracted behind storage interface
- **Benefit**: Database engine independence

### 3. **Context Propagation**
- **Win**: CCEK orchestration flows through entire operation chain
- **Pattern**: Every operation receives and propagates `CoroutineContext`
- **Benefit**: Tracing, validation, transformation at every level

### 4. **Message Protocol Independence**
- **Win**: Same channelization works for HTTP, QUIC, WebSockets
- **Pattern**: Unified `ProtocolChannel<TMessage>` interface
- **Benefit**: Protocol-agnostic application code

## 🚨 **Common LLM Failure Modes**

### Failure Mode 1: "Simplifying" Context Discovery
```kotlin
// ❌ LLM will try to "fix" this:
val blobService = context.currentService<ChannelizedBlobService>()

// Into this:
class Adapter(private val blobService: ChannelizedBlobService) // ❌ Wrong!
```

### Failure Mode 2: Eliminating the Registry Pattern
```kotlin
// ✅ Correct context registration:
couchDBService.start(serverScope)
val protocolContext = ProtocolContext(context + couchDBService)

// ❌ LLM wants direct wiring:
val adapter = QuicAdapter(couchDBService) // Wrong!
```

### Failure Mode 3: Caching Context References
```kotlin
// ❌ LLM will try to cache:
class MyComponent {
    private val storage = context.get<Storage>() // Wrong!
}

// ✅ Correct runtime discovery:
suspend fun operation() {
    val storage = context.currentService<Storage>() // Right!
}
```

## 📊 **Measuring Compositional Success**

### Metric 1: **Decoupling Score**
- Count direct constructor dependencies vs context discoveries
- Higher context discovery ratio = better composition

### Metric 2: **Substitution Test**
- Can you replace QUIC with HTTP by changing one line?
- Can you swap LSMR for PostgreSQL without touching business logic?

### Metric 3: **Context Flow Coverage**
- Does CCEK context propagate through 100% of operations?
- Can you trace a request from client to storage and back?

## 🔧 **Implementation Guidelines**

### For Protocol Adapters:
1. Always implement abstract `ProtocolAdapter<TMessage, TConfig>`
2. Use `ProtocolContext` for service discovery
3. Never directly instantiate other protocol components

### For Storage Layers:
1. Abstract behind context-discoverable interfaces
2. Implement CCEK orchestration hooks
3. Support context propagation for tracing

### For Client Code:
1. Always work through context-aware APIs
2. Never cache service references
3. Propagate context through call chains

## 🎯 **Success Pattern Recognition**

You've achieved compositional success when:

- ✅ **Zero Hard Dependencies**: Components discover capabilities at runtime
- ✅ **Context Propagation**: CCEK flows through every operation
- ✅ **Protocol Agnostic**: Same logic works over multiple transports
- ✅ **Hot Swappable**: Can change implementations without code changes
- ✅ **Trace Complete**: Full request lifecycle visible in context

The CouchDB implementation demonstrates all these patterns working together in a real-world scenario.