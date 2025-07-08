package borg.trikeshed.ssh

import borg.trikeshed.lib.*

/**
 * SSH Mock File System Implementation
 * 
 * Provides a mock file system for testing SSH file transfer operations
 * without requiring actual file system access.
 */

// Mock File System implementation
class SSHMockFileSystem : SSHFileSystem {
    private val files = mutableMapOf<String, FileInfo>()
    private val directories = mutableSetOf<String>()
    
    init {
        // Initialize with some test files
        files["/local/test.txt"] = FileInfo("/local/test.txt", "Hello, SCP!".toByteArray())
        files["/local/source.txt"] = FileInfo("/local/source.txt", "Source file content".toByteArray())
        files["/local/data.bin"] = FileInfo("/local/data.bin", ByteArray(1024) { it.toByte() })
        
        // Initialize with some test directories
        directories.add("/local")
        directories.add("/local/dir")
        directories.add("/remote")
    }
    
    override fun readFile(path: String): FileInfo? {
        println("MockFS: Reading file $path")
        return files[path]
    }
    
    override fun writeFile(path: String, content: ByteArray): Boolean {
        println("MockFS: Writing file $path (${content.size} bytes)")
        files[path] = FileInfo(path, content)
        return true
    }
    
    override fun fileExists(path: String): Boolean {
        val exists = files.containsKey(path)
        println("MockFS: Checking if file exists $path: $exists")
        return exists
    }
    
    override fun deleteFile(path: String): Boolean {
        val deleted = files.remove(path) != null
        println("MockFS: Deleting file $path: $deleted")
        return deleted
    }
    
    override fun listFiles(directory: String): List<String> {
        println("MockFS: Listing files in directory $directory")
        
        if (!directories.contains(directory)) {
            return emptyList()
        }
        
        return files.keys
            .filter { it.startsWith(directory) && it != directory }
            .map { it.substringAfterLast("/") }
    }
    
    // Additional helper methods for testing
    fun createDirectory(path: String) {
        println("MockFS: Creating directory $path")
        directories.add(path)
    }
    
    fun deleteDirectory(path: String): Boolean {
        println("MockFS: Deleting directory $path")
        
        // Remove all files in the directory
        val filesToRemove = files.keys.filter { it.startsWith(path) }
        filesToRemove.forEach { files.remove(it) }
        
        // Remove the directory itself
        return directories.remove(path)
    }
    
    fun directoryExists(path: String): Boolean {
        return directories.contains(path)
    }
    
    fun getFileCount(): Int = files.size
    
    fun getDirectoryCount(): Int = directories.size
    
    fun clear() {
        println("MockFS: Clearing all files and directories")
        files.clear()
        directories.clear()
    }
    
    fun getFileSize(path: String): Int {
        return files[path]?.size ?: 0
    }
    
    fun getFileContent(path: String): String? {
        return files[path]?.content?.decodeToString()
    }
    
    fun listAllFiles(): List<String> {
        return files.keys.toList()
    }
    
    fun listAllDirectories(): List<String> {
        return directories.toList()
    }
}

// Mock File System factory
object SSHMockFileSystemFactory {
    fun createMockFileSystem(): SSHMockFileSystem {
        return SSHMockFileSystem()
    }
} 