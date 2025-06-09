package evolution

<<<<<<< HEAD
import borg.trikeshed.reactor.UdpSocket // Keep for deprecated QuicSocketContextValue
=======
// import borg.trikeshed.reactor.UdpSocket // Will be removed if unused
>>>>>>> origin/jules_wip_6906935130323988499
import kotlinx.coroutines.CoroutineDispatcher
<<<<<<< HEAD
import kotlinx.coroutines.Dispatchers // Native usually has its own Dispatchers, but Default is common.
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
=======
// import kotlinx.coroutines.Dispatchers // Native typically uses its own dispatcher mechanisms or a global one.
import kotlin.coroutines.CoroutineContext
// Import new types from QuicSpecTypes.kt
import evolution.* // Using wildcard import
>>>>>>> origin/jules_wip_8844705664950451013

// Actual implementations for the simple context value interfaces for Native
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

actual class NativeQuicConnectionContextValue(
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
        println("Native: Connection state transitioned to ESTABLISHED")
    }
}

actual class NativeQuicCryptoContextValue : QuicCryptoContextValue {
    override fun generateClientHello(serverName: String): ByteArray {
<<<<<<< HEAD
<<<<<<< HEAD
        // Assuming global evolution.generateClientHello and connection.connectionId is available
        // This might need a QuicConnection instance if not available globally.
        // For now, assuming it can get a connectionId or one isn't strictly needed by this impl.
        // The JVM version was updated to use connection.connectionId.
        // Let's assume this class also gets a connection instance or this hello doesn't need a specific CID.
        // For consistency with the prompt's previous JVM change, if this class had `val connection: QuicConnection`,
        // it would be: evolution.generateClientHello(connectionIdForHello = connection.connectionId, serverName = serverName)
        // Since it doesn't, we use a placeholder or rely on a global/default.
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
        // Placeholder: In a real scenario, this would call native crypto libraries
        println("Native: Generating ClientHello for $serverName")
        return "NATIVE_CLIENT_HELLO_FOR_$serverName".encodeToByteArray()
    }

    override fun processServerResponse(response: ByteArray): Boolean {
        // Placeholder: In a real scenario, this would process the server's handshake message
        println("Native: Processing ServerResponse: ${response.decodeToString().take(50)}...")
        return true // Assume success for placeholder
    }

    // Signature changed: clientDstConnId: ByteArray -> clientDstConnId: ConnectionID
    override fun deriveInitialSecrets(clientDstConnId: ConnectionID): QuicInitialKeys {
        // Use .value to get the underlying ByteArray
        println("Native: Deriving initial secrets for DCID ${clientDstConnId.toHexString()}")
        return QuicInitialKeys() // Placeholder
>>>>>>> origin/jules_wip_8844705664950451013
    }
}

@Deprecated("Replaced by direct use of PlatformUdpChannel and PlatformIoService from context.")
actual class NativeQuicSocketContextValue(
    override val socket: UdpSocket, // UdpSocket from borg.trikeshed
    override val host: String,
    override val port: Int
) : QuicSocketContextValue {
    // Signature changed: packet: ByteArray -> packet: ProtectedPayload
    override suspend fun sendPacket(packet: ProtectedPayload): Boolean {
        // Use .data (or .value) to send the underlying ByteArray
        println("Native: Sending packet of size ${packet.data.size} to $host:$port")
        return socket.send(packet.data, host, port)
    }

    override suspend fun receivePacket(buffer: ByteArray): Int {
        println("Native: Attempting to receive packet into buffer of size ${buffer.size}")
        val receivedBytes = socket.receive(buffer)
        println("Native: Received $receivedBytes bytes")
        return receivedBytes
    }
}

