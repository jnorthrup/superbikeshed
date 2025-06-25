package borg.trikeshed.net.quic

/**
 * Sealed class hierarchy for QUIC errors
 */
sealed class QuicError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /**
     * Connection-level errors
     */
    sealed class ConnectionError(message: String, cause: Throwable? = null) : QuicError(message, cause) {
        class NotConnected : ConnectionError("QUIC connection not established")
        class ConnectionClosed : ConnectionError("QUIC connection already closed")
        class FlowControlBlocked(val windowSize: Long, val attempted: Long) : 
            ConnectionError("Connection flow control blocked: window=$windowSize, attempted=$attempted")
        class HandshakeFailed(cause: Throwable? = null) : 
            ConnectionError("QUIC handshake failed", cause)
        class InvalidState(message: String) : ConnectionError(message)
    }

    /**
     * Stream-level errors
     */
    sealed class StreamError(message: String, cause: Throwable? = null) : QuicError(message, cause) {
        class StreamNotFound(val streamId: Long) : 
            StreamError("Stream $streamId not found")
        class StreamClosed(val streamId: Long) : 
            StreamError("Stream $streamId is closed")
        class FlowControlBlocked(val streamId: Long, val windowSize: Long, val attempted: Long) :
            StreamError("Stream $streamId flow control blocked: window=$windowSize, attempted=$attempted")
        class InvalidStreamId(val streamId: Long) :
            StreamError("Invalid stream ID: $streamId")
        class StreamLimitExceeded :
            StreamError("Maximum number of streams exceeded")
    }

    /**
     * Protocol-level errors
     */
    sealed class ProtocolError(message: String, cause: Throwable? = null) : QuicError(message, cause) {
        class InvalidPacket(message: String) : ProtocolError(message)
        class VersionMismatch(val local: Long, val remote: Long) :
            ProtocolError("QUIC version mismatch: local=$local, remote=$remote")
        class CryptoError(message: String, cause: Throwable? = null) :
            ProtocolError(message, cause)
    }

    /**
     * Transport-level errors
     */
    sealed class TransportError(message: String, cause: Throwable? = null) : QuicError(message, cause) {
        class NetworkError(message: String, cause: Throwable? = null) :
            TransportError(message, cause)
        class PacketTooLarge(val size: Int, val mtu: Int) :
            TransportError("Packet size $size exceeds MTU $mtu")
    }
} 