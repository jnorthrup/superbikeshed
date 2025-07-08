package fiduciary.protocol

import borg.trikeshed.lib.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.collections.mutableMapOf

/**
 * WireProto - Basic Protocol Implementation
 * 
 * From Fiduciary Omnibus Architecture:
 * - Inherits basic protocol functionality
 * - Manages SessionID key relationships
 * - Supports concurrent tubular flows
 */
@Serializable
data class WireMessage(
    val sessionId: String,
    val payload: ByteArray,
    val messageType: MessageType,
    val timestamp: Long,
    val sequenceNumber: Long = 0L,
    val transactionId: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        
        other as WireMessage
        
        if (sessionId != other.sessionId) return false
        if (!payload.contentEquals(other.payload)) return false
        if (messageType != other.messageType) return false
        if (timestamp != other.timestamp) return false
        if (sequenceNumber != other.sequenceNumber) return false
        if (transactionId != other.transactionId) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + messageType.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + sequenceNumber.hashCode()
        result = 31 * result + (transactionId?.hashCode() ?: 0)
        return result
    }
}

@Serializable
enum class MessageType {
    DATA,
    CONTROL,
    ERROR,
    HEARTBEAT,
    TRANSACTION_BEGIN,
    TRANSACTION_COMMIT,
    TRANSACTION_ROLLBACK
}

