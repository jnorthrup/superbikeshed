@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.net.ssh

import borg.trikeshed.lib.*
import borg.trikeshed.crypto.*
import borg.trikeshed.net.quic.*
import kotlinx.coroutines.*
import kotlin.jvm.JvmInline

// === SSH TAXONOMICAL TYPEALIASES ===

// Protocol types
typealias SSHVersion = String
typealias SSHPacketType = Byte
typealias SSHChannelType = String
typealias SSHChannelID = UInt
typealias SSHWindowSize = UInt
typealias SSHPacketLength = UInt
typealias SSHPaddingLength = Byte
typealias SSHSequenceNumber = UInt
typealias SSHReasonCode = UInt
typealias SSHSignalName = String

// Crypto types
typealias SSHKeyExchangeAlgorithm = String
typealias SSHServerHostKeyAlgorithm = String
typealias SSHEncryptionAlgorithm = String
typealias SSHMacAlgorithm = String
typealias SSHCompressionAlgorithm = String
typealias SSHPublicKey = Indexed<Byte>
typealias SSHPrivateKey = Indexed<Byte>
typealias SSHSessionID = Indexed<Byte>
typealias SSHCookie = Indexed<Byte>

// Data types
typealias SSHPayload = Indexed<Byte>
typealias SSHString = Indexed<Byte>
typealias SSHMpint = Indexed<Byte>
typealias SSHNameList = Indexed<String>

// Value classes for type safety
@JvmInline value class PacketSequence(val value: UInt)
@JvmInline value class ChannelWindow(val bytes: UInt)
@JvmInline value class ChannelPacketSize(val bytes: UInt)
@JvmInline value class ExitStatus(val code: Int)
@JvmInline value class ExitSignal(val name: String)

/**
 * SSH Protocol Implementation (RFC 4251-4254)
 */
object SSHProtocol {
    
    // === SSH VERSION ===
    const val VERSION = "SSH-2.0-TrikeShed_1.0"
    
    // === SSH MESSAGE TYPES ===
    object MessageTypes {
        // Transport layer protocol
        const val SSH_MSG_DISCONNECT: SSHPacketType = 1
        const val SSH_MSG_IGNORE: SSHPacketType = 2
        const val SSH_MSG_UNIMPLEMENTED: SSHPacketType = 3
        const val SSH_MSG_DEBUG: SSHPacketType = 4
        const val SSH_MSG_SERVICE_REQUEST: SSHPacketType = 5
        const val SSH_MSG_SERVICE_ACCEPT: SSHPacketType = 6
        
        // Key exchange
        const val SSH_MSG_KEXINIT: SSHPacketType = 20
        const val SSH_MSG_NEWKEYS: SSHPacketType = 21
        
        // Diffie-Hellman key exchange
        const val SSH_MSG_KEXDH_INIT: SSHPacketType = 30
        const val SSH_MSG_KEXDH_REPLY: SSHPacketType = 31
        
        // User authentication protocol
        const val SSH_MSG_USERAUTH_REQUEST: SSHPacketType = 50
        const val SSH_MSG_USERAUTH_FAILURE: SSHPacketType = 51
        const val SSH_MSG_USERAUTH_SUCCESS: SSHPacketType = 52
        const val SSH_MSG_USERAUTH_BANNER: SSHPacketType = 53
        const val SSH_MSG_USERAUTH_INFO_REQUEST: SSHPacketType = 60
        const val SSH_MSG_USERAUTH_INFO_RESPONSE: SSHPacketType = 61
        
