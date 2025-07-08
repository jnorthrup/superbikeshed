package com.superbikeshed.trikeshed

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

// Core Trikeshed contracts using expect/actual pattern

expect class TrikeshedRing {
    fun init(entries: UInt): Result<Unit>
    suspend fun submitRead(fd: Int, buffer: ByteArray, offset: Long): Result<Int>
    suspend fun submitWrite(fd: Int, buffer: ByteArray, offset: Long): Result<Int>
    suspend fun submitAccept(fd: Int): Result<Int>
    suspend fun submitConnect(fd: Int, address: SocketAddress): Result<Unit>
    suspend fun poll(): Flow<CompletionEvent>
    fun close()
}

expect class SocketAddress {
    companion object {
        fun inet(host: String, port: Int): SocketAddress
        fun unix(path: String): SocketAddress
    }
}

data class CompletionEvent(
    val fd: Int,
    val result: Int,
    val userData: Long
)

// CouchDB Protocol Contracts
expect interface CouchDBConnection {
    suspend fun handshake(): Result<CouchDBSession>
    suspend fun close()
}

expect interface CouchDBSession {
    suspend fun listDatabases(): Result<List<String>>
    suspend fun createDatabase(name: String): Result<DatabaseInfo>
    suspend fun deleteDatabase(name: String): Result<Unit>
    suspend fun getDocument(db: String, id: String): Result<Document>
    suspend fun putDocument(db: String, doc: Document): Result<DocumentResult>
    suspend fun deleteDocument(db: String, id: String, rev: String): Result<DocumentResult>
    suspend fun bulkDocs(db: String, docs: List<Document>): Result<List<DocumentResult>>
    suspend fun changes(db: String, since: String? = null): Flow<ChangeEvent>
    suspend fun replicate(source: String, target: String, continuous: Boolean = false): Result<ReplicationHandle>
}

data class DatabaseInfo(
    val dbName: String,
    val docCount: Long,
    val updateSeq: String
)

data class Document(
    val id: String,
    val rev: String? = null,
    val data: Map<String, Any?>,
    val attachments: Map<String, Attachment>? = null
)

data class Attachment(
    val contentType: String,
    val length: Long,
    val digest: String,
    val data: ByteArray? = null
)

data class DocumentResult(
    val id: String,
    val rev: String,
    val ok: Boolean
)

data class ChangeEvent(
    val seq: String,
    val id: String,
    val changes: List<Change>,
    val deleted: Boolean = false
)

data class Change(
    val rev: String
)

data class ReplicationHandle(
    val id: String,
    val continuous: Boolean
) {
    suspend fun cancel(): Result<Unit> = TODO()
    suspend fun status(): Result<ReplicationStatus> = TODO()
}

data class ReplicationStatus(
    val docsRead: Long,
    val docsWritten: Long,
    val lastSeq: String?
)

// QUIC Protocol Implementation (pure Kotlin)
expect class QUICConnection {
    suspend fun connect(address: SocketAddress): Result<Unit>
    suspend fun acceptStream(): Result<QUICStream>
    suspend fun openStream(): Result<QUICStream>
    suspend fun close()
}

expect class QUICStream {
    suspend fun write(data: ByteArray): Result<Int>
    suspend fun read(buffer: ByteArray): Result<Int>
    suspend fun finish(): Result<Unit>
    suspend fun close()
}

// Hermetic VM Contracts
expect interface TrikeshedVM {
    suspend fun spawn(config: VMConfig): Result<VMHandle>
}

data class VMConfig(
    val type: VMType,
    val memory: Long,
    val ipcChannel: Int? = null
)

enum class VMType {
    JVM,
    WASM
}

expect interface VMHandle {
    val pid: Long
    suspend fun sendMessage(message: ByteArray): Result<Unit>
    suspend fun receiveMessage(): Result<ByteArray>
    suspend fun terminate(): Result<Unit>
}

// IPC Protocol
sealed class IPCMessage {
    @Serializable
    data class Request(
        val id: String,
        val method: String,
        val params: Map<String, Any?>
    ) : IPCMessage()
    
    @Serializable
    data class Response(
        val id: String,
        val result: Any? = null,
        val error: String? = null
    ) : IPCMessage()
    
    @Serializable
    data class Notification(
        val event: String,
        val data: Any?
    ) : IPCMessage()
}