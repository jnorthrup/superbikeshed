package borg.trikeshed.isam

/**
 * Common interface for closeable resources
 */
interface CommonCloseable {
    fun close()
}

/**
 * Platform-abstracted file access for ISAM operations
 * 
 * This provides a common interface for file operations across platforms,
 * with platform-specific implementations handling the actual I/O.
 */
expect abstract class FileAccess(filename: String) : CommonCloseable {
    val filename: String
    
    /**
     * Platform-specific closeable resource (e.g., FileChannel on JVM)
     */
    abstract val platformCloseable: Any?
    
    /**
     * Size of the file in bytes
     */
    abstract val size: Long
    
    /**
     * Read data from file at specific position
     */
    abstract fun readAt(position: Long, length: Int): ByteArray
    
    /**
     * Write data to file at specific position
     */
    abstract fun writeAt(position: Long, data: ByteArray)
}