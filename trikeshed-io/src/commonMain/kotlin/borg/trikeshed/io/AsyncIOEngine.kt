@file:OptIn(kotlin.ExperimentalStdlibApi::class, kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

import kotlinx.coroutines.flow.Flow

/**
 * Platform-specific AsyncIOEngine implementation
 */
expect class AsyncIOEngineImpl : AsyncIOEngine

/**
 * Platform-specific creation of AsyncIOEngine
 */
expect fun createAsyncIOEngine(): AsyncIOEngine

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
    suspend fun read(handle: Int, buffer: ByteArray, offset: Long): Int
    
    /**
     * Submit a write operation
     */
    suspend fun write(handle: Int, data: ByteArray, offset: Long): Int
    
    /**
     * Submit multiple operations and wait for completion
     */
    suspend fun submitBatch(operations: List<IOOperation>): List<IOResult> {
        return operations.map { operation ->
            when (operation.type) {
                IOOperation.IOType.READ -> {
                    val bytesRead = read(operation.handle, operation.buffer, operation.offset)
                    IOResult(operation.id, bytesRead, if (bytesRead >= 0) 0 else 1)
                }
                IOOperation.IOType.WRITE -> {
                    val bytesWritten = write(operation.handle, operation.buffer, operation.offset)
                    IOResult(operation.id, bytesWritten, if (bytesWritten >= 0) 0 else 1)
                }
            }
        }
    }

    /**
     * Submit operations with progress tracking
     */
    suspend fun submitBatchWithProgress(
        operations: List<IOOperationWithProgress>,
        totalSize: Long
    ): List<IOResult> {
        var completedBytes = 0L
        return operations.map { opWithProgress ->
            val result = when (opWithProgress.operation.type) {
                IOOperation.IOType.READ -> {
                    val bytesRead = read(opWithProgress.operation.handle, opWithProgress.operation.buffer, opWithProgress.operation.offset)
                    IOResult(opWithProgress.operation.id, bytesRead, if (bytesRead >= 0) 0 else 1)
                }
                IOOperation.IOType.WRITE -> {
                    val bytesWritten = write(opWithProgress.operation.handle, opWithProgress.operation.buffer, opWithProgress.operation.offset)
                    IOResult(opWithProgress.operation.id, bytesWritten, if (bytesWritten >= 0) 0 else 1)
                }
            }
            
            completedBytes += result.bytesTransferred.coerceAtLeast(0)
            opWithProgress.progressCallback?.invoke(completedBytes, totalSize)
            result
        }
    }

    /**
     * Get a flow of completed operations
     */
    fun completedOperations(): Flow<IOResult> = throw NotImplementedError("Flow-based completion not implemented yet")
    
    companion object {
        fun create(): AsyncIOEngine = createAsyncIOEngine()
    }
}

/**
 * Progress callback for long-running operations
 */
typealias ProgressCallback = suspend (Long, Long) -> Unit

/**
 * Represents an I/O operation with progress tracking
 */
data class IOOperationWithProgress(
    val operation: IOOperation,
    val progressCallback: ProgressCallback? = null
)

/**
 * Represents an I/O operation
 */
data class IOOperation(
    val id: Long,
    val type: IOType,
    val handle: Int,
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
 * I/O handle type
 * TODO: Commented out to avoid conflict with IOHandle.kt interface
 */
// typealias IOHandle = Int 