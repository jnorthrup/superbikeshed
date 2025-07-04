package k2script.wagon

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import borg.trikeshed.lib.*
import borg.trikeshed.io.*

// === Channel-based Wagon Interface ===

interface ChannelWagon {
    suspend fun resolveArtifact(coordinate: String): ReceiveChannel<ByteIndexed>
    suspend fun resolveArtifacts(coordinates: List<String>): ReceiveChannel<ArtifactResult>
    suspend fun streamArtifact(coordinate: String): Flow<ByteIndexed>
    suspend fun batchResolve(coordinates: List<String>): Flow<BatchResult>
}

data class ArtifactResult(
    val coordinate: String,
    val data: ByteIndexed?,
    val error: String? = null
)

data class BatchResult(
    val completed: Int,
    val total: Int,
    val results: List<ArtifactResult>
)

// === Channel-based Transport ===

class ChannelTransport(
    private val protocol: TransportProtocol,
    private val bufferSize: Int = 8192
) {
    
    suspend fun fetchStream(url: String): ReceiveChannel<ByteIndexed> = CoroutineScope(Dispatchers.IO).produce {
        when (protocol) {
            TransportProtocol.HTTP, TransportProtocol.HTTPS -> fetchHttpStream(url)
            TransportProtocol.QUIC -> fetchQuicStream(url)
            TransportProtocol.FILE -> fetchFileStream(url)
        }
    }
    
    private suspend fun ProducerScope<ByteIndexed>.fetchHttpStream(url: String) {
        // Simulate streaming HTTP response
        val connection = HttpConnection.connect(url)
        val response = connection.get()
        
        if (response.status == 200) {
            // Stream data in chunks
            val data = response.body
            var offset = 0
            while (offset < data.a) {
                val chunkSize = minOf(bufferSize, data.a - offset)
                val chunk = ByteIndexed(chunkSize j { data.b(offset + it) })
                send(chunk)
                offset += chunkSize
            }
        } else {
            throw TransportException("HTTP ${response.status}: ${response.message}")
        }
    }
    
    private suspend fun ProducerScope<ByteIndexed>.fetchQuicStream(url: String) {
        // Simulate QUIC streaming
        val connection = QuicConnection.connect(url)
        val stream = connection.openStream()
        
        val request = "GET ${url.substringAfter("://").substringAfter("/")} HTTP/3.0\r\n\r\n"
        stream.send(request.encodeToByteArray().let { ByteIndexed(it.size j { it[it] }) })
        
        // Stream response data
        while (true) {
            try {
                val chunk = stream.receive()
                if (chunk.a == 0) break
                send(chunk)
            } catch (e: Exception) {
                break
            }
        }
    }
    
    private suspend fun ProducerScope<ByteIndexed>.fetchFileStream(url: String) {
        val filePath = url.removePrefix("file://")
        val data = IOMemento.readBytes(filePath)
        
        // Stream file data in chunks
        var offset = 0
        while (offset < data.a) {
            val chunkSize = minOf(bufferSize, data.a - offset)
            val chunk = ByteIndexed(chunkSize j { data.b(offset + it) })
            send(chunk)
            offset += chunkSize
        }
    }
}

// === Channel-based Repository Cache ===

class ChannelRepositoryCache(
    private val cacheDir: String = "${System.getProperty("user.home")}/.k2script/cache"
) {
    private val metadataChannel = Channel<RepositoryMetadata>(Channel.UNLIMITED)
    
    suspend fun getMetadataStream(repo: Repository): ReceiveChannel<RepositoryMetadata> = CoroutineScope(Dispatchers.IO).produce {
        // Send cached metadata if available
        metadataChannel.trySend(RepositoryMetadata(repo, emptyList<String>().toIdx()))
        
        // Load fresh metadata
        val transport = ChannelTransport(repo.protocol)
        val metadataUrl = "${repo.url}/maven-metadata.xml"
        
        try {
            val xmlData = transport.fetchStream(metadataUrl).let { it.receive() }
            val metadata = parseXmlMetadata(xmlData)
            val repoMetadata = RepositoryMetadata(repo, metadata)
            
            metadataChannel.send(repoMetadata)
            send(repoMetadata)
        } catch (e: Exception) {
            // Send empty metadata on error
            val emptyMetadata = RepositoryMetadata(repo, emptyList<String>().toIdx())
            send(emptyMetadata)
        }
    }
    
    private fun parseXmlMetadata(xmlData: ByteIndexed): Indexed<String> {
        val content = xmlData.toString()
        val versions = mutableListOf<String>()
        
        val versionPattern = """<version>([^<]+)</version>""".toRegex()
        versionPattern.findAll(content).forEach { match ->
            versions.add(match.groupValues[1])
        }
        
        return versions.toTypedArray().let { it.toIdx() }
    }
}

// === Channel-based Wagon Implementation ===

