package com.superbikeshed.mcp

import com.superbikeshed.mcp.trikeshed.TrikeshedMcpServer
import com.superbikeshed.mcp.trikeshed.TrikeshedServiceRegistry
import com.superbikeshed.mcp.trikeshed.TrikeshedReactor
import com.superbikeshed.mcp.trikeshed.CouchDbMcpAdapter
import com.superbikeshed.mcp.trikeshed.MockCouchClient
import com.superbikeshed.mcp.trikeshed.QuicMcpAdapter
import borg.trikeshed.net.quic.QuicEngine
import borg.trikeshed.net.quic.QuicConnectionState
import borg.trikeshed.net.quic.QuicEngine.Role
import mu.KotlinLogging
import kotlinx.coroutines.*

internal val logger = KotlinLogging.logger {}

fun main() {
    runBlocking {
        logger.info { "Starting standalone Trikeshed MCP Server" }

        val serviceRegistry = TrikeshedServiceRegistry()
        val reactor = TrikeshedReactor()
        val mockCouchClient = MockCouchClient()

        val couchDbAdapter = CouchDbMcpAdapter(
            name = "couchdb-mcp-server",
            version = "1.0.0",
            capabilities = setOf("get", "put", "listDatabases", "createDatabase", "deleteDatabase"),
            couchClient = mockCouchClient
        )

        // Initialize QuicEngine with dummy values for now
        val quicEngine = QuicEngine(
            role = Role.SERVER,
            initialState = QuicConnectionState(
                localConnectionId = borg.trikeshed.net.quic.ConnectionId(byteArrayOf(1,2,3,4).toIndexed()),
                remoteConnectionId = borg.trikeshed.net.quic.ConnectionId(byteArrayOf(5,6,7,8).toIndexed())
            ),
            port = 8443, // Default QUIC port
            privateKey = byteArrayOf(0,0,0,0).toIndexed() // Dummy internal key
        )

        val quicAdapter = QuicMcpAdapter(
            name = "quic-mcp-server",
            version = "1.0.0",
            capabilities = setOf("connect", "createStream", "sendData", "closeConnection"),
            quicEngine = quicEngine
        )

        val mcpServer = TrikeshedMcpServer(
            name = "standalone-mcp-server",
            version = "1.0.0",
            capabilities = setOf("register", "invoke", "list"),
            serviceRegistry = serviceRegistry,
            reactor = reactor
        )

        // Register the CouchDB and QUIC adapters with the MCP server's service registry
        serviceRegistry.register(couchDbAdapter.name, couchDbAdapter)
        serviceRegistry.register(quicAdapter.name, quicAdapter)

        mcpServer.start()

        // Keep the server running until the application is shut down
        Runtime.getRuntime().addShutdownHook(Thread {
            mcpServer.stop()
        })

        // Keep main coroutine alive
        while (true) {
            delay(1000)
        }
    }
}