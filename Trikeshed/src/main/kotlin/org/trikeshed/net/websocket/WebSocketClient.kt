package org.trikeshed.net.websocket

import org.trikeshed.util.Logger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class WebSocketClient {
    private val logger = Logger.getLogger<WebSocketClient>()
    private val client = HttpClient.newHttpClient()
    private val json = Json { 
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val _messages = MutableSharedFlow<WebSocketMessage>()
    val messages: SharedFlow<WebSocketMessage> = _messages.asSharedFlow()
    
    private var webSocket: WebSocket? = null
    
    fun connect(url: String) {
        val listener = object : WebSocket.Listener {
            override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*> {
                val message = json.decodeFromString<WebSocketMessage>(data.toString())
                _messages.tryEmit(message)
                return CompletableFuture.completedFuture(null)
            }
            
            override fun onError(webSocket: WebSocket, error: Throwable) {
                logger.severe("WebSocket error: ${error.message}")
            }
            
            override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String) {
                logger.info("WebSocket closed: $statusCode $reason")
            }
        }
        
        webSocket = client.newWebSocketBuilder()
            .buildAsync(URI(url), listener)
            .join()
    }
    
    fun send(message: WebSocketMessage) {
        webSocket?.sendText(json.encodeToString(message), true)
    }
    
    fun close() {
        webSocket?.sendClose(WebSocket.NORMAL_CLOSURE, "Client closing")
    }
} 