        // Connection protocol
        const val SSH_MSG_GLOBAL_REQUEST: SSHPacketType = 80
        const val SSH_MSG_REQUEST_SUCCESS: SSHPacketType = 81
        const val SSH_MSG_REQUEST_FAILURE: SSHPacketType = 82
        const val SSH_MSG_CHANNEL_OPEN: SSHPacketType = 90
        const val SSH_MSG_CHANNEL_OPEN_CONFIRMATION: SSHPacketType = 91
        const val SSH_MSG_CHANNEL_OPEN_FAILURE: SSHPacketType = 92
        const val SSH_MSG_CHANNEL_WINDOW_ADJUST: SSHPacketType = 93
        const val SSH_MSG_CHANNEL_DATA: SSHPacketType = 94
        const val SSH_MSG_CHANNEL_EXTENDED_DATA: SSHPacketType = 95
        const val SSH_MSG_CHANNEL_EOF: SSHPacketType = 96
        const val SSH_MSG_CHANNEL_CLOSE: SSHPacketType = 97
        const val SSH_MSG_CHANNEL_REQUEST: SSHPacketType = 98
        const val SSH_MSG_CHANNEL_SUCCESS: SSHPacketType = 99
        const val SSH_MSG_CHANNEL_FAILURE: SSHPacketType = 100
    }
    
    // === DISCONNECT REASON CODES ===
    object DisconnectReasons {
        const val SSH_DISCONNECT_HOST_NOT_ALLOWED_TO_CONNECT: SSHReasonCode = 1u
        const val SSH_DISCONNECT_PROTOCOL_ERROR: SSHReasonCode = 2u
        const val SSH_DISCONNECT_KEY_EXCHANGE_FAILED: SSHReasonCode = 3u
        const val SSH_DISCONNECT_RESERVED: SSHReasonCode = 4u
        const val SSH_DISCONNECT_MAC_ERROR: SSHReasonCode = 5u
        const val SSH_DISCONNECT_COMPRESSION_ERROR: SSHReasonCode = 6u
        const val SSH_DISCONNECT_SERVICE_NOT_AVAILABLE: SSHReasonCode = 7u
        const val SSH_DISCONNECT_PROTOCOL_VERSION_NOT_SUPPORTED: SSHReasonCode = 8u
        const val SSH_DISCONNECT_HOST_KEY_NOT_VERIFIABLE: SSHReasonCode = 9u
        const val SSH_DISCONNECT_CONNECTION_LOST: SSHReasonCode = 10u
        const val SSH_DISCONNECT_BY_APPLICATION: SSHReasonCode = 11u
        const val SSH_DISCONNECT_TOO_MANY_CONNECTIONS: SSHReasonCode = 12u
        const val SSH_DISCONNECT_AUTH_CANCELLED_BY_USER: SSHReasonCode = 13u
        const val SSH_DISCONNECT_NO_MORE_AUTH_METHODS_AVAILABLE: SSHReasonCode = 14u
        const val SSH_DISCONNECT_ILLEGAL_USER_NAME: SSHReasonCode = 15u
    }
    
    // === CHANNEL OPEN FAILURE REASON CODES ===
    object ChannelOpenFailureReasons {
        const val SSH_OPEN_ADMINISTRATIVELY_PROHIBITED: SSHReasonCode = 1u
        const val SSH_OPEN_CONNECT_FAILED: SSHReasonCode = 2u
        const val SSH_OPEN_UNKNOWN_CHANNEL_TYPE: SSHReasonCode = 3u
        const val SSH_OPEN_RESOURCE_SHORTAGE: SSHReasonCode = 4u
    }
    
    // === EXTENDED DATA TYPES ===
    object ExtendedDataTypes {
        const val SSH_EXTENDED_DATA_STDERR: UInt = 1u
    }
}

/**
 * SSH Packet
 */
