package borg.trikeshed.protocol

// Base protocol interface
interface Protocol {
    val name: String
    suspend fun connect(): Boolean
    suspend fun disconnect(): Boolean
    fun isConnected(): Boolean
}

// Transport protocol interface
interface TransportProtocol : Protocol {
    val transportType: String
}

// Application protocol interface
interface ApplicationProtocol : Protocol {
    val applicationType: String
}

// Example protocol tree structure
sealed class ProtocolNode(val protocol: Protocol) {
    class TransportNode(protocol: TransportProtocol) : ProtocolNode(protocol)
    class ApplicationNode(protocol: ApplicationProtocol) : ProtocolNode(protocol)
    // Add more node types as needed
}

// Example protocol family branches (placeholders)
object ProtocolFamilies {
    // Transport protocols
    object TCP : TransportProtocol {
        override val name = "TCP"
        override val transportType = "tcp"
        override suspend fun connect() = TODO()
        override suspend fun disconnect() = TODO()
        override fun isConnected() = TODO()
    }
    object UDP : TransportProtocol {
        override val name = "UDP"
        override val transportType = "udp"
        override suspend fun connect() = TODO()
        override suspend fun disconnect() = TODO()
        override fun isConnected() = TODO()
    }
    // Application protocols
    object HTTP : ApplicationProtocol {
        override val name = "HTTP"
        override val applicationType = "http"
        override suspend fun connect() = TODO()
        override suspend fun disconnect() = TODO()
        override fun isConnected() = TODO()
    }
    object WebSocket : ApplicationProtocol {
        override val name = "WebSocket"
        override val applicationType = "websocket"
        override suspend fun connect() = TODO()
        override suspend fun disconnect() = TODO()
        override fun isConnected() = TODO()
    }
    // Add more protocol families as needed
} 