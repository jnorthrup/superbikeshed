package borg.trikeshed.io

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Common interface for async I/O engines
 */
interface AsyncIOEngine {
    /**
     * Initialize the async I/O engine
     */
    suspend fun initialize()
    
    /**
     * Clean up resources
     */
    suspend fun cleanup()
    
    /**
     * Submit a read operation
     */
    suspend fun read(handle: IOHandle, buffer: ByteArray, offset: Long): Int
    
    /**
     * Submit a write operation
     */
    suspend fun write(handle: IOHandle, data: ByteArray, offset: Long): Int
    
    /**
     * Submit multiple operations and wait for completion
     */
    suspend fun submitBatch(operations: List<IOOperation>): List<IOResult>
    
    /**
     * Get a flow of completed operations
     */
    fun completedOperations(): Flow<IOResult>
    
    companion object {
        fun create(): AsyncIOEngine = object : AsyncIOEngine {
            override suspend fun initialize() { /* placeholder */ }
            override suspend fun cleanup() { /* placeholder */ }
            override suspend fun read(handle: IOHandle, buffer: ByteArray, offset: Long): Int = 0
            override suspend fun write(handle: IOHandle, data: ByteArray, offset: Long): Int = 0
            override suspend fun submitBatch(operations: List<IOOperation>): List<IOResult> = emptyList()
            override fun completedOperations(): Flow<IOResult> = emptyFlow()
        }
    }
}

/**
 * Represents an I/O operation
 */
data class IOOperation(
    val id: Long,
    val type: IOType,
    val handle: IOHandle,
    val buffer: ByteArray,
    val offset: Long = 0
) {
    enum class IOType {
        READ, WRITE
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (this::class != other!!::class) return false
        
        other as IOOperation
        
        if (id != other.id) return false
        if (type != other.type) return false
        if (handle != other.handle) return false
        if (!buffer.contentEquals(other.buffer)) return false
        if (offset != other.offset) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + handle.hashCode()
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

/**
 * I/O handle type - using interface IOHandle from IOHandle.kt
 */ 