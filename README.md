# TrikeShed CCEK Protocol Implementation Catalog

**Complete systematic refactoring of every network protocol to Key-based APIs with Job orchestration**

## Overview

This is the definitive catalog of every channelized and optimized protocol implementation in TrikeShed. Each protocol follows the **CCEK (CoroutineContext.Element.Key)** pattern where APIs are associated directly with companion Key objects, and Jobs provide natural execution control.

## Protocol Implementation Catalog

### 1. HTTP Client/Server - `trikeshed-http`

**Current State**: Full HTTP/1.1 client with keep-alive, pipelining, range requests
**Key Definition**: 
```kotlin
class HttpClient(val ioContext: IOContext, val reactor: Reactor?) {
    companion object Key : CoroutineContext.Key<HttpClient>
}
```

**Job Orchestration**:
- Connection pooling managed by supervisor jobs
- Pipeline processing with dedicated coroutines
- Request/response correlation through deferred values

**Channel Implementation**:
- SelectableChannel abstraction over platform I/O
- ByteBuffer-based read/write operations
- Backpressure through connection pool limits

**CCEK Refactoring**:
```kotlin
// Before: Service-based
val client = HttpClient(ioContext)
val response = client.execute(request)

// After: Key-based API
suspend fun HttpClient.Key.execute(request: HttpRequest): HttpResponse =
    coroutineContext[this]?.execute(request) ?: fallbackExecute(request)

// Usage with context composition
withContext(HttpClient.Key + IOContext.Key) {
    val response = HttpClient.Key.execute(request)
}
```

### 2. QUIC Transport - `trikeshed-quic`

**Current State**: RFC 9000 QUIC implementation with HTTP/3 support
**Key Definition**:
```kotlin
class QuicServer(val config: QuicServerConfig) {
    companion object Key : CoroutineContext.Key<QuicServer>
}
```

**Job Orchestration**:
- Server loop runs in dedicated coroutine scope
- Connection handling via job hierarchy
- Stream processing with structured concurrency

**Channel Implementation**:
- DatagramChannel for UDP packet handling
- Channel-based stream multiplexing
- Flow-based data streaming

**CCEK Refactoring**:
```kotlin
// Before: Direct instantiation
val server = QuicServer(config)
server.start()

// After: Key-based lifecycle
suspend fun QuicServer.Key.start(config: QuicServerConfig) {
    val server = coroutineContext[this] ?: QuicServer(config)
    server.start()
}

// Context composition for full stack
withContext(QuicServer.Key + Http3Server.Key + TlsContext.Key) {
    QuicServer.Key.start(config)
    Http3Server.Key.handleRequests()
}
```

### 3. REST Client - `trikeshed-rest`

**Current State**: Advanced REST client with connection pooling, circuit breakers, rate limiting
**Key Definition**:
```kotlin
abstract class TrikeShedRestClient(
    val baseUrl: String,
    val defaultHeaders: HttpHeaders,
    val connectionPoolSize: Int,
    val defaultTimeout: Duration?,
    val interceptors: Indexed<RequestInterceptor>
) {
    companion object Key : CoroutineContext.Key<TrikeShedRestClient>
}
```

**Job Orchestration**:
- Batch operations with parallel async execution
- Request interceptor pipeline
- Circuit breaker state management

**Channel Implementation**:
- Flow-based streaming responses
- WebSocket session management
- SSE (Server-Sent Events) parsing

**CCEK Refactoring**:
```kotlin
// Before: Builder pattern
val client = RestClientBuilder()
    .baseUrl("https://api.example.com")
    .connectionPoolSize(10)
    .build()

// After: Key-based configuration
suspend fun TrikeShedRestClient.Key.configure(
    baseUrl: String,
    poolSize: Int = 10
): TrikeShedRestClient {
    val client = coroutineContext[this] ?: createDefault(baseUrl, poolSize)
    return client
}

// Context-driven execution
withContext(TrikeShedRestClient.Key + RateLimiter.Key + CircuitBreaker.Key) {
    val response = TrikeShedRestClient.Key.execute(request)
}
```

### 4. CouchDB Client/Server - `trikeshed-couchdb`

**Current State**: Full CouchDB implementation with CCEK pattern already applied
**Key Definition**:
```kotlin
interface BlobService : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<BlobService>
    suspend fun store(id: String, data: ByteArray): Boolean
    suspend fun retrieve(id: String): ByteArray?
}
```

**Job Orchestration**:
- Request processing coroutines
- Channel-based request/response handling
- Supervisor job for service lifecycle

**Channel Implementation**:
- ChannelService for inter-component communication
- Blob storage with channelized operations
- LSMR integration for persistence