@Serializable
data class WireSession(
    val sessionId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActivity: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Exception thrown by WireProto operations
 */
class WireProtoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Base WireProto implementation
 */
open class WireProto {
    private val sessions = mutableMapOf<String, WireSession>()
    private val sessionMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Serialize a wire message to bytes
     */
    open suspend fun serialize(message: WireMessage): ByteArray {
        return try {
            json.encodeToString(message).toByteArray(Charsets.UTF_8)
        } catch (e: Exception) {
            throw WireProtoException("Failed to serialize message", e)
        }
    }
    
    /**
     * Deserialize bytes to a wire message
     */
    open suspend fun deserialize(data: ByteArray): WireMessage {
        return try {
            val jsonString = data.toString(Charsets.UTF_8)
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            throw WireProtoException("Failed to deserialize message", e)
        }
    }
    
    /**
     * Create a new session
     */
    open suspend fun createSession(sessionId: String): WireSession {
        sessionMutex.withLock {
            val session = WireSession(sessionId)
            sessions[sessionId] = session
            return session
        }
    }
    
    /**
     * Get an existing session
     */
    open suspend fun getSession(sessionId: String): WireSession? {
        sessionMutex.withLock {
            return sessions[sessionId]
        }
    }
    
    /**
     * Update session activity
     */
    open suspend fun updateSessionActivity(sessionId: String) {
        sessionMutex.withLock {
            sessions[sessionId]?.let { session ->
                sessions[sessionId] = session.copy(lastActivity = System.currentTimeMillis())
            }
        }
    }
    
    /**
     * Validate message integrity
     */
    open fun validateMessage(message: WireMessage): Boolean {
        return message.sessionId.isNotEmpty() &&
               message.payload.isNotEmpty() &&
               message.timestamp > 0
    }
    
    /**
     * Filter messages based on predicate
     */
    open fun filterMessages(
        messages: List<WireMessage>,
        predicate: (WireMessage) -> Boolean
    ): List<WireMessage> {
        return messages.filter(predicate)
    }
    
    /**
     * Close a session
     */
    open suspend fun closeSession(sessionId: String) {
        sessionMutex.withLock {
            sessions.remove(sessionId)
        }
    }
    
    /**
     * Get all active sessions
     */
    open suspend fun getActiveSessions(): List<WireSession> {
        sessionMutex.withLock {
            return sessions.values.toList()
        }
    }
}

/**
 * LogStructuredWireProto - Inherits from WireProto
 * 
 * Adds log-structured storage capabilities:
 * - Append-only log for all messages
 * - Transaction support
 * - Log compaction and rotation
 * - Message replay functionality
 */
class LogStructuredWireProto(
    private val maxLogSize: Int = 10000
) : WireProto() {
    
    private val log = mutableListOf<WireMessage>()
    private val logMutex = Mutex()
    private val transactions = mutableMapOf<String, MutableList<WireMessage>>()
    private val transactionMutex = Mutex()
    private var sequenceCounter = 0L
    
    /**
     * Append message to log
     */
    suspend fun appendToLog(message: WireMessage) {
        logMutex.withLock {
            val logEntry = message.copy(sequenceNumber = ++sequenceCounter)
            log.add(logEntry)
            
            // Handle log rotation
            if (log.size > maxLogSize) {
                rotateLog()
            }
            
            // Update session activity
            updateSessionActivity(message.sessionId)
        }
    }
    
    /**
     * Get the complete log
     */
    suspend fun getLog(): List<WireMessage> {
        logMutex.withLock {
            return log.toList()
        }
    }
    
    /**
     * Get messages for a specific session
     */
    suspend fun getMessagesForSession(sessionId: String): List<WireMessage> {
        logMutex.withLock {
            return log.filter { it.sessionId == sessionId }
                .sortedBy { it.timestamp }
        }
    }
    
    /**
     * Compact the log by removing older messages
     */
    suspend fun compactLog() {
        logMutex.withLock {
            // Keep only the most recent 50% of messages
            val keepCount = log.size / 2
            if (keepCount > 0) {
                val toKeep = log.sortedBy { it.timestamp }.takeLast(keepCount)
                log.clear()
                log.addAll(toKeep)
            }
        }
    }
    
    /**
     * Rotate log when it exceeds maximum size
     */
    private fun rotateLog() {
        // Keep only the most recent messages up to max size
        val keepCount = (maxLogSize * 0.8).toInt() // Keep 80% after rotation
        if (log.size > keepCount) {
            val toKeep = log.takeLast(keepCount)
            log.clear()
            log.addAll(toKeep)
        }
    }
    
    /**
     * Begin a transaction
     */
    suspend fun beginTransaction(transactionId: String) {
        transactionMutex.withLock {
            transactions[transactionId] = mutableListOf()
        }
        
        // Log transaction begin
        appendToLog(WireMessage(
            sessionId = "system",
            payload = "BEGIN_TRANSACTION".toByteArray(),
            messageType = MessageType.TRANSACTION_BEGIN,
            timestamp = System.currentTimeMillis(),
            transactionId = transactionId
        ))
    }
    
    /**
     * Commit a transaction
     */
    suspend fun commitTransaction(transactionId: String) {
        transactionMutex.withLock {
            transactions[transactionId]?.let { transactionMessages ->
                // All messages in this transaction are already in the log
                // Mark transaction as committed
                appendToLog(WireMessage(
                    sessionId = "system",
                    payload = "COMMIT_TRANSACTION".toByteArray(),
                    messageType = MessageType.TRANSACTION_COMMIT,
                    timestamp = System.currentTimeMillis(),
                    transactionId = transactionId
                ))
            }
        }
    }
    
    /**
     * Get messages for a specific transaction
     */
    suspend fun getTransactionMessages(transactionId: String): List<WireMessage> {
        logMutex.withLock {
            return log.filter { it.transactionId == transactionId }
                .filter { it.messageType !in setOf(
                    MessageType.TRANSACTION_BEGIN,
                    MessageType.TRANSACTION_COMMIT,
                    MessageType.TRANSACTION_ROLLBACK
                )}
                .sortedBy { it.timestamp }
        }
    }
    
    /**
     * Replay log from a specific timestamp
     */
    suspend fun replayLog(
        fromTimestamp: Long = 0L,
        onMessage: (WireMessage) -> Unit
    ) {
        logMutex.withLock {
            log.filter { it.timestamp >= fromTimestamp }
                .sortedBy { it.timestamp }
                .forEach { onMessage(it) }
        }
    }
    
    /**
     * Override appendToLog to handle transactions
     */
    override suspend fun serialize(message: WireMessage): ByteArray {
        // Add to transaction if one is active
        message.transactionId?.let { txnId ->
            transactionMutex.withLock {
                transactions[txnId]?.add(message)
            }
        }
        
        return super.serialize(message)
    }
    
    /**
     * Get log statistics
     */
    suspend fun getLogStats(): LogStats {
        logMutex.withLock {
            val sessionCounts = log.groupingBy { it.sessionId }.eachCount()
            val typeCounts = log.groupingBy { it.messageType }.eachCount()
            
            return LogStats(
                totalMessages = log.size,
                oldestTimestamp = log.minOfOrNull { it.timestamp } ?: 0L,
                newestTimestamp = log.maxOfOrNull { it.timestamp } ?: 0L,
                sessionCounts = sessionCounts,
                typeCounts = typeCounts
            )
        }
    }
}

/**
 * Log statistics
 */
@Serializable
data class LogStats(
    val totalMessages: Int,
    val oldestTimestamp: Long,
    val newestTimestamp: Long,
    val sessionCounts: Map<String, Int>,
    val typeCounts: Map<MessageType, Int>
)

/**
 * Key types for the omnibus architecture
 */
@Serializable
data class SessionKey(
    val sessionId: String,
    val keyType: String = "SESSION",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Session key manager
 */
class SessionKeyManager {
    private val keys = mutableMapOf<String, SessionKey>()
    private val keyMutex = Mutex()
    
    suspend fun createKey(sessionId: String): SessionKey {
        keyMutex.withLock {
            val key = SessionKey(sessionId)
            keys[sessionId] = key
            return key
        }
    }
    
    suspend fun getKey(sessionId: String): SessionKey? {
        keyMutex.withLock {
            return keys[sessionId]
        }
    }
    
    suspend fun getAllKeys(): List<SessionKey> {
        keyMutex.withLock {
            return keys.values.toList()
        }
    }
}