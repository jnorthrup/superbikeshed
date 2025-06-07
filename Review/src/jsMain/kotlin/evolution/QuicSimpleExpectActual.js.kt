package evolution

<<<<<<< HEAD
import borg.trikeshed.reactor.UdpSocket // Keep for deprecated QuicSocketContextValue
=======
// import borg.trikeshed.reactor.UdpSocket // Will be removed if unused
>>>>>>> origin/jules_wip_6906935130323988499
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers // Default dispatcher for JS
import kotlin.coroutines.CoroutineContext
<<<<<<< HEAD
import kotlin.coroutines.EmptyCoroutineContext
=======
// Import new types from QuicSpecTypes.kt
import evolution.* // Using wildcard import
>>>>>>> origin/jules_wip_8844705664950451013

// Actual implementations for the simple context value interfaces for JS

actual object QuicConnectionContextKey : CoroutineContext.Key<QuicConnectionContextValue>
actual object QuicCryptoContextKey : CoroutineContext.Key<QuicCryptoContextValue>

<<<<<<< HEAD
@Deprecated("Replaced by direct use of PlatformUdpChannel and PlatformIoService from context.")
actual object QuicSocketContextKey : CoroutineContext.Key<QuicSocketContextValue>
=======
// @Deprecated("...") actual object QuicSocketContextKey ... REMOVED
>>>>>>> origin/jules_wip_6906935130323988499

actual object QuicProtectionContextKey : CoroutineContext.Key<QuicProtectionContextValue>
actual object QuicObservabilityContextKey : CoroutineContext.Key<QuicObservabilityContextValue>