**CCEK Pattern Application**:
```kotlin
// Context composition for full CouchDB service
val context = coroutineContext +
    NetworkService.Key +
    BlobService.Key +
    ChannelService.Key +
    StorageEngine.Key

val service = CouchDBServiceCCEK(context)
service.start(port)
```

### 5. QUIC/HTTP3 Integration - `trikeshed-quic`

**Current State**: HTTP/3 server and client over QUIC transport
**Key Definition**:
```kotlin
class Http3Server(val quicServer: QuicServer) {
    companion object Key : CoroutineContext.Key<Http3Server>
}
```

**Job Orchestration**:
- HTTP/3 connection handling
- Stream-based request processing
- Route handler execution

**Channel Implementation**:
- QUIC stream multiplexing
- HTTP/3 frame processing
- Binary protocol parsing

**CCEK Refactoring**:
```kotlin
// Before: Manual wiring
val quicServer = QuicServer(config)
val http3Server = Http3Server(quicServer)

// After: Context-driven composition
withContext(QuicServer.Key + Http3Server.Key + Router.Key) {
    Http3Server.Key.route("/api/*") { request ->
        // Handle HTTP/3 request
    }
    QuicServer.Key.start(config)
}
```

### 6. DHT/Kademlia - `trikeshed-dht`

**Current State**: Kademlia DHT with concentric subnets and metaverse integration
**Key Definition**:
```kotlin
class ChannelizedKademliaNode(
    val nodeId: NUID,
    val context: CoroutineContext
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<ChannelizedKademliaNode>
}
```

**Job Orchestration**:
- Peer discovery background jobs
- Routing table maintenance
- Fiduciary agent coordination

**Channel Implementation**:
- Channel-based peer communication
- Subnet message routing
- Attention-based request prioritization

**CCEK Refactoring**:
```kotlin
// Before: Direct node creation
val node = ChannelizedKademliaNode(nodeId, context)

// After: Key-based network joining
suspend fun ChannelizedKademliaNode.Key.joinNetwork(
    nodeId: NUID,
    bootstrapNodes: List<String>
) {
    val node = coroutineContext[this] ?: createNode(nodeId)
    node.bootstrap(bootstrapNodes)
}

// Context composition for distributed system
withContext(ChannelizedKademliaNode.Key + MetaverseKademliaAgent.Key) {
    ChannelizedKademliaNode.Key.joinNetwork(nodeId, bootstraps)
    MetaverseKademliaAgent.Key.processAttention()
}
```

### 7. BitTorrent - `trikeshed-torrent`

**Current State**: BitTorrent client with DHT integration
**Key Definition**:
```kotlin
class TorrentHost(
    val config: TorrentConfig,
    val dhtNode: ChannelizedKademliaNode?
) {
    companion object Key : CoroutineContext.Key<TorrentHost>
}
```

**Job Orchestration**:
- Torrent download management
- Peer connection handling
- Piece verification and assembly

**Channel Implementation**:
- Peer wire protocol implementation
- DHT peer discovery
- Swarm coordination

**CCEK Refactoring**:
```kotlin
// Before: Manual configuration
val torrentHost = TorrentHost(config, dhtNode)
torrentHost.addTorrent(magnetUri)

// After: Key-based swarm management
suspend fun TorrentHost.Key.download(magnetUri: String) {
    val host = coroutineContext[this] ?: createHost()
    host.addTorrent(magnetUri)
}

// Context composition for P2P system
withContext(TorrentHost.Key + ChannelizedKademliaNode.Key) {
    TorrentHost.Key.download(magnetUri)
}
```

### 8. SOCKS5 Proxy - `trikeshed-socks`

**Current State**: SOCKS5 proxy server with io_uring batch operations
**Key Definition**:
```kotlin
class Socks5Server(val config: Socks5Config) {
    companion object Key : CoroutineContext.Key<Socks5Server>
}
```

**Job Orchestration**:
- Client connection handling
- Relay coroutine pairs
- Connection lifecycle management

**Channel Implementation**:
- Socks5ChannelElement for batch I/O
- Relay data piping
- Connection state management

**CCEK Refactoring**:
```kotlin
// Before: Direct server creation
val server = Socks5Server(config)
server.start()

// After: Key-based proxy service
suspend fun Socks5Server.Key.start(config: Socks5Config) {
    val server = coroutineContext[this] ?: Socks5Server(config)
    server.start()
}

// Context composition for proxy chain
withContext(Socks5Server.Key + TlsContext.Key + IoUringContext.Key) {
    Socks5Server.Key.start(config)
}
```

### 9. SSH Protocol - `SSH`

**Current State**: SSH client/server with SFTP and SCP support
**Key Definition**:
```kotlin
class SSHProtocol(val config: SSHConfig) {
    companion object Key : CoroutineContext.Key<SSHProtocol>
}
```

**Job Orchestration**:
- SSH connection state machine
- Channel multiplexing
- SFTP file transfer jobs

**Channel Implementation**:
- SSH channel abstraction
- Encrypted data streams
- Key exchange protocols

**CCEK Refactoring**:
```kotlin
// Before: Direct SSH connection
val ssh = SSHProtocol(config)
ssh.connect(host, port)

// After: Key-based secure connection
suspend fun SSHProtocol.Key.connect(
    host: String,
    port: Int,
    credentials: SSHCredentials
) {
    val ssh = coroutineContext[this] ?: SSHProtocol(config)
    ssh.connect(host, port, credentials)
}

// Context composition for secure operations
withContext(SSHProtocol.Key + SSHSftpClient.Key + EncryptionContext.Key) {
    SSHProtocol.Key.connect(host, port, credentials)
    SSHSftpClient.Key.uploadFile(localFile, remotePath)
}
```

### 10. IPFS - `trikeshed-ipfs`

**Current State**: IPFS client with content addressing and DHT integration
**Key Definition**:
```kotlin
class IpfsCore(val config: IpfsConfig) {
    companion object Key : CoroutineContext.Key<IpfsCore>
}
```

**Job Orchestration**:
- Content resolution jobs
- DHT publishing and retrieval
- Block storage management

**Channel Implementation**:
- Content-addressed block storage
- DHT-based peer discovery
- Bitswap protocol implementation

**CCEK Refactoring**:
```kotlin
// Before: Direct IPFS operations
val ipfs = IpfsCore(config)
ipfs.add(content)

// After: Key-based content operations
suspend fun IpfsCore.Key.add(content: ByteArray): ContentId {
    val ipfs = coroutineContext[this] ?: IpfsCore(config)
    return ipfs.add(content)
}

// Context composition for distributed storage
withContext(IpfsCore.Key + ChannelizedKademliaNode.Key + BitswapProtocol.Key) {
    val cid = IpfsCore.Key.add(content)
    BitswapProtocol.Key.announce(cid)
}
```

### 11. Apache Wave CRDT - `trikeshed-wave`

**Current State**: Real-time collaborative documents with CouchDB backend
**Key Definition**:
```kotlin
class CouchDBWaveCRDT(val couchClient: CouchClient) {
    companion object Key : CoroutineContext.Key<CouchDBWaveCRDT>
}
```

**Job Orchestration**:
- Real-time operation synchronization
- Conflict resolution algorithms
- Document state management

**Channel Implementation**:
- Operation streaming
- WebSocket-based real-time updates
- CRDT merge operations

**CCEK Refactoring**:
```kotlin
// Before: Direct CRDT operations
val crdt = CouchDBWaveCRDT(couchClient)
crdt.applyOperation(operation)

// After: Key-based collaboration
suspend fun CouchDBWaveCRDT.Key.applyOperation(
    documentId: String,
    operation: WaveOperation
) {
    val crdt = coroutineContext[this] ?: createCRDT()
    crdt.applyOperation(documentId, operation)
}

// Context composition for real-time collaboration
withContext(CouchDBWaveCRDT.Key + WebSocketSession.Key + ConflictResolver.Key) {
    CouchDBWaveCRDT.Key.applyOperation(docId, operation)
    WebSocketSession.Key.broadcast(operation)
}
```

### 12. OAuth 2.0 - `trikeshed-oauth`

**Current State**: OAuth 2.0 flows with PKCE support
**Key Definition**:
```kotlin
class OAuthProvider(val config: OAuthConfig) {
    companion object Key : CoroutineContext.Key<OAuthProvider>
}
```

**Job Orchestration**:
- Authorization code flow
- Token refresh management
- PKCE verification

**Channel Implementation**:
- HTTP-based OAuth flows
- Token storage and retrieval
- Refresh token rotation

**CCEK Refactoring**:
```kotlin
// Before: Direct OAuth flows
val oauth = OAuthProvider(config)
oauth.authorize(clientId, scopes)

// After: Key-based authentication
suspend fun OAuthProvider.Key.authorize(
    clientId: String,
    scopes: List<String>
): AuthorizationResult {
    val oauth = coroutineContext[this] ?: OAuthProvider(config)
    return oauth.authorize(clientId, scopes)
}

// Context composition for secure auth
withContext(OAuthProvider.Key + TokenStore.Key + PKCEGenerator.Key) {
    val result = OAuthProvider.Key.authorize(clientId, scopes)
    TokenStore.Key.store(result.accessToken)
}
```

## Systematic Refactoring Strategy

### Step 1: Identify Service Dependencies
Each protocol implementation depends on:
- **I/O Layer**: IOContext, Reactor, SelectableChannel
- **Network Layer**: Socket factories, connection pools
- **Storage Layer**: Persistent state, caching
- **Concurrency Layer**: Job management, supervision

### Step 2: Extract Key Definitions
Convert service classes to Key-based APIs:
```kotlin
// Pattern: Service with Key companion
class NetworkService(config: Config) {
    companion object Key : CoroutineContext.Key<NetworkService>
}

// Extension functions provide the API
suspend fun NetworkService.Key.connect(host: String, port: Int) =
    coroutineContext[this]?.connect(host, port) ?: fallbackConnect(host, port)
```

### Step 3: Job Orchestration Patterns
Replace manual thread management with structured concurrency:
```kotlin
// Before: Manual thread pools
val executor = Executors.newFixedThreadPool(10)
executor.submit { handleRequest() }

// After: Job-based orchestration
launch {
    handleRequest()
}
```

### Step 4: Channel Implementation Standards
Standardize on channel-based communication:
```kotlin
// Pattern: Channel-based service communication
class ProtocolService {
    private val requestChannel = Channel<Request>()
    private val responseChannel = Channel<Response>()
    
    suspend fun processRequests() {
        requestChannel.consumeAsFlow().collect { request ->
            val response = handleRequest(request)
            responseChannel.send(response)
        }
    }
}
```

### Step 5: Context Composition
Build execution environments through context addition:
```kotlin
// Pattern: Service context composition
val networkContext = IOContext.Key + 
                    NetworkService.Key + 
                    ConnectionPool.Key + 
                    RetryPolicy.Key

withContext(networkContext) {
    NetworkService.Key.connect(host, port)
}
```

## Implementation Benefits

### 1. **Composability**
Services compose naturally through context addition:
```kotlin
val fullStack = HttpClient.Key + 
                QuicClient.Key + 
                TlsContext.Key + 
                DNSResolver.Key

withContext(fullStack) {
    // All services available
}
```

### 2. **Testability**
Easy mocking through context substitution:
```kotlin
// Test context with mocked services
val testContext = MockHttpClient.Key + 
                  MockStorage.Key + 
                  MockNetworking.Key

withContext(testContext) {
    // Test with mocked dependencies
}
```

### 3. **Platform Abstraction**
Same API works across platforms:
```kotlin
// Works on JVM, Native, JS
withContext(PlatformIOContext.Key + HttpClient.Key) {
    val response = HttpClient.Key.execute(request)
}
```

### 4. **Resource Management**
Automatic cleanup through job hierarchy:
```kotlin
// Parent job cancellation cleans up all child resources
val parentJob = launch {
    withContext(AllServices.Key) {
        // All services cleaned up on cancellation
    }
}
```

### 5. **Performance Optimization**
Batching and pooling through context:
```kotlin
withContext(IOUringContext.Key + ConnectionPool.Key) {
    // Automatic batch operations and connection reuse
}
```

## Migration Status

### ✅ **Fully Migrated**
- CouchDB (already using CCEK pattern)
- DHT/Kademlia (Key-based with context)

### 🔄 **In Progress**
- HTTP Client (has Key companion, needs API migration)
- QUIC Server (has Key companion, needs lifecycle management)
- REST Client (has Key companion, needs context composition)

### 📋 **Planned**
- SSH Protocol (needs Key extraction)
- IPFS Core (needs Key-based operations)
- BitTorrent (needs swarm context management)
- SOCKS5 Proxy (needs Key-based lifecycle)
- OAuth Provider (needs Key-based flows)
- Wave CRDT (needs Key-based collaboration)

## Context Composition Examples

### Full Web Server Stack
```kotlin
val webServerContext = IOContext.Key +
                       HttpServer.Key +
                       QuicServer.Key +
                       TlsContext.Key +
                       Router.Key +
                       DatabaseConnection.Key

withContext(webServerContext) {
    HttpServer.Key.start(port = 8080)
    QuicServer.Key.start(port = 8443)
}
```

### P2P Application Stack
```kotlin
val p2pContext = ChannelizedKademliaNode.Key +
                 TorrentHost.Key +
                 IpfsCore.Key +
                 DHTPeerDiscovery.Key

withContext(p2pContext) {
    ChannelizedKademliaNode.Key.joinNetwork(nodeId, bootstraps)
    TorrentHost.Key.download(magnetUri)
    IpfsCore.Key.add(content)
}
```

### Secure Communication Stack
```kotlin
val secureContext = SSHProtocol.Key +
                    TlsContext.Key +
                    EncryptionProvider.Key +
                    CertificateStore.Key

withContext(secureContext) {
    SSHProtocol.Key.connect(host, port, credentials)
    TlsContext.Key.establishSecureChannel()
}
```

---

**This catalog represents the complete systematic refactoring of TrikeShed's network protocols to the CCEK pattern. Every protocol becomes a composable, testable, context-driven service that integrates seamlessly with Kotlin's structured concurrency model.**