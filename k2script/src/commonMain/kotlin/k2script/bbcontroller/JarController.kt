@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package k2script.bbcontroller

import borg.trikeshed.lib.*
import borg.trikeshed.cursor.*
import borg.trikeshed.io.*
import borg.trikeshed.net.*
import borg.trikeshed.reactor.*
import borg.trikeshed.services.*
import kotlinx.coroutines.*
import kotlinx.datetime.*
import borg.trikeshed.lib.platform.PlatformDetection
import borg.trikeshed.lib.platform.OperatingSystem
import k2script.platform.Environment
import borg.trikeshed.io.*

/**
 * BBCursor JAR Dependency Controller
 * 
 * Intelligent bandwidth management and perfect caching for JAR dependencies.
 * Uses BBCursive patterns for annotation parsing and service orchestration.
 */

// === Dependency Annotation Types ===

data class DependencyAnnotation(
    val coordinate: String,
    val repository: String?,
    val scope: DependencyScope = DependencyScope.COMPILE,
    val transitive: Boolean = true,
    val optional: Boolean = false
)

enum class DependencyScope {
    COMPILE, RUNTIME, TEST, PROVIDED
}

data class RepositoryAnnotation(
    val id: String,
    val url: String,
    val layout: String = "default",
    val priority: Int = 50
)

// === BBCursive Annotation Parser ===

object AnnotationBBParser {
    
    fun parseFileAnnotations(content: ByteArray): AnnotationSet {
        val dependencies = mutableListOf<DependencyAnnotation>()
        val repositories = mutableListOf<RepositoryAnnotation>()
        
        // Convert to string for regex parsing
        val text = content.decodeToString()
        
        // Parse @file:DependsOn annotations
        val depPattern = """@file:DependsOn\("([^"]+)"\)""".toRegex()
        depPattern.findAll(text).forEach { match ->
            dependencies.add(DependencyAnnotation(
                coordinate = match.groupValues[1],
                repository = null
            ))
        }
        
        // Parse @file:Repository annotations  
        val repoPattern = """@file:Repository\("([^"]+)"\)""".toRegex()
        repoPattern.findAll(text).forEach { match ->
            repositories.add(RepositoryAnnotation(
                id = "script-repo-${repositories.size}",
                url = match.groupValues[1]
            ))
        }
        
        return AnnotationSet(
            dependencies = \1 j { \2: Int -> dependencies[i] },
            repositories = \1 j { \2: Int -> repositories[i] },
            sourceFile = "parsed"
        )
    }
}

data class AnnotationSet(
    val dependencies: Indexed<DependencyAnnotation>,
    val repositories: Indexed<RepositoryAnnotation>,
    val sourceFile: String
)

// === Bandwidth Management ===

data class BandwidthProfile(
    val name: String,
    val maxConcurrent: Int,
    val maxBytesPerSecond: Long,
    val retryBackoff: Long = 1000L,
    val maxRetries: Int = 3
) {
    companion object {
        val FAST_NETWORK = BandwidthProfile("fast", 8, 10_000_000L) // 10MB/s
        val NORMAL_NETWORK = BandwidthProfile("normal", 4, 2_000_000L) // 2MB/s  
        val SLOW_NETWORK = BandwidthProfile("slow", 2, 500_000L) // 500KB/s
        val MOBILE_NETWORK = BandwidthProfile("mobile", 1, 100_000L) // 100KB/s
    }
}