data class SSHPacket(
    val packetLength: SSHPacketLength,
    val paddingLength: SSHPaddingLength,
    val payload: SSHPayload,
    val padding: Indexed<Byte>,
    val mac: Indexed<Byte> = 0 j { 0.toByte() }
) {
    fun encode(): Indexed<Byte> {
        val packet = mutableListOf<Byte>()
        
        // Packet length (does not include MAC or packet length field itself)
        val length = 1 + payload.a + padding.a // padding_length + payload + padding
        packet.add((length shr 24).toByte())
        packet.add((length shr 16).toByte())
        packet.add((length shr 8).toByte())
        packet.add(length.toByte())
        
        // Padding length
        packet.add(paddingLength)
        
        // Payload
        for (i in 0 until payload.a) {
            packet.add(payload[i])
        }
        
        // Padding
        for (i in 0 until padding.a) {
            packet.add(padding[i])
        }
        
        // MAC (if present)
        for (i in 0 until mac.a) {
            packet.add(mac[i])
        }
        
        return packet.size j { i: Int -> packet[i] }
    }
    
    companion object {
        fun createPacket(
            payload: SSHPayload,
            blockSize: Int = 8,
            crypto: CommonCrypto = CryptoFactory.getSecureRandom()
        ): SSHPacket {
            // Calculate padding
            val payloadLength = payload.a
            val paddingLengthFieldSize = 1
            val packetLengthFieldSize = 4
            
            var paddingLength = blockSize - ((packetLengthFieldSize + paddingLengthFieldSize + payloadLength) % blockSize)
            if (paddingLength < 4) {
                paddingLength += blockSize
            }
            
            // Generate random padding
            val padding = crypto.randomBytes(paddingLength)
            
            val packetLength = SSHPacketLength((paddingLengthFieldSize + payloadLength + paddingLength).toUInt())
            
            return SSHPacket(
                packetLength = packetLength,
                paddingLength = SSHPaddingLength(paddingLength.toByte()),
                payload = payload,
                padding = padding
            )
        }
    }
}

/**
 * SSH Key Exchange Init Message
 */
data class KexInit(
    val cookie: SSHCookie,
    val kexAlgorithms: SSHNameList,
    val serverHostKeyAlgorithms: SSHNameList,
    val encryptionAlgorithmsClientToServer: SSHNameList,
    val encryptionAlgorithmsServerToClient: SSHNameList,
    val macAlgorithmsClientToServer: SSHNameList,
    val macAlgorithmsServerToClient: SSHNameList,
    val compressionAlgorithmsClientToServer: SSHNameList,
    val compressionAlgorithmsServerToClient: SSHNameList,
    val languagesClientToServer: SSHNameList = 0 j { "" },
    val languagesServerToClient: SSHNameList = 0 j { "" },
    val firstKexPacketFollows: Boolean = false,
    val reserved: UInt = 0u
) {
    fun encode(): SSHPayload {
        val payload = mutableListOf<Byte>()
        
        // Message type
        payload.add(SSHProtocol.MessageTypes.SSH_MSG_KEXINIT)
        
        // Cookie (16 bytes)
        for (i in 0 until 16) {
            payload.add(if (i < cookie.a) cookie[i] else 0.toByte())
        }
        
        // Name lists
        payload.addAll(encodeNameList(kexAlgorithms))
        payload.addAll(encodeNameList(serverHostKeyAlgorithms))
        payload.addAll(encodeNameList(encryptionAlgorithmsClientToServer))
        payload.addAll(encodeNameList(encryptionAlgorithmsServerToClient))
        payload.addAll(encodeNameList(macAlgorithmsClientToServer))
        payload.addAll(encodeNameList(macAlgorithmsServerToClient))
        payload.addAll(encodeNameList(compressionAlgorithmsClientToServer))
        payload.addAll(encodeNameList(compressionAlgorithmsServerToClient))
        payload.addAll(encodeNameList(languagesClientToServer))
        payload.addAll(encodeNameList(languagesServerToClient))
        
        // First kex packet follows
        payload.add(if (firstKexPacketFollows) 1 else 0)
        
        // Reserved
        payload.add((reserved shr 24).toByte())
        payload.add((reserved shr 16).toByte())
        payload.add((reserved shr 8).toByte())
        payload.add(reserved.toByte())
        
        return payload.size j { i: Int -> payload[i] }
    }
    
    private fun encodeNameList(names: SSHNameList): List<Byte> {
        val joined = names.play.joinToString(",")
        val bytes = joined.encodeToByteArray()
        
        val result = mutableListOf<Byte>()
        result.add((bytes.size shr 24).toByte())
        result.add((bytes.size shr 16).toByte())
        result.add((bytes.size shr 8).toByte())
        result.add(bytes.size.toByte())
        result.addAll(bytes.toList())
        
        return result
    }
}

