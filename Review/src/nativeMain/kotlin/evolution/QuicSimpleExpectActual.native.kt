package evolution

import borg.trikeshed.reactor.UdpSocket // Keep for deprecated QuicSocketContextValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext
import evolution.* // Using wildcard import

// Actual implementations for the simple context value interfaces for Native
actual object QuicConnectionContextKey : CoroutineContext.Key<QuicConnectionContextValue>
actual object QuicCryptoContextKey : CoroutineContext.Key<QuicCryptoContextValue>

actual class NativeQuicConnectionContextValue(
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
            payload = payload
        )
    }

    // Signature changed: payload: ByteArray -> payload: StreamFrameData
    override fun createDataPacket(payload: StreamFrameData): QuicPacket {
        val currentPnValue = (connection.packetNumber as PacketNumber).value
        connection.packetNumber = PacketNumber(currentPnValue + 1uL)

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
        return evolution.generateClientHello(initialDestConnId = ByteArray(0), serverName = serverName)
    }

    override fun processServerResponse(response: ByteArray): Boolean {
        return evolution.processServerHello(response)
    }

    // Signature changed: clientDstConnId: ByteArray -> clientDstConnId: ConnectionID
    override fun deriveInitialSecrets(clientDstConnId: ConnectionID): QuicInitialKeys {
        // Use .value to get the underlying ByteArray
        println("Native: Deriving initial secrets for DCID ${clientDstConnId.toHexString()}")
        return QuicInitialKeys() // Placeholder
    }
}

actual class NativeQuicProtectionContextValue : QuicProtectionContextValue {
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

    actual fun protection(context: QuicProtectionContextValue): SimpleQuicContextBuilder {
        this.protectionContext = context
        return this
    }

    actual fun observability(context: QuicObservabilityContextValue): SimpleQuicContextBuilder {
        this.observabilityContext = context
        return this
    }

    actual fun build(): CoroutineContext {
        // Native might use a specific dispatcher or EmptyCoroutineContext if not specified
        var context: CoroutineContext = kotlin.coroutines.EmptyCoroutineContext
        connectionContext?.let { context += it }
        cryptoContext?.let { context += it }
        protectionContext?.let { context += it }
        observabilityContext?.let { context += it }
        return context
    }
}

// Actual implementations for QuicConnection, QuicPacket, QuicInitialKeys using new types
actual class QuicConnection {
    actual var connectionId: Any = ConnectionID(byteArrayOf(1,2,3,4)) // Example, should be ConnectionID
    actual var packetNumber: Any = PacketNumber(0uL) // Example, should be PacketNumber
    actual var state: QuicConnectionStateEnum = QuicConnectionStateEnum.IDLE
}

actual class QuicPacket(
    val packetType: QuicPacketType?,
    actual val connectionId: Any, // Should be ConnectionID, type Any for placeholder flexibility initially
    actual val packetNumber: Any, // Should be PacketNumber
    actual val payload: ByteArray
)

actual class QuicInitialKeys // Remains a simple placeholder

// Local enums for placeholder types
enum class QuicPacketType { INITIAL, DATA }
enum class QuicConnectionStateEnum { IDLE, HANDSHAKING, CONNECTED, CLOSED }

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
