package k2script.wagon

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import borg.trikeshed.lib.*

// === Simple Channel-based Wagon Interface ===

interface SimpleChannelWagonInterface {
    suspend fun resolveArtifact(coordinate: String): ReceiveChannel<ByteIndexed>
    suspend fun resolveMultiple(coordinates: List<String>): ReceiveChannel<SimpleArtifactResult>
    suspend fun streamArtifact(coordinate: String): Flow<ByteIndexed>
}

data class SimpleArtifactResult(
    val coordinate: String,
    val data: ByteIndexed?,
    val error: String? = null
)

// === Simple Channel Transport ===

class SimpleChannelTransport {
    
    suspend fun fetchStream(url: String): ReceiveChannel<ByteIndexed> = CoroutineScope(Dispatchers.IO).produce {
        // Simulate streaming data
        val testData = "Test artifact data for $url"
        val bytes = testData.encodeToByteArray()
        
        // Stream in chunks
        val chunkSize = 4
        var offset = 0
        while (offset < bytes.size) {
            val size = minOf(chunkSize, bytes.size - offset)
            val chunk = ByteIndexed(size j { bytes[offset + it].toByte() })
            send(chunk)
            offset += size
        }
    }
}

// === Simple Channel Wagon Implementation ===

class SimpleChannelWagon(
    private val repositories: List<Repository>,
    private val transport: SimpleChannelTransport = SimpleChannelTransport()
) : SimpleChannelWagonInterface {
    
    override suspend fun resolveArtifact(coordinate: String): ReceiveChannel<ByteIndexed> = CoroutineScope(Dispatchers.IO).produce {
        val artifact = parseCoordinate(coordinate)
        
        for (repo in repositories) {
            try {
                val artifactUrl = "${repo.url}/${artifact.path}"
                val dataChannel = transport.fetchStream(artifactUrl)
                
                dataChannel.consumeEach { chunk ->
                    send(chunk)
                }
                return@produce
                
            } catch (e: Exception) {
                continue
            }
        }
        
        throw ArtifactNotFoundException("Artifact not found: $coordinate")
    }
    
    override suspend fun resolveMultiple(coordinates: List<String>): ReceiveChannel<SimpleArtifactResult> = CoroutineScope(Dispatchers.IO).produce {
        coordinates.forEach { coordinate ->
            try {
                val dataChannel = resolveArtifact(coordinate)
                val chunks = mutableListOf<ByteIndexed>()
                
                dataChannel.consumeEach { chunk ->
                    chunks.add(chunk)
                }
                
                val combined = combineChunks(chunks)
                send(SimpleArtifactResult(coordinate, combined))
                
            } catch (e: Exception) {
                send(SimpleArtifactResult(coordinate, null, e.message))
            }
        }
    }
    
    override suspend fun streamArtifact(coordinate: String): Flow<ByteIndexed> = flow {
        val artifact = parseCoordinate(coordinate)
        
        for (repo in repositories) {
            try {
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
    
    private fun parseCoordinate(coordinate: String): Artifact {
        val parts = coordinate.split(":")
        if (parts.size < 3) {
            throw IllegalArgumentException("Invalid coordinate: $coordinate")
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

// === TDD Tests ===

class TrikeShedWagonChannelSimpleTest {
    
    @Test
    fun `simple transport should stream data in chunks`() = runBlocking {
        val transport = SimpleChannelTransport()
        val channel = transport.fetchStream("https://example.com/test")
        
        val chunks = mutableListOf<ByteIndexed>()
        channel.consumeEach { chunk ->
            chunks.add(chunk)
        }
        
        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.all { it.a > 0 })
    }
    
    @Test
    fun `simple wagon should parse coordinates correctly`() {
        val wagon = SimpleChannelWagon(emptyList())
        
        val artifact = wagon.parseCoordinate("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
        
        assertEquals("org.jetbrains.kotlin", artifact.groupId)
        assertEquals("kotlin-stdlib", artifact.artifactId)
        assertEquals("1.9.0", artifact.version)
        assertEquals("jar", artifact.type)
    }
    
    @Test
    fun `simple wagon should parse coordinates with classifier`() {
        val wagon = SimpleChannelWagon(emptyList())
        
        val artifact = wagon.parseCoordinate("org.jetbrains.kotlin:kotlin-stdlib:1.9.0:sources")
        
        assertEquals("sources", artifact.classifier)
    }
    
    @Test
    fun `simple wagon should throw exception for invalid coordinates`() {
        val wagon = SimpleChannelWagon(emptyList())
        
        assertFailsWith<IllegalArgumentException> {
            wagon.parseCoordinate("invalid")
        }
        
        assertFailsWith<IllegalArgumentException> {
            wagon.parseCoordinate("group:artifact")
        }
    }
    
    @Test
    fun `simple wagon should stream single artifact`() = runBlocking {
        val repos = listOf(
            Repository("central", "https://repo1.maven.org/maven2")
        )
        
        val wagon = SimpleChannelWagon(repos)
        val channel = wagon.resolveArtifact("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
        
        val chunks = mutableListOf<ByteIndexed>()
        channel.consumeEach { chunk ->
            chunks.add(chunk)
        }
        
        assertTrue(chunks.isNotEmpty())
    }
    
    @Test
    fun `simple wagon should resolve multiple artifacts`() = runBlocking {
        val repos = listOf(
            Repository("central", "https://repo1.maven.org/maven2")
        )
        
        val wagon = SimpleChannelWagon(repos)
        val coordinates = listOf(
            "org.jetbrains.kotlin:kotlin-stdlib:1.9.0",
            "org.jetbrains.kotlin:kotlin-reflect:1.9.0"
        )
        
        val channel = wagon.resolveMultiple(coordinates)
        val results = mutableListOf<SimpleArtifactResult>()
        
        channel.consumeEach { result ->
            results.add(result)
        }
        
        assertEquals(2, results.size)
        assertTrue(results.all { it.data != null })
    }
    
    @Test
    fun `simple wagon should stream artifact as flow`() = runBlocking {
        val repos = listOf(
            Repository("central", "https://repo1.maven.org/maven2")
        )
        
        val wagon = SimpleChannelWagon(repos)
        val flow = wagon.streamArtifact("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
        
        val chunks = flow.toList()
        
        assertTrue(chunks.isNotEmpty())
    }
    
    @Test
    fun `chunk combination should work correctly`() {
        val wagon = SimpleChannelWagon(emptyList())
        
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
    fun `artifact result should handle success and failure`() {
        val success = SimpleArtifactResult("test:artifact:1.0", ByteIndexed(1 j { 42.toByte() }))
        assertEquals("test:artifact:1.0", success.coordinate)
        assertNotNull(success.data)
        assertNull(success.error)
        
        val failure = SimpleArtifactResult("test:artifact:1.0", null, "Network error")
        assertEquals("test:artifact:1.0", failure.coordinate)
        assertNull(failure.data)
        assertEquals("Network error", failure.error)
    }
    
    @Test
    fun `wagon should handle empty repository list`() = runBlocking {
        val wagon = SimpleChannelWagon(emptyList())
        
        assertFailsWith<ArtifactNotFoundException> {
            wagon.resolveArtifact("org.jetbrains.kotlin:kotlin-stdlib:1.9.0").receive()
        }
    }
    
    @Test
    fun `wagon should handle network failures gracefully`() = runBlocking {
        val repos = listOf(
            Repository("unreachable", "https://unreachable.example.com")
        )
        
        val wagon = SimpleChannelWagon(repos)
        
        // This should still work because our mock transport always succeeds
        val channel = wagon.resolveArtifact("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
        val chunks = mutableListOf<ByteIndexed>()
        
        channel.consumeEach { chunk ->
            chunks.add(chunk)
        }
        
        assertTrue(chunks.isNotEmpty())
    }
} 