/**
 * SSH Channel
 */
data class SSHChannel(
    val localId: SSHChannelID,
    val remoteId: SSHChannelID,
    val type: SSHChannelType,
    val localWindow: ChannelWindow,
    val remoteWindow: ChannelWindow,
    val localMaxPacketSize: ChannelPacketSize,
    val remoteMaxPacketSize: ChannelPacketSize,
    var state: ChannelState = ChannelState.INIT
) {
    enum class ChannelState {
        INIT,
        OPEN_SENT,
        OPEN_CONFIRMED,
        EOF_SENT,
        EOF_RECEIVED,
        CLOSE_SENT,
        CLOSED
    }
    
    fun adjustWindow(bytes: UInt) {
        // Adjust window size for flow control
    }
    
    fun canSend(): Boolean {
        return state == ChannelState.OPEN_CONFIRMED && remoteWindow.bytes > 0u
    }
}

/**
 * SSH Connection
 */
class SSHConnection(
    private val transport: QuicConnection,
    private val role: Role = Role.CLIENT,
    private val crypto: CommonCrypto = CryptoFactory.getSecureRandom()
) {
    enum class Role { CLIENT, SERVER }
    enum class State {
        INIT,
        VERSION_EXCHANGED,
        KEX_INIT_SENT,
        KEX_INIT_RECEIVED,
        KEX_DH_INIT_SENT,
        KEX_DH_REPLY_RECEIVED,
        NEW_KEYS_SENT,
        NEW_KEYS_RECEIVED,
        AUTHENTICATED,
        CONNECTED,
        DISCONNECTED
    }
    
    private var state = State.INIT
    private var sequenceNumberOut = PacketSequence(0u)
    private var sequenceNumberIn = PacketSequence(0u)
    
    // Crypto state
    private var sessionId: SSHSessionID? = null
    private var encryptionKey: SymmetricKey? = null
    private var decryptionKey: SymmetricKey? = null
    private var integrityKeyOut: SymmetricKey? = null
    private var integrityKeyIn: SymmetricKey? = null
    
    // Channels
    private val channels = mutableMapOf<SSHChannelID, SSHChannel>()
    private var nextChannelId = SSHChannelID(0u)
    
    // Supported algorithms
    private val supportedKexAlgorithms = listOf(
        "diffie-hellman-group14-sha256",
        "diffie-hellman-group16-sha512",
        "ecdh-sha2-nistp256",
        "ecdh-sha2-nistp384",
        "ecdh-sha2-nistp521"
    )
    
    private val supportedHostKeyAlgorithms = listOf(
        "ssh-rsa",
        "rsa-sha2-256",
        "rsa-sha2-512",
        "ecdsa-sha2-nistp256",
        "ssh-ed25519"
    )
    
    private val supportedCiphers = listOf(
        "aes128-gcm@openssh.com",
        "aes256-gcm@openssh.com",
        "chacha20-poly1305@openssh.com",
        "aes128-ctr",
        "aes192-ctr",
        "aes256-ctr"
    )
    
    private val supportedMacs = listOf(
        "hmac-sha2-256",
        "hmac-sha2-512",
        "hmac-sha1"
    )
    
    private val supportedCompression = listOf(
        "none",
        "zlib@openssh.com"
    )
    
    /**
     * Start SSH connection
     */
    suspend fun connect() {
        // Exchange version strings
        sendVersionString()
        state = State.VERSION_EXCHANGED
        
        // Send KEXINIT
        sendKexInit()
        state = State.KEX_INIT_SENT
    }
    
    /**
     * Send version string
     */
    private suspend fun sendVersionString() {
        val version = "${SSHProtocol.VERSION}\r\n"
        val stream = transport.createStream() ?: return
        stream.writeBytes(version.encodeToByteArray().size j { i: Int -> version.encodeToByteArray()[i] })
    }
    
    /**
     * Send KEXINIT message
     */
    private suspend fun sendKexInit() {
        val cookie = crypto.randomBytes(16)
        
        val kexInit = KexInit(
            cookie = cookie,
            kexAlgorithms = supportedKexAlgorithms.size j { i: Int -> supportedKexAlgorithms[i] },
            serverHostKeyAlgorithms = supportedHostKeyAlgorithms.size j { i: Int -> supportedHostKeyAlgorithms[i] },
            encryptionAlgorithmsClientToServer = supportedCiphers.size j { i: Int -> supportedCiphers[i] },
            encryptionAlgorithmsServerToClient = supportedCiphers.size j { i: Int -> supportedCiphers[i] },
            macAlgorithmsClientToServer = supportedMacs.size j { i: Int -> supportedMacs[i] },
            macAlgorithmsServerToClient = supportedMacs.size j { i: Int -> supportedMacs[i] },
            compressionAlgorithmsClientToServer = supportedCompression.size j { i: Int -> supportedCompression[i] },
            compressionAlgorithmsServerToClient = supportedCompression.size j { i: Int -> supportedCompression[i] }
        )
        
        val payload = kexInit.encode()
        sendPacket(payload)
    }
    
    /**
     * Send SSH packet
     */
    private suspend fun sendPacket(payload: SSHPayload) {
        val packet = SSHPacket.createPacket(payload, crypto = crypto)
        
        // Encrypt packet if keys are established
        val encoded = if (encryptionKey != null) {
            encryptPacket(packet)
        } else {
            packet.encode()
        }
        
        // Send via transport
        val stream = transport.createStream() ?: return
        stream.writeBytes(encoded)
        
        // Increment sequence number
        sequenceNumberOut = PacketSequence(sequenceNumberOut.value + 1u)
    }
    
    /**
     * Encrypt packet
     */
    private fun encryptPacket(packet: SSHPacket): Indexed<Byte> {
        // Simplified - would use actual encryption
        return packet.encode()
    }
    
    /**
     * Open channel
     */
    suspend fun openChannel(
        type: SSHChannelType = "session",
        windowSize: ChannelWindow = ChannelWindow(2097152u), // 2MB
        maxPacketSize: ChannelPacketSize = ChannelPacketSize(32768u) // 32KB
    ): SSHChannel? {
        val localId = nextChannelId
        nextChannelId = SSHChannelID(nextChannelId + 1u)
        
        val channel = SSHChannel(
            localId = localId,
            remoteId = SSHChannelID(0u), // Will be set when confirmed
            type = type,
            localWindow = windowSize,
            remoteWindow = ChannelWindow(0u),
            localMaxPacketSize = maxPacketSize,
            remoteMaxPacketSize = ChannelPacketSize(0u)
        )
        
        channels[localId] = channel
        
        // Send CHANNEL_OPEN
        sendChannelOpen(channel)
        channel.state = SSHChannel.ChannelState.OPEN_SENT
        
        return channel
    }
    
    /**
     * Send channel open
     */
    private suspend fun sendChannelOpen(channel: SSHChannel) {
        val payload = mutableListOf<Byte>()
        
        // Message type
        payload.add(SSHProtocol.MessageTypes.SSH_MSG_CHANNEL_OPEN)
        
        // Channel type
        val typeBytes = channel.type.encodeToByteArray()
        payload.add((typeBytes.size shr 24).toByte())
        payload.add((typeBytes.size shr 16).toByte())
        payload.add((typeBytes.size shr 8).toByte())
        payload.add(typeBytes.size.toByte())
        payload.addAll(typeBytes.toList())
        
        // Sender channel
        payload.add((channel.localId shr 24).toByte())
        payload.add((channel.localId shr 16).toByte())
        payload.add((channel.localId shr 8).toByte())
        payload.add(channel.localId.toByte())
        
        // Initial window size
        payload.add((channel.localWindow.bytes shr 24).toByte())
        payload.add((channel.localWindow.bytes shr 16).toByte())
        payload.add((channel.localWindow.bytes shr 8).toByte())
        payload.add(channel.localWindow.bytes.toByte())
        
        // Maximum packet size
        payload.add((channel.localMaxPacketSize.bytes shr 24).toByte())
        payload.add((channel.localMaxPacketSize.bytes shr 16).toByte())
        payload.add((channel.localMaxPacketSize.bytes shr 8).toByte())
        payload.add(channel.localMaxPacketSize.bytes.toByte())
        
        sendPacket(payload.size j { i: Int -> payload[i] })
    }
    
    /**
     * Send data on channel
     */
    suspend fun sendChannelData(channelId: SSHChannelID, data: Indexed<Byte>) {
        val channel = channels[channelId] ?: return
        
        if (!channel.canSend()) return
        
        // Split data into chunks that fit in remote window and max packet size
        var offset = 0
        while (offset < data.a) {
            val chunkSize = minOf(
                data.a - offset,
                channel.remoteWindow.bytes.toInt(),
                channel.remoteMaxPacketSize.bytes.toInt()
            )
            
            if (chunkSize <= 0) break
            
            val chunk = chunkSize j { i: Int -> data[offset + i] }
            sendChannelDataPacket(channel.remoteId, chunk)
            
            // Adjust window
            channel.remoteWindow = ChannelWindow(channel.remoteWindow.bytes - chunkSize.toUInt())
            
            offset += chunkSize
        }
    }
    
    /**
     * Send channel data packet
     */
    private suspend fun sendChannelDataPacket(channelId: SSHChannelID, data: Indexed<Byte>) {
        val payload = mutableListOf<Byte>()
        
        // Message type
        payload.add(SSHProtocol.MessageTypes.SSH_MSG_CHANNEL_DATA)
        
        // Recipient channel
        payload.add((channelId shr 24).toByte())
        payload.add((channelId shr 16).toByte())
        payload.add((channelId shr 8).toByte())
        payload.add(channelId.toByte())
        
        // Data length
        payload.add((data.a shr 24).toByte())
        payload.add((data.a shr 16).toByte())
        payload.add((data.a shr 8).toByte())
        payload.add(data.a.toByte())
        
        // Data
        for (i in 0 until data.a) {
            payload.add(data[i])
        }
        
        sendPacket(payload.size j { i: Int -> payload[i] })
    }
    
    /**
     * Execute command on channel
     */
    suspend fun executeCommand(channelId: SSHChannelID, command: String) {
        sendChannelRequest(channelId, "exec", command.encodeToByteArray().size j { i: Int -> command.encodeToByteArray()[i] })
    }
    
    /**
     * Request PTY
     */
    suspend fun requestPty(
        channelId: SSHChannelID,
        termType: String = "xterm-256color",
        columns: Int = 80,
        rows: Int = 24
    ) {
        val payload = mutableListOf<Byte>()
        
        // Terminal type
        val termBytes = termType.encodeToByteArray()
        payload.add((termBytes.size shr 24).toByte())
        payload.add((termBytes.size shr 16).toByte())
        payload.add((termBytes.size shr 8).toByte())
        payload.add(termBytes.size.toByte())
        payload.addAll(termBytes.toList())
        
        // Terminal dimensions
        payload.add((columns shr 24).toByte())
        payload.add((columns shr 16).toByte())
        payload.add((columns shr 8).toByte())
        payload.add(columns.toByte())
        
        payload.add((rows shr 24).toByte())
        payload.add((rows shr 16).toByte())
        payload.add((rows shr 8).toByte())
        payload.add(rows.toByte())
        
        // Pixel dimensions (0 = use default)
        repeat(8) { payload.add(0) }
        
        // Terminal modes (empty for now)
        payload.add(0)
        payload.add(0)
        payload.add(0)
        payload.add(0)
        
        sendChannelRequest(channelId, "pty-req", payload.size j { i: Int -> payload[i] })
    }
    
    /**
     * Send channel request
     */
    private suspend fun sendChannelRequest(channelId: SSHChannelID, requestType: String, data: Indexed<Byte>) {
        val payload = mutableListOf<Byte>()
        
        // Message type
        payload.add(SSHProtocol.MessageTypes.SSH_MSG_CHANNEL_REQUEST)
        
        // Recipient channel
        payload.add((channelId shr 24).toByte())
        payload.add((channelId shr 16).toByte())
        payload.add((channelId shr 8).toByte())
        payload.add(channelId.toByte())
        
        // Request type
        val typeBytes = requestType.encodeToByteArray()
        payload.add((typeBytes.size shr 24).toByte())
        payload.add((typeBytes.size shr 16).toByte())
        payload.add((typeBytes.size shr 8).toByte())
        payload.add(typeBytes.size.toByte())
        payload.addAll(typeBytes.toList())
        
        // Want reply
        payload.add(1) // true
        
        // Request specific data
        for (i in 0 until data.a) {
            payload.add(data[i])
        }
        
        sendPacket(payload.size j { i: Int -> payload[i] })
    }
    
    /**
     * Setup port forwarding
     */
    suspend fun setupLocalForward(
        localHost: String,
        localPort: Int,
        remoteHost: String,
        remotePort: Int
    ) {
        val channel = openChannel("direct-tcpip") ?: return
        
        val payload = mutableListOf<Byte>()
        
        // Remote host
        val remoteHostBytes = remoteHost.encodeToByteArray()
        payload.add((remoteHostBytes.size shr 24).toByte())
        payload.add((remoteHostBytes.size shr 16).toByte())
        payload.add((remoteHostBytes.size shr 8).toByte())
        payload.add(remoteHostBytes.size.toByte())
        payload.addAll(remoteHostBytes.toList())
        
        // Remote port
        payload.add((remotePort shr 24).toByte())
        payload.add((remotePort shr 16).toByte())
        payload.add((remotePort shr 8).toByte())
        payload.add(remotePort.toByte())
        
        // Originator IP
        val originatorBytes = localHost.encodeToByteArray()
        payload.add((originatorBytes.size shr 24).toByte())
        payload.add((originatorBytes.size shr 16).toByte())
        payload.add((originatorBytes.size shr 8).toByte())
        payload.add(originatorBytes.size.toByte())
        payload.addAll(originatorBytes.toList())
        
        // Originator port
        payload.add((localPort shr 24).toByte())
        payload.add((localPort shr 16).toByte())
        payload.add((localPort shr 8).toByte())
        payload.add(localPort.toByte())
        
        // Send as channel open data
        // In real implementation, this would be part of channel open
    }
    
    /**
     * Disconnect
     */
    suspend fun disconnect(reason: SSHReasonCode = SSHProtocol.DisconnectReasons.SSH_DISCONNECT_BY_APPLICATION) {
        val payload = mutableListOf<Byte>()
        
        // Message type
        payload.add(SSHProtocol.MessageTypes.SSH_MSG_DISCONNECT)
        
        // Reason code
        payload.add((reason shr 24).toByte())
        payload.add((reason shr 16).toByte())
        payload.add((reason shr 8).toByte())
        payload.add(reason.toByte())
        
        // Description
        val description = "Connection closed by application"
        val descBytes = description.encodeToByteArray()
        payload.add((descBytes.size shr 24).toByte())
        payload.add((descBytes.size shr 16).toByte())
        payload.add((descBytes.size shr 8).toByte())
        payload.add(descBytes.size.toByte())
        payload.addAll(descBytes.toList())
        
        // Language tag (empty)
        payload.add(0)
        payload.add(0)
        payload.add(0)
        payload.add(0)
        
        sendPacket(payload.size j { i: Int -> payload[i] })
        
        state = State.DISCONNECTED
        transport.close()
    }
}

