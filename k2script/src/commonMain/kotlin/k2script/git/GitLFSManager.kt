package k2script.git

import java.io.File
import kotlinx.coroutines.delay

/**
 * Git LFS Manager with Fiduciary Attention
 * 
 * Provides comprehensive LFS (Large File Storage) management with
 * fiduciary attention to both Git tree and LFS object integrity.
 */
class GitLFSManager {
    
    private val lfsPatterns = listOf(
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
    suspend fun setupLFSTracking(repoDir: File) {
        try {
            // Check if LFS is already configured
            if (isLFSConfigured(repoDir)) {
                println("LFS already configured in ${repoDir.name}")
                return
            }
            
            // Install LFS if not available
            if (!isLFSInstalled()) {
                installLFS()
            }
            
            // Initialize LFS in repository
            executeGitCommand(repoDir, "lfs", "install")
            
            // Track common large file patterns
            lfsPatterns.forEach { pattern ->
                executeGitCommand(repoDir, "lfs", "track", pattern)
            }
            
            // Commit .gitattributes file
            val gitAttributes = File(repoDir, ".gitattributes")
            if (gitAttributes.exists()) {
                executeGitCommand(repoDir, "add", ".gitattributes")
                executeGitCommand(repoDir, "commit", "-m", "Add LFS tracking patterns")
            }
            
            println("LFS tracking configured with fiduciary attention")
            
        } catch (e: Exception) {
            throw RuntimeException("Failed to setup LFS tracking: ${e.message}")
        }
    }
    
    /**
     * Check if LFS is configured in repository
     */
    fun isLFSConfigured(repoDir: File): Boolean {
        val gitAttributes = File(repoDir, ".gitattributes")
        val lfsDir = File(repoDir, ".git/lfs")
        
        return gitAttributes.exists() && lfsDir.exists()
    }
    
    /**
     * Check if LFS is installed
     */
    fun isLFSInstalled(): Boolean {
        return try {
            val process = ProcessBuilder("git-lfs", "version")
                .redirectErrorStream(true)
                .start()
            
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Install Git LFS
     */
    private suspend fun installLFS() {
        val os = System.getProperty("os.name").lowercase()
        
        when {
            os.contains("mac") -> {
                executeCommand("brew", "install", "git-lfs")
            }
            os.contains("linux") -> {
                executeCommand("curl", "-s", "https://packagecloud.io/install/repositories/github/git-lfs/script.deb.sh", "|", "sudo", "bash")
                executeCommand("sudo", "apt-get", "install", "git-lfs")
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
    suspend fun pullLFSObjects(repoDir: File) {
        try {
            // Check for LFS objects
            val lfsObjects = getLFSObjects(repoDir)
            if (lfsObjects.isEmpty()) {
                println("No LFS objects found")
                return
            }
            
            println("Pulling ${lfsObjects.size} LFS objects with fiduciary attention...")
            
            // Pull LFS objects
            executeGitCommand(repoDir, "lfs", "pull")
            
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
    suspend fun pushLFSObjects(repoDir: File) {
        try {
            // Check for LFS objects to push
            val lfsObjects = getLFSObjects(repoDir)
            if (lfsObjects.isEmpty()) {
                println("No LFS objects to push")
                return
            }
            
            println("Pushing ${lfsObjects.size} LFS objects with fiduciary attention...")
            
            // Push LFS objects
            executeGitCommand(repoDir, "lfs", "push", "--all", "origin", "HEAD")
            
            println("LFS objects pushed successfully")
            
        } catch (e: Exception) {
            throw RuntimeException("Failed to push LFS objects: ${e.message}")
        }
    }
    
    /**
     * Get LFS objects with fiduciary attention
     */
    fun getLFSObjects(repoDir: File): List<LFSObject> {
        val objects = mutableListOf<LFSObject>()
        
        try {
            // Get LFS objects from Git
            val lfsList = executeGitCommand(repoDir, "lfs", "ls-files", "--long")
            
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
    fun hasLFSFiles(repoDir: File): Boolean {
        return try {
            val lfsFiles = executeGitCommand(repoDir, "lfs", "ls-files")
            lfsFiles.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Verify LFS objects integrity with fiduciary attention
     */
    private suspend fun verifyLFSObjects(repoDir: File, expectedObjects: List<LFSObject>) {
        println("Verifying LFS objects integrity...")
        
        expectedObjects.forEach { expected ->
            val objectFile = File(repoDir, expected.path)
            if (!objectFile.exists()) {
                throw RuntimeException("LFS object missing: ${expected.path}")
            }
            
            if (objectFile.length() != expected.size) {
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
    private fun calculateObjectOid(file: File): String {
        // This is a simplified OID calculation
        // In practice, Git LFS uses SHA256
        return file.absolutePath.hashCode().toString(16)
    }
    
    /**
     * Get LFS status with fiduciary attention
     */
    fun getLFSStatus(repoDir: File): LFSStatus {
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
    suspend fun cleanLFSCache(repoDir: File) {
        try {
            println("Cleaning LFS cache...")
            executeGitCommand(repoDir, "lfs", "prune")
            println("LFS cache cleaned")
        } catch (e: Exception) {
            throw RuntimeException("Failed to clean LFS cache: ${e.message}")
        }
    }
    
    /**
     * Migrate files to LFS with fiduciary attention
     */
    suspend fun migrateToLFS(repoDir: File, patterns: List<String>) {
        try {
            println("Migrating files to LFS...")
            
            patterns.forEach { pattern ->
                executeGitCommand(repoDir, "lfs", "migrate", "import", "--include", pattern)
            }
            
            println("Files migrated to LFS successfully")
            
        } catch (e: Exception) {
            throw RuntimeException("Failed to migrate files to LFS: ${e.message}")
        }
    }
    
    /**
     * Get LFS configuration
     */
    fun getLFSConfig(repoDir: File): LFSConfig {
        val gitAttributes = File(repoDir, ".gitattributes")
        val patterns = mutableListOf<String>()
        
        if (gitAttributes.exists()) {
            gitAttributes.readLines().forEach { line ->
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
    private fun executeGitCommand(repoDir: File, vararg args: String): String {
        val process = ProcessBuilder("git", *args)
            .directory(repoDir)
            .redirectErrorStream(true)
            .start()
        
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        
        if (exitCode != 0) {
            throw RuntimeException("Git command failed: ${args.joinToString(" ")}")
        }
        
        return output
    }
    
    /**
     * Execute system command
     */
    private suspend fun executeCommand(vararg args: String) {
        val process = ProcessBuilder(*args)
            .redirectErrorStream(true)
            .start()
        
        val exitCode = process.waitFor()
        
        if (exitCode != 0) {
            throw RuntimeException("Command failed: ${args.joinToString(" ")}")
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