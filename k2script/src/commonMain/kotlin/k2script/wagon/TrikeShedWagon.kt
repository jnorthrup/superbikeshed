@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package k2script.wagon

import borg.trikeshed.lib.*
import borg.trikeshed.cursor.*
import borg.trikeshed.io.*
import borg.trikeshed.net.*
import borg.trikeshed.reactor.*
import kotlinx.coroutines.*

/**
 * TrikeShed Wagon - Native dependency fetcher using TrikeShed networking
 * 
 * Implements Maven Wagon protocol using:
 * - trikeshed-net for REST/QUIC transport
 * - trikeshed-cursor for repository metadata
 * - trikeshed-io for efficient file operations
 * - trikeshed-reactor for async coordination
 */

// === Maven Repository Types ===

data class Repository(
    val id: String,
    val url: String,
    val layout: String = "default",
    val protocol: TransportProtocol = TransportProtocol.HTTPS
)

enum class TransportProtocol {
    HTTP, HTTPS, QUIC, FILE
}

data class Artifact(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val classifier: String? = null,
    val type: String = "jar"
) {
    val path: String get() = "${groupId.replace('.', '/')}/$artifactId/$version/$fileName"
    val fileName: String get() = buildString {
        append(artifactId)
        append("-")
        append(version)
        classifier?.let { 
            append("-")
            append(it)
        }
        append(".")
        append(type)
    }
}

// === TrikeShed Network Transport ===

class TrikeShedTransport(
    internal val protocol: TransportProtocol,
    internal val reactor: Reactor = Reactor()
) {
    
    suspend fun fetch(url: String): ByteIndexed = when (protocol) {
        TransportProtocol.HTTP, TransportProtocol.HTTPS -> fetchHttp(url)
        TransportProtocol.QUIC -> fetchQuic(url)
        TransportProtocol.FILE -> fetchFile(url)
    }
    
    internal suspend fun fetchHttp(url: String): ByteIndexed {
        // Use trikeshed-net HTTP client
        val connection = HttpConnection.connect(url)
        val response = connection.get()
        
        return when (response.status) {
            200 -> response.body.toByteIndexed()
            404 -> throw ArtifactNotFoundException("Artifact not found: $url")
            else -> throw TransportException("HTTP ${response.status}: ${response.message}")
        }
    }
    
    internal suspend fun fetchQuic(url: String): ByteIndexed {
        // Use trikeshed-net QUIC client for faster transfers
        val connection = QuicConnection.connect(url)
        val stream = connection.openStream()
        
        val request = "GET ${url.substringAfter("://").substringAfter("/")} HTTP/3.0\r\n\r\n"
        stream.send(request.encodeToByteArray().toByteIndexed())
        
        val response = stream.receive()
        return response.extractBody()
    }
    
    internal suspend fun fetchFile(url: String): ByteIndexed {
        val filePath = url.removePrefix("file://")
        return IOMemento.readBytes(filePath)
    }
}

// === Repository Metadata Cache ===

class RepositoryCache(
    internal val cacheDir: String = "${System.getProperty("user.home")}/.k2script/cache"
) {
    internal val metadataCache = mutableMapOf<String, RepositoryMetadata>()
    
    suspend fun getMetadata(repo: Repository): RepositoryMetadata {
        return metadataCache.getOrPut(repo.id) {
            loadMetadata(repo)
        }
    }
    
    internal suspend fun loadMetadata(repo: Repository): RepositoryMetadata {
        val transport = TrikeShedTransport(repo.protocol)
        val metadataUrl = "${repo.url}/maven-metadata.xml"
        
        val xmlData = try {
            transport.fetch(metadataUrl)
        } catch (e: ArtifactNotFoundException) {
            // No metadata file, create empty metadata
            return RepositoryMetadata(repo, emptyList<String>().toIdx())
        }
        
        // Parse XML metadata using trikeshed-cursor for structured access
        val metadata = parseXmlMetadata(xmlData)
        return RepositoryMetadata(repo, metadata)
    }
    
    internal fun parseXmlMetadata(xmlData: ByteIndexed): Indexed<String> {
        // Simple XML parsing for Maven metadata
        val content = xmlData.toString()
        val versions = mutableListOf<String>()
        
        val versionPattern = """<version>([^<]+)</version>""".toRegex()
        versionPattern.findAll(content).forEach { match ->
            versions.add(match.groupValues[1])
        }
        
        return versions.toTypedArray().toIdx()
    }
}

data class RepositoryMetadata(
    val repository: Repository,
    val availableVersions: Indexed<String>
)

// === TrikeShed Wagon Implementation ===

