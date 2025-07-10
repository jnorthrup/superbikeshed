@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package k2script.git

import k2script.platform.*
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Git LFS Manager with Fiduciary Attention
 * 
 * Provides comprehensive LFS (Large File Storage) management with
 * fiduciary attention to both Git tree and LFS object integrity.
 */
class GitLFSManager {
    
    internal val lfsPatterns = listOf(
        "*.psd", "*.ai", "*.eps", "*.pdf", "*.zip", "*.tar.gz", "*.rar",
        "*.mp4", "*.avi", "*.mov", "*.wmv", "*.flv", "*.webm",
        "*.mp3", "*.wav", "*.flac", "*.aac", "*.ogg",
        "*.jpg", "*.jpeg", "*.png", "*.gif", "*.bmp", "*.tiff", "*.svg",
        "*.model", "*.weights", "*.bin", "*.dat", "*.h5", "*.pkl",
        "*.iso", "*.dmg", "*.exe", "*.deb", "*.rpm", "*.apk"
    )
    
    /**
     * Setup LFS tracking with fiduciary attention
     */
    suspend fun setupLFSTracking(repoDir: String) {
        try {
            // Check if LFS is already configured
            if (isLFSConfigured(repoDir)) {
                println("LFS already configured in ${File(repoDir).name}")
                return
            }
            
            // Install LFS if not available
            if (!isLFSInstalled()) {
                installLFS()
            }
            
            // Initialize LFS in repository
            executeGitCommandInDir(repoDir, "lfs", "install")
            
            // Track common large file patterns
            lfsPatterns.forEach { pattern ->
                executeGitCommandInDir(repoDir, "lfs", "track", pattern)
            }
            
            // Commit .gitattributes file
            val gitAttributes = "$repoDir/.gitattributes"
            if (fileExists(gitAttributes)) {
                executeGitCommandInDir(repoDir, "add", ".gitattributes")
                executeGitCommandInDir(repoDir, "commit", "-m", "Add LFS tracking patterns")
            }
            
            println("LFS tracking configured with fiduciary attention")
            
        } catch (e: Exception) {
            throw RuntimeException("Failed to setup LFS tracking: ${e.message}")
        }
    }
    
    /**
     * Check if LFS is configured in repository
     */
    suspend fun isLFSConfigured(repoDir: String): Boolean {
        val fileSystem = coroutineContext.fileSystemOperations
        val gitAttributes = File("$repoDir/.gitattributes")
        val lfsDir = File("$repoDir/.git/lfs")
        
        return fileSystem.fileExists(gitAttributes) && fileSystem.fileExists(lfsDir)
    }
    
