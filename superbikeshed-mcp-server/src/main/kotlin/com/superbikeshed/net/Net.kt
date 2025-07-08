package com.superbikeshed.net

import com.superbikeshed.common.*
import com.superbikeshed.json.*
import java.net.*
import java.io.*

interface NetworkManager : TrikeshedComponent, Lifecycle {
    fun bind(port: Int)
    fun <T> send(data: T)
    fun <T> receive(): T
    fun close()
}

class TrikeshedNetworkManager : NetworkManager {
    internal var serverSocket: ServerSocket? = null
    internal var clientSocket: Socket? = null
    internal var running = false
    internal val jsonSerializer = TrikeshedJsonSerializer()
    
    override fun getName(): String = "TrikeshedNetworkManager"
    override fun getVersion(): String = "1.0.0"
    
    override fun start() {
        running = true
    }
    
    override fun stop() {
        running = false
        close()
    }
    
    override fun isRunning(): Boolean = running
    
    override fun bind(port: Int) {
        serverSocket = ServerSocket(port)
    }
    
    override fun <T> send(data: T) {
        clientSocket?.let { socket ->
            val writer = PrintWriter(socket.getOutputStream(), true)
            val json = jsonSerializer.serialize(data)
            writer.println(json)
        }
    }
    
    override fun <T> receive(): T {
        return clientSocket?.let { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val json = reader.readLine()
            jsonSerializer.deserialize(json, Any::class.java) as T
        } ?: throw IllegalStateException("No client socket available")
    }
    
    override fun close() {
        clientSocket?.close()
        serverSocket?.close()
    }
} 