actual class NativeQuicProtectionContextValue : QuicProtectionContextValue {
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

// @Deprecated("...") actual class NativeQuicSocketContextValue ... REMOVED

actual class NativeQuicProtectionContextValue : QuicProtectionContextValue {
    actual override suspend fun protectPacket(context: CoroutineContext, packet: QuicPacket, keys: QuicInitialKeys, connection: QuicConnection): ByteArray {
        return evolution.protectPacket(context, packet, keys, connection)
    }

    actual override suspend fun unprotectPacket(context: CoroutineContext, packet: ByteArray, keys: QuicInitialKeys, connection: QuicConnection): QuicPacket? {
        return evolution.unprotectPacket(context, packet, keys, connection)
>>>>>>> origin/jules_wip_6906935130323988499
=======
    // Return type changed: ByteArray -> ProtectedPayload
    override fun protectPacket(packet: QuicPacket, keys: QuicInitialKeys, connection: QuicConnection): ProtectedPayload {
        println("Native: Protecting packet PN ${(packet.packetNumber as PacketNumber).value} for CID ${(connection.connectionId as ConnectionID).toHexString()}")
        // Placeholder: returns the original payload, wrapped
        return ProtectedPayload(packet.payload)
    }

    // Signature changed: packet: ByteArray -> packet: ProtectedPayload
    override fun unprotectPacket(packet: ProtectedPayload, keys: QuicInitialKeys, connection: QuicConnection): QuicPacket? {
        println("Native: Attempting to unprotect packet of size ${packet.data.size} for CID ${(connection.connectionId as ConnectionID).toHexString()}")
        try {
            // 1. Conceptual Steps for Unprotection:
            //    a. Isolate Protected Header and Ciphertext+Tag from packet.data.
            //       This requires parsing initial header fields (like first byte, CID lengths)
            //       to determine the packet number offset and length of the header.
            //    b. Extract Ciphertext Sample: A sample of the ciphertext (usually 16 bytes)
            //       is taken starting at an offset relative to the packet number field.
            //       The exact offset depends on the packet number length, which is itself protected.
            //    c. Generate Header Protection Mask: Use keys.hp and the sample with AES-ECB
            //       (or the specific cipher for HP) to generate a 5-byte mask.
            //    d. Remove Header Protection: XOR the mask with the protected header fields
            //       (first byte and packet number bytes). This reveals the true first byte
            //       (including actual PN length) and the cleartext packet number.
            //    e. Decode Packet Number: Decode the now cleartext packet number. This is `currentPacketNumber`.
            //    f. Reconstruct AAD: The AAD is the now cleartext full header.
            //    g. Construct Nonce: Use the decoded `currentPacketNumber` and `keys.iv`.
            //    h. Decrypt Payload: Call Crypto.aesGcmDecrypt with key, nonce, (Ciphertext+Tag), and AAD.

            // The following is a simplified placeholder acknowledging these complexities.
            // TODO: Implement full header parsing and header protection removal logic.

            // For this placeholder, we'll use connection.packetNumber and an empty AAD,
            // acknowledging this is not correct for a real implementation.
            val currentPacketNumber = connection.packetNumber as PacketNumber // Placeholder for actual decoded PN

            // 2. Construct Nonce (using the placeholder packet number)
            val nonce = ByteArray(keys.iv.size)
            val pnValue = currentPacketNumber.value
            for (i in nonce.indices) {
                val shift = (nonce.size - 1 - i) * 8
                val pnByte = if (shift < 64) ((pnValue shr shift) and 0xFFuL).toByte() else 0
                nonce[i] = (keys.iv.getOrElse(i) { 0 } xor pnByte)
            }

            // 3. AAD (Associated Additional Data) - Placeholder
            // This should be the cleartext header after HP removal.
            val aadForDecryption = ByteArray(0) // Highly simplified placeholder
            // This should be only the ciphertext + tag, not the full packet.data
            val ciphertextAndTag = packet.data // Incorrect, needs header part stripped

            // 4. Call Crypto.aesGcmDecrypt
            val plaintext = Crypto.aesGcmDecrypt(keys.key, nonce, ciphertextAndTag, aadForDecryption)

            // 5. If decryption is successful, construct QuicPacket
            println("Native: Decryption successful (conceptually), plaintext size: ${plaintext.size}")
            return QuicPacket(
                packetType = null, // Or determine from key type / plaintext
                connectionId = connection.connectionId as ConnectionID,
                packetNumber = currentPacketNumber,
                payload = plaintext
            )
        } catch (e: Exception) {
            println("Native: Packet unprotection failed: ${e.message}")
            return null
        }
>>>>>>> origin/jules_wip_8844705664950451013
    }
}

actual class NativeQuicObservabilityContextValue : QuicObservabilityContextValue {
    override fun recordHandshakeAttempt() { println("Native: Handshake attempt...") }
    override fun recordHandshakeSuccess() { println("Native: Handshake success!") }
    override fun recordHandshakeFailure() { println("Native: Handshake failed.") }
    override fun recordHandshakeError(error: Exception) { println("Native: Handshake error: ${error.message}") }
    override fun recordDataSendAttempt(size: Int) { println("Native: Sending data ($size bytes)...") }
    override fun recordDataSendSuccess(size: Int) { println("Native: Data sent ($size bytes).") }
    override fun recordDataSendError(error: Exception) { println("Native: Data send error: ${error.message}") }
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
        contextElement += Dispatchers.Default // Add a default dispatcher suitable for Native
=======
        contextElement += Dispatchers.Default
>>>>>>> origin/jules_wip_6906935130323988499

        connectionContext?.let { contextElement += it }
        cryptoContext?.let { contextElement += it }
        // No socketContext to add
        protectionContext?.let { contextElement += it }
        observabilityContext?.let { contextElement += it }
        return contextElement
    }
}

// Note: Ensure common types like QuicPacket, QuicInitialKeys, QuicConnection, QuicPacketType,
// QuicConnectionStateEnum are properly imported or accessible from their commonMain definitions
// (e.g., from evolution.QuicCurl.kt).
// The generateClientHello in NativeQuicCryptoContextValue was kept with ByteArray(0) for initialDestConnId
// as this class, unlike JvmQuicConnectionContextValue, does not have its own `connection` property.
// If it needs one, its constructor should be updated.
=======
        // Native might use a specific dispatcher or EmptyCoroutineContext if not specified
        var context: CoroutineContext = kotlin.coroutines.EmptyCoroutineContext
        connectionContext?.let { context += it }
        cryptoContext?.let { context += it }
        socketContext?.let { context += it }
        protectionContext?.let { context += it }
        observabilityContext?.let { context += it }
        return context
    }
}

