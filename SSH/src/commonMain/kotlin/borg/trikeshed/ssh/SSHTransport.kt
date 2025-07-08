package borg.trikeshed.ssh

import borg.trikeshed.lib.*
import borg.trikeshed.net.quic.QuicConnection
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * SSH Transport Layer Implementation
 * 
 * Handles connection establishment, packet transmission, and transport-level
 * protocol management for both TCP and QUIC backends.
 */

// Transport interface
interface SSHTransport {
    suspend fun connect(server: SSHServerInfo, context: SSHTransportContext): SSHTransportState
    suspend fun sendPacket(packet: SSHPacket, context: SSHTransportContext)
    suspend fun receivePacket(context: SSHTransportContext): SSHPacket
    suspend fun disconnect(reason: SSHDisconnectReason, context: SSHTransportContext)
    suspend fun isConnected(): Boolean
}

// TCP Transport Implementation
class SSHTcpTransport : SSHTransport {
    internal var socket: Any? = null // Platform-specific socket
    internal var sequenceNumber: SSHSequenceNumber = 0u
    internal var windowSize: SSHWindowSize = SSHConstants.INITIAL_WINDOW_SIZE
    internal var maxPacketSize: SSHMaxPacketSize = SSHConstants.MAX_PACKET_SIZE
    
    override suspend fun connect(server: SSHServerInfo, context: SSHTransportContext): SSHTransportState {
        return withContext(context.b) {
            val host = server.a
            val port = server.b
            
            // Platform-specific connection
            socket = createSocket(host, port)
            
            // Send version string
            val versionString = "${SSHProtocol.VERSION}\r\n"
            sendRaw(versionString.encodeToByteArray())
            
            // Receive server version
            val serverVersion = receiveVersionString()
            
            // Create initial state
            val initialState = 0 j { SSHState.CONNECTING }
            val connectionInfo = Join(host, Join(port, serverVersion))
            
            Join(initialState, connectionInfo)
        }
    }
    
    override suspend fun sendPacket(packet: SSHPacket, context: SSHTransportContext) {
        withContext(context.b) {
            val encoded = packet.encode()
            sendRaw(encoded.toByteArray())
            sequenceNumber++
        }
    }
    
    override suspend fun receivePacket(context: SSHTransportContext): SSHPacket {
        return withContext(context.b) {
            // Read packet length (4 bytes)
            val lengthBytes = receiveRaw(4)
            val packetLength = (lengthBytes[0].toUInt() shl 24) or
                             (lengthBytes[1].toUInt() shl 16) or
                             (lengthBytes[2].toUInt() shl 8) or
                             lengthBytes[3].toUInt()
            
            if (packetLength < SSHProtocol.MIN_PACKET_SIZE || packetLength > SSHProtocol.MAX_PACKET_SIZE) {
                throw SSHException("Invalid packet length: $packetLength")
            }
            
            // Read full packet
            val packetData = receiveRaw(packetLength.toInt())
            
            // Parse packet
            parsePacket(packetData)
        }
    }
    
    override suspend fun disconnect(reason: SSHDisconnectReason, context: SSHTransportContext) {
        withContext(context.b) {
            // Send disconnect message
            val disconnectPayload = SSHMessages.disconnect(reason, "Disconnected by client")
            val packet = SSHPacket(
                length = (disconnectPayload.a + 1).toUInt(),
                paddingLength = 0,
                messageType = SSHMessageType.DISCONNECT,
                payload = disconnectPayload,
                padding = 0 j { 0.toByte() },
                mac = 0 j { 0.toByte() }
            )
            sendPacket(packet, context)
            
            // Close socket
            closeSocket()
            socket = null
        }
    }
    
    override suspend fun isConnected(): Boolean {
        return socket != null
    }
    
    internal suspend fun sendRaw(data: ByteArray) {
        // Platform-specific send implementation
        sendRawImpl(data)
    }
    
    internal suspend fun receiveRaw(length: Int): ByteArray {
        // Platform-specific receive implementation
        return receiveRawImpl(length)
    }
    
