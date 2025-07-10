@file:JvmName("LaunchFiduciary")
package borg.trikeshed.launcher

import kotlinx.coroutines.*
import borg.trikeshed.dht.kademlia.id.NUID
import fiduciary.concentric.*
import kotlin.system.exitProcess

/**
 * LAUNCH THE EXISTING URINGCOUCHDBSERVER
 * 
 * We already built it all - just need to start it!
 */
fun main(args: Array<String>) = runBlocking {
    println("""
    ╔═══════════════════════════════════════════════════════╗
    ║   🚀 LAUNCHING URINGCOUCHDBSERVER 🚀                  ║
    ║                                                       ║
    ║   Using the EXISTING implementation!                  ║
    ╚═══════════════════════════════════════════════════════╝
    """.trimIndent())
    
    try {
        // Create platform launcher
        println("⚡ Initializing PlatformLauncher...")
        val launcher = PlatformLauncher()
        launcher.initialize()
        println("✅ PlatformLauncher initialized")
        
        // Create the EXISTING UringCouchDBServer
        println("\n🔥 Creating UringCouchDBServer...")
        val server = UringCouchDBServer(
            port = 5984,
            quicPort = 5985,
            ipfsPort = 5986,
            launcher = launcher
        )
        
        // Initialize it
        println("🔧 Initializing server components...")
        val initResult = server.initialize()
        if (initResult.isSuccess) {
            println("✅ Server initialized successfully")
            println("   - io_uring: ${if (server.isUringActive()) "ACTIVE" else "INACTIVE"}")
            println("   - Type: ${server.getUringType()}")
        } else {
            println("❌ Server initialization failed: ${initResult.exceptionOrNull()?.message}")
            exitProcess(1)
        }
        
        // Start the server
        println("\n🚀 Starting server...")
        server.start()
        println("✅ Server started!")
        
        // Initialize agent network
        println("\n🕸️ Initializing concentric agent network...")
        val agentNetwork = server.initializeAgentNetwork()
        println("✅ Agent network initialized:")
        agentNetwork.forEach { (ring, agents) ->
            println("   - ${ring.name}: ${agents.size} agents")
        }
        
        // Create some initial databases
        println("\n📦 Creating initial databases...")
        createInitialDatabases(server)
        
        // Start some curation agents
        println("\n🤖 Starting curation agents...")
        startCurationAgents(server)
        
        println("""
        
        ╔═══════════════════════════════════════════════════════╗
        ║   🟢 URINGCOUCHDBSERVER IS RUNNING!                   ║
        ║                                                       ║
        ║   REST API:  http://localhost:5984                   ║
        ║   QUIC API:  quic://localhost:5985                   ║
        ║   IPFS API:  http://localhost:5986                   ║
        ║                                                       ║
        ║   Test commands:                                      ║
        ║   curl -X GET http://localhost:5984/                 ║
        ║   curl -X PUT http://localhost:5984/test_db          ║
        ║   curl -X GET http://localhost:5984/_all_dbs         ║
        ║                                                       ║
        ║   Press Ctrl+C to shutdown                            ║
        ╚═══════════════════════════════════════════════════════╝
        """.trimIndent())
        
        // Keep running
        awaitCancellation()
        
    } catch (e: CancellationException) {
        println("\n🛑 Shutdown signal received")
    } catch (e: Exception) {
        println("\n❌ Fatal error: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    } finally {
        println("👋 Shutting down...")
    }
}

private suspend fun createInitialDatabases(server: UringCouchDBServer) {
    val databases = listOf(
        "_users",
        "_replicator",
        "fiduciary_ledger",
        "patrick_devine_archives",
        "agent_coordination"
    )
    
    databases.forEach { db ->
        try {
            val response = server.handleRestRequest("PUT", "/$db", null)
            println("   - $db: ${if (response.statusCode == 201) "✅ created" else "⚠️ ${response.statusCode}"}")
        } catch (e: Exception) {
            println("   - $db: ❌ failed - ${e.message}")
        }
    }
}

private suspend fun startCurationAgents(server: UringCouchDBServer) {
    // The server already has agent initialization built in
    // Just need to ensure they're active
    try {
        val agents = server.getActiveAgents()
        println("   - Active agents: ${agents.size}")
        
        // Submit a test task
        val testTask = ConcentricTask(
            id = NUID.random(),
            type = TaskType.CONTENT_INGESTION,
            payload = "Test task".toByteArray(),
            priority = TaskPriority.NORMAL
        )
        
        server.submitTask(testTask)
        println("   - Test task submitted")
    } catch (e: Exception) {
        println("   - Agent activation failed: ${e.message}")
    }
}