/**
 * SSH SFTP subsystem
 */
class SFTPClient(
    private val sshConnection: SSHConnection,
    private val channelId: SSHChannelID
) {
    // SFTP packet types
    object PacketTypes {
        const val SSH_FXP_INIT: Byte = 1
        const val SSH_FXP_VERSION: Byte = 2
        const val SSH_FXP_OPEN: Byte = 3
        const val SSH_FXP_CLOSE: Byte = 4
        const val SSH_FXP_READ: Byte = 5
        const val SSH_FXP_WRITE: Byte = 6
        const val SSH_FXP_LSTAT: Byte = 7
        const val SSH_FXP_FSTAT: Byte = 8
        const val SSH_FXP_SETSTAT: Byte = 9
        const val SSH_FXP_FSETSTAT: Byte = 10
        const val SSH_FXP_OPENDIR: Byte = 11
        const val SSH_FXP_READDIR: Byte = 12
        const val SSH_FXP_REMOVE: Byte = 13
        const val SSH_FXP_MKDIR: Byte = 14
        const val SSH_FXP_RMDIR: Byte = 15
        const val SSH_FXP_REALPATH: Byte = 16
        const val SSH_FXP_STAT: Byte = 17
        const val SSH_FXP_RENAME: Byte = 18
        const val SSH_FXP_READLINK: Byte = 19
        const val SSH_FXP_SYMLINK: Byte = 20
        const val SSH_FXP_STATUS: Byte = 101
        const val SSH_FXP_HANDLE: Byte = 102
        const val SSH_FXP_DATA: Byte = 103
        const val SSH_FXP_NAME: Byte = 104
        const val SSH_FXP_ATTRS: Byte = 105
        const val SSH_FXP_EXTENDED: Byte = -56 // 200
        const val SSH_FXP_EXTENDED_REPLY: Byte = -55 // 201
    }
    
    private var requestId = 1u
    
    /**
     * Initialize SFTP subsystem
     */
    suspend fun init() {
        sshConnection.sendChannelData(channelId, "sftp".encodeToByteArray().size j { i: Int -> "sftp".encodeToByteArray()[i] })
        
        // Send SFTP INIT
        val payload = mutableListOf<Byte>()
        
        // Length (placeholder)
        repeat(4) { payload.add(0) }
        
        // Type
        payload.add(PacketTypes.SSH_FXP_INIT)
        
        // Version
        payload.add(0)
        payload.add(0)
        payload.add(0)
        payload.add(3) // SFTP v3
        
        // Update length
        val length = payload.size - 4
        payload[0] = (length shr 24).toByte()
        payload[1] = (length shr 16).toByte()
        payload[2] = (length shr 8).toByte()
        payload[3] = length.toByte()
        
        sshConnection.sendChannelData(channelId, payload.size j { i: Int -> payload[i] })
    }
    
    /**
     * Open file
     */
    suspend fun open(filename: String, flags: Int = 0x01, attrs: Map<String, Any> = emptyMap()): String? {
        val id = requestId++
        val payload = mutableListOf<Byte>()
        
        // Length (placeholder)
        repeat(4) { payload.add(0) }
        
        // Type
        payload.add(PacketTypes.SSH_FXP_OPEN)
        
        // Request ID
        payload.add((id shr 24).toByte())
        payload.add((id shr 16).toByte())
        payload.add((id shr 8).toByte())
        payload.add(id.toByte())
        
        // Filename
        val filenameBytes = filename.encodeToByteArray()
        payload.add((filenameBytes.size shr 24).toByte())
        payload.add((filenameBytes.size shr 16).toByte())
        payload.add((filenameBytes.size shr 8).toByte())
        payload.add(filenameBytes.size.toByte())
        payload.addAll(filenameBytes.toList())
        
        // Flags
        payload.add((flags shr 24).toByte())
        payload.add((flags shr 16).toByte())
        payload.add((flags shr 8).toByte())
        payload.add(flags.toByte())
        
        // Attributes (empty for now)
        repeat(4) { payload.add(0) }
        
        // Update length
        val length = payload.size - 4
        payload[0] = (length shr 24).toByte()
        payload[1] = (length shr 16).toByte()
        payload[2] = (length shr 8).toByte()
        payload[3] = length.toByte()
        
        sshConnection.sendChannelData(channelId, payload.size j { i: Int -> payload[i] })
        
        // In real implementation, would wait for SSH_FXP_HANDLE response
        return "handle-$id"
    }
}