    internal fun parsePacket(data: ByteArray): SSHPacket {
        var offset = 0
        
        // Packet length (already read)
        val packetLength = ((data[offset].toUInt() shl 24) or
                           (data[offset + 1].toUInt() shl 16) or
                           (data[offset + 2].toUInt() shl 8) or
                           data[offset + 3].toUInt())
        offset += 4
        
        // Padding length
        val paddingLength = data[offset].toInt()
        offset += 1
        
        // Payload
        val payloadLength = packetLength.toInt() - paddingLength - 1
        val payload = payloadLength j { i: Int -> data[offset + i] }
        offset += payloadLength
        
        // Padding
        val padding = paddingLength j { i: Int -> data[offset + i] }
        offset += paddingLength
        
        // MAC (if present)
        val mac = if (offset < data.size) {
            (data.size - offset) j { i: Int -> data[offset + i] }
        } else {
            0 j { 0.toByte() }
        }
        
        return SSHPacket(
            length = packetLength,
            paddingLength = paddingLength.toByte(),
            messageType = if (payload.a > 0) SSHMessageType.fromByte(payload[0]) ?: SSHMessageType.IGNORE else SSHMessageType.IGNORE,
            payload = if (payload.a > 1) {
                (payload.a - 1) j { i: Int -> payload[i + 1] }
            } else {
                0 j { 0.toByte() }
            },
            padding = padding,
            mac = mac
        )
    }
    
    internal suspend fun receiveVersionString(): String {
        val buffer = StringBuilder()
        var char: Char
        
        // Read until we get a complete version string
        while (true) {
            val data = receiveRaw(1)
            char = data[0].toChar()
            buffer.append(char)
            
            // Check for end of version string
            if (buffer.length >= 2 && buffer.endsWith("\r\n")) {
                break
            }
        }
        
        return buffer.toString().trim()
    }
    
    // Platform-specific implementations
    internal suspend fun createSocket(host: String, port: Int): Any = TODO("Platform-specific socket creation")
    internal suspend fun sendRawImpl(data: ByteArray) = TODO("Platform-specific send")
    internal suspend fun receiveRawImpl(length: Int): ByteArray = TODO("Platform-specific receive")
    internal suspend fun closeSocket() = TODO("Platform-specific close")
}

// QUIC Transport Implementation
class SSHQuicTransport : SSHTransport {
    internal var quicConnection: QuicConnection? = null
    internal var sequenceNumber: SSHSequenceNumber = 0u
    internal var windowSize: SSHWindowSize = SSHConstants.INITIAL_WINDOW_SIZE
    internal var maxPacketSize: SSHMaxPacketSize = SSHConstants.MAX_PACKET_SIZE
    
    override suspend fun connect(server: SSHServerInfo, context: SSHTransportContext): SSHTransportState {
        return withContext(context.b) {
            val host = server.a
            val port = server.b
            
            // Create QUIC connection
            quicConnection = createQuicConnection(host, port)
            
            // Send version string over QUIC stream
            val versionString = "${SSHProtocol.VERSION}\r\n"
            sendRaw(versionString.encodeToByteArray())
            
            // Receive server version
            val serverVersion = receiveVersionString()
            
            // Create initial state
            val initialState = 0 j { SSHState.CONNECTING }
            val connectionInfo = Join(host, Join(port, serverVersion))
            
            Join(initialState, connectionInfo)
        }
    }
    
    override suspend fun sendPacket(packet: SSHPacket, context: SSHTransportContext) {
        withContext(context.b) {
            val encoded = packet.encode()
            sendRaw(encoded.toByteArray())
            sequenceNumber++
        }
    }
    
