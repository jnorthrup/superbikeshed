// Simple launcher to run the existing CouchDB server
@file:JvmName("RunCouchDBNow")

package quicklaunch

import borg.trikeshed.couchdb.*
import kotlinx.coroutines.*

fun main() = runBlocking {
    println("🚀 LAUNCHING COUCHDB SERVER NOW!")
    
    val blobService = ChannelizedBlobService()
    val serverContext = newSingleThreadContext("CouchDBServer")
    val server = CouchDBServer(blobService, serverContext)
    
    // Start the server
    launch { server.start() }
    
    // Give it a moment to start
    delay(1000)
    
    println("✅ Server started!")
    println("📋 Now you need to send requests to the channels")
    println("   - Use server.httpRequestChannel.send(request)")
    println("   - Get responses from server.httpResponseChannel.receive()")
    
    // Create a simple HTTP bridge
    println("\n🌐 Starting HTTP bridge on port 5984...")
    startHttpBridge(server)
}

suspend fun startHttpBridge(server: CouchDBServer) = coroutineScope {
    try {
        val httpServer = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(5984), 0)
        
        httpServer.createContext("/") { exchange ->
            runBlocking {
                val method = exchange.requestMethod
                val path = exchange.requestURI.path
                val body = exchange.requestBody.readBytes().decodeToString()
                
                println("📨 $method $path")
                
                // Send to CouchDB server
                val request = MockHttpRequest(method, path, emptyMap(), body.ifEmpty { null })
                server.httpRequestChannel.send(request)
                
                // Get response
                val response = server.httpResponseChannel.receive()
                
                // Send HTTP response
                val responseBytes = (response.body ?: "").toByteArray()
                exchange.sendResponseHeaders(response.status, responseBytes.size.toLong())
                exchange.responseBody.use { os ->
                    os.write(responseBytes)
                }
            }
        }
        
        httpServer.start()
        println("✅ HTTP bridge started on http://localhost:5984")
        println("\n📋 Test commands:")
        println("   curl http://localhost:5984/")
        println("   curl -X PUT http://localhost:5984/test_db")
        println("   curl http://localhost:5984/_all_dbs")
        
    } catch (e: Exception) {
        println("❌ Failed to start HTTP bridge: ${e.message}")
    }
}