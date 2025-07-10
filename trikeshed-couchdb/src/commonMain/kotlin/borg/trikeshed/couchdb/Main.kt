package borg.trikeshed.couchdb

import kotlinx.coroutines.*
import kotlinx.serialization.json.Json

/**
 * Main entry point for channelized CouchDB server
 * 
 * Demonstrates the full dogfooding exercise:
 * 1. Channelized blob service with all processors
 * 2. Channelized HTTP compositions  
 * 3. Real network bridge using trikeshed-net
 * 4. Full CouchDB protocol implementation
 */
suspend fun main() = coroutineScope {
    println("""
    ╔═══════════════════════════════════════════════════════╗
    ║   🚀 CHANNELIZED COUCHDB SERVER                       ║
    ║                                                       ║
    ║   Pure KMP Implementation using:                      ║
    ║   • ChannelizedBlobService                           ║
    ║   • Channelized HTTP Compositions                    ║
    ║   • trikeshed-net C10K Server                        ║
    ║   • QUIC Protocol Support                             ║
    ╚═══════════════════════════════════════════════════════╝
    """.trimIndent())
    
    try {
        // Launch the complete channelized system
        println("⚡ Launching channelized CouchDB with network bridge...")
        val bridge = launchChannelizedCouchDB(
            port = 5984,
            enableQuic = true
        )
        
        // Give it a moment to start
        delay(1000)
        
        // Exercise the dogfooding scenario
        println("\n🧪 Exercising dogfooding scenario...")
        exerciseChannelizedOperations(bridge)
        
        println("""
        
        ╔═══════════════════════════════════════════════════════╗
        ║   ✅ CHANNELIZED COUCHDB RUNNING                      ║
        ║                                                       ║
        ║   Network Endpoints:                                  ║
        ║   • http://localhost:5984                            ║
        ║   • quic://localhost:5984                            ║
        ║                                                       ║
        ║   Test Commands:                                      ║
        ║   curl http://localhost:5984/                        ║
        ║   curl -X PUT http://localhost:5984/fiduciary        ║
        ║   curl -X GET http://localhost:5984/_all_dbs         ║
        ║                                                       ║
        ║   All channelized processors active!                 ║
        ╚═══════════════════════════════════════════════════════╝
        """.trimIndent())
        
        // Keep running
        awaitCancellation()
        
    } catch (e: Exception) {
        println("❌ Failed to start channelized CouchDB: ${e.message}")
        e.printStackTrace()
    }
}

/**
 * Exercise the full dogfooding scenario using channelized compositions
 */
private suspend fun exerciseChannelizedOperations(bridge: CouchDBNetworkBridge) = coroutineScope {
    val couchServer = bridge.couchServer // Access the underlying server
    
    println("📋 Testing channelized HTTP compositions...")
    
    // Test 1: Server greeting
    launch {
        val request = ChannelizedHttpRequest("GET", "/")
        couchServer.httpRequestChannel.send(request)
        val response = couchServer.httpResponseChannel.receive()
        println("✅ GET / → ${response.status} (${response.body?.take(50)}...)")
    }
    
    delay(100)
    
    // Test 2: List databases (initially empty)
    launch {
        val request = ChannelizedHttpRequest("GET", "/_all_dbs")
        couchServer.httpRequestChannel.send(request)
        val response = couchServer.httpResponseChannel.receive()
        println("✅ GET /_all_dbs → ${response.status} (${response.body})")
    }
    
    delay(100)
    
    // Test 3: Create fiduciary database
    launch {
        val request = ChannelizedHttpRequest("PUT", "/fiduciary")
        couchServer.httpRequestChannel.send(request)
        val response = couchServer.httpResponseChannel.receive()
        println("✅ PUT /fiduciary → ${response.status} (${response.body})")
    }
    
    delay(100)
    
    // Test 4: Create patrick_devine_archives database
    launch {
        val request = ChannelizedHttpRequest("PUT", "/patrick_devine_archives")
        couchServer.httpRequestChannel.send(request)
        val response = couchServer.httpResponseChannel.receive()
        println("✅ PUT /patrick_devine_archives → ${response.status} (${response.body})")
    }
    
    delay(100)
    
    // Test 5: List databases (should show our new databases)
    launch {
        val request = ChannelizedHttpRequest("GET", "/_all_dbs")
        couchServer.httpRequestChannel.send(request)
        val response = couchServer.httpResponseChannel.receive()
        println("✅ GET /_all_dbs → ${response.status} (${response.body})")
    }
    
    delay(100)
    
    // Test 6: Store a document
    launch {
        val docBody = """{
            "_id": "fiduciary_config",
            "version": "1.0",
            "channelized": true,
            "processors": ["PUT", "GET", "UPDATE", "DELETE", "BULK"],
            "networking": "trikeshed-net"
        }"""
        
        val request = ChannelizedHttpRequest("PUT", "/fiduciary/fiduciary_config", body = docBody)
        couchServer.httpRequestChannel.send(request)
        val response = couchServer.httpResponseChannel.receive()
        println("✅ PUT /fiduciary/fiduciary_config → ${response.status} (${response.body})")
    }
    
    delay(100)
    
    // Test 7: Retrieve the document
    launch {
        val request = ChannelizedHttpRequest("GET", "/fiduciary/fiduciary_config")
        couchServer.httpRequestChannel.send(request)
        val response = couchServer.httpResponseChannel.receive()
        println("✅ GET /fiduciary/fiduciary_config → ${response.status} (${response.body?.take(100)}...)")
    }
    
    delay(100)
    
    // Test 8: Bulk operations
    launch {
        val bulkBody = """{
            "docs": [
                {"_id": "agent1", "type": "curation_agent", "ring": "core"},
                {"_id": "agent2", "type": "curation_agent", "ring": "dyad"},
                {"_id": "agent3", "type": "curation_agent", "ring": "triad"}
            ]
        }"""
        
        val request = ChannelizedHttpRequest("POST", "/fiduciary/_bulk_docs", body = bulkBody)
        couchServer.httpRequestChannel.send(request)
        val response = couchServer.httpResponseChannel.receive()
        println("✅ POST /fiduciary/_bulk_docs → ${response.status} (${response.body?.take(100)}...)")
    }
    
    delay(500) // Let all operations complete
    
    println("🎯 Dogfooding exercise completed - all channelized processors exercised!")
}