    override suspend fun receivePacket(context: SSHTransportContext): SSHPacket {
        return withContext(context.b) {
            // Read packet length (4 bytes)
            val lengthBytes = receiveRaw(4)
            val packetLength = (lengthBytes[0].toUInt() shl 24) or
                             (lengthBytes[1].toUInt() shl 16) or
                             (lengthBytes[2].toUInt() shl 8) or
                             lengthBytes[3].toUInt()
            
            if (packetLength < SSHProtocol.MIN_PACKET_SIZE || packetLength > SSHProtocol.MAX_PACKET_SIZE) {
                throw SSHException("Invalid packet length: $packetLength")
            }
            
            // Read full packet
            val packetData = receiveRaw(packetLength.toInt())
            
            // Parse packet (same as TCP)
            parsePacket(packetData)
        }
    }
    
    override suspend fun disconnect(reason: SSHDisconnectReason, context: SSHTransportContext) {
        withContext(context.b) {
            // Send disconnect message
            val disconnectPayload = SSHMessages.disconnect(reason, "Disconnected by client")
            val packet = SSHPacket(
                length = (disconnectPayload.a + 1).toUInt(),
                paddingLength = 0,
                messageType = SSHMessageType.DISCONNECT,
                payload = disconnectPayload,
                padding = 0 j { 0.toByte() },
                mac = 0 j { 0.toByte() }
            )
            sendPacket(packet, context)
            
            // Close QUIC connection
            quicConnection?.close()
            quicConnection = null
        }
    }
    
    override suspend fun isConnected(): Boolean {
        return quicConnection?.isConnected() == true
    }
    
    internal suspend fun sendRaw(data: ByteArray) {
        quicConnection?.send(data) ?: throw SSHException("No QUIC connection")
    }
    
    internal suspend fun receiveRaw(length: Int): ByteArray {
        return quicConnection?.receive(length) ?: throw SSHException("No QUIC connection")
    }
    
    internal fun parsePacket(data: ByteArray): SSHPacket {
        // Same implementation as TCP transport
        var offset = 0
        
        val packetLength = ((data[offset].toUInt() shl 24) or
                           (data[offset + 1].toUInt() shl 16) or
                           (data[offset + 2].toUInt() shl 8) or
                           data[offset + 3].toUInt())
        offset += 4
        
        val paddingLength = data[offset].toInt()
        offset += 1
        
        val payloadLength = packetLength.toInt() - paddingLength - 1
        val payload = payloadLength j { i: Int -> data[offset + i] }
        offset += payloadLength
        
        val padding = paddingLength j { i: Int -> data[offset + i] }
        offset += paddingLength
        
        val mac = if (offset < data.size) {
            (data.size - offset) j { i: Int -> data[offset + i] }
        } else {
            0 j { 0.toByte() }
        }
        
        return SSHPacket(
            length = packetLength,
            paddingLength = paddingLength.toByte(),
            messageType = if (payload.a > 0) SSHMessageType.fromByte(payload[0]) ?: SSHMessageType.IGNORE else SSHMessageType.IGNORE,
            payload = if (payload.a > 1) {
                (payload.a - 1) j { i: Int -> payload[i + 1] }
            } else {
                0 j { 0.toByte() }
            },
            padding = padding,
            mac = mac
        )
    }
    
    internal suspend fun receiveVersionString(): String {
        val buffer = StringBuilder()
        var char: Char
        
        while (true) {
            val data = receiveRaw(1)
            char = data[0].toChar()
            buffer.append(char)
            
            if (buffer.length >= 2 && buffer.endsWith("\r\n")) {
                break
            }
        }
        
        return buffer.toString().trim()
    }
    
    internal suspend fun createQuicConnection(host: String, port: Int): QuicConnection {
        // Create QUIC connection using TrikeShed QUIC implementation
        return QuicConnection.connect(host, port)
    }
}

// Transport factory
object SSHTransportFactory {
    fun createTransport(backend: TransportBackend): SSHTransport {
        return when (backend) {
            TransportBackend.TCP -> SSHTcpTransport()
            TransportBackend.QUIC -> SSHQuicTransport()
            TransportBackend.WEBSOCKET -> throw UnsupportedOperationException("WebSocket transport not yet implemented")
        }
    }
}

enum class TransportBackend {
    TCP,
    QUIC,
    WEBSOCKET
}

// SSH Exception
class SSHException(message: String) : Exception(message) 