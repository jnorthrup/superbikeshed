package borg.trikeshed.io

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DSL wrapper for high-performance I/O operations
 * Provides a fluent API for working with the I/O daemon
 */
class IODSL(private val daemon: IODaemon) {
    
    /**
     * Create a new I/O session with a file descriptor
     */
    fun session(fd: Int): IOSession = IOSession(fd, daemon)
    
    /**
     * Create a new I/O handle for a specific operation type
     * TODO: Commented out due to IOHandle class conflict
     */
    // fun handle(type: IODaemonOperation.IODaemonOperationType): IOHandle = IOHandle(type, daemon)
    
    /**
     * Execute a batch of operations
     */
    suspend fun batch(block: IOBatchBuilder.() -> Unit): List<IODaemonResult> {
        val builder = IOBatchBuilder(daemon)
        builder.block()
        return builder.execute()
    }
    
    /**
     * Stream operations as they complete
     */
    fun stream(block: IOStreamBuilder.() -> Unit): Flow<IODaemonResult> {
        val builder = IOStreamBuilder(daemon)
        builder.block()
        return builder.stream()
    }
}

/**
 * I/O Session for managing operations on a specific file descriptor
 */
class IOSession(
    private val fd: Int,
    private val daemon: IODaemon
) {
    private var operationId = 0L
    
    /**
     * Read operation
     */
    suspend fun read(buffer: ByteArray, offset: Long = 0, flags: Int = 0): IODaemonResult {
        val operation = IODaemonOperation(
            id = ++operationId,
            type = IODaemonOperation.IODaemonOperationType.READ,
            fd = fd,
            buffer = buffer,
            offset = offset,
            flags = flags
        )
        return daemon.submit(operation)
    }
    
    /**
     * Write operation
     */
    suspend fun write(data: ByteArray, offset: Long = 0, flags: Int = 0): IODaemonResult {
        val operation = IODaemonOperation(
            id = ++operationId,
            type = IODaemonOperation.IODaemonOperationType.WRITE,
            fd = fd,
            buffer = data,
            offset = offset,
            flags = flags
        )
        return daemon.submit(operation)
    }
    
    /**
     * Poll operation
     */
    suspend fun poll(events: Int, flags: Int = 0): IODaemonResult {
        val operation = IODaemonOperation(
            id = ++operationId,
            type = IODaemonOperation.IODaemonOperationType.POLL,
            fd = fd,
            buffer = ByteArray(0),
            flags = flags
        )
        return daemon.submit(operation)
    }
    
    /**
     * Accept operation (for sockets)
     */
    suspend fun accept(flags: Int = 0): IODaemonResult {
        val operation = IODaemonOperation(
            id = ++operationId,
            type = IODaemonOperation.IODaemonOperationType.ACCEPT,
            fd = fd,
            buffer = ByteArray(0),
            flags = flags
        )
        return daemon.submit(operation)
    }
    
    /**
     * Send operation (for sockets)
     */
    suspend fun send(data: ByteArray, flags: Int = 0): IODaemonResult {
        val operation = IODaemonOperation(
            id = ++operationId,
            type = IODaemonOperation.IODaemonOperationType.SEND,
            fd = fd,
            buffer = data,
            flags = flags
        )
        return daemon.submit(operation)
    }
    
    /**
     * Receive operation (for sockets)
     */
    suspend fun recv(buffer: ByteArray, flags: Int = 0): IODaemonResult {
        val operation = IODaemonOperation(
            id = ++operationId,
            type = IODaemonOperation.IODaemonOperationType.RECV,
            fd = fd,
            buffer = buffer,
            flags = flags
        )
        return daemon.submit(operation)
    }
}

/**
 * I/O Handle for specific operation types
 * TODO: Remove duplicate IOHandle definition - conflicts with IOHandle.kt
 */
/*
class IOHandle(
    private val type: IODaemonOperation.IODaemonOperationType,
    private val daemon: IODaemon
) {
    private var operationId = 0L
    
    /**
     * Execute the operation with the given parameters
     */
    suspend fun execute(fd: Int, buffer: ByteArray, offset: Long = 0, flags: Int = 0): IODaemonResult {
        val operation = IODaemonOperation(
            id = ++operationId,
            type = type,
            fd = fd,
            buffer = buffer,
            offset = offset,
            flags = flags
        )
        return daemon.submit(operation)
    }
}
*/

/**
 * Batch builder for multiple operations
 */
class IOBatchBuilder(private val daemon: IODaemon) {
    private val operations = mutableListOf<IODaemonOperation>()
    private var operationId = 0L
    
    /**
     * Add a read operation
     */
    fun read(fd: Int, buffer: ByteArray, offset: Long = 0, flags: Int = 0) {
        operations.add(IODaemonOperation(
            id = ++operationId,
            type = IODaemonOperation.IODaemonOperationType.READ,
            fd = fd,
            buffer = buffer,
            offset = offset,
            flags = flags
        ))
    }
    
    /**
     * Add a write operation
     */
    fun write(fd: Int, data: ByteArray, offset: Long = 0, flags: Int = 0) {
        operations.add(IODaemonOperation(
            id = ++operationId,
            type = IODaemonOperation.IODaemonOperationType.WRITE,
            fd = fd,
            buffer = data,
            offset = offset,
            flags = flags
        ))
    }
    
    /**
     * Add a poll operation
     */
    fun poll(fd: Int, events: Int, flags: Int = 0) {
        operations.add(IODaemonOperation(
            id = ++operationId,
            type = IODaemonOperation.IODaemonOperationType.POLL,
            fd = fd,
            buffer = ByteArray(0),
            flags = flags
        ))
    }
    
    /**
     * Execute the batch
     */
    suspend fun execute(): List<IODaemonResult> = daemon.submitBatch(operations)
}

/**
 * Stream builder for continuous operations
 */
class IOStreamBuilder(private val daemon: IODaemon) {
    private val operations = mutableListOf<IODaemonOperation>()
    private var operationId = 0L
    
    /**
     * Add a read operation
     */
    fun read(fd: Int, buffer: ByteArray, offset: Long = 0, flags: Int = 0) {
        operations.add(IODaemonOperation(
            id = ++operationId,
            type = IODaemonOperation.IODaemonOperationType.READ,
            fd = fd,
            buffer = buffer,
            offset = offset,
            flags = flags
        ))
    }
    
    /**
     * Add a write operation
     */
    fun write(fd: Int, data: ByteArray, offset: Long = 0, flags: Int = 0) {
        operations.add(IODaemonOperation(
            id = ++operationId,
            type = IODaemonOperation.IODaemonOperationType.WRITE,
            fd = fd,
            buffer = data,
            offset = offset,
            flags = flags
        ))
    }
    
    /**
     * Stream the operations
     */
    fun stream(): Flow<IODaemonResult> = daemon.completedOperations()
}

/**
 * Extension function to create I/O DSL from daemon
 */
fun IODaemon.dsl(): IODSL = IODSL(this)

/**
 * Extension function to create I/O DSL from coroutine scope
 */
fun CoroutineScope.ioDsl(): IODSL = IODSL(IODaemon.create(this)) 