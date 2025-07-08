package com.superbikeshed.ipc

import com.superbikeshed.common.*
import com.superbikeshed.json.*

interface IpcManager : TrikeshedComponent, Lifecycle {
    fun sendMessage(target: String, message: String)
    fun receiveMessage(): String?
    fun registerHandler(handler: (String) -> String)
}

class TrikeshedIpcManager : IpcManager {
    internal var running = false
    internal var messageHandler: ((String) -> String)? = null
    
    override fun getName(): String = "TrikeshedIpcManager"
    override fun getVersion(): String = "1.0.0"
    
    override fun start() {
        running = true
    }
    
    override fun stop() {
        running = false
    }
    
    override fun isRunning(): Boolean = running
    
    override fun sendMessage(target: String, message: String) {
        // Simulate IPC message sending
        println("IPC: Sending to $target: $message")
    }
    
    override fun receiveMessage(): String? {
        // Simulate IPC message receiving
        return null
    }
    
    override fun registerHandler(handler: (String) -> String) {
        messageHandler = handler
    }
} 