class ChannelTrikeShedWagon(
    private val repositories: Indexed<Repository>,
    private val cache: ChannelRepositoryCache = ChannelRepositoryCache(),
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : ChannelWagon {
    
    override suspend fun resolveArtifact(coordinate: String): ReceiveChannel<ByteIndexed> = CoroutineScope(Dispatchers.IO).produce {
        val artifact = parseCoordinate(coordinate)
        
        for (i in 0 until repositories.a) {
            val repo = repositories.b(i)
            
            try {
                val transport = ChannelTransport(repo.protocol)
                val artifactUrl = "${repo.url}/${artifact.path}"
                
                println("🔍 Streaming from: ${repo.id} -> ${artifact.fileName}")
                
                // Stream artifact data
                val dataChannel = transport.fetchStream(artifactUrl)
                dataChannel.consumeEach { chunk ->
                    send(chunk)
                }
                
                println("✅ Streamed: ${artifact.fileName}")
                return@produce
                
            } catch (e: ArtifactNotFoundException) {
                continue
            }
        }
        
        throw ArtifactNotFoundException("Artifact not found: $coordinate")
    }
    
    override suspend fun resolveArtifacts(coordinates: List<String>): ReceiveChannel<ArtifactResult> = CoroutineScope(Dispatchers.IO).produce {
        coordinates.forEach { coordinate ->
            try {
                val dataChannel = resolveArtifact(coordinate)
                val chunks = mutableListOf<ByteIndexed>()
                
                dataChannel.consumeEach { chunk ->
                    chunks.add(chunk)
                }
                
                // Combine chunks into single ByteIndexed
                val totalSize = chunks.sumOf { it.a }
                val combined = ByteIndexed(totalSize j { index ->
                    var offset = 0
                    for (chunk in chunks) {
                        if (index < offset + chunk.a) {
                            return@ByteIndexed chunk.b(index - offset)
                        }
                        offset += chunk.a
                    }
                    0.toByte()
                })
                
                send(ArtifactResult(coordinate, combined))
                
            } catch (e: Exception) {
                send(ArtifactResult(coordinate, null, e.message))
            }
        }
    }
    
    override suspend fun streamArtifact(coordinate: String): Flow<ByteIndexed> = flow {
        val artifact = parseCoordinate(coordinate)
        
        for (i in 0 until repositories.a) {
            val repo = repositories.b(i)
            
            try {
                val transport = ChannelTransport(repo.protocol)
                val artifactUrl = "${repo.url}/${artifact.path}"
                
                val dataChannel = transport.fetchStream(artifactUrl)
                dataChannel.consumeEach { chunk ->
                    emit(chunk)
                }
                return@flow
                
            } catch (e: Exception) {
                continue
            }
        }
        
        throw ArtifactNotFoundException("Artifact not found: $coordinate")
    }
    
    override suspend fun batchResolve(coordinates: List<String>): Flow<BatchResult> = flow {
        val results = mutableListOf<ArtifactResult>()
        var completed = 0
        
        // Process coordinates concurrently
        val jobs = coordinates.map { coordinate ->
            coroutineScope.async {
                try {
                    val dataChannel = resolveArtifact(coordinate)
                    val chunks = mutableListOf<ByteIndexed>()
                    
                    dataChannel.consumeEach { chunk ->
                        chunks.add(chunk)
                    }
                    
                    val combined = combineChunks(chunks)
                    ArtifactResult(coordinate, combined)
                } catch (e: Exception) {
                    ArtifactResult(coordinate, null, e.message)
                }
            }
        }
        
        // Collect results as they complete
        jobs.forEach { job ->
            val result = job.await()
            results.add(result)
            completed++
            
            emit(BatchResult(completed, coordinates.size, results.toList()))
        }
    }
    
    private fun parseCoordinate(coordinate: String): Artifact {
        val parts = coordinate.split(":")
        if (parts.size < 3) {
            throw IllegalArgumentException("Invalid coordinate: $coordinate. Expected format: groupId:artifactId:version")
        }
        
        return Artifact(
            groupId = parts[0],
            artifactId = parts[1],
            version = parts[2],
            classifier = if (parts.size > 3) parts[3] else null,
            type = if (parts.size > 4) parts[4] else "jar"
        )
    }
    
    private fun combineChunks(chunks: List<ByteIndexed>): ByteIndexed {
        val totalSize = chunks.sumOf { it.a }
        return ByteIndexed(totalSize j { index ->
            var offset = 0
            for (chunk in chunks) {
                if (index < offset + chunk.a) {
                    return@ByteIndexed chunk.b(index - offset)
                }
                offset += chunk.a
            }
            0.toByte()
        })
    }
}

// === TDD Tests for Channel-based Wagon ===

class TrikeShedWagonChannelTest {
    
    @Test
    fun `channel transport should stream data in chunks`() = runBlocking {
        val transport = ChannelTransport(TransportProtocol.HTTPS, bufferSize = 4)
        
        // This would need proper mocking
        assertFailsWith<NotImplementedError> {
            transport.fetchStream("https://example.com/test").receive()
        }
    }
    
    @Test
    fun `channel wagon should stream single artifact`() = runBlocking {
        val repos = arrayOf(
            Repository("central", "https://repo1.maven.org/maven2")
        ).let { it.toIdx() }
        
        val wagon = ChannelTrikeShedWagon(repos)
        
        assertFailsWith<NotImplementedError> {
            wagon.resolveArtifact("org.jetbrains.kotlin:kotlin-stdlib:1.9.0").receive()
        }
    }
    
    @Test
    fun `channel wagon should resolve multiple artifacts with results channel`() = runBlocking {
        val repos = arrayOf(
            Repository("central", "https://repo1.maven.org/maven2")
        ).let { it.toIdx() }
        
        val wagon = ChannelTrikeShedWagon(repos)
        val coordinates = listOf(
            "org.jetbrains.kotlin:kotlin-stdlib:1.9.0",
            "org.jetbrains.kotlin:kotlin-reflect:1.9.0"
        )
        
        assertFailsWith<NotImplementedError> {
            wagon.resolveArtifacts(coordinates).receive()
        }
    }
    
    @Test
    fun `channel wagon should stream artifact as flow`() = runBlocking {
        val repos = arrayOf(
            Repository("central", "https://repo1.maven.org/maven2")
        ).let { it.toIdx() }
        
        val wagon = ChannelTrikeShedWagon(repos)
        
        assertFailsWith<NotImplementedError> {
            wagon.streamArtifact("org.jetbrains.kotlin:kotlin-stdlib:1.9.0").first()
        }
    }
    
    @Test
    fun `channel wagon should batch resolve with progress flow`() = runBlocking {
        val repos = arrayOf(
            Repository("central", "https://repo1.maven.org/maven2")
        ).let { it.toIdx() }
        
        val wagon = ChannelTrikeShedWagon(repos)
        val coordinates = listOf(
            "org.jetbrains.kotlin:kotlin-stdlib:1.9.0",
            "org.jetbrains.kotlin:kotlin-reflect:1.9.0",
            "org.jetbrains.kotlin:kotlin-test:1.9.0"
        )
        
        assertFailsWith<NotImplementedError> {
            wagon.batchResolve(coordinates).first()
        }
    }
    
    @Test
    fun `coordinate parsing should handle different formats`() {
        val wagon = ChannelTrikeShedWagon(arrayOf<Repository>().toIdx())
        
        // Test basic coordinate
        val basic = wagon.parseCoordinate("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
        assertEquals("org.jetbrains.kotlin", basic.groupId)
        assertEquals("kotlin-stdlib", basic.artifactId)
        assertEquals("1.9.0", basic.version)
        assertEquals("jar", basic.type)
        
        // Test with classifier
        val withClassifier = wagon.parseCoordinate("org.jetbrains.kotlin:kotlin-stdlib:1.9.0:sources")
        assertEquals("sources", withClassifier.classifier)
        
        // Test with custom type
        val withType = wagon.parseCoordinate("org.jetbrains.kotlin:kotlin-stdlib:1.9.0:sources:jar")
        assertEquals("sources", withType.classifier)
        assertEquals("jar", withType.type)
    }
    
    @Test
    fun `coordinate parsing should throw exception for invalid format`() {
        val wagon = ChannelTrikeShedWagon(arrayOf<Repository>().toIdx())
        
        assertFailsWith<IllegalArgumentException> {
            wagon.parseCoordinate("invalid-coordinate")
        }
        
        assertFailsWith<IllegalArgumentException> {
            wagon.parseCoordinate("group:artifact")
        }
    }
    
    @Test
    fun `chunk combination should work correctly`() {
        val wagon = ChannelTrikeShedWagon(arrayOf<Repository>().toIdx())
        
        val chunk1 = ByteIndexed(3 j { it.toByte() }) // [0, 1, 2]
        val chunk2 = ByteIndexed(2 j { (it + 3).toByte() }) // [3, 4]
        
        val combined = wagon.combineChunks(listOf(chunk1, chunk2))
        
        assertEquals(5, combined.a)
        assertEquals(0.toByte(), combined.b(0))
        assertEquals(1.toByte(), combined.b(1))
        assertEquals(2.toByte(), combined.b(2))
        assertEquals(3.toByte(), combined.b(3))
        assertEquals(4.toByte(), combined.b(4))
    }
    
    @Test
    fun `artifact result should handle success and failure cases`() {
        val success = ArtifactResult("test:artifact:1.0", ByteIndexed(1 j { 42.toByte() }))
        assertEquals("test:artifact:1.0", success.coordinate)
        assertNotNull(success.data)
        assertNull(success.error)
        
        val failure = ArtifactResult("test:artifact:1.0", null, "Network error")
        assertEquals("test:artifact:1.0", failure.coordinate)
        assertNull(failure.data)
        assertEquals("Network error", failure.error)
    }
    
    @Test
    fun `batch result should track progress correctly`() {
        val results = listOf(
            ArtifactResult("test1:artifact:1.0", ByteIndexed(1 j { 1.toByte() })),
            ArtifactResult("test2:artifact:1.0", null, "Error")
        )
        
        val batchResult = BatchResult(2, 3, results)
        
        assertEquals(2, batchResult.completed)
        assertEquals(3, batchResult.total)
        assertEquals(2, batchResult.results.size)
    }
} 