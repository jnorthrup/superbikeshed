#!/usr/bin/env kotlin

// EMERGENCY BLOB SERVER - Just run with: kotlin EmergencyBlobServer.kts

import java.net.ServerSocket
import java.net.Socket
import java.io.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

val databases = ConcurrentHashMap<String, MutableMap<String, String>>()

fun main() {
    println("🚀 EMERGENCY BLOB SERVER STARTING...")
    
    // Create default databases
    databases["_users"] = mutableMapOf()
    databases["fiduciary"] = mutableMapOf()
    
    val server = ServerSocket(5984)
    println("✅ Server listening on http://localhost:5984")
    println("📋 Test with: curl http://localhost:5984/")
    
    while (true) {
        val client = server.accept()
        thread { handleClient(client) }
    }
}

fun handleClient(socket: Socket) {
    try {
        val input = BufferedReader(InputStreamReader(socket.getInputStream()))
        val output = PrintWriter(socket.getOutputStream(), true)
        
        val request = input.readLine() ?: return
        val parts = request.split(" ")
        if (parts.size < 2) return
        
        val method = parts[0]
        val path = parts[1]
        
        // Read headers
        var contentLength = 0
        var line: String?
        while (input.readLine().also { line = it } != null && line!!.isNotEmpty()) {
            if (line!!.startsWith("Content-Length:")) {
                contentLength = line!!.substringAfter(":").trim().toInt()
            }
        }
        
        // Read body if present
        val body = if (contentLength > 0) {
            val buffer = CharArray(contentLength)
            input.read(buffer, 0, contentLength)
            String(buffer)
        } else null
        
        println("$method $path")
        
        val response = when {
            path == "/" && method == "GET" -> {
                """{"couchdb":"Welcome","version":"emergency-1.0"}"""
            }
            path == "/_all_dbs" && method == "GET" -> {
                "[${databases.keys.joinToString(",") { "\"$it\"" }}]"
            }
            path.startsWith("/") && path.count { it == '/' } == 1 && method == "PUT" -> {
                val dbName = path.substring(1)
                if (databases.containsKey(dbName)) {
                    output.println("HTTP/1.1 409 Conflict")
                    output.println("Content-Type: application/json")
                    output.println()
                    """{"error":"file_exists","reason":"The database could not be created, the file already exists."}"""
                } else {
                    databases[dbName] = mutableMapOf()
                    """{"ok":true}"""
                }
            }
            path.count { it == '/' } == 2 -> {
                val pathParts = path.split("/").filter { it.isNotEmpty() }
                val dbName = pathParts[0]
                val docId = pathParts[1]
                val db = databases[dbName]
                
                when (method) {
                    "GET" -> {
                        if (db != null && db.containsKey(docId)) {
                            db[docId]!!
                        } else {
                            output.println("HTTP/1.1 404 Not Found")
                            output.println("Content-Type: application/json")
                            output.println()
                            """{"error":"not_found","reason":"missing"}"""
                        }
                    }
                    "PUT" -> {
                        if (db != null && body != null) {
                            db[docId] = body
                            """{"ok":true,"id":"$docId","rev":"1-${body.hashCode()}"}"""
                        } else {
                            output.println("HTTP/1.1 400 Bad Request")
                            output.println("Content-Type: application/json")
                            output.println()
                            """{"error":"bad_request"}"""
                        }
                    }
                    else -> {
                        output.println("HTTP/1.1 405 Method Not Allowed")
                        output.println("Content-Type: application/json")
                        output.println()
                        """{"error":"method_not_allowed"}"""
                    }
                }
            }
            else -> {
                output.println("HTTP/1.1 404 Not Found")
                output.println("Content-Type: application/json")
                output.println()
                """{"error":"not_found"}"""
            }
        }
        
        // Send response
        if (!output.checkError()) {
            output.println("HTTP/1.1 200 OK")
            output.println("Content-Type: application/json")
            output.println("Content-Length: ${response.length}")
            output.println()
            output.print(response)
        }
        
        socket.close()
    } catch (e: Exception) {
        println("Error: ${e.message}")
    }
}

main()