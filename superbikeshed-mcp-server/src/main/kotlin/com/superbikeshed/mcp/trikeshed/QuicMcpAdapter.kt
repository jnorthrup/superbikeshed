package com.superbikeshed.mcp.trikeshed

import com.v2superbikeshed.nexus.rpc.McpError
import com.v2superbikeshed.nexus.rpc.McpRequest
import com.v2superbikeshed.nexus.rpc.McpResponse
import com.v2superbikeshed.nexus.rpc.McpServerInterface
import borg.trikeshed.net.quic.QuicConfig
import borg.trikeshed.net.quic.QuicConnection
import borg.trikeshed.net.quic.QuicEngine
import borg.trikeshed.net.quic.QuicSessionCache
import borg.trikeshed.net.quic.QuicSessionData
import borg.trikeshed.nio.PlatformByteBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging

internal val logger = KotlinLogging.logger {}

// Mock QuicSessionCache for now
class MockQuicSessionCache : QuicSessionCache {
    internal val sessions = mutableMapOf<Pair<String, Int>, QuicSessionData>()

    override fun getSession(serverAddress: String, port: Int): QuicSessionData? {
        logger.info { "MockQuicSessionCache: Getting session for $serverAddress:$port" }
        return sessions[Pair(serverAddress, port)]
    }

    override fun storeSession(serverAddress: String, port: Int, sessionData: QuicSessionData) {
        logger.info { "MockQuicSessionCache: Storing session for $serverAddress:$port" }
        sessions[Pair(serverAddress, port)] = sessionData
    }

    override fun clearSession(serverAddress: String, port: Int) {
        logger.info { "MockQuicSessionCache: Clearing session for $serverAddress:$port" }
        sessions.remove(Pair(serverAddress, port))
    }
}

class QuicMcpAdapter(
    override val name: String,
    override val version: String,
    override val capabilities: Set<String>,
    internal val quicEngine: QuicEngine // Using QuicEngine for simplicity
) : McpServerInterface {

    internal val connections = mutableMapOf<String, QuicConnection>()
    internal val sessionCache = MockQuicSessionCache()
    internal val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override suspend fun handleRequest(request: McpRequest): McpResponse {
        return try {
            when (request.method) {
                "connect" -> {
                    val serverAddress = request.params?.jsonObject?.get("serverAddress")?.jsonPrimitive?.content
                    val port = request.params?.jsonObject?.get("port")?.jsonPrimitive?.content?.toIntOrNull()
                    val connectionId = request.params?.jsonObject?.get("connectionId")?.jsonPrimitive?.content

                    if (serverAddress != null && port != null && connectionId != null) {
                        val config = QuicConfig() // Default config for now
                        val connection = QuicConnection(config, sessionCache, scope)
                        connection.connectWith0RTT(serverAddress, port) // Attempt 0-RTT
                        connections[connectionId] = connection
                        McpResponse(
                            jsonrpc = "2.0",
                            id = request.id,
                            result = JsonPrimitive("Connected to $serverAddress:$port with ID $connectionId")
                        )
                    } else {
                        McpResponse(
                            jsonrpc = "2.0",
                            id = request.id,
                            error = McpError(-32602, "Invalid 'connect' request: missing serverAddress, port, or connectionId")
                        )
                    }
                }
                "createStream" -> {
                    val connectionId = request.params?.jsonObject?.get("connectionId")?.jsonPrimitive?.content
                    val priority = request.params?.jsonObject?.get("priority")?.jsonPrimitive?.content?.toIntOrNull()

                    if (connectionId != null) {
                        val connection = connections[connectionId]
                        if (connection != null) {
                            val stream = connection.createStream(priority)
                            McpResponse(
                                jsonrpc = "2.0",
                                id = request.id,
                                result = JsonPrimitive(stream.id.toString())
                            )
                        }
                        else {
                            McpResponse(
                                jsonrpc = "2.0",
                                id = request.id,
                                error = McpError(-32601, "Connection $connectionId not found")
                            )
                        }
                    } else {
                        McpResponse(
                            jsonrpc = "2.0",
                            id = request.id,
                            error = McpError(-32602, "Invalid 'createStream' request: missing connectionId")
                        )
                    }
                }
                "sendData" -> {
                    val connectionId = request.params?.jsonObject?.get("connectionId")?.jsonPrimitive?.content
                    val streamId = request.params?.jsonObject?.get("streamId")?.jsonPrimitive?.content?.toLongOrNull()
                    val data = request.params?.jsonObject?.get("data")?.jsonPrimitive?.content // Base64 encoded string

                    if (connectionId != null && streamId != null && data != null) {
                        val connection = connections[connectionId]
                        if (connection != null) {
                            val byteBuffer = PlatformByteBuffer.wrap(data.encodeToByteArray()) // Assuming data is plain string for now
                            val success = connection.sendData(streamId, byteBuffer)
                            McpResponse(
                                jsonrpc = "2.0",
                                id = request.id,
                                result = JsonPrimitive(success)
                            )
                        } else {
                            McpResponse(
                                jsonrpc = "2.0",
                                id = request.id,
                                error = McpError(-32601, "Connection $connectionId not found")
                            )
                        }
                    } else {
                        McpResponse(
                            jsonrpc = "2.0",
                            id = request.id,
                            error = McpError(-32602, "Invalid 'sendData' request: missing connectionId, streamId, or data")
                        )
                    }
                }
                "closeConnection" -> {
                    val connectionId = request.params?.jsonObject?.get("connectionId")?.jsonPrimitive?.content
                    if (connectionId != null) {
                        val connection = connections[connectionId]
                        if (connection != null) {
                            connection.close()
                            connections.remove(connectionId)
                            McpResponse(
                                jsonrpc = "2.0",
                                id = request.id,
                                result = JsonPrimitive("Connection $connectionId closed")
                            )
                        } else {
                            McpResponse(
                                jsonrpc = "2.0",
                                id = request.id,
                                error = McpError(-32601, "Connection $connectionId not found")
                            )
                        }
                    } else {
                        McpResponse(
                            jsonrpc = "2.0",
                            id = request.id,
                            error = McpError(-32602, "Invalid 'closeConnection' request: missing connectionId")
                        )
                    }
                }
                else -> {
                    McpResponse(
                        jsonrpc = "2.0",
                        id = request.id,
                        error = McpError(-32601, "Unknown method: ${request.method}")
                    )
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error handling QUIC MCP request: ${e.message}" }
            McpResponse(
                jsonrpc = "2.0",
                id = request.id,
                error = McpError(-32603, "Internal server error: ${e.message}", JsonPrimitive(e.stackTraceToString()))
            )
        }
    }
}