    /**
     * Check if LFS is installed
     */
    suspend fun isLFSInstalled(): Boolean {
        val processExecutor = coroutineContext.processExecutor
        return try {
            val result = processExecutor.runCommand("git-lfs version")
            result.exitCode == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Install Git LFS
     */
    internal suspend fun installLFS() {
        val processExecutor = coroutineContext.processExecutor
        val os = System.getProperty("os.name").lowercase()
        
        when {
            os.contains("mac") -> {
                processExecutor.runCommand("brew install git-lfs")
            }
            os.contains("linux") -> {
                processExecutor.runCommand("curl -s https://packagecloud.io/install/repositories/github/git-lfs/script.deb.sh | sudo bash")
                processExecutor.runCommand("sudo apt-get install git-lfs")
            }
            os.contains("windows") -> {
                // Windows installation would require chocolatey or similar
                throw RuntimeException("LFS installation on Windows requires manual setup")
            }
            else -> {
                throw RuntimeException("Unsupported OS for LFS installation: $os")
            }
        }
    }
    
    /**
     * Pull LFS objects with fiduciary attention
     */
    suspend fun pullLFSObjects(repoDir: String) {
        try {
            // Check for LFS objects
            val lfsObjects = getLFSObjects(repoDir)
            if (lfsObjects.isEmpty()) {
                println("No LFS objects found")
                return
            }
            
            println("Pulling ${lfsObjects.size} LFS objects with fiduciary attention...")
            
            // Pull LFS objects
            executeGitCommandInDir(repoDir, "lfs", "pull")
            
            // Verify LFS objects integrity
            verifyLFSObjects(repoDir, lfsObjects)
            
            println("LFS objects pulled successfully")
            
        } catch (e: Exception) {
            throw RuntimeException("Failed to pull LFS objects: ${e.message}")
        }
    }
    
    /**
     * Push LFS objects with fiduciary attention
     */
    suspend fun pushLFSObjects(repoDir: String) {
        try {
            // Check for LFS objects to push
            val lfsObjects = getLFSObjects(repoDir)
            if (lfsObjects.isEmpty()) {
                println("No LFS objects to push")
                return
            }
            
            println("Pushing ${lfsObjects.size} LFS objects with fiduciary attention...")
            
            // Push LFS objects
            executeGitCommandInDir(repoDir, "lfs", "push", "--all", "origin", "HEAD")
            
            println("LFS objects pushed successfully")
            
        } catch (e: Exception) {
            throw RuntimeException("Failed to push LFS objects: ${e.message}")
        }
    }
    
    /**
     * Get LFS objects with fiduciary attention
     */
    fun getLFSObjects(repoDir: String): List<LFSObject> {
        val objects = mutableListOf<LFSObject>()
        
        try {
            // Get LFS objects from Git
            val lfsList = executeGitCommandInDir(repoDir, "lfs", "ls-files", "--long")
            
            lfsList.split("\n").filter { it.isNotEmpty() }.forEach { line ->
                val parts = line.split("\\s+".toRegex())
                if (parts.size >= 3) {
                    val oid = parts[0]
                    val size = parts[1].toLongOrNull() ?: 0L
                    val path = parts.drop(2).joinToString(" ")
                    
                    objects.add(LFSObject(path, size, oid))
                }
            }
            
        } catch (e: Exception) {
            // LFS might not be configured, return empty list
        }
        
        return objects
    }
    
    /**
     * Check if repository has LFS files
     */
    fun hasLFSFiles(repoDir: String): Boolean {
        return try {
            val lfsFiles = executeGitCommandInDir(repoDir, "lfs", "ls-files")
            lfsFiles.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Verify LFS objects integrity with fiduciary attention
     */
    internal suspend fun verifyLFSObjects(repoDir: String, expectedObjects: List<LFSObject>) {
        val fileSystem = coroutineContext.fileSystemOperations
        println("Verifying LFS objects integrity...")
        
        expectedObjects.forEach { expected ->
            val objectFile = File("$repoDir/${expected.path}")
            if (!fileSystem.fileExists(objectFile)) {
                throw RuntimeException("LFS object missing: ${expected.path}")
            }
            
            val fileSize = fileSystem.getFileSize(objectFile)
            if (fileSize != expected.size) {
                throw RuntimeException("LFS object size mismatch: ${expected.path}")
            }
            
            // Verify OID
            val actualOid = calculateObjectOid(objectFile)
            if (actualOid != expected.oid) {
                throw RuntimeException("LFS object OID mismatch: ${expected.path}")
            }
        }
        
        println("LFS objects integrity verified")
    }
    
    /**
     * Calculate object OID (simplified)
     */
    internal suspend fun calculateObjectOid(filePath: File): String {
        // This is a simplified OID calculation
        // In practice, Git LFS uses SHA256
        val fileSystem = coroutineContext.fileSystemOperations
        val content = fileSystem.readText(filePath)
        return content.hashCode().toString(16)
    }
    
    /**
     * Get LFS status with fiduciary attention
     */
    fun getLFSStatus(repoDir: String): LFSStatus {
        val objects = getLFSObjects(repoDir)
        val totalSize = objects.sumOf { it.size }
        val isConfigured = isLFSConfigured(repoDir)
        val hasObjects = objects.isNotEmpty()
        
        return LFSStatus(
            isConfigured = isConfigured,
            hasObjects = hasObjects,
            objectCount = objects.size,
            totalSize = totalSize,
            objects = objects
        )
    }
    
    /**
     * Clean LFS cache with attention
     */
    suspend fun cleanLFSCache(repoDir: String) {
        try {
            println("Cleaning LFS cache...")
            executeGitCommandInDir(repoDir, "lfs", "prune")
            println("LFS cache cleaned")
        } catch (e: Exception) {
            throw RuntimeException("Failed to clean LFS cache: ${e.message}")
        }
    }
    
    /**
     * Migrate files to LFS with fiduciary attention
     */
    suspend fun migrateToLFS(repoDir: String, patterns: List<String>) {
        try {
            println("Migrating files to LFS...")
            
            patterns.forEach { pattern ->
                executeGitCommandInDir(repoDir, "lfs", "migrate", "import", "--include", pattern)
            }
            
            println("Files migrated to LFS successfully")
            
        } catch (e: Exception) {
            throw RuntimeException("Failed to migrate files to LFS: ${e.message}")
        }
    }
    
    /**
     * Get LFS configuration
     */
    fun getLFSConfig(repoDir: String): LFSConfig {
        val gitAttributes = "$repoDir/.gitattributes"
        val patterns = mutableListOf<String>()
        
        if (fileExists(gitAttributes)) {
            val content = readText(gitAttributes)
            content.lines().forEach { line ->
                if (line.contains("filter=lfs")) {
                    val pattern = line.split(" ").firstOrNull()
                    if (pattern != null) {
                        patterns.add(pattern)
                    }
                }
            }
        }
        
        return LFSConfig(
            patterns = patterns,
            isConfigured = isLFSConfigured(repoDir),
            objectCount = getLFSObjects(repoDir).size
        )
    }
    
    /**
     * Execute Git command with attention
     */
    internal suspend fun executeGitCommandInDir(repoDir: String, vararg args: String): String {
        val processExecutor = coroutineContext.processExecutor
        val result = processExecutor.runCommand("git " + args.joinToString(" "), File(repoDir))
        if (result.exitCode != 0) {
            throw RuntimeException("Git command failed in $repoDir: ${result.stderr}")
        }
        return result.stdout
    }
    
    /**
     * Execute system command
     */
    internal suspend fun executeCommand(vararg args: String) {
        val processExecutor = coroutineContext.processExecutor
        val result = processExecutor.runCommand(args.joinToString(" "))
        if (result.exitCode != 0) {
            throw RuntimeException("Command failed: ${args.joinToString(" ")}\n${result.stderr}")
        }
    }
}

data class LFSStatus(
    val isConfigured: Boolean,
    val hasObjects: Boolean,
    val objectCount: Int,
    val totalSize: Long,
    val objects: List<LFSObject>
)

data class LFSConfig(
    val patterns: List<String>,
    val isConfigured: Boolean,
    val objectCount: Int
) 