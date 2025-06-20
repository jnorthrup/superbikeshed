package borg.trikeshed.io

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Common interface for async I/O engines
 */
expect class AsyncIOEngine {
    /**
     * Initialize the async I/O engine
     */
    fun initialize()
    
    /**
     * Clean up resources
     */
    fun cleanup()
    
    /**
     * Submit a read operation
     */
    suspend fun read(fd: Int, buffer: ByteArray, offset: Long = 0): Int
    
    /**
     * Submit a write operation
     */
    suspend fun write(fd: Int, data: ByteArray, offset: Long = 0): Int
    
    /**
     * Submit multiple operations and wait for completion
     */
    suspend fun submitBatch(operations: List<IOOperation>): List<IOResult>
    
    /**
     * Get a flow of completed operations
     */
    fun completedOperations(): Flow<IOResult>
    
    companion object {
        fun create(): AsyncIOEngine
    }
}

/**
 * Represents an I/O operation
 */
data class IOOperation(
    val id: Long,
    val type: IOType,
    val fd: Int,
    val buffer: ByteArray,
    val offset: Long = 0
) {
    enum class IOType {
        READ, WRITE
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as IOOperation
        
        if (id != other.id) return false
        if (type != other.type) return false
        if (fd != other.fd) return false
        if (!buffer.contentEquals(other.buffer)) return false
        if (offset != other.offset) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + fd
        result = 31 * result + buffer.contentHashCode()
        result = 31 * result + offset.hashCode()
        return result
    }
}

/**
 * Result of an I/O operation
 */
data class IOResult(
    val operationId: Long,
    val bytesTransferred: Int,
    val error: Int = 0
) {
    val isSuccess: Boolean get() = error == 0
    val isError: Boolean get() = error != 0
} 