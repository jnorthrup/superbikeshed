package evolution

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.KeyPairGenerator
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec as HmacKeySpec
import kotlinx.coroutines.*
import kotlin.experimental.and
import kotlin.experimental.or

class JvmUdpSocket : UdpSocket {
    private val socket = DatagramSocket()
    
    init {
        socket.soTimeout = 5000 // 5 second timeout
    }
    
    override suspend fun send(data: ByteArray, host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val address = InetAddress.getByName(host)
            val packet = DatagramPacket(data, data.size, address, port)
            socket.send(packet)
            return@withContext true
        } catch (e: Exception) {
            println("Send failed: ${e.message}")
            return@withContext false
        }
    }
    
    override suspend fun receive(buffer: ByteArray): Int = withContext(Dispatchers.IO) {
        try {
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            return@withContext packet.length
        } catch (e: Exception) {
            println("Receive failed: ${e.message}")
            return@withContext -1
        }
    }
    
    override fun close() {
        socket.close()
    }
}

actual fun createUdpSocket(): UdpSocket = JvmUdpSocket()

actual fun getIODispatcher(): CoroutineDispatcher = Dispatchers.IO

// Removed old generateClientHello(initialDestConnId: ByteArray, serverName: String)
// Replaced with new actual fun generateClientHelloBytes
actual fun generateClientHelloBytes(initialDestConnId: ConnectionID, serverName: String, clientHello: ClientHelloPayload): ByteArray {
    // In a real JVM implementation, this would use a TLS library like JSSE or BouncyCastle
    // to construct the ClientHello message from the ClientHelloPayload object.
    // This placeholder will just serialize some fields conceptually.
    println("JVM: generateClientHelloBytes for SN: $serverName, CID: ${initialDestConnId.toHexString()}")
    println("JVM: ClientHelloPayload: ProtocolVersion: ${clientHello.protocolVersion.value}, NumCipherSuites: ${clientHello.cipherSuites.size}")

    // Conceptual serialization:
    // This is NOT a real TLS ClientHello a actual byte-level serialization is complex.
    var result = mutableListOf<Byte>()
    // Record Layer
    result.add(0x16) // ContentType: Handshake
    result.add((clientHello.protocolVersion.value shr 8).toByte()) // Legacy Record Version (e.g., 0x0303 for TLS 1.2)
    result.add(clientHello.protocolVersion.value.toByte())
    // Length placeholder (UInt16)
    val recordLengthPos = result.size
    result.add(0x00); result.add(0x00)

    // Handshake Protocol
    result.add(0x01) // HandshakeType: ClientHello
    // Length placeholder (UInt24)
    val handshakeLengthPos = result.size
    result.add(0x00); result.add(0x00); result.add(0x00)

    // ClientHello specific fields
    result.add((clientHello.protocolVersion.value shr 8).toByte()) // ProtocolVersion (e.g. 0x0304 for TLS 1.3)
    result.add(clientHello.protocolVersion.value.toByte())
    result.addAll(clientHello.random.toList())
    result.add(clientHello.sessionId.size.toByte())
    result.addAll(clientHello.sessionId.toList())

    // Cipher Suites
    result.add(((clientHello.cipherSuites.size * 2) shr 8).toByte())
    result.add((clientHello.cipherSuites.size * 2).toByte())
    clientHello.cipherSuites.forEach {
        result.add((it.value shr 8).toByte())
        result.add(it.value.toByte())
    }

    // Compression Methods
    result.add(clientHello.compressionMethods.size.toByte())
    result.addAll(clientHello.compressionMethods.toList())
    
    // Extensions
    val extensionsStartPos = result.size
    result.add(0x00); result.add(0x00) // Extensions Length Placeholder
    clientHello.extensions.forEach { ext ->
        result.add((ext.type shr 8).toByte())
        result.add(ext.type.toByte())
        result.add((ext.data.size shr 8).toByte())
        result.add(ext.data.size.toByte())
        result.addAll(ext.data.toList())
    }
    val extensionsLength = result.size - extensionsStartPos - 2
    result[extensionsStartPos] = (extensionsLength shr 8).toByte()
    result[extensionsStartPos + 1] = extensionsLength.toByte()

    // Update Handshake Length
    val handshakeLength = result.size - handshakeLengthPos - 3
    result[handshakeLengthPos] = (handshakeLength shr 16).toByte()
    result[handshakeLengthPos + 1] = (handshakeLength shr 8).toByte()
    result[handshakeLengthPos + 2] = handshakeLength.toByte()

    // Update Record Length
    val recordLength = result.size - recordLengthPos - 2
    result[recordLengthPos] = (recordLength shr 8).toByte()
    result[recordLengthPos+1] = recordLength.toByte()

    return result.toByteArray()
}

