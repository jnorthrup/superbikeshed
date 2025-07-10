package borg.trikeshed.couchdb

import borg.trikeshed.channel.api.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * QUIC protocol adapter for CouchDB operations.
 * Implements CouchDB REST API over QUIC transport.
 */

@Serializable
data class QuicCouchDBConfig(
    val serverHost: String = "localhost",
    val serverPort: Int = 5984,
    val maxPacketSize: Int = 1200,
    val connectionTimeout: Long = 30000,
    val streamTimeout: Long = 10000
)

@Serializable
sealed class QuicCouchDBMessage {
    @Serializable
    data class DocumentPut(
        val method: String = "PUT",
        val database: String,
        val documentId: String,
        val document: String,
        val revision: String? = null,
        val headers: Map<String, String> = emptyMap()
    ) : QuicCouchDBMessage()
    
    @Serializable
    data class DocumentGet(
        val method: String = "GET",
        val database: String,
        val documentId: String,
        val revision: String? = null,
        val headers: Map<String, String> = emptyMap()
    ) : QuicCouchDBMessage()
    
    @Serializable
    data class DocumentDelete(
        val method: String = "DELETE",
        val database: String,
        val documentId: String,
        val revision: String,
        val headers: Map<String, String> = emptyMap()
    ) : QuicCouchDBMessage()
    
    @Serializable
    data class DatabaseCreate(
        val method: String = "PUT",
        val database: String,
        val headers: Map<String, String> = emptyMap()
    ) : QuicCouchDBMessage()
    
    @Serializable
    data class DatabaseList(
        val method: String = "GET",
        val path: String = "_all_dbs",
        val headers: Map<String, String> = emptyMap()
    ) : QuicCouchDBMessage()
    
    @Serializable
    data class BulkDocs(
        val method: String = "POST",
        val database: String,
        val path: String = "_bulk_docs",
        val docs: List<String>,
        val headers: Map<String, String> = emptyMap()
    ) : QuicCouchDBMessage()
    
    @Serializable
    data class Response(
        val statusCode: Int,
        val headers: Map<String, String> = emptyMap(),
        val body: String,
        val success: Boolean = true,
        val error: String? = null
    ) : QuicCouchDBMessage()
}

/**
 * QUIC CouchDB Protocol Adapter implementing the unified protocol interface.
 */
class QuicCouchDBAdapter(
    override val config: QuicCouchDBConfig
) : AbstractProtocolAdapter<QuicCouchDBMessage, QuicCouchDBConfig>(config) {
    
    override val protocolName: String = "QUIC-CouchDB"
    
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    override suspend fun parseMessage(data: ByteIndexed, position: Int): Join<QuicCouchDBMessage?, Int>? {
        return try {
            // QUIC framing: [length:4][type:1][data:length-1]
            if (position + 5 > data.a) return null // Need at least 5 bytes
            
            val length = ProtocolUtils.readUint32(data, position).toInt()
            if (position + 4 + length > data.a) return null // Need complete message
            
            val messageType = data[position + 4].toInt()
            val messageStart = position + 5
            val messageEnd = messageStart + length - 1
            
            val messageData = data.slice(messageStart until messageEnd)
            val messageJson = messageData.decodeToString()
            
            val message = when (messageType) {
                0x01 -> json.decodeFromString<QuicCouchDBMessage.DocumentPut>(messageJson)
                0x02 -> json.decodeFromString<QuicCouchDBMessage.DocumentGet>(messageJson)
                0x03 -> json.decodeFromString<QuicCouchDBMessage.DocumentDelete>(messageJson)
                0x04 -> json.decodeFromString<QuicCouchDBMessage.DatabaseCreate>(messageJson)
                0x05 -> json.decodeFromString<QuicCouchDBMessage.DatabaseList>(messageJson)
                0x06 -> json.decodeFromString<QuicCouchDBMessage.BulkDocs>(messageJson)
                0x10 -> json.decodeFromString<QuicCouchDBMessage.Response>(messageJson)
                else -> return null
            }
            
            (message to (position + 4 + length)) j { it }
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun serializeMessage(message: QuicCouchDBMessage): ByteIndexed {
        val messageJson = json.encodeToString(message)
        val messageData = messageJson.encodeToByteArray()
        
        val messageType = when (message) {
            is QuicCouchDBMessage.DocumentPut -> 0x01
            is QuicCouchDBMessage.DocumentGet -> 0x02
            is QuicCouchDBMessage.DocumentDelete -> 0x03
            is QuicCouchDBMessage.DatabaseCreate -> 0x04
            is QuicCouchDBMessage.DatabaseList -> 0x05
            is QuicCouchDBMessage.BulkDocs -> 0x06
            is QuicCouchDBMessage.Response -> 0x10
        }.toByte()
        
        val length = messageData.size + 1 // +1 for message type
        val frame = ByteArray(4 + 1 + messageData.size)
        
        // Write length (4 bytes)
        ProtocolUtils.writeUint32(frame, 0, length.toLong())
        // Write message type (1 byte)
        frame[4] = messageType
        // Write message data
        messageData.copyInto(frame, 5)
        
        return frame.toIndexed()
    }
    
    override suspend fun handleMessage(
        message: QuicCouchDBMessage, 
        context: ProtocolContext
    ): Flow<QuicCouchDBMessage> = flow {
        when (message) {
            is QuicCouchDBMessage.DocumentPut -> {
                emit(handleDocumentPut(message, context))
            }
            is QuicCouchDBMessage.DocumentGet -> {
                emit(handleDocumentGet(message, context))
            }
            is QuicCouchDBMessage.DocumentDelete -> {
                emit(handleDocumentDelete(message, context))
            }
            is QuicCouchDBMessage.DatabaseCreate -> {
                emit(handleDatabaseCreate(message, context))
            }
            is QuicCouchDBMessage.DatabaseList -> {
                emit(handleDatabaseList(message, context))
            }
            is QuicCouchDBMessage.BulkDocs -> {
                emit(handleBulkDocs(message, context))
            }
            is QuicCouchDBMessage.Response -> {
                emit(message) // Echo responses back
            }
        }
    }
    
    override fun wrapChannel(channel: Channel): ProtocolChannel<QuicCouchDBMessage> {
        return QuicCouchDBChannel(this, channel)
    }
    
    private suspend fun handleDocumentPut(
        message: QuicCouchDBMessage.DocumentPut,
        context: ProtocolContext
    ): QuicCouchDBMessage.Response {
        return try {
            val blobService = context.currentService<ChannelizedBlobService>()
            if (blobService != null) {
                val docData = message.document.encodeToByteArray()
                val response = blobService.putBlob(
                    dbName = message.database,
                    id = message.documentId,
                    data = docData,
                    context = context
                )
                
                if (response.success) {
                    QuicCouchDBMessage.Response(
                        statusCode = 201,
                        headers = mapOf("Content-Type" to "application/json"),
                        body = """{"ok":true,"id":"${message.documentId}","rev":"${response.rev}"}""",
                        success = true
                    )
                } else {
                    QuicCouchDBMessage.Response(
                        statusCode = 409,
                        body = """{"error":"conflict","reason":"${response.message}"}""",
                        success = false,
                        error = response.message
                    )
                }
            } else {
                QuicCouchDBMessage.Response(
                    statusCode = 500,
                    body = """{"error":"internal_server_error","reason":"CouchDB service not available"}""",
                    success = false,
                    error = "Service unavailable"
                )
            }
        } catch (e: Exception) {
            QuicCouchDBMessage.Response(
                statusCode = 500,
                body = """{"error":"internal_server_error","reason":"${e.message}"}""",
                success = false,
                error = e.message
            )
        }
    }
    
    private suspend fun handleDocumentGet(
        message: QuicCouchDBMessage.DocumentGet,
        context: ProtocolContext
    ): QuicCouchDBMessage.Response {
        return try {
            val blobService = context.currentService<ChannelizedBlobService>()
            if (blobService != null) {
                val response = blobService.getBlob(
                    dbName = message.database,
                    id = message.documentId,
                    context = context
                )
                
                if (response.found && response.data != null) {
                    QuicCouchDBMessage.Response(
                        statusCode = 200,
                        headers = mapOf("Content-Type" to "application/json"),
                        body = response.data.decodeToString(),
                        success = true
                    )
                } else {
                    QuicCouchDBMessage.Response(
                        statusCode = 404,
                        body = """{"error":"not_found","reason":"missing"}""",
                        success = false,
                        error = "Document not found"
                    )
                }
            } else {
                QuicCouchDBMessage.Response(
                    statusCode = 500,
                    body = """{"error":"internal_server_error","reason":"CouchDB service not available"}""",
                    success = false,
                    error = "Service unavailable"
                )
            }
        } catch (e: Exception) {
            QuicCouchDBMessage.Response(
                statusCode = 500,
                body = """{"error":"internal_server_error","reason":"${e.message}"}""",
                success = false,
                error = e.message
            )
        }
    }
    
    private suspend fun handleDocumentDelete(
        message: QuicCouchDBMessage.DocumentDelete,
        context: ProtocolContext
    ): QuicCouchDBMessage.Response {
        return try {
            val blobService = context.currentService<ChannelizedBlobService>()
            if (blobService != null) {
                val response = blobService.deleteBlob(
                    dbName = message.database,
                    id = message.documentId,
                    rev = message.revision,
                    context = context
                )
                
                if (response.success) {
                    QuicCouchDBMessage.Response(
                        statusCode = 200,
                        headers = mapOf("Content-Type" to "application/json"),
                        body = """{"ok":true,"id":"${message.documentId}","rev":"${response.rev}"}""",
                        success = true
                    )
                } else {
                    QuicCouchDBMessage.Response(
                        statusCode = 409,
                        body = """{"error":"conflict","reason":"${response.message}"}""",
                        success = false,
                        error = response.message
                    )
                }
            } else {
                QuicCouchDBMessage.Response(
                    statusCode = 500,
                    body = """{"error":"internal_server_error","reason":"CouchDB service not available"}""",
                    success = false,
                    error = "Service unavailable"
                )
            }
        } catch (e: Exception) {
            QuicCouchDBMessage.Response(
                statusCode = 500,
                body = """{"error":"internal_server_error","reason":"${e.message}"}""",
                success = false,
                error = e.message
            )
        }
    }
    
    private suspend fun handleDatabaseCreate(
        message: QuicCouchDBMessage.DatabaseCreate,
        context: ProtocolContext
    ): QuicCouchDBMessage.Response {
        return try {
            val blobService = context.currentService<ChannelizedBlobService>()
            if (blobService != null) {
                val response = blobService.createDb(message.database, context)
                
                if (response.success) {
                    QuicCouchDBMessage.Response(
                        statusCode = 201,
                        headers = mapOf("Content-Type" to "application/json"),
                        body = """{"ok":true}""",
                        success = true
                    )
                } else {
                    QuicCouchDBMessage.Response(
                        statusCode = 412,
                        body = """{"error":"file_exists","reason":"${response.message}"}""",
                        success = false,
                        error = response.message
                    )
                }
            } else {
                QuicCouchDBMessage.Response(
                    statusCode = 500,
                    body = """{"error":"internal_server_error","reason":"CouchDB service not available"}""",
                    success = false,
                    error = "Service unavailable"
                )
            }
        } catch (e: Exception) {
            QuicCouchDBMessage.Response(
                statusCode = 500,
                body = """{"error":"internal_server_error","reason":"${e.message}"}""",
                success = false,
                error = e.message
            )
        }
    }
    
    private suspend fun handleDatabaseList(
        message: QuicCouchDBMessage.DatabaseList,
        context: ProtocolContext
    ): QuicCouchDBMessage.Response {
        return try {
            val blobService = context.currentService<ChannelizedBlobService>()
            if (blobService != null) {
                val response = blobService.listDbs(context)
                
                if (response.success) {
                    val dbListJson = json.encodeToString(response.dbNames)
                    QuicCouchDBMessage.Response(
                        statusCode = 200,
                        headers = mapOf("Content-Type" to "application/json"),
                        body = dbListJson,
                        success = true
                    )
                } else {
                    QuicCouchDBMessage.Response(
                        statusCode = 500,
                        body = """{"error":"internal_server_error","reason":"${response.message}"}""",
                        success = false,
                        error = response.message
                    )
                }
            } else {
                QuicCouchDBMessage.Response(
                    statusCode = 500,
                    body = """{"error":"internal_server_error","reason":"CouchDB service not available"}""",
                    success = false,
                    error = "Service unavailable"
                )
            }
        } catch (e: Exception) {
            QuicCouchDBMessage.Response(
                statusCode = 500,
                body = """{"error":"internal_server_error","reason":"${e.message}"}""",
                success = false,
                error = e.message
            )
        }
    }
    
    private suspend fun handleBulkDocs(
        message: QuicCouchDBMessage.BulkDocs,
        context: ProtocolContext
    ): QuicCouchDBMessage.Response {
        return try {
            val blobService = context.currentService<ChannelizedBlobService>()
            if (blobService != null) {
                val docBytes = message.docs.map { it.encodeToByteArray() }
                val response = blobService.bulkDocs(message.database, docBytes, context)
                
                if (response.success) {
                    val resultsJson = json.encodeToString(response.results)
                    QuicCouchDBMessage.Response(
                        statusCode = 201,
                        headers = mapOf("Content-Type" to "application/json"),
                        body = resultsJson,
                        success = true
                    )
                } else {
                    QuicCouchDBMessage.Response(
                        statusCode = 500,
                        body = """{"error":"internal_server_error","reason":"${response.message}"}""",
                        success = false,
                        error = response.message
                    )
                }
            } else {
                QuicCouchDBMessage.Response(
                    statusCode = 500,
                    body = """{"error":"internal_server_error","reason":"CouchDB service not available"}""",
                    success = false,
                    error = "Service unavailable"
                )
            }
        } catch (e: Exception) {
            QuicCouchDBMessage.Response(
                statusCode = 500,
                body = """{"error":"internal_server_error","reason":"${e.message}"}""",
                success = false,
                error = e.message
            )
        }
    }
}

/**
 * QUIC CouchDB Channel implementation.
 */
class QuicCouchDBChannel(
    adapter: QuicCouchDBAdapter,
    channel: Channel
) : EnhancedProtocolChannel<QuicCouchDBMessage>(adapter, channel) {
    
    /**
     * Get all document responses.
     */
    fun documentResponses(): Flow<QuicCouchDBMessage.Response> = 
        filterIncomingMessages<QuicCouchDBMessage.Response>()
    
    /**
     * Get successful document responses.
     */
    fun successfulResponses(): Flow<QuicCouchDBMessage.Response> =
        filterAndTransform<QuicCouchDBMessage.Response, QuicCouchDBMessage.Response> { response ->
            if (response.success) response else null
        }.filterNotNull()
    
    /**
     * Get error responses.
     */
    fun errorResponses(): Flow<QuicCouchDBMessage.Response> =
        filterAndTransform<QuicCouchDBMessage.Response, QuicCouchDBMessage.Response> { response ->
            if (!response.success) response else null
        }.filterNotNull()
}

// Extension function to filter non-null results from flows
fun <T> Flow<T?>.filterNotNull(): Flow<T> = flow {
    collect { value ->
        if (value != null) {
            emit(value)
        }
    }
}