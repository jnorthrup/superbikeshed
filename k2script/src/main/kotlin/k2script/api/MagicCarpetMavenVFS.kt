@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package k2script.api

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.net.URL
import java.net.HttpURLConnection
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Magic Carpet Maven VFS - Dynamically populates Maven coordinates in ~/.m2 during materialization
 * 
 * This creates a virtual file system that materializes Maven repository structure on-demand,
 * downloading artifacts and POMs as needed and caching them in the local ~/.m2/repository.
 */
class MagicCarpetMavenVFS(
    internal val localRepository: Path = Paths.get(System.getProperty("user.home"), ".m2", "repository"),
    internal val remoteRepositories: List<String> = listOf(
        "https://repo1.maven.org/maven2/",
        "https://s01.oss.sonatype.org/content/repositories/releases/"
    )
) {
    
    internal val materializationMutex = Mutex()
    internal val materializedCoordinates = ConcurrentHashMap<String, Boolean>()
    
    /**
     * Materialize a Maven coordinate - download and cache if not present
     */
    suspend fun materializeCoordinate(coordinate: String): Path {
        return materializationMutex.withLock {
            if (materializedCoordinates[coordinate] == true) {
                return@withLock getLocalPath(coordinate)
            }
            
            val (groupId, artifactId, version) = parseCoordinate(coordinate)
            val localPath = getLocalPath(coordinate)
            
            // Check if already exists locally
            if (Files.exists(localPath)) {
                materializedCoordinates[coordinate] = true
                return@withLock localPath
            }
            
            // Materialize the coordinate
            materializeArtifact(groupId, artifactId, version)
            materializedCoordinates[coordinate] = true
            
            localPath
        }
    }
    
    /**
     * Materialize multiple coordinates and return classpath
     */
    suspend fun materializeClasspath(coordinates: List<String>): String {
        val paths = coordinates.map { coordinate ->
            materializeCoordinate(coordinate)
        }
        return paths.joinToString(File.pathSeparator)
    }
    
    /**
     * Materialize an artifact and its POM
     */
    internal suspend fun materializeArtifact(groupId: String, artifactId: String, version: String) {
        val groupPath = groupId.replace(".", "/")
        val artifactDir = localRepository.resolve(groupPath).resolve(artifactId).resolve(version)
        
        // Create directory structure
        Files.createDirectories(artifactDir)
        
        // Download POM first
        val pomUrl = findPomUrl(groupId, artifactId, version)
        val pomFile = artifactDir.resolve("$artifactId-$version.pom")
        downloadFile(pomUrl, pomFile)
        
        // Download JAR
        val jarUrl = findJarUrl(groupId, artifactId, version)
        val jarFile = artifactDir.resolve("$artifactId-$version.jar")
        downloadFile(jarUrl, jarFile)
        
        // Download checksums
        try {
            val sha1Url = findSha1Url(groupId, artifactId, version)
            val sha1File = artifactDir.resolve("$artifactId-$version.jar.sha1")
            downloadFile(sha1Url, sha1File)
        } catch (e: Exception) {
            // SHA1 not available, continue
        }
    }
    
    /**
     * Find POM URL from remote repositories
     */
    internal fun findPomUrl(groupId: String, artifactId: String, version: String): String {
        val groupPath = groupId.replace(".", "/")
        val pomName = "$artifactId-$version.pom"
        
        for (repo in remoteRepositories) {
            val url = "$repo$groupPath/$artifactId/$version/$pomName"
            if (urlExists(url)) {
                return url
            }
        }
        
        throw IOException("POM not found for $groupId:$artifactId:$version")
    }
    
    /**
     * Find JAR URL from remote repositories
     */
    internal fun findJarUrl(groupId: String, artifactId: String, version: String): String {
        val groupPath = groupId.replace(".", "/")
        val jarName = "$artifactId-$version.jar"
        
        for (repo in remoteRepositories) {
            val url = "$repo$groupPath/$artifactId/$version/$jarName"
            if (urlExists(url)) {
                return url
            }
        }
        
        throw IOException("JAR not found for $groupId:$artifactId:$version")
    }
    
    /**
     * Find SHA1 URL from remote repositories
     */
    internal fun findSha1Url(groupId: String, artifactId: String, version: String): String {
        val groupPath = groupId.replace(".", "/")
        val sha1Name = "$artifactId-$version.jar.sha1"
        
        for (repo in remoteRepositories) {
            val url = "$repo$groupPath/$artifactId/$version/$sha1Name"
            if (urlExists(url)) {
                return url
            }
        }
        
        throw IOException("SHA1 not found for $groupId:$artifactId:$version")
    }
    
    /**
     * Check if URL exists
     */
    internal fun urlExists(urlString: String): Boolean {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode == 200
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Download file from URL to local path
     */
    internal suspend fun downloadFile(urlString: String, localPath: Path) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 30000
        
        try {
            connection.inputStream.use { input ->
                Files.newOutputStream(localPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { output ->
                    input.copyTo(output)
                }
            }
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * Get local path for a coordinate
     */
    internal fun getLocalPath(coordinate: String): Path {
        val (groupId, artifactId, version) = parseCoordinate(coordinate)
        val groupPath = groupId.replace(".", "/")
        return localRepository.resolve(groupPath).resolve(artifactId).resolve(version).resolve("$artifactId-$version.jar")
    }
    
    /**
     * Parse Maven coordinate string
     */
    internal fun parseCoordinate(coordinate: String): Triple<String, String, String> {
        val parts = coordinate.split(":")
        if (parts.size != 3) {
            throw IllegalArgumentException("Invalid Maven coordinate: $coordinate. Expected format: groupId:artifactId:version")
        }
        return Triple(parts[0], parts[1], parts[2])
    }
    
    /**
     * Get materialization status
     */
    fun isMaterialized(coordinate: String): Boolean {
        return materializedCoordinates[coordinate] == true || Files.exists(getLocalPath(coordinate))
    }
    
    /**
     * Get all materialized coordinates
     */
    fun getMaterializedCoordinates(): Set<String> {
        return materializedCoordinates.keys.toSet()
    }
    
    /**
     * Clear materialization cache (but keep files)
     */
    fun clearCache() {
        materializedCoordinates.clear()
    }
    
    /**
     * Get local repository path
     */
    fun getLocalRepositoryPath(): Path = localRepository
}

/**
 * Extension functions for easy usage
 */
suspend fun MagicCarpetMavenVFS.materialize(vararg coordinates: String): String {
    return materializeClasspath(coordinates.toList())
}

/**
 * Convenience object for common operations
 */
object MagicCarpetMaven {
    
    internal val vfs = MagicCarpetMavenVFS()
    
    /**
     * Materialize coordinates and return classpath
     */
    suspend fun materializeClasspath(coordinates: List<String>): String {
        return vfs.materializeClasspath(coordinates)
    }
    
    /**
     * Materialize single coordinate
     */
    suspend fun materializeCoordinate(coordinate: String): Path {
        return vfs.materializeCoordinate(coordinate)
    }
    
    /**
     * Check if coordinate is materialized
     */
    fun isMaterialized(coordinate: String): Boolean {
        return vfs.isMaterialized(coordinate)
    }
    
    /**
     * Get materialization status
     */
    fun getMaterializationStatus(): Map<String, Boolean> {
        return vfs.getMaterializedCoordinates().associateWith { true }
    }
} 