// Removed old processServerHello(serverHello: ByteArray)
// Replaced with new actual fun parseServerHelloPayload
actual fun parseServerHelloPayload(data: ByteArray): ServerHelloPayload? {
    // In a real JVM implementation, this would use a TLS library to parse the ServerHello.
    // This placeholder will just check for a marker and return a dummy object.
    // For simplicity, we're not actually parsing `data` here but returning a fixed object
    // if we imagine `data` is a valid ServerHello.
    println("JVM: parseServerHelloPayload, data size: ${data.size}")
    if (data.isNotEmpty()) { // Replace with actual parsing logic
        println("JVM: Assuming valid ServerHello. Returning dummy ServerHelloPayload.")
        return ServerHelloPayload(
            protocolVersion = QuicTlsVersion.TLS_1_3,
            random = Random.nextBytes(32), // Dummy random
            sessionId = Random.nextBytes(0), // Dummy empty session ID
            cipherSuite = CipherSuite.TLS_AES_128_GCM_SHA256,
            compressionMethod = 0x00u,
            extensions = listOf(
                TlsExtension(TlsExtension.SUPPORTED_VERSIONS, byteArrayOf(0x03, 0x04)), // TLS 1.3
                TlsExtension(TlsExtension.KEY_SHARE, Random.nextBytes(34)) // Dummy key share (e.g. group + key)
            )
        )
    }
    println("JVM: ServerHello data was empty or invalid.")
    return null
}

// Actual JVM implementations for Crypto expect object
actual object Crypto {
    actual fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
    hello.addAll(listOf(0x16, 0x03, 0x03).map { it.toByte() })
    val recordLengthPos = hello.size
    hello.addAll(listOf(0x00, 0x00).map { it.toByte() })
    
    // ClientHello Message
    hello.add(0x01)
    val msgLengthPos = hello.size
    hello.addAll(listOf(0x00, 0x00, 0x00).map { it.toByte() })
    
    // Protocol Version (TLS 1.2 for compatibility)
    hello.addAll(listOf(0x03, 0x03).map { it.toByte() })
    
    // Random (32 bytes) - cryptographically secure
    val random = SecureRandom()
    val randomBytes = ByteArray(32)
    random.nextBytes(randomBytes)
    hello.addAll(randomBytes.toList())
    
    // Session ID (empty)
    hello.add(0x00)
    
    // Cipher Suites (TLS 1.3)
    hello.addAll(listOf(0x00, 0x08).map { it.toByte() })
    hello.addAll(listOf(0x13, 0x01).map { it.toByte() })
    hello.addAll(listOf(0x13, 0x02).map { it.toByte() })
    hello.addAll(listOf(0x13, 0x03).map { it.toByte() })
    hello.addAll(listOf(0x13, 0x04).map { it.toByte() })
    
    // Compression Methods
    hello.addAll(listOf(0x01, 0x00).map { it.toByte() })
    
    // Extensions
    val extLengthPos = hello.size
    hello.addAll(listOf(0x00, 0x00).map { it.toByte() })
    
    // supported_versions extension
    hello.addAll(listOf(0x00, 0x2b).map { it.toByte() })
    hello.addAll(listOf(0x00, 0x03).map { it.toByte() })
    hello.add(0x02)
    hello.addAll(listOf(0x03, 0x04).map { it.toByte() })
    
    // key_share extension with X25519
    hello.addAll(listOf(0x00, 0x33).map { it.toByte() })
    hello.addAll(listOf(0x00, 0x26).map { it.toByte() })
    hello.addAll(listOf(0x00, 0x24).map { it.toByte() })
    hello.addAll(listOf(0x00, 0x1d).map { it.toByte() })
    hello.addAll(listOf(0x00, 0x20).map { it.toByte() })
    
    // Generate X25519 public key
    try {
        val keyGen = KeyPairGenerator.getInstance("X25519")
        val keyPair = keyGen.generateKeyPair()
        val publicKey = keyPair.public.encoded
        hello.addAll(publicKey.takeLast(32))
    } catch (e: Exception) {
        // Fallback for environments without X25519 support (e.g., older JVMs)
        // This is a placeholder and should ideally be handled by a proper TLS library.
        repeat(32) { hello.add(random.nextInt(256).toByte()) }
    }
    
    // server_name extension
    hello.addAll(listOf(0x00, 0x00).map { it.toByte() })
    val nameBytes = serverName.toByteArray()
    val extLength = nameBytes.size + 5
    hello.addAll(listOf((extLength shr 8).toByte(), extLength.toByte()))
    val listLength = nameBytes.size + 3
    hello.addAll(listOf((listLength shr 8).toByte(), listLength.toByte()))
    hello.add(0x00)
    hello.addAll(listOf((nameBytes.size shr 8).toByte(), nameBytes.size.toByte()))
    hello.addAll(nameBytes.toList())
    
    // Update lengths
    val extLengthTotal = hello.size - extLengthPos - 2
    hello[extLengthPos] = (extLengthTotal shr 8).toByte()
    hello[extLengthPos + 1] = extLengthTotal.toByte()
    
    val msgLength = hello.size - msgLengthPos - 3
    hello[msgLengthPos] = (msgLength shr 16).toByte()
    hello[msgLengthPos + 1] = (msgLength shr 8).toByte()
    hello[msgLengthPos + 2] = msgLength.toByte()
    
    val recordLength = hello.size - recordLengthPos - 2
    hello[recordLengthPos] = (recordLength shr 8).toByte()
    hello[recordLengthPos + 1] = recordLength.toByte()
    
    return hello.toByteArray()
}