// Placeholder actual implementations for types expected by the common code
// These would typically interact with native libraries or platform APIs.

actual fun getIODispatcher(): CoroutineDispatcher {
    // On Native, Dispatchers.IO might not be available or suitable.
    // This often requires a platform-specific dispatcher (e.g., for Ktor client engine).
    // For a simple placeholder, we can return a default or throw NotImplementedError.
    // Note: kotlinx.coroutines.Dispatchers is not directly available in common native code
    // without specific dependencies like kotlinx-coroutines-core.
    // However, for the sake of this example, let's assume a global dispatcher if available,
    // or a custom one. For now, this will be a conceptual placeholder.
    throw NotImplementedError("Native IODispatcher not implemented in this placeholder")
}

actual fun createUdpSocket(): UdpSocket {
    // Placeholder for actual native UDP socket creation
    println("Native: Creating UDP Socket (Placeholder)")
    return object : UdpSocket {
        override suspend fun send(data: ByteArray, host: String, port: Int): Boolean {
            println("Native UdpSocket: Sending ${data.size} bytes to $host:$port (Placeholder)")
            return true // Assume success
        }
        override suspend fun receive(buffer: ByteArray): Int {
            println("Native UdpSocket: Receiving data (Placeholder, returning 0)")
            // Simulate receiving some data for testing if needed
            // val simulatedData = "PONG".encodeToByteArray()
            // simulatedData.copyInto(buffer, 0, 0, minOf(buffer.size, simulatedData.size))
            // return minOf(buffer.size, simulatedData.size)
            return 0
        }
        override fun close() {
            println("Native UdpSocket: Closed (Placeholder)")
        }
        override val localPort: Int = 12345 // Placeholder
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
    actual val connectionId: Any, // Should be ConnectionID, type Any for placeholder flexibility initially
    actual val packetNumber: Any, // Should be PacketNumber
    actual val payload: ByteArray
)

actual class QuicInitialKeys // Remains a simple placeholder

// Local enums for placeholder types if not defined commonly
enum class QuicPacketType { INITIAL, DATA }
enum class QuicConnectionStateEnum { IDLE, HANDSHAKING, CONNECTED, CLOSED }

// Dummy common functions that were expected by actual classes (if not defined in common)
// These would call actual native APIs or be common Kotlin logic.

// Removed old: fun generateClientHello(clientInitialDcid: ByteArray, serverName: String): ByteArray
// Removed old: fun processServerHello(response: ByteArray): Boolean

// Actual implementations for TLS handshake functions from QuicCurl.kt
actual fun generateClientHelloBytes(initialDestConnId: ConnectionID, serverName: String, clientHello: ClientHelloPayload): ByteArray {
    // Placeholder for Native. A real implementation would use a native TLS library (e.g., OpenSSL's libssl)
    // to serialize the ClientHelloPayload into bytes.
    println("Native: actual generateClientHelloBytes for SN: $serverName, CID: ${initialDestConnId.toHexString()}")
    println("Native: ClientHelloPayload: ProtocolVersion: ${clientHello.protocolVersion.value}, Random: ${clientHello.random.take(4).joinToString()}, NumCipherSuites: ${clientHello.cipherSuites.size}")

    // Conceptual serialization (NOT a real TLS ClientHello):
    var result = "NATIVE_CLIENT_HELLO_START:".encodeToByteArray()
    result += "Version:${clientHello.protocolVersion.value}".encodeToByteArray() // Simplified
    result += clientHello.random
    // In a real scenario, proper TLS encoding of each field and extension is needed.
    return result + "NATIVE_CLIENT_HELLO_END:".encodeToByteArray()
}

actual fun parseServerHelloPayload(data: ByteArray): ServerHelloPayload? {
    // Placeholder for Native. A real implementation would use a native TLS library.
    println("Native: actual parseServerHelloPayload, data size: ${data.size}")
    // Simple check for a marker, not real parsing.
    if (data.isNotEmpty() && data.decodeToString().contains("NATIVE_SERVER_HELLO_RESPONSE")) { // Example marker
        println("Native: ServerHello marker found. Returning dummy ServerHelloPayload.")
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
    println("Native: ServerHello marker NOT found or data empty for placeholder parsing.")
    return null
}

// The actual for deriveInitialSecrets should be here if it's not common
// The expect internal fun deriveInitialSecrets(clientDstConnId: ConnectionID): QuicInitialKeys is in QuicCurl.kt
// Its actual implementation would typically use Crypto.
internal actual fun deriveInitialSecrets(clientDstConnId: ConnectionID): QuicInitialKeys {
     println("Native: actual deriveInitialSecrets for DCID ${clientDstConnId.toHexString()}")
    // This should call the common logic in QuicCurl.kt or directly use Crypto
    // Replicating common logic here for platform-specific actual:
    val initialSalt = byteArrayOf(
        0x38, 0x76, 0x25, 0x7a, 0x4c, 0x8f, 0xac, 0x34,
        0x44, 0xce, 0x8f, 0x9a, 0x38, 0x60, 0xdf, 0x0b,
        0x60, 0x63, 0x0e, 0x84
    )
    val initialSecret = Crypto.hkdfExtract(initialSalt, clientDstConnId.value) // Crypto is native actual
    val clientInitialSecret = Crypto.hkdfExpand(initialSecret, HKDFLabel("client in".encodeToByteArray()).value, 32)
    val keyLen = 16; val ivLen = 12; val hpLen = 16
    val clientKey = Crypto.hkdfExpand(clientInitialSecret, HKDFLabel("quic key".encodeToByteArray()).value, keyLen)
    val clientIv = Crypto.hkdfExpand(clientInitialSecret, HKDFLabel("quic iv".encodeToByteArray()).value, ivLen)
    val clientHp = Crypto.hkdfExpand(clientInitialSecret, HKDFLabel("quic hp".encodeToByteArray()).value, hpLen)
    return QuicInitialKeys(clientKey, clientIv, clientHp)
}


// Actual implementation for the expect fun in QuicCurl.kt
internal actual fun protectPacket(packet: QuicPacket, keys: QuicInitialKeys, connection: QuicConnection): ProtectedPayload {
    val protectedByteArray = commonProtectPacket(packet, keys, connection)
    return ProtectedPayload(protectedByteArray)
}
>>>>>>> origin/jules_wip_8844705664950451013
