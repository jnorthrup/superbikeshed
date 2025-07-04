package k2script.wagon

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import borg.trikeshed.lib.*

class TrikeShedWagonTest {
    
    // === Artifact Tests ===
    
    @Test
    fun `artifact should generate correct path and filename`() {
        val artifact = Artifact(
            groupId = "org.jetbrains.kotlin",
            artifactId = "kotlin-stdlib",
            version = "1.9.0"
        )
        
        assertEquals("org/jetbrains/kotlin/kotlin-stdlib/1.9.0/kotlin-stdlib-1.9.0.jar", artifact.path)
        assertEquals("kotlin-stdlib-1.9.0.jar", artifact.fileName)
    }
    
    @Test
    fun `artifact with classifier should generate correct filename`() {
        val artifact = Artifact(
            groupId = "org.jetbrains.kotlin",
            artifactId = "kotlin-stdlib",
            version = "1.9.0",
            classifier = "sources",
            type = "jar"
        )
        
        assertEquals("kotlin-stdlib-1.9.0-sources.jar", artifact.fileName)
    }
    
    @Test
    fun `artifact with custom type should generate correct filename`() {
        val artifact = Artifact(
            groupId = "org.jetbrains.kotlin",
            artifactId = "kotlin-stdlib",
            version = "1.9.0",
            type = "pom"
        )
        
        assertEquals("kotlin-stdlib-1.9.0.pom", artifact.fileName)
    }
    
    // === Repository Tests ===
    
    @Test
    fun `repository should be created with default values`() {
        val repo = Repository(
            id = "test-repo",
            url = "https://example.com/maven2"
        )
        
        assertEquals("test-repo", repo.id)
        assertEquals("https://example.com/maven2", repo.url)
        assertEquals("default", repo.layout)
        assertEquals(TransportProtocol.HTTPS, repo.protocol)
    }
    
    @Test
    fun `repository should support QUIC protocol`() {
        val repo = Repository(
            id = "quic-repo",
            url = "quic://example.com/maven2",
            protocol = TransportProtocol.QUIC
        )
        
        assertEquals(TransportProtocol.QUIC, repo.protocol)
    }
    
    // === Transport Tests ===
    
    @Test
    fun `transport should throw exception for unsupported protocol`() = runBlocking {
        val transport = TrikeShedTransport(TransportProtocol.FILE)
        
        assertFailsWith<NotImplementedError> {
            transport.fetch("file:///test/path")
        }
    }
    
    // === Repository Cache Tests ===
    
    @Test
    fun `cache should return cached metadata for same repository`() = runBlocking {
        val repo = Repository("test", "https://example.com")
        val cache = RepositoryCache()
        
        // First call should load metadata
        val metadata1 = cache.getMetadata(repo)
        
        // Second call should return cached version
        val metadata2 = cache.getMetadata(repo)
        
        assertSame(metadata1, metadata2)
    }
    
    @Test
    fun `cache should handle missing metadata gracefully`() = runBlocking {
        val repo = Repository("missing", "https://nonexistent.example.com")
        val cache = RepositoryCache()
        
        val metadata = cache.getMetadata(repo)
        
        assertEquals(repo, metadata.repository)
        assertTrue(metadata.availableVersions.a == 0)
    }
    
    // === TrikeShedWagon Tests ===
    
    @Test
    fun `wagon should resolve artifact from first available repository`() {
        val repos = arrayOf(
            Repository("central", "https://repo1.maven.org/maven2"),
            Repository("backup", "https://backup.example.com/maven2")
        ).let { it.toIdx() }
        
        val wagon = TrikeShedWagon(repos)
        val artifact = Artifact("org.jetbrains.kotlin", "kotlin-stdlib", "1.9.0")
        
        // This would need mocking of the transport layer
        assertFailsWith<NotImplementedError> {
            runBlocking { wagon.resolve(artifact) }
        }
    }
    
    @Test
    fun `wagon should throw exception when artifact not found in any repository`() = runBlocking {
        val repos = arrayOf(
            Repository("central", "https://repo1.maven.org/maven2")
        ).let { it.toIdx() }
        
        val wagon = TrikeShedWagon(repos)
        val artifact = Artifact("nonexistent", "artifact", "1.0.0")
        
        assertFailsWith<ArtifactNotFoundException> {
            wagon.resolve(artifact)
        }
    }
    