actual fun processServerHello(serverHello: ByteArray): Boolean {
    // A real implementation would parse the ServerHello, extract server parameters,
    // perform certificate validation, and derive handshake keys.
    // This is a placeholder for the complex TLS handshake logic.
    return serverHello.isNotEmpty() // Simplified check
}

// Refactored actual implementations for TLS handshake functions
actual fun generateClientHelloBytes(initialDestConnId: ConnectionID, serverName: String, clientHello: ClientHelloPayload): ByteArray {
    // In a real JVM implementation, this would use a TLS library like JSSE or BouncyCastle
    // to construct the ClientHello message from the ClientHelloPayload object.
    // This placeholder will just serialize some fields conceptually.
    println("JVM: generateClientHelloBytes for SN: $serverName, CID: ${initialDestConnId.toHexString()}")
    println("JVM: ClientHelloPayload: ProtocolVersion: ${clientHello.protocolVersion.value}, NumCipherSuites: ${clientHello.cipherSuites.size}")

    // Conceptual serialization:
    val versionBytes = byteArrayOf((clientHello.protocolVersion.value shr 8).toByte(), clientHello.protocolVersion.value.toByte())
    val randomBytes = clientHello.random
    var result = "CLIENT_HELLO_START".encodeToByteArray() + versionBytes + randomBytes
    clientHello.cipherSuites.forEach { result += byteArrayOf((it.value shr 8).toByte(), it.value.toByte()) }
    clientHello.extensions.forEach { ext ->
        result += byteArrayOf((ext.type shr 8).toByte(), ext.type.toByte())
        result += ext.data
    }
    return result + "CLIENT_HELLO_END".encodeToByteArray()
}

actual fun parseServerHelloPayload(data: ByteArray): ServerHelloPayload? {
    // In a real JVM implementation, this would use a TLS library to parse the ServerHello.
    // This placeholder will just check for a marker and return a dummy object.
    println("JVM: parseServerHelloPayload, data size: ${data.size}")
    val KOTLIN_TLS_SERVER_HELLO_MARKER = "SERVER_HELLO_START"
    val KOTLIN_TLS_SERVER_HELLO_END_MARKER = "SERVER_HELLO_END"

    val text = data.decodeToString() // Simple check
    if (text.startsWith(KOTLIN_TLS_SERVER_HELLO_MARKER) && text.endsWith(KOTLIN_TLS_SERVER_HELLO_END_MARKER)) {
        println("JVM: ServerHello marker found. Returning dummy ServerHelloPayload.")
        return ServerHelloPayload(
            protocolVersion = QuicTlsVersion.TLS_1_3,
            random = ByteArray(32) { 0x22.toByte() },
            sessionId = byteArrayOf(0x01, 0x02),
            cipherSuite = CipherSuite.TLS_AES_128_GCM_SHA256,
            compressionMethod = 0x00u,
            extensions = listOf(
                TlsExtension(TlsExtension.SUPPORTED_VERSIONS, byteArrayOf(0x03, 0x04))
            )
        )
    }
    println("JVM: ServerHello marker NOT found.")
    return null
}