actual class JsQuicConnectionContextValue(
<<<<<<< HEAD
    override val connection: QuicConnection // Assuming common QuicConnection
) : QuicConnectionContextValue {
    override fun createInitialPacket(payload: ByteArray): QuicPacket {
        connection.packetNumber++ // Assuming common QuicConnection has var packetNumber
<<<<<<< HEAD
        // Uses the secondary constructor of common QuicPacket from QuicCurl.kt
=======
>>>>>>> origin/jules_wip_6906935130323988499
        return QuicPacket(
            packetType = QuicPacketType.INITIAL,
            connectionId = connection.connectionId, // Assuming common QuicConnection has connectionId
            packetNumber = connection.packetNumber,
=======
    override val connection: QuicConnection // QuicConnection's fields will be updated below
) : QuicConnectionContextValue {
    override fun createInitialPacket(payload: ByteArray): QuicPacket {
        // Ensure connection.packetNumber is PacketNumber and handle its increment correctly
        val currentPnValue = (connection.packetNumber as PacketNumber).value
        connection.packetNumber = PacketNumber(currentPnValue + 1uL)

        return QuicPacket(
            packetType = QuicPacketType.INITIAL, // Assuming local QuicPacketType enum
            connectionId = connection.connectionId, // Assuming QuicConnection.connectionId is ConnectionID
            packetNumber = connection.packetNumber as PacketNumber,
>>>>>>> origin/jules_wip_8844705664950451013
            payload = payload
        )
    }

<<<<<<< HEAD
    override fun createDataPacket(payload: ByteArray): QuicPacket {
        connection.packetNumber++
        // Uses the secondary constructor of common QuicPacket from QuicCurl.kt
=======
    // Signature changed: payload: ByteArray -> payload: StreamFrameData
    override fun createDataPacket(payload: StreamFrameData): QuicPacket {
        val currentPnValue = (connection.packetNumber as PacketNumber).value
        connection.packetNumber = PacketNumber(currentPnValue + 1uL)

>>>>>>> origin/jules_wip_8844705664950451013
        return QuicPacket(
            packetType = null, // Short header
            connectionId = connection.connectionId,
            packetNumber = connection.packetNumber as PacketNumber,
            payload = payload.payload // Use .payload to get the underlying ByteArray
        )
    }

    override fun transitionToEstablished() {
        connection.state = QuicConnectionStateEnum.CONNECTED // Assuming local QuicConnectionStateEnum
        println("JS: Connection state transitioned to ESTABLISHED")
    }
}

actual class JsQuicCryptoContextValue : QuicCryptoContextValue {
    override fun generateClientHello(serverName: String): ByteArray {
<<<<<<< HEAD
<<<<<<< HEAD
        // Consistent with Native, assuming no direct connection instance here.
=======
>>>>>>> origin/jules_wip_6906935130323988499
        return evolution.generateClientHello(initialDestConnId = ByteArray(0), serverName = serverName)
    }

    override fun processServerResponse(response: ByteArray): Boolean {
        return evolution.processServerHello(response)
    }

    actual override suspend fun deriveInitialSecrets(context: CoroutineContext, clientDstConnId: ByteArray): QuicInitialKeys {
<<<<<<< HEAD
        return evolution.deriveInitialSecrets(context, clientDstConnId) // Delegate to global CCEK-ified fun
=======
        // Placeholder: In a real scenario, this would call JS crypto libraries or common logic
        println("JS: Generating ClientHello for $serverName")
        // Assuming evolution.generateClientHello is a common function
        return evolution.generateClientHello(ByteArray(0), serverName)
    }

    override fun processServerResponse(response: ByteArray): Boolean {
        // Placeholder: In a real scenario, this would process the server's handshake message
        println("JS: Processing ServerResponse: ${response.decodeToString().take(50)}...")
        // Assuming evolution.processServerHello is a common function
        return evolution.processServerHello(response)
    }

    // Signature changed: clientDstConnId: ByteArray -> clientDstConnId: ConnectionID
    override fun deriveInitialSecrets(clientDstConnId: ConnectionID): QuicInitialKeys {
        // Use .value to get the underlying ByteArray for the common function
        println("JS: Deriving initial secrets for DCID ${clientDstConnId.toHexString()}")
        // Assuming evolution.deriveInitialSecrets is a common function
        return evolution.deriveInitialSecrets(clientDstConnId.value)
>>>>>>> origin/jules_wip_8844705664950451013
    }
}

@Deprecated("Replaced by direct use of PlatformUdpChannel and PlatformIoService from context.")
actual class JsQuicSocketContextValue(
    override val socket: UdpSocket, // UdpSocket from borg.trikeshed
    override val host: String,
    override val port: Int
) : QuicSocketContextValue {
    // Signature changed: packet: ByteArray -> packet: ProtectedPayload
    override suspend fun sendPacket(packet: ProtectedPayload): Boolean {
        // Use .data (or .value) to send the underlying ByteArray
        println("JS: Sending packet of size ${packet.data.size} to $host:$port")
        return socket.send(packet.data, host, port)
    }

    override suspend fun receivePacket(buffer: ByteArray): Int {
        println("JS: Attempting to receive packet into buffer of size ${buffer.size}")
        val receivedBytes = socket.receive(buffer)
        println("JS: Received $receivedBytes bytes")
        return receivedBytes
    }
}

actual class JsQuicProtectionContextValue : QuicProtectionContextValue {
<<<<<<< HEAD
    actual override suspend fun protectPacket(context: CoroutineContext, packet: QuicPacket, keys: QuicInitialKeys, connection: QuicConnection): ByteArray {
        return evolution.protectPacket(context, packet, keys, connection) // Delegate to global CCEK-ified fun
    }

    actual override suspend fun unprotectPacket(context: CoroutineContext, packet: ByteArray, keys: QuicInitialKeys, connection: QuicConnection): QuicPacket? {
        return evolution.unprotectPacket(context, packet, keys, connection) // Delegate to global CCEK-ified fun
=======
        return evolution.deriveInitialSecrets(context, clientDstConnId)
    }
}

// @Deprecated("...") actual class JsQuicSocketContextValue ... REMOVED

actual class JsQuicProtectionContextValue : QuicProtectionContextValue {
    actual override suspend fun protectPacket(context: CoroutineContext, packet: QuicPacket, keys: QuicInitialKeys, connection: QuicConnection): ByteArray {
        return evolution.protectPacket(context, packet, keys, connection)
    }

    actual override suspend fun unprotectPacket(context: CoroutineContext, packet: ByteArray, keys: QuicInitialKeys, connection: QuicConnection): QuicPacket? {
        return evolution.unprotectPacket(context, packet, keys, connection)
>>>>>>> origin/jules_wip_6906935130323988499
=======
    // Return type changed: ByteArray -> ProtectedPayload
    override fun protectPacket(packet: QuicPacket, keys: QuicInitialKeys, connection: QuicConnection): ProtectedPayload {
        println("JS: Protecting packet PN ${(packet.packetNumber as PacketNumber).value} for CID ${(connection.connectionId as ConnectionID).toHexString()}")
        // Assuming evolution.protectPacket is a common function returning ByteArray
        val protectedBytes = evolution.protectPacket(packet, keys, connection)
        return ProtectedPayload(protectedBytes) // Wrap the result
    }

    // Signature changed: packet: ByteArray -> packet: ProtectedPayload
    override fun unprotectPacket(packet: ProtectedPayload, keys: QuicInitialKeys, connection: QuicConnection): QuicPacket? {
        println("JS: Attempting to unprotect packet of size ${packet.data.size} for CID ${(connection.connectionId as ConnectionID).toHexString()}")
        // TODO: Actual JS crypto implementation (e.g., using Web Crypto API) is required here.
        // The following is a conceptual placeholder structure.
        try {
            // 1. Conceptual Steps for Unprotection (similar to Native/JVM):
            //    a. Isolate Protected Header and Ciphertext+Tag from packet.data.
            //    b. Conceptually Remove Header Protection (needs HP key, sample, AES-ECB from Crypto).
            //       This would yield the cleartext header and the true PacketNumber.
            //    c. Reconstruct AAD from the cleartext header.
            //    d. Construct Nonce using the true PacketNumber and keys.iv.
            //    e. Decrypt Payload using Crypto.aesGcmDecrypt.

            // TODO: Implement full header parsing and header protection removal logic for JS.
            // For this placeholder, we use connection.packetNumber and an empty AAD.
            val currentPacketNumber = connection.packetNumber as PacketNumber // Placeholder for actual decoded PN

            // 2. Construct Nonce (using placeholder packet number)
            val nonce = ByteArray(keys.iv.size)
            val pnValue = currentPacketNumber.value
            for (i in nonce.indices) {
                val shift = (nonce.size - 1 - i) * 8
                val pnByte = if (shift < 64) ((pnValue shr shift) and 0xFFuL).toByte() else 0
                nonce[i] = (keys.iv.getOrElse(i) { 0 } xor pnByte)
            }

            // 3. AAD (Associated Additional Data) - Placeholder
            val aadForDecryption = ByteArray(0) // This should be the cleartext header
            // This should be only the ciphertext + tag part of packet.data
            val ciphertextAndTag = packet.data

            // 4. Call Crypto.aesGcmDecrypt
            // This calls the JS actual Crypto.aesGcmDecrypt, which might use Web Crypto API (async)
            // or Node.js crypto. The current Crypto stubs are simplified.
            val plaintext = Crypto.aesGcmDecrypt(keys.key, nonce, ciphertextAndTag, aadForDecryption)

            println("JS: Decryption successful (conceptually, using placeholder Crypto), plaintext size: ${plaintext.size}")
            return QuicPacket(
                packetType = null, // Or determine from key type / plaintext
                connectionId = connection.connectionId as ConnectionID,
                packetNumber = currentPacketNumber,
                payload = plaintext
            )
        } catch (e: Exception) {
            println("JS: Packet unprotection failed: ${e.message}")
            // For JS, a more specific catch for DOMException or other Web Crypto errors might be needed.
            // If Crypto.aesGcmDecrypt itself throws NotImplementedError, that will propagate.
            return null
        }
        // Original TODO: throw NotImplementedError("JS QuicProtectionContextValue.unprotectPacket not implemented")
>>>>>>> origin/jules_wip_8844705664950451013
    }
}

actual class JsQuicObservabilityContextValue : QuicObservabilityContextValue {
    override fun recordHandshakeAttempt() { println("JS: Handshake attempt...") }
    override fun recordHandshakeSuccess() { println("JS: Handshake success!") }
    override fun recordHandshakeFailure() { println("JS: Handshake failed.") }
<<<<<<< HEAD
    override fun recordHandshakeError(error: Exception) { println("JS: Handshake error: ${error.message}") } // Consider console.error for JS
    override fun recordDataSendAttempt(size: Int) { println("JS: Sending data ($size bytes)...") }
    override fun recordDataSendSuccess(size: Int) { println("JS: Data sent ($size bytes).") }
    override fun recordDataSendError(error: Exception) { println("JS: Data send error: ${error.message}") } // Consider console.error
=======
    override fun recordHandshakeError(error: Exception) { println("JS: Handshake error: ${error.message}") } // console.error in JS
    override fun recordDataSendAttempt(size: Int) { println("JS: Sending data ($size bytes)...") }
    override fun recordDataSendSuccess(size: Int) { println("JS: Data sent ($size bytes).") }
    override fun recordDataSendError(error: Exception) { println("JS: Data send error: ${error.message}") } // console.error in JS
>>>>>>> origin/jules_wip_8844705664950451013
}

actual class SimpleQuicContextBuilder actual constructor() {
    private var connectionContext: QuicConnectionContextValue? = null
    private var cryptoContext: QuicCryptoContextValue? = null
    // private var socketContext: QuicSocketContextValue? = null // REMOVED
    private var protectionContext: QuicProtectionContextValue? = null
    private var observabilityContext: QuicObservabilityContextValue? = null

    actual fun connection(context: QuicConnectionContextValue): SimpleQuicContextBuilder {
        this.connectionContext = context
        return this
    }

    actual fun crypto(context: QuicCryptoContextValue): SimpleQuicContextBuilder {
        this.cryptoContext = context
        return this
    }

    // actual fun socket(...) REMOVED

    actual fun protection(context: QuicProtectionContextValue): SimpleQuicContextBuilder {
        this.protectionContext = context
        return this
    }

    actual fun observability(context: QuicObservabilityContextValue): SimpleQuicContextBuilder {
        this.observabilityContext = context
        return this
    }

    actual fun build(): CoroutineContext {
<<<<<<< HEAD
        var contextElement: CoroutineContext = EmptyCoroutineContext
<<<<<<< HEAD
        // For JS, Dispatchers.Default is appropriate.
        // If specific JS dispatchers (like Main for UI or a dedicated IO if available) are needed,
        // that would be a more advanced setup.
=======
>>>>>>> origin/jules_wip_6906935130323988499
        contextElement += Dispatchers.Default

        connectionContext?.let { contextElement += it }
        cryptoContext?.let { contextElement += it }
        // No socketContext to add
        protectionContext?.let { contextElement += it }
        observabilityContext?.let { contextElement += it }
        return contextElement
    }
}

// Ensure common types like QuicPacket, QuicInitialKeys, QuicConnection, QuicPacketType,
// QuicConnectionStateEnum are properly imported or accessible from their commonMain definitions.
=======
        var context: CoroutineContext = Dispatchers.Default // Default dispatcher for JS
        connectionContext?.let { context += it }
        cryptoContext?.let { context += it }
        socketContext?.let { context += it }
        protectionContext?.let { context += it }
        observabilityContext?.let { context += it }
        return context
    }
}

// Placeholder actual implementations for types expected by the common code
actual fun getIODispatcher(): CoroutineDispatcher = Dispatchers.Default // For JS, Default is often fine for IO

actual fun createUdpSocket(): UdpSocket {
    // Placeholder for actual JS UDP socket creation (e.g., using Node.js dgram or WebRTC DataChannels if applicable)
    println("JS: Creating UDP Socket (Placeholder - No actual JS UDP API in browser, Node.js needed for dgram)")
    return object : UdpSocket {
        override suspend fun send(data: ByteArray, host: String, port: Int): Boolean {
            println("JS UdpSocket: Sending ${data.size} bytes to $host:$port (Placeholder)")
            // TODO: Implement actual JS UDP send if environment supports it (e.g. Node.js)
            return false // Assume failure for placeholder
        }
        override suspend fun receive(buffer: ByteArray): Int {
            println("JS UdpSocket: Receiving data (Placeholder, returning 0)")
            // TODO: Implement actual JS UDP receive if environment supports it
            return 0
        }
        override fun close() {
            println("JS UdpSocket: Closed (Placeholder)")
        }
        override val localPort: Int = 0 // Placeholder
    }
}

// Actual implementations for QuicConnection, QuicPacket, QuicInitialKeys using new types
actual class QuicConnection {
    actual var connectionId: Any = ConnectionID(byteArrayOf(1, 2, 3, 4)) // Using ConnectionID
    actual var packetNumber: Any = PacketNumber(0uL) // Using PacketNumber
    actual var state: QuicConnectionStateEnum = QuicConnectionStateEnum.IDLE
}

actual class QuicPacket(
    val packetType: QuicPacketType?,
    actual val connectionId: Any, // Should be ConnectionID
    actual val packetNumber: Any, // Should be PacketNumber
    actual val payload: ByteArray
)

actual class QuicInitialKeys // Remains a simple placeholder

// Local enums for placeholder types if not defined commonly
enum class QuicPacketType { INITIAL, DATA }
enum class QuicConnectionStateEnum { IDLE, HANDSHAKING, CONNECTED, CLOSED }

// Dummy common functions that were expected by actual classes (if not defined in common)
// These would call actual JS APIs or be common Kotlin logic.

// Removed old: fun generateClientHello(clientInitialDcid: ByteArray, serverName: String): ByteArray
// Removed old: fun processServerHello(response: ByteArray): Boolean

// Actual implementations for TLS handshake functions from QuicCurl.kt
actual fun generateClientHelloBytes(initialDestConnId: ConnectionID, serverName: String, clientHello: ClientHelloPayload): ByteArray {
    // Placeholder for JS. A real implementation would use Web Crypto or a JS/WASM TLS library.
    println("JS: actual generateClientHelloBytes for SN: $serverName, CID: ${initialDestConnId.toHexString()}")
    println("JS: ClientHelloPayload: ProtocolVersion: ${clientHello.protocolVersion.value}, RandomLen: ${clientHello.random.size}, CipherSuiteCount: ${clientHello.cipherSuites.size}")

    // Conceptual serialization (NOT a real TLS ClientHello):
    var result = "JS_CLIENT_HELLO_START:".encodeToByteArray()
    result += "Ver:${clientHello.protocolVersion.value}".encodeToByteArray()
    result += clientHello.random
    // In a real scenario, proper TLS encoding of each field and extension is needed.
    return result + "JS_CLIENT_HELLO_END:".encodeToByteArray()
}

actual fun parseServerHelloPayload(data: ByteArray): ServerHelloPayload? {
    // Placeholder for JS. A real implementation would use a JS/WASM TLS library.
    println("JS: actual parseServerHelloPayload, data size: ${data.size}")
    // Simple check for a marker, not real parsing.
    if (data.isNotEmpty() && data.decodeToString().contains("JS_SERVER_HELLO_RESPONSE")) { // Example marker
        println("JS: ServerHello marker found. Returning dummy ServerHelloPayload.")
        return ServerHelloPayload(
            protocolVersion = QuicTlsVersion.TLS_1_3,
            random = Random.nextBytes(32),
            sessionId = Random.nextBytes(0),
            cipherSuite = CipherSuite.TLS_AES_128_GCM_SHA256,
            compressionMethod = 0x00u,
            extensions = listOf(
                TlsExtension(TlsExtension.SUPPORTED_VERSIONS, byteArrayOf(0x03, 0x04)),
                TlsExtension(TlsExtension.KEY_SHARE, Random.nextBytes(32))
            )
        )
    }
    println("JS: ServerHello marker NOT found or data empty for placeholder parsing.")
    return null
}

// The expect internal fun deriveInitialSecrets(clientDstConnId: ConnectionID): QuicInitialKeys is in QuicCurl.kt
// Its actual implementation would typically use Crypto.
internal actual fun deriveInitialSecrets(clientDstConnId: ConnectionID): QuicInitialKeys {
     println("JS: actual deriveInitialSecrets for DCID ${clientDstConnId.toHexString()}")
    // This should call the common logic in QuicCurl.kt or directly use Crypto
    // Replicating common logic here for platform-specific actual:
    val initialSalt = byteArrayOf(
        0x38, 0x76, 0x25, 0x7a, 0x4c, 0x8f, 0xac, 0x34,
        0x44, 0xce, 0x8f, 0x9a, 0x38, 0x60, 0xdf, 0x0b,
        0x60, 0x63, 0x0e, 0x84
    )
    // Crypto actuals for JS use WebCrypto or Node.js crypto, which might be async.
    // For this synchronous expect, these calls would need to be adapted if actual Crypto is async.
    // Assuming Crypto calls are synchronous for this placeholder structure.
    val initialSecret = Crypto.hkdfExtract(initialSalt, clientDstConnId.value)
    val clientInitialSecret = Crypto.hkdfExpand(initialSecret, HKDFLabel("client in".encodeToByteArray()).value, 32)
    val keyLen = 16; val ivLen = 12; val hpLen = 16
    val clientKey = Crypto.hkdfExpand(clientInitialSecret, HKDFLabel("quic key".encodeToByteArray()).value, keyLen)
    val clientIv = Crypto.hkdfExpand(clientInitialSecret, HKDFLabel("quic iv".encodeToByteArray()).value, ivLen)
    val clientHp = Crypto.hkdfExpand(clientInitialSecret, HKDFLabel("quic hp".encodeToByteArray()).value, hpLen)
    return QuicInitialKeys(clientKey, clientIv, clientHp)
}

// Actual implementation for the expect fun in QuicCurl.kt
internal actual fun protectPacket(packet: QuicPacket, keys: QuicInitialKeys, connection: QuicConnection): ProtectedPayload {
    // This will call the common logic, which in turn uses Crypto.aesGcmEncrypt etc.
    // The JS Crypto object currently has placeholder / TODO() for its methods.
    // So, this will effectively use those placeholders until real JS crypto is implemented.
    println("JS: actual protectPacket called, will delegate to commonProtectPacket.")
    val protectedByteArray = commonProtectPacket(packet, keys, connection)
    return ProtectedPayload(protectedByteArray)
}
>>>>>>> origin/jules_wip_8844705664950451013