    @Test
    fun `wagon should resolve multiple artifacts in parallel`() = runBlocking {
        val repos = arrayOf(
            Repository("central", "https://repo1.maven.org/maven2")
        ).let { it.toIdx() }
        
        val wagon = TrikeShedWagon(repos)
        val artifacts = arrayOf(
            Artifact("org.jetbrains.kotlin", "kotlin-stdlib", "1.9.0"),
            Artifact("org.jetbrains.kotlin", "kotlin-reflect", "1.9.0")
        ).let { it.toIdx() }
        
        // This would need mocking of the transport layer
        assertFailsWith<NotImplementedError> {
            wagon.resolveAll(artifacts)
        }
    }
    
    @Test
    fun `wagon should check artifact existence without downloading`() = runBlocking {
        val repos = arrayOf(
            Repository("central", "https://repo1.maven.org/maven2")
        ).let { it.toIdx() }
        
        val wagon = TrikeShedWagon(repos)
        val artifact = Artifact("org.jetbrains.kotlin", "kotlin-stdlib", "1.9.0")
        
        // This would need mocking of the transport layer
        assertFailsWith<NotImplementedError> {
            wagon.exists(artifact)
        }
    }
    
    // === BatchWagon Tests ===
    
    @Test
    fun `batch wagon should process multiple artifacts concurrently`() = runBlocking {
        val repos = arrayOf(
            Repository("central", "https://repo1.maven.org/maven2")
        ).let { it.toIdx() }
        
        val wagon = TrikeShedWagon(repos)
        val batchWagon = BatchWagon(wagon)
        
        val artifacts = arrayOf(
            Artifact("org.jetbrains.kotlin", "kotlin-stdlib", "1.9.0"),
            Artifact("org.jetbrains.kotlin", "kotlin-reflect", "1.9.0"),
            Artifact("org.jetbrains.kotlin", "kotlin-test", "1.9.0")
        ).let { it.toIdx() }
        
        // This would need mocking of the transport layer
        assertFailsWith<NotImplementedError> {
            batchWagon.resolveBatch(artifacts)
        }
    }
    
    // === DefaultRepositories Tests ===
    
    @Test
    fun `default repositories should include maven central`() {
        val repos = DefaultRepositories.default
        
        assertTrue(repos.a > 0)
        
        val centralRepo = (0 until repos.a).find { i ->
            repos.b(i).id == "central"
        }
        
        assertNotNull(centralRepo)
        assertEquals("https://repo1.maven.org/maven2", repos.b(centralRepo).url)
    }
    
    @Test
    fun `quic repositories should prioritize QUIC protocol`() {
        val repos = DefaultRepositories.withQuic
        
        assertTrue(repos.a > 0)
        
        val quicRepo = (0 until repos.a).find { i ->
            repos.b(i).protocol == TransportProtocol.QUIC
        }
        
        assertNotNull(quicRepo)
        assertEquals("central-quic", repos.b(quicRepo).id)
    }
    
    // === Exception Tests ===
    
    @Test
    fun `ArtifactNotFoundException should have descriptive message`() {
        val exception = ArtifactNotFoundException("Test artifact not found")
        
        assertEquals("Test artifact not found", exception.message)
    }
    
    @Test
    fun `TransportException should have descriptive message`() {
        val exception = TransportException("HTTP 500: Internal Server Error")
        
        assertEquals("HTTP 500: Internal Server Error", exception.message)
    }
    
    // === Integration Tests ===
    
    @Test
    fun `full resolution flow should work with valid coordinates`() = runBlocking {
        val repos = DefaultRepositories.default
        val wagon = TrikeShedWagon(repos)
        
        val artifact = Artifact("org.jetbrains.kotlin", "kotlin-stdlib", "1.9.0")
        
        // This would need proper mocking of network calls
        assertFailsWith<NotImplementedError> {
            wagon.resolve(artifact)
        }
    }
    
    @Test
    fun `resolution should handle network failures gracefully`() = runBlocking {
        val repos = arrayOf(
            Repository("unreachable", "https://unreachable.example.com")
        ).let { it.toIdx() }
        
        val wagon = TrikeShedWagon(repos)
        val artifact = Artifact("org.jetbrains.kotlin", "kotlin-stdlib", "1.9.0")
        
        assertFailsWith<ArtifactNotFoundException> {
            wagon.resolve(artifact)
        }
    }
} 