// Actual JVM implementations for Crypto expect object
actual object Crypto {
    actual fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = HmacKeySpec(salt.takeIf { it.isNotEmpty() } ?: ByteArray(32), "HmacSHA256")
        mac.init(keySpec)
        return mac.doFinal(ikm)
    }

    actual fun hkdfExpand(prk: ByteArray, info: ByteArray, len: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = HmacKeySpec(prk, "HmacSHA256")
        
        val result = ByteArray(len)
        var offset = 0
        var counter = 1
        
        while (offset < len) {
            mac.init(keySpec)
            mac.update(info)
            mac.update(counter.toByte())
            
            val hash = mac.doFinal()
            val copyLength = minOf(hash.size, len - offset)
            hash.copyInto(result, offset, 0, copyLength)
            
            offset += copyLength
            counter++
        }
        
        return result
    }

    actual fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(128, iv) // 128-bit auth tag
        
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    actual fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    actual fun aesEcbEncrypt(plaintext: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        return cipher.doFinal(plaintext)
    }
}

// Actual implementation of protectPacket for JVM
internal actual fun protectPacket(packet: QuicPacket, keys: QuicInitialKeys, connection: QuicConnection): ByteArray {
    // This is a simplified implementation for demonstration.
    // A full implementation requires careful handling of header protection and AEAD.

    // 1. Construct the unprotected header (excluding packet number and protected bits)
    val headerBytes = mutableListOf<Byte>()
    var firstByte: UByte = 0u

    if (packet.packetType != null) { // Long Header
        firstByte = firstByte or 0x80u // Long Header Form
        firstByte = firstByte or 0x40u // Fixed Bit
        firstByte = firstByte or (packet.packetType.typeValue shl 4) // Packet Type

        // Determine Packet Number Length (simplified to 4 bytes for now)
        val pnLengthBits = 0x03u // 4 bytes
        firstByte = firstByte or pnLengthBits
        headerBytes.add(firstByte.toByte())

        headerBytes.addAll(byteArrayOf(0x00, 0x00, 0x00, 0x01).toList()) // Version (QUIC v1)

        // Destination Connection ID
        headerBytes.add(packet.connectionId.size.toByte())
        headerBytes.addAll(packet.connectionId.toList())

        // Source Connection ID (empty for client initial)
        headerBytes.add(0x00) // SCID Length 0

        // Token Length (0 for initial client hello)
        headerBytes.addAll(encodeVarint(0uL).toList())

        // Placeholder for Length (Packet Number + Protected Payload + AEAD Tag)
        // This will be filled after encryption and PN encoding
        val lengthPlaceholderIndex = headerBytes.size
        headerBytes.addAll(byteArrayOf(0x00, 0x00).toList()) // Placeholder for 2-byte varint length

        // Packet Number (unprotected part)
        val pnBytes = ByteArray(4) // Assuming 4-byte PN for simplicity
        pnBytes[0] = (packet.packetNumber shr 24).toByte()
        pnBytes[1] = (packet.packetNumber shr 16).toByte()
        pnBytes[2] = (packet.packetNumber shr 8).toByte()
        pnBytes[3] = (packet.packetNumber and 0xFFu).toByte()
        headerBytes.addAll(pnBytes.toList())

        // 2. Encrypt the payload
        // Nonce construction: IV (12 bytes) XORed with Packet Number (padded to 12 bytes)
        val rawIv = keys.iv
        val packetNumberBytes = ByteArray(12)
        val pnAsBytes = packet.packetNumber.toLong().toBigInteger().toByteArray() // Convert ULong to BigInteger then bytes
        pnAsBytes.copyInto(packetNumberBytes, 12 - pnAsBytes.size) // Pad with zeros at the beginning

        val nonce = ByteArray(12) { i -> (rawIv[i] xor packetNumberBytes[i]) }

        // AAD (Authenticated Associated Data) for AEAD is the header up to the Packet Number
        val aad = headerBytes.toByteArray()

        val encryptedPayloadAndTag = Crypto.aesGcmEncrypt(keys.key, nonce, packet.payload, aad)

        // Update the Length field in the header
        val totalProtectedLength = pnBytes.size + encryptedPayloadAndTag.size
        val encodedLength = encodeVarint(totalProtectedLength.toULong())
        // Replace placeholder with actual length
        if (encodedLength.size > 2) error("Encoded length too large for 2-byte placeholder")
        headerBytes[lengthPlaceholderIndex] = encodedLength[0]
        headerBytes[lengthPlaceholderIndex + 1] = encodedLength[1]

        // 3. Apply Header Protection (simplified - actual implementation is complex)
        // This involves encrypting a sample of the ciphertext and XORing it with parts of the header.
        // For a real implementation, this is critical and complex.
        // TODO: Implement proper header protection as per RFC 9000 Section 5.4
        val protectedHeader = headerBytes.toByteArray() // For now, no actual header protection applied

        return protectedHeader + encryptedPayloadAndTag
    } else { // Short Header
        // Short header protection is also complex.
        // TODO: Implement short header protection
        throw NotImplementedError("Short header packet protection not implemented yet.")
    }
}
