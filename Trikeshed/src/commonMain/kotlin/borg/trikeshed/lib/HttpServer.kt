package borg.trikeshed.lib

enum class ConnectionContext {
    PLAIN,
    SECURE;
    
    val isSecure: Boolean get() = this == SECURE
}

enum class MessageType {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH,
    HEAD,
    OPTIONS;
    
    val methodName: String get() = this.toString()
}

class HttpServer(private val context: ConnectionContext) {
    fun handleRequest(type: MessageType, path: String): String {
        // Implementation would depend on context and message type
        return ""
    }
    
    fun sendResponse(type: MessageType, data: String): Boolean {
        // Implementation would depend on context and message type
        return false
    }
} 