class TrikeShedWagon(
    internal val repositories: Indexed<Repository>,
    internal val cache: RepositoryCache = RepositoryCache(),
    internal val reactor: Reactor = Reactor()
) {
    
    suspend fun resolve(artifact: Artifact): ByteIndexed {
        // Try each repository until we find the artifact
        for (i in 0 until repositories.a) {
            val repo = repositories.b(i)
            
            try {
                return fetchFromRepository(repo, artifact)
            } catch (e: ArtifactNotFoundException) {
                // Try next repository
                continue
            }
        }
        
        throw ArtifactNotFoundException("Artifact not found in any repository: ${artifact.groupId}:${artifact.artifactId}:${artifact.version}")
    }
    
    internal suspend fun fetchFromRepository(repo: Repository, artifact: Artifact): ByteIndexed {
        val transport = TrikeShedTransport(repo.protocol, reactor)
        val artifactUrl = "${repo.url}/${artifact.path}"
        
        println("🔍 Trying: ${repo.id} -> ${artifact.fileName}")
        
        val data = transport.fetch(artifactUrl)
        
        // Cache the artifact locally
        cacheArtifact(artifact, data)
        
        println("✅ Downloaded: ${artifact.fileName} (${data.a} bytes)")
        return data
    }
    
    internal suspend fun cacheArtifact(artifact: Artifact, data: ByteIndexed) {
        val cacheFile = "${cache.cacheDir}/${artifact.fileName}"
        IOMemento.writeBytes(cacheFile, data)
    }
    
    // Parallel resolution for multiple artifacts
    suspend fun resolveAll(artifacts: Indexed<Artifact>): Indexed<Join<Artifact, ByteIndexed>> {
        return artifacts.a j { i ->
            val artifact = artifacts.b(i)
            val data = resolve(artifact)
            artifact j data
        }
    }
    
    // Check if artifact exists without downloading
    suspend fun exists(artifact: Artifact): Boolean {
        for (i in 0 until repositories.a) {
            val repo = repositories.b(i)
            
            try {
                val transport = TrikeShedTransport(repo.protocol)
                val headUrl = "${repo.url}/${artifact.path}"
                
                // Use HEAD request to check existence
                val connection = HttpConnection.connect(headUrl)
                val response = connection.head()
                
                if (response.status == 200) {
                    return true
                }
            } catch (e: Exception) {
                // Continue to next repository
            }
        }
        
        return false
    }
}

// === Reactor-based Batch Processing ===

class BatchWagon(
    internal val wagon: TrikeShedWagon,
    internal val reactor: Reactor<ResolveResult> = Reactor()
) {
    
    suspend fun resolveBatch(artifacts: Indexed<Artifact>): Indexed<ResolveResult> {
        // Use reactor for concurrent downloads
        val results = mutableListOf<ResolveResult>()
        
        // Create resolution tasks
        val tasks = artifacts.a j { i ->
            val artifact = artifacts.b(i)
            Reactor.spawn(artifact) { a ->
                try {
                    val data = wagon.resolve(a)
                    ResolveResult.Success(a, data)
                } catch (e: Exception) {
                    ResolveResult.Failure(a, e.message ?: "Unknown error")
                }
            }
        }
        
        // Await all tasks
        for (i in 0 until tasks.a) {
            val result = tasks.b(i).await()
            results.add(result)
        }
        
        return results.toTypedArray().toIdx()
    }
}

sealed class ResolveResult {
    data class Success(val artifact: Artifact, val data: ByteIndexed) : ResolveResult()
    data class Failure(val artifact: Artifact, val error: String) : ResolveResult()
}

// === Repository Configuration ===

object DefaultRepositories {
    val mavenCentral = Repository(
        id = "central",
        url = "https://repo1.maven.org/maven2",
        protocol = TransportProtocol.HTTPS
    )
    
    val mavenCentralQuic = Repository(
        id = "central-quic", 
        url = "quic://repo1.maven.org/maven2",
        protocol = TransportProtocol.QUIC
    )
    
    val gradlePlugins = Repository(
        id = "gradle-plugins",
        url = "https://plugins.gradle.org/m2",
        protocol = TransportProtocol.HTTPS
    )
    
    val kotlinEap = Repository(
        id = "kotlin-eap",
        url = "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev",
        protocol = TransportProtocol.HTTPS
    )
    
    val default: Indexed<Repository> = arrayOf(
        mavenCentral,
        gradlePlugins,
        kotlinEap
    ).toIdx()
    
    val withQuic: Indexed<Repository> = arrayOf(
        mavenCentralQuic,  // Try QUIC first for better performance
        mavenCentral,      // Fallback to HTTPS
        gradlePlugins,
        kotlinEap
    ).toIdx()
}

// === Exception Types ===

class ArtifactNotFoundException(message: String) : Exception(message)
class TransportException(message: String) : Exception(message)

// === HTTP/QUIC Connection Stubs (would be implemented in trikeshed-net) ===

// These would actually be implemented in trikeshed-net
data class HttpConnection(val url: String) {
    companion object {
        fun connect(url: String) = HttpConnection(url)
    }
    
    suspend fun get(): HttpResponse = TODO("Implement in trikeshed-net")
    suspend fun head(): HttpResponse = TODO("Implement in trikeshed-net")
}

data class QuicConnection(val url: String) {
    companion object {
        fun connect(url: String) = QuicConnection(url)
    }
    
    suspend fun openStream(): QuicStream = TODO("Implement in trikeshed-net")
}

data class QuicStream(val connection: QuicConnection) {
    suspend fun send(data: ByteIndexed) = TODO("Implement in trikeshed-net")
    suspend fun receive(): ByteIndexed = TODO("Implement in trikeshed-net")
}

data class HttpResponse(
    val status: Int,
    val message: String,
    val body: ByteIndexed
)

// === Extension Functions ===

internal fun ByteIndexed.extractBody(): ByteIndexed {
    // Extract HTTP response body after headers
    val content = this.toString()
    val bodyStart = content.indexOf("\r\n\r\n") + 4
    return if (bodyStart > 3) {
        content.substring(bodyStart).encodeToByteArray().toByteIndexed()
    } else {
        this
    }
}

internal fun ByteArray.toByteIndexed(): ByteIndexed = 
    ByteIndexed(size j { this[it] })

internal fun String.toByteIndexed(): ByteIndexed =
    this.encodeToByteArray().toByteIndexed()