class IntelligentBandwidthManager(
    internal val reactor: Reactor<Any> = Reactor<Any>()
) {
    internal var currentProfile = BandwidthProfile.NORMAL_NETWORK
    internal val activeDownloads = mutableSetOf<String>()
    internal var lastSpeedTest = 0L
    internal var measuredSpeed = 0L
    
    suspend fun selectOptimalProfile(): BandwidthProfile {
        val now = Clock.System.now().toEpochMilliseconds()
        
        // Test speed every 5 minutes
        if (now - lastSpeedTest > 300_000L) {
            measuredSpeed = measureNetworkSpeed()
            lastSpeedTest = now
            
            currentProfile = when {
                measuredSpeed > 5_000_000L -> BandwidthProfile.FAST_NETWORK
                measuredSpeed > 1_000_000L -> BandwidthProfile.NORMAL_NETWORK
                measuredSpeed > 200_000L -> BandwidthProfile.SLOW_NETWORK
                else -> BandwidthProfile.MOBILE_NETWORK
            }
            
            println("📡 Network speed: ${measuredSpeed / 1_000_000L}MB/s, profile: ${currentProfile.name}")
        }
        
        return currentProfile
    }
    
    internal suspend fun measureNetworkSpeed(): Long {
        return try {
            val testUrl = "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/1.9.24/kotlin-stdlib-1.9.24.pom"
            val start = Clock.System.now().toEpochMilliseconds()
            
            // Fetch small file to test speed
            val transport = NetworkTransport()
            val data = transport.fetch(testUrl)
            
            val elapsed = Clock.System.now().toEpochMilliseconds() - start
            val bytesPerMs = data.component1().toDouble() / elapsed.coerceAtLeast(1)
            (bytesPerMs * 1000).toLong() // bytes per second
        } catch (e: Exception) {
            1_000_000L // Default to 1MB/s on error
        }
    }
    
    suspend fun throttledDownload(url: String, onProgress: (Long, Long) -> Unit = { _, _ -> }): ByteIndexed {
        val profile = selectOptimalProfile()
        
        // Wait if too many concurrent downloads
        while (activeDownloads.size >= profile.maxConcurrent) {
            delay(100)
        }
        
        activeDownloads.add(url)
        
        return try {
            downloadWithThrottling(url, profile, onProgress)
        } finally {
            activeDownloads.remove(url)
        }
    }
    
    internal suspend fun downloadWithThrottling(
        url: String, 
        profile: BandwidthProfile,
        onProgress: (Long, Long) -> Unit
    ): ByteIndexed {
        val transport = NetworkTransport()
        var retries = 0
        
        while (retries <= profile.maxRetries) {
            try {
                return transport.fetchWithProgress(url, profile.maxBytesPerSecond, onProgress)
            } catch (e: Exception) {
                retries++
                if (retries > profile.maxRetries) throw e
                
                val backoff = profile.retryBackoff * retries
                println("⚠️  Retry $retries/$retries for $url in ${backoff}ms")
                delay(backoff)
            }
        }
        
        throw RuntimeException("Max retries exceeded for $url")
    }
}

// === Perfect Caching System ===

data class CacheEntry(
    val key: String,
    val data: ByteIndexed,
    val timestamp: Long,
    val etag: String?,
    val size: Long,
    val accessCount: Int = 0,
    val lastAccess: Long = Clock.System.now().toEpochMilliseconds()
)

data class CacheStats(
    val totalEntries: Int,
    val totalSizeBytes: Long,
    val hitRate: Double,
    val oldestEntry: Long,
    val newestEntry: Long
)

class PerfectCacheManager(
    internal val cacheDir: String = "${Environment.userHome}/.k2script/perfect-cache",
    internal val maxSizeBytes: Long = 2_000_000_000L, // 2GB
    internal val maxAgeMs: Long = 7 * 24 * 60 * 60 * 1000L // 7 days
) {
    internal val index = mutableMapOf<String, CacheEntry>()
    internal var hits = 0L
    internal var misses = 0L
    
    init {
        loadCacheIndex()
    }
    
    suspend fun get(key: String): ByteIndexed? {
        val entry = index[key]
        
        if (entry == null) {
            misses++
            return null
        }
        
        // Check if expired
        if (Clock.System.now().toEpochMilliseconds() - entry.timestamp > maxAgeMs) {
            remove(key)
            misses++
            return null
        }
        
        // Update access stats
        index[key] = entry.copy(
            accessCount = entry.accessCount + 1,
            lastAccess = Clock.System.now().toEpochMilliseconds()
        )
        
        hits++
        return loadFromDisk(key) ?: run {
            remove(key)
            misses++
            null
        }
    }
    
    suspend fun put(key: String, data: ByteIndexed, etag: String? = null) {
        // Clean up if needed before adding
        ensureSpace(data.component1().toLong())
        
        val entry = CacheEntry(
            key = key,
            data = data,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            etag = etag,
            size = data.component1().toLong()
        )
        
        // Save to disk
        saveToDisk(key, data)
        
        // Update index
        index[key] = entry
        
        // Persist index
        saveCacheIndex()
    }
    
    suspend fun remove(key: String) {
        index.remove(key)?.let {
            deleteFromDisk(key)
        }
        saveCacheIndex()
    }
    
    internal suspend fun ensureSpace(newDataSize: Long) {
        val currentSize = index.values.sumOf { it.size }
        
        if (currentSize + newDataSize <= maxSizeBytes) return
        
        // LRU eviction with access count weighting
        val now = Clock.System.now().toEpochMilliseconds()
        val sortedEntries = index.values.sortedWith { a, b ->
            val scoreA = a.accessCount.toDouble() / ((now - a.lastAccess) / 3600000.0)
            val scoreB = b.accessCount.toDouble() / ((now - b.lastAccess) / 3600000.0)
            scoreA.compareTo(scoreB)
        }
        
        var freedBytes = 0L
        for (entry in sortedEntries) {
            if (currentSize - freedBytes + newDataSize <= maxSizeBytes) break
            
            remove(entry.key)
            freedBytes += entry.size
            println("🗑️  Evicted ${entry.key} (${entry.size} bytes)")
        }
    }
    
    internal suspend fun loadFromDisk(key: String): ByteIndexed? {
        val filePath = "$cacheDir/${key.replace(':', '_').replace('/', '_')}.jar"
        return try {
            readBytes(filePath).toByteIndexed()
        } catch (e: Exception) {
            null
        }
    }
    
    internal suspend fun saveToDisk(key: String, data: ByteIndexed) {
        val filePath = "$cacheDir/${key.replace(':', '_').replace('/', '_')}.jar"
        // Convert ByteIndexed back to ByteArray
        val byteArray = ByteArray(data.component1()) { i -> data.buf.component2()(i) }
        writeBytes(filePath, byteArray)
    }
    
    internal suspend fun deleteFromDisk(key: String) {
        val filePath = "$cacheDir/${key.replace(':', '_').replace('/', '_')}.jar"
        try {
            deleteFile(filePath)
        } catch (e: Exception) {
            // Ignore deletion errors
        }
    }
    
    internal fun loadCacheIndex() {
        // Load cache index from disk - simplified implementation
        // In real implementation, would load from JSON/binary format
    }
    
    internal fun saveCacheIndex() {
        // Save cache index to disk - simplified implementation
        // In real implementation, would save to JSON/binary format
    }
    
    fun getStats(): CacheStats {
        val entries = index.values
        return CacheStats(
            totalEntries = entries.size,
            totalSizeBytes = entries.sumOf { it.size },
            hitRate = if (hits + misses > 0) hits.toDouble() / (hits + misses) else 0.0,
            oldestEntry = entries.minOfOrNull { it.timestamp } ?: 0L,
            newestEntry = entries.maxOfOrNull { it.timestamp } ?: 0L
        )
    }
}

// === JAR Controller Service ===

class JarController(
    internal val reactor: Reactor<Any> = Reactor<Any>(),
    internal val bandwidthManager: IntelligentBandwidthManager = IntelligentBandwidthManager(reactor),
    internal val cacheManager: PerfectCacheManager = PerfectCacheManager()
) {
    
    suspend fun analyzeScript(scriptPath: String): AnnotationSet {
        println("🔍 Analyzing script annotations: $scriptPath")
        
        val scriptContent = readBytes(scriptPath)
        return AnnotationBBParser.parseFileAnnotations(scriptContent)
    }
    
    suspend fun resolveDependencies(annotations: AnnotationSet): JarResolutionResult {
        println("🚀 Resolving ${annotations.dependencies.component1()} dependencies with intelligent caching...")
        
        val resolved = mutableListOf<ResolvedJar>()
        val failed = mutableListOf<String>()
        val cached = mutableListOf<String>()
        
        // Process dependencies in parallel with bandwidth management
        val tasks = mutableListOf<Deferred<SingleResolveResult>>()
        
        for (i in 0 until annotations.dependencies.component1()) {
            val dep = annotations.dependencies.component2()(i)
            val task = ServiceManager.scope.async {
                resolveSingleDependency(dep, annotations.repositories)
            }
            tasks.add(task)
        }
        
        // Collect results
        for (task in tasks) {
            when (val result = task.await()) {
                is SingleResolveResult.Success -> {
                    resolved.add(result.jar)
                    if (result.fromCache) {
                        cached.add(result.jar.artifact.coordinate)
                    }
                }
                is SingleResolveResult.Failure -> {
                    failed.add("${result.coordinate}: ${result.error}")
                }
            }
        }
        
        // Display statistics
        val stats = cacheManager.getStats()
        println("📊 Resolution complete:")
        println("   ✅ Resolved: ${resolved.size}")
        println("   🎯 From cache: ${cached.size}")
        println("   ❌ Failed: ${failed.size}")
        println("   💾 Cache hit rate: ${(stats.hitRate * 100).toInt()}%")
        
        return JarResolutionResult(
            resolved = \1 j { \2: Int -> resolved[i] },
            failed = \1 j { \2: Int -> failed[i] },
            cached = \1 j { \2: Int -> cached[i] },
            stats = stats
        )
    }
    
    internal suspend fun resolveSingleDependency(
        dep: DependencyAnnotation,
        repositories: Indexed<RepositoryAnnotation>
    ): SingleResolveResult {
        val coordinate = dep.coordinate
        
        // Check cache first
        val cacheKey = generateCacheKey(coordinate)
        val cachedData = cacheManager.get(cacheKey)
        
        if (cachedData != null) {
            println("💾 Cache hit: $coordinate")
            return SingleResolveResult.Success(
                jar = ResolvedJar(
                    artifact = parseCoordinate(coordinate),
                    data = cachedData,
                    source = "cache"
                ),
                fromCache = true
            )
        }
        
        // Try repositories in priority order
        val repoList = buildRepositoryList(repositories)
        
        for (i in 0 until repoList.component1()) {
            val repo = repoList.component2()(i)
            
            try {
                val jarData = fetchFromRepository(repo, coordinate)
                
                // Cache the successful result
                cacheManager.put(cacheKey, jarData)
                
                println("✅ Downloaded: $coordinate from ${repo.id}")
                return SingleResolveResult.Success(
                    jar = ResolvedJar(
                        artifact = parseCoordinate(coordinate),
                        data = jarData,
                        source = repo.id
                    ),
                    fromCache = false
                )
                
            } catch (e: Exception) {
                println("⚠️  Failed ${repo.id}: $coordinate - ${e.message}")
                continue
            }
        }
        
        return SingleResolveResult.Failure(coordinate, "Not found in any repository")
    }
    
    internal suspend fun fetchFromRepository(repo: RepositoryAnnotation, coordinate: String): ByteIndexed {
        val artifact = parseCoordinate(coordinate)
        val url = "${repo.url}/${artifact.path}"
        
        return bandwidthManager.throttledDownload(url) { downloaded, total ->
            if (total > 0) {
                val percent = (downloaded * 100 / total).toInt()
                print("\r📦 ${artifact.artifactId}: $percent%")
            }
        }
    }
    
    internal fun buildRepositoryList(repositories: Indexed<RepositoryAnnotation>): Indexed<RepositoryAnnotation> {
        val defaultRepos = arrayOf(
            RepositoryAnnotation("central", "https://repo1.maven.org/maven2", priority = 100),
            RepositoryAnnotation("gradle", "https://plugins.gradle.org/m2", priority = 90)
        )
        
        val allRepos = mutableListOf<RepositoryAnnotation>()
        
        // Add script repositories
        for (i in 0 until repositories.component1()) {
            allRepos.add(repositories.component2()(i))
        }
        
        // Add defaults
        allRepos.addAll(defaultRepos)
        
        // Sort by priority (higher first)
        allRepos.sortByDescending { it.priority }
        
        return \1 j { \2: Int -> allRepos[i] }
    }
    
    internal fun generateCacheKey(coordinate: String): String {
        // Use coordinate as cache key, but normalize it
        return coordinate.replace(' ', '_').replace('\t', '_')
    }
    
    internal fun parseCoordinate(coordinate: String): ArtifactDescriptor {
        val parts = coordinate.split(":")
        return when (parts.size) {
            3 -> ArtifactDescriptor(parts[0], parts[1], parts[2])
            4 -> ArtifactDescriptor(parts[0], parts[1], parts[2], parts[3])
            else -> throw IllegalArgumentException("Invalid coordinate: $coordinate")
        }
    }
    
    fun createClasspath(resolved: JarResolutionResult): String {
        val paths = mutableListOf<String>()
        
        for (i in 0 until resolved.resolved.component1()) {
            val jar = resolved.resolved.component2()(i)
            // For now, return coordinate as path (in real impl, would be file path)
            paths.add(jar.artifact.coordinate)
        }
        
        // Use platform-appropriate path separator
        val pathSeparator = when (PlatformDetection.getPlatformInfo().os) {
            OperatingSystem.WINDOWS -> ";"
            else -> ":"
        }
        return paths.joinToString(pathSeparator)
    }
}

// === Data Types ===

data class ArtifactDescriptor(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val classifier: String? = null,
    val type: String = "jar"
) {
    val coordinate: String get() = listOfNotNull(groupId, artifactId, version, classifier).joinToString(":")
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

data class ResolvedJar(
    val artifact: ArtifactDescriptor,
    val data: ByteIndexed,
    val source: String
)

data class JarResolutionResult(
    val resolved: Indexed<ResolvedJar>,
    val failed: Indexed<String>,
    val cached: Indexed<String>,
    val stats: CacheStats
)

sealed class SingleResolveResult {
    data class Success(val jar: ResolvedJar, val fromCache: Boolean) : SingleResolveResult()
    data class Failure(val coordinate: String, val error: String) : SingleResolveResult()
}

// === Network Transport Stub ===

class NetworkTransport {
    suspend fun fetch(url: String): ByteIndexed {
        // Stub implementation - would use trikeshed-net
        TODO("Implement with trikeshed-net HTTP client")
    }
    
    suspend fun fetchWithProgress(
        url: String, 
        maxBytesPerSecond: Long,
        onProgress: (Long, Long) -> Unit
    ): ByteIndexed {
        // Stub implementation - would use trikeshed-net with throttling
        TODO("Implement with trikeshed-net HTTP client + throttling")
    }
}