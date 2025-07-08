# TrikeDownloader Evolution Plan

## 🏗️ Foundation Architecture

The current implementation provides a **protocol-agnostic foundation** that can evolve into a full-featured downloader:

### Core Design Principles
- **Sealed class hierarchy** for download types (easy to extend)
- **Coroutine-based concurrency** (scalable and testable)
- **Channel-based communication** (decoupled components)
- **Persona-based CLI** (familiar interfaces)
- **Shared backend** (code reuse)

## 🚀 Evolution Paths

### Phase 1: Protocol Implementation
```kotlin
// Current: Simulated downloads
private suspend fun executeHttpDownload(task: DownloadTask.HttpDownload) {
    // Simulate HTTP download
    var downloaded = 0L
    val total = 1024 * 1024L
    while (downloaded < total) {
        delay(100)
        downloaded += 1024 * 10
    }
}

// Evolution: Real HTTP client
private suspend fun executeHttpDownload(task: DownloadTask.HttpDownload) {
    val client = HttpClient {
        install(HttpTimeout) { 
            connectTimeoutMillis = task.timeout 
        }
        install(HttpRequestRetry) { 
            retryOnServerErrors(maxRetries = 3) 
        }
    }
    
    client.request(task.url) {
        method = HttpMethod.parse(task.method)
        headers { 
            task.headers.forEach { (k, v) -> append(k, v) }
        }
    }.bodyAsChannel().copyTo(fileChannel)
}
```

### Phase 2: BitTorrent Protocol
```kotlin
// Add to sealed class hierarchy
data class TorrentDownload(
    // ... existing fields
    val pieceSize: Int = 16384,
    val pieces: List<PieceHash> = emptyList(),
    val files: List<TorrentFile> = emptyList()
) : DownloadTask()

// Implement BitTorrent client
class BitTorrentClient(
    private val dht: DHTService,
    private val peerManager: PeerManager
) {
    suspend fun download(torrent: TorrentDownload): Flow<DownloadProgress> = flow {
        // 1. Parse torrent file/magnet link
        val torrentInfo = parseTorrent(torrent.url)
        
        // 2. Discover peers via DHT
        val peers = dht.findPeers(torrentInfo.infoHash)
        
        // 3. Connect to peers and download pieces
        peerManager.connect(peers).collect { piece ->
            emit(DownloadProgress(piece))
        }
    }
}
```

### Phase 3: Advanced Features
```kotlin
// Plugin system for additional protocols
interface DownloadProtocol {
    suspend fun download(task: DownloadTask): Flow<DownloadProgress>
    fun supports(url: String): Boolean
}

class FTPProtocol : DownloadProtocol {
    override suspend fun download(task: DownloadTask): Flow<DownloadProgress> {
        // FTP implementation
    }
    
    override fun supports(url: String): Boolean = url.startsWith("ftp://")
}

class SFTPProtocol : DownloadProtocol {
    // SFTP implementation
}

// Protocol registry
class ProtocolRegistry {
    private val protocols = mutableListOf<DownloadProtocol>()
    
    fun register(protocol: DownloadProtocol) {
        protocols.add(protocol)
    }
    
    fun findProtocol(url: String): DownloadProtocol? {
        return protocols.find { it.supports(url) }
    }
}
```

### Phase 4: Performance Optimizations
```kotlin
// Zero-copy file operations
class ZeroCopyFileManager {
    suspend fun transfer(
        source: ByteChannel,
        target: FileChannel,
        buffer: ByteBuffer
    ) {
        source.transferTo(0, buffer.remaining(), target)
    }
}

// Memory-mapped files for large downloads
class MemoryMappedDownloader {
    suspend fun downloadLargeFile(url: String, file: Path) {
        val channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        val buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize)
        // Download directly into mapped memory
    }
}
```

### Phase 5: Distributed Features
```kotlin
// Multi-node download coordination
class DistributedDownloader(
    private val cluster: ClusterManager,
    private val coordinator: DownloadCoordinator
) {
    suspend fun downloadDistributed(torrent: TorrentDownload) {
        // 1. Split download across nodes
        val chunks = coordinator.splitDownload(torrent)
        
        // 2. Assign chunks to cluster nodes
        cluster.distribute(chunks)
        
        // 3. Collect and reassemble
        coordinator.reassemble(chunks)
    }
}
```

## 🔧 Extension Points

### 1. Protocol Extensions
```kotlin
// Easy to add new protocols
class WebDAVProtocol : DownloadProtocol {
    override suspend fun download(task: DownloadTask): Flow<DownloadProgress> {
        // WebDAV implementation
    }
}

// Register automatically
ProtocolRegistry().register(WebDAVProtocol())
```

### 2. Storage Backends
```kotlin
interface StorageBackend {
    suspend fun write(path: String, data: ByteArray)
    suspend fun read(path: String): ByteArray
    suspend fun exists(path: String): Boolean
}

class S3StorageBackend : StorageBackend {
    // AWS S3 implementation
}

class LocalStorageBackend : StorageBackend {
    // Local filesystem implementation
}
```

### 3. Progress Reporting
```kotlin
interface ProgressReporter {
    fun report(progress: DownloadProgress)
}

class ConsoleReporter : ProgressReporter {
    override fun report(progress: DownloadProgress) {
        println("${progress.id}: ${progress.downloadedBytes}/${progress.totalBytes}")
    }
}

class WebhookReporter : ProgressReporter {
    override fun report(progress: DownloadProgress) {
        // Send to webhook
    }
}
```

### 4. CLI Extensions
```kotlin
// Easy to add new personas
object TrikeWget : DownloaderPersona {
    override fun parseArgs(args: Array<String>): DownloadTask {
        // wget-compatible argument parsing
    }
}

object TrikeAxel : DownloaderPersona {
    override fun parseArgs(args: Array<String>): DownloadTask {
        // axel-compatible argument parsing
    }
}
```

## 🎯 Evolution Milestones

### Milestone 1: Real HTTP (Week 1-2)
- [ ] Implement HTTP client with resume support
- [ ] Add proper error handling and retries
- [ ] Support for redirects and authentication
- [ ] Progress reporting with real data

### Milestone 2: Basic BitTorrent (Week 3-4)
- [ ] Torrent file parsing
- [ ] Magnet link support
- [ ] Basic peer wire protocol
- [ ] Piece verification

### Milestone 3: DHT Integration (Week 5-6)
- [ ] Connect to existing DHT networks
- [ ] Peer discovery via DHT
- [ ] Tracker fallback support
- [ ] Distributed hash table operations

### Milestone 4: Advanced Features (Week 7-8)
- [ ] Plugin system for protocols
- [ ] Multiple storage backends
- [ ] Advanced progress reporting
- [ ] Configuration management

### Milestone 5: Performance (Week 9-10)
- [ ] Zero-copy operations
- [ ] Memory-mapped files
- [ ] Connection pooling
- [ ] Async I/O optimization

## 🔄 Continuous Evolution

The foundation is designed for **continuous evolution**:

1. **Backward Compatibility**: Existing personas continue to work
2. **Incremental Features**: Add protocols one at a time
3. **Performance Tuning**: Optimize without breaking interfaces
4. **Community Extensions**: Plugin system enables community contributions

## 🎉 Success Metrics

- **Protocol Coverage**: Support all major download protocols
- **Performance**: Match or exceed aria2c/curl performance
- **Usability**: Maintain familiar command-line interfaces
- **Extensibility**: Enable community plugin development
- **Reliability**: Production-ready error handling and recovery

This foundation provides the **architectural flexibility** to evolve into a comprehensive download solution while maintaining the **simplicity and familiarity** of the original tools. 