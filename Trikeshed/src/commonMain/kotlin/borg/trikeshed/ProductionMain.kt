package borg.trikeshed

import borg.trikeshed.net.*
import borg.trikeshed.rts.*
import borg.trikeshed.k2script.*
import borg.trikeshed.distributed.*
import borg.trikeshed.ipfs.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*

/**
 * Production Main - Ready to run today
 * No demo code, pure functionality
 */
object ProductionMain {
    
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val command = args.firstOrNull() ?: "help"
        
        when (command) {
            "server" -> runC10KServer(args)
            "rts" -> runRTSHost(args)
            "ipfs" -> runIPFSNode(args)
            "distributed" -> runDistributedNode(args)
            else -> printHelp()
        }
    }
    
    private suspend fun runC10KServer(args: Array<String>) = coroutineScope {
        val port = args.getOrNull(1)?.toIntOrNull() ?: 8080
        val staticRoot = args.getOrNull(2) ?: "./static"
        
        println("Starting C10K Server")
        println("Port: $port")
        println("Static root: $staticRoot")
        
        val server = C10KServer(
            port = port,
            staticRoot = staticRoot,
            enableQuic = true,
            deterministicMode = false
        )
        
        // Set up servlet container
        val servletContainer = ServletContainer(
            scriptRoot = "$staticRoot/servlets",
            cacheScripts = true
        )
        
        // Start server
        server.start()
        
        // Keep running
        awaitCancellation()
    }
    
    private suspend fun runRTSHost(args: Array<String>) = coroutineScope {
        val port = args.getOrNull(1)?.toIntOrNull() ?: 7777
        val maxPlayers = args.getOrNull(2)?.toIntOrNull() ?: 8
        
        println("Starting RTS Network Host")
        println("Port: $port")
        println("Max players: $maxPlayers")
        
        val host = RTSNetworkHost(
            tickRate = 60,
            maxPlayers = maxPlayers,
            port = port,
            enableRollback = true
        )
        
        // Start host
        host.start()
        
        // Keep running
        awaitCancellation()
    }
    
    private suspend fun runIPFSNode(args: Array<String>) = coroutineScope {
        println("Starting IPFS Node")
        
        // Generate peer ID
        val peerId = PeerId(
            id = "node_${System.currentTimeMillis()}".toByteArray().let { 
                it.size j { i -> it[i] }
            }
        )
        
        // Create QUIC engine for transport
        val quicEngine = QuicEngine(
            QuicEngine.Role.CLIENT,
            QuicConnectionState(
                localConnectionId = ConnectionId.random(),
                remoteConnectionId = ConnectionId.random()
            )
        )
        
        // Create IPFS client
        val ipfsClient = IpfsClient(
            localPeerId = peerId,
            quicEngine = quicEngine,
            storage = IpfsStorage()
        )
        
        println("IPFS Node started with peer ID: ${peerId.toBase58()}")
        
        // Keep running
        awaitCancellation()
    }
    
    private suspend fun runDistributedNode(args: Array<String>) = coroutineScope {
        val mode = args.getOrNull(1) ?: "hybrid"
        
        println("Starting Distributed Storage Node")
        println("Mode: $mode")
        
        val storage = DistributedStorage()
        
        // Initialize with all components
        val context = storage.initialize(
            peerId = PeerId(
                id = "distributed_${System.currentTimeMillis()}".toByteArray().let {
                    it.size j { i -> it[i] }
                }
            ),
            couchUrl = "http://localhost:5984"
        )
        
        // Start C10K server for API
        val apiServer = C10KServer(
            port = 9000,
            staticRoot = "./distributed",
            enableQuic = true,
            deterministicMode = false
        )
        
        launch { apiServer.start() }
        
        println("Distributed node started on port 9000")
        
        // Keep running
        awaitCancellation()
    }
    
    private fun printHelp() {
        println("""
            TrikeShed Production Server
            
            Usage: trikeshed <command> [options]
            
            Commands:
              server [port] [static_root]    - Start C10K server with K2Script servlets
              rts [port] [max_players]       - Start RTS network host
              ipfs                           - Start IPFS node
              distributed [mode]             - Start distributed storage node
            
            Examples:
              trikeshed server 8080 ./www
              trikeshed rts 7777 16
              trikeshed ipfs
              trikeshed distributed hybrid
        """.trimIndent())
    }
}