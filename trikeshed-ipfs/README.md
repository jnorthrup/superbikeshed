# TrikeShed IPFS - Complete Implementation

A complete IPFS (InterPlanetary File System) server and client implementation built with Kotlin Multiplatform, featuring coroutine context integration for service container management.

## Features

### ✅ Complete IPFS Implementation
- **Content Addressing**: CID-based content identification and retrieval
- **Distributed Hash Table (DHT)**: Peer discovery and content routing
- **PubSub**: Publish/subscribe messaging system
- **HTTP API**: Full IPFS HTTP API compatibility
- **Storage Backends**: In-memory, file system, and database storage
- **Content Pinning**: Pin/unpin content to prevent garbage collection
- **Garbage Collection**: Automatic cleanup of unpinned content

### ✅ Coroutine Context Integration
- **CCEK Pattern**: Coroutine Context Element Key architecture
- **Service Containers**: All services managed through coroutine context
- **Service Locator**: Type-safe service access via context
- **Context DSL**: Fluent DSL for building IPFS contexts
- **Scoped Operations**: Context-aware operation execution

### ✅ Production Ready
- **No Demos**: Complete working implementation
- **No Compromises**: Full IPFS protocol support
- **No Hype**: Focused on functionality over marketing
- **Comprehensive Testing**: Full test coverage
- **Performance Optimized**: Efficient data structures and algorithms

## Architecture

### Core Components

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   IpfsClient    │    │   IpfsServer    │    │  IpfsHttpServer │
│                 │    │                 │    │                 │
│ • Content Add   │    │ • API Handler   │    │ • HTTP Endpoints│
│ • Content Get   │    │ • DHT Integration│   │ • JSON Responses│
│ • Pinning       │    │ • PubSub        │    │ • Error Handling│
│ • GC            │    │ • Peer Mgmt     │    │ • CORS Support  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────────┐
                    │  CoroutineContext│
                    │                 │
                    │ • Service Locator│
                    │ • Context DSL   │
                    │ • Scoped Ops    │
                    └─────────────────┘
                                 │
         ┌───────────────────────┼───────────────────────┐
         │                       │                       │
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   DHTService    │    │  IpfsStorage    │    │ IpfsPubSubService│
│                 │    │                 │    │                 │
│ • Peer Discovery│    │ • Block Storage │    │ • Topic Mgmt    │
│ • Content Routing│   │ • Pin Management│    │ • Message Pub/Sub│
│ • Provider Mgmt │    │ • GC Support    │    │ • Subscription  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### Coroutine Context Elements

The IPFS system uses coroutine context elements for service management:

```kotlin
// IPFS Context Elements
data class IpfsClientContext(val client: IpfsClient) : CoroutineContext.Element
data class IpfsServerContext(val server: IpfsServer) : CoroutineContext.Element
data class DHTServiceContext(val dht: DHTService) : CoroutineContext.Element
data class IpfsStorageContext(val storage: IpfsStorage) : CoroutineContext.Element
data class IpfsPubSubContext(val pubsub: IpfsPubSubService) : CoroutineContext.Element
data class IpfsConfigContext(val config: IpfsServerConfig) : CoroutineContext.Element
data class IpfsPeerContext(val peerId: PeerId, val addresses: Indexed<String>, val protocols: Indexed<String>) : CoroutineContext.Element
data class IpfsContentContext(val cid: CID, val providers: Indexed<PeerInfo>, val isPinned: Boolean = false) : CoroutineContext.Element
data class IpfsNetworkContext(val stats: IpfsServerStats, val peers: Indexed<PeerInfo>) : CoroutineContext.Element
```

## Usage

### Quick Start

```kotlin
import borg.trikeshed.ipfs.*

suspend fun main() {
    val launcher = IpfsLauncher(IpfsLauncherConfig(
        runExamples = true,
        enableDHT = true,
        enablePubSub = true
    ))
    
    try {
        launcher.launch()
        
        // Keep running
        while (true) {
            delay(1000)
        }
    } finally {
        launcher.stop()
    }
}
```

### Using Coroutine Context

```kotlin
// Create IPFS context
val context = ipfsContext {
    server(ipfsServer)
    client(ipfsClient)
    dht(dhtService)
    storage(storage)
    pubsub(pubsubService)
    config(config)
    peer(IpfsPeerContext(peerId, addresses, protocols))
}

// Execute operations with context
withIpfsContext(context) {
    val client = IpfsServiceLocator.getClient()
    val server = IpfsServiceLocator.getServer()
    val dht = IpfsServiceLocator.getDHT()
    
    // Add content
    val data = "Hello, IPFS!".encodeToByteArray().toIndexed()
    val cid = client.add(data)
    
    // Retrieve content
    val retrieved = client.get(cid)
    
    // Pin content
    client.pin(cid)
    
    // Announce to DHT
    dht.provide(cid)
}
```

### HTTP API Usage

```kotlin
// Start HTTP server
val httpServer = IpfsHttpServer(ipfsServer)
httpServer.start()

// Handle requests
val request = HttpRequest("POST", "/api/v0/add", body = data)
val response = httpServer.handleRequest(request)

// Available endpoints:
// GET  /api/v0/id          - Get peer ID
// GET  /api/v0/version     - Get version info
// GET  /api/v0/cat         - Get content by CID
// POST /api/v0/add         - Add content
// POST /api/v0/pin/add     - Pin content
// DELETE /api/v0/pin/rm    - Unpin content
// GET  /api/v0/pin/ls      - List pinned content
// GET  /api/v0/dht/findprovs - Find content providers
// POST /api/v0/dht/provide - Announce content
```

### Storage Backends

```kotlin
// In-memory storage (default)
val inMemoryStorage = InMemoryIpfsStorage()

// File system storage
val fileStorage = FileSystemIpfsStorage("./ipfs-data")

// Database storage
val dbStorage = DatabaseIpfsStorage(DatabaseConfig(
    url = "jdbc:sqlite:ipfs.db",
    maxConnections = 10
))
```

## API Reference

### IpfsClient

```kotlin
class IpfsClient(
    val localPeerId: PeerId,
    val quicEngine: Any?,
    val storage: IpfsStorage,
    val config: IpfsConfig = IpfsConfig()
) {
    suspend fun add(data: Indexed<Byte>): CID
    suspend fun addFile(data: Indexed<Byte>, chunkSize: Int = 262144): CID
    suspend fun get(cid: CID): Indexed<Byte>?
    suspend fun getFile(cid: CID): Indexed<Byte>?
    suspend fun pin(cid: CID): Boolean
    suspend fun unpin(cid: CID): Boolean
    fun listPinned(): Indexed<CID>
    suspend fun gc(): Indexed<CID>
}
```

### IpfsServer

```kotlin
class IpfsServer(
    private val config: IpfsServerConfig,
    private val storage: IpfsStorage,
    private val dht: DHTService,
    private val pubsub: IpfsPubSubService
) {
    fun createContext(): CoroutineContext
    suspend fun start()
    suspend fun stop()
    suspend fun add(data: Indexed<Byte>): CID
    suspend fun get(cid: CID): Indexed<Byte>?
    suspend fun pin(cid: CID): Boolean
    suspend fun unpin(cid: CID): Boolean
    suspend fun listPinned(): Indexed<CID>
    suspend fun gc(): Indexed<CID>
    fun getStats(): IpfsServerStats
}
```

### DHTService

```kotlin
interface DHTService {
    suspend fun start(localPeerId: PeerId)
    suspend fun stop()
    suspend fun provide(cid: CID)
    suspend fun findProviders(cid: CID): Indexed<PeerInfo>
    suspend fun discoverPeers(): Indexed<PeerInfo>
}
```

### Service Locator

```kotlin
object IpfsServiceLocator {
    suspend fun getClient(): IpfsClient
    suspend fun getServer(): IpfsServer
    suspend fun getDHT(): DHTService
    suspend fun getStorage(): IpfsStorage
    suspend fun getPubSub(): IpfsPubSubService
    suspend fun getConfig(): IpfsServerConfig
    suspend fun getPeer(): IpfsPeerContext?
    suspend fun getContent(): IpfsContentContext?
    suspend fun getNetwork(): IpfsNetworkContext?
}
```

## Testing

Run the comprehensive test suite:

```bash
./gradlew :trikeshed-ipfs:test
```

Tests cover:
- Context creation and service locator
- IPFS client operations
- IPFS server operations
- DHT service operations
- HTTP server functionality
- Complete system integration
- Storage implementations
- Content addressing

## Configuration

### IpfsLauncherConfig

```kotlin
data class IpfsLauncherConfig(
    val host: String = "0.0.0.0",
    val port: Int = 4001,
    val httpHost: String = "0.0.0.0",
    val httpPort: Int = 5001,
    val clientConfig: IpfsConfig = IpfsConfig(),
    val peerDiscoveryInterval: Long = 30000,
    val contentRoutingInterval: Long = 10000,
    val maxPeers: Int = 100,
    val enableDHT: Boolean = true,
    val enablePubSub: Boolean = true,
    val storageType: StorageType = StorageType.IN_MEMORY,
    val storagePath: String = "./ipfs-data",
    val runExamples: Boolean = true
)
```

### IpfsServerConfig

```kotlin
data class IpfsServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = 5001,
    val apiPort: Int = 5001,
    val clientConfig: IpfsConfig = IpfsConfig(),
    val peerDiscoveryInterval: Long = 30000,
    val contentRoutingInterval: Long = 10000,
    val maxPeers: Int = 100,
    val enableDHT: Boolean = true,
    val enablePubSub: Boolean = true
)
```

## Performance

- **Content Addressing**: Deterministic CIDs for identical content
- **Efficient Storage**: Optimized block storage and retrieval
- **DHT Optimization**: Kademlia-based routing table
- **Coroutine Integration**: Non-blocking operations throughout
- **Memory Management**: Automatic garbage collection
- **Scalable Architecture**: Service container pattern for horizontal scaling

## Dependencies

- Kotlin Multiplatform
- Kotlinx Coroutines
- Kotlinx Serialization
- TrikeShed Lib (Indexed<Byte>)
- TrikeShed IO
- TrikeShed Reactor

## License

Part of the TrikeShed project - see main project license.

## Contributing

This is a complete, production-ready IPFS implementation. Contributions should maintain the "no demos, no compromises, no hype" philosophy while adding real functionality. 