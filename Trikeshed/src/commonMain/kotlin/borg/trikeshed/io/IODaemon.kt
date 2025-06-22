package borg.trikeshed.io

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * I/O Daemon that coordinates high-performance I/O operations
 * Acts as a bridge between JVM and native Linux implementations
 */
expect class IODaemon {
    /**
     * Initialize the daemon with configuration
     */
    suspend fun initialize(config: IODaemonConfig = IODaemonConfig())
    
    /**
     * Shutdown the daemon gracefully
     */
    suspend fun shutdown()
    
    /**
     * Submit an I/O operation to the daemon
     */
    suspend fun submit(operation: IODaemonOperation): IODaemonResult
    
    /**
     * Submit multiple operations in batch
     */
    suspend fun submitBatch(operations: List<IODaemonOperation>): List<IODaemonResult>
    
    /**
     * Get a flow of completed operations
     */
    fun completedOperations(): Flow<IODaemonResult>
    
    /**
     * Get daemon statistics
     */
    suspend fun getStats(): IODaemonStats
    
    companion object {
        fun create(scope: CoroutineScope): IODaemon
    }
}

/**
 * Configuration for the I/O daemon
 */
data class IODaemonConfig(
    val maxConcurrentOperations: Int = 1024,
    val ringSize: Int = 256,
    val batchSize: Int = 32,
    val timeoutMs: Long = 1000,
    val enablePolling: Boolean = true,
    val enableSqpoll: Boolean = false,
    val sqThreadIdle: Int = 2000
)

/**
 * I/O operation for the daemon
 */
data class IODaemonOperation(
    val id: Long,
    val type: IODaemonOperationType,
    val fd: Int,
    val buffer: ByteArray,
    val offset: Long = 0,
    val flags: Int = 0,
    val priority: Int = 0
) {
    enum class IODaemonOperationType {
        READ, WRITE, READV, WRITEV, POLL, ACCEPT, CONNECT, SEND, RECV
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (this::class != other!!::class) return false
        
        other as IODaemonOperation
        
        if (id != other.id) return false
        if (type != other.type) return false
        if (fd != other.fd) return false
        if (!buffer.contentEquals(other.buffer)) return false
        if (offset != other.offset) return false
        if (flags != other.flags) return false
        if (priority != other.priority) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + fd
        result = 31 * result + buffer.contentHashCode()
        result = 31 * result + offset.hashCode()
        result = 31 * result + flags
        result = 31 * result + priority
        return result
    }
}

/**
 * Result of an I/O daemon operation
 */
data class IODaemonResult(
    val operationId: Long,
    val bytesTransferred: Int,
    val error: Int = 0,
    val flags: Int = 0,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds()
) {
    val isSuccess: Boolean get() = error == 0
    val isError: Boolean get() = error != 0
}

/**
 * Daemon statistics
 */
data class IODaemonStats(
    val totalOperations: Long,
    val successfulOperations: Long,
    val failedOperations: Long,
    val totalBytesTransferred: Long,
    val averageLatencyMs: Double,
    val queueDepth: Int,
    val uptimeMs: Long
) 