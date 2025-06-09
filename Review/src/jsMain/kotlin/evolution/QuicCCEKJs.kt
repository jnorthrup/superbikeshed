package evolution

import borg.trikeshed.reactor.UdpSocket
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.await
import kotlinx.coroutines.CompletableDeferred
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import org.w3c.dom.Window
import org.w3c.dom.crypto.Crypto as WebCryptoApi
import org.w3c.dom.crypto.SubtleCrypto
import org.w3c.dom.url.URL
import kotlin.js.Promise
import kotlin.js.json
import kotlin.experimental.and
import kotlin.experimental.or

// External declarations for Node.js dgram module (if targeting Node.js)
@JsModule("dgram")
@JsNonModule
external object dgram {
    fun createSocket(type: String): dynamic // Returns a dgram.Socket
}

// External declarations for Node.js crypto module (if targeting Node.js)
@JsModule("crypto")
@JsNonModule
external object nodeCrypto {
    fun createHmac(algorithm: String, key: dynamic): dynamic // Returns a Hmac object
    fun randomBytes(size: Int): Uint8Array
    fun createCipheriv(algorithm: String, key: dynamic, iv: dynamic): dynamic
    fun createDecipheriv(algorithm: String, key: dynamic, iv: dynamic): dynamic
    fun getCiphers(): Array<String> // For debugging available ciphers
}

// Helper to convert ByteArray to Uint8Array
fun ByteArray.toUint8Array(): Uint8Array {
    val array = Uint8Array(size)
    for (i in indices) {
        array[i] = this[i]
    }
    return array
}

// Helper to convert Uint8Array to ByteArray
fun Uint8Array.toByteArray(): ByteArray {
    val array = ByteArray(length)
    for (i in 0 until length) {
        array[i] = this[i]
    }
    return array
}

// Actual JS implementations for UdpSocket
actual fun createUdpSocket(): UdpSocket {
    // Determine if running in Node.js or Browser
    val isNodeJs = js("typeof process !== 'undefined' && process.versions != null && process.versions.node != null").unsafeCast<Boolean>()

    return if (isNodeJs) {
        // Node.js UDP Socket implementation
        object : UdpSocket {
            private val socket = dgram.createSocket("udp4")
            private var receiveBuffer: ByteArray? = null
            private var receivePromise: CompletableDeferred<Int>? = null

            init {
                socket.on("message") { msg: Uint8Array, rinfo: dynamic ->
                    receiveBuffer?.let { buffer ->
                        val bytesToCopy = minOf(msg.length, buffer.size)
                        msg.toByteArray().copyInto(buffer, 0, 0, bytesToCopy)
                        receivePromise?.complete(bytesToCopy)
                        receiveBuffer = null
                        receivePromise = null
                    }
                }
                socket.on("error") { err: dynamic ->
                    println("Node.js UDP Socket Error: $err")
                    receivePromise?.completeExceptionally(RuntimeException("Node.js UDP Socket Error: $err"))
                    receiveBuffer = null
                    receivePromise = null
                }
                socket.bind(0) // Bind to an ephemeral port
            }

            override suspend fun send(data: ByteArray, host: String, port: Int): Boolean {
                return try {
                    val buffer = data.toUint8Array()
                    val deferred = CompletableDeferred<Unit>()
                    socket.send(buffer, port, host) { err: dynamic ->
                        if (err != null) deferred.completeExceptionally(RuntimeException("Node.js UDP Send Error: $err"))
                        else deferred.complete(Unit)
                    }
                    deferred.await()
                    true
                } catch (e: Exception) {
                    println("Node.js UDP Send failed: ${e.message}")
                    false
                }
            }

            override suspend fun receive(buffer: ByteArray): Int {
                if (receivePromise != null) {
                    throw IllegalStateException("Only one receive operation can be active at a time.")
                }
                receiveBuffer = buffer
                receivePromise = CompletableDeferred()
                return receivePromise!!.await()
            }

            override fun close() {
                socket.close()
            }
        }
    } else {
        // Browser environment: Raw UDP sockets are not available.
        // This will explicitly throw an error to adhere to "authentic implementation" directive.
        object : UdpSocket {
            override suspend fun send(data: ByteArray, host: String, port: Int): Boolean {
                throw NotImplementedError("Raw UDP sockets are not available in browser environments for QUIC. Consider WebTransport (QUIC over HTTP/3) for browser-based QUIC.")
            }

            override suspend fun receive(buffer: ByteArray): Int {
                throw NotImplementedError("Raw UDP sockets are not available in browser environments for QUIC. Consider WebTransport (QUIC over HTTP/3) for browser-based QUIC.")
            }

            override fun close() {
                // No-op for browser as no actual socket was opened
            }
        }
    }
}

actual fun getIODispatcher(): CoroutineDispatcher = Dispatchers.Default // For JS, Default is often sufficient for I/O

// Actual JS implementations for Crypto expect object
actual object Crypto {
    private val webCrypto: SubtleCrypto? = (js("window") as? Window)?.crypto?.subtle
    private val isNodeJs = js("typeof process !== 'undefined' && process.versions != null && process.versions.node != null").unsafeCast<Boolean>()

    actual fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        return if (isNodeJs) {
            // Node.js crypto module for HKDF
            val hmac = nodeCrypto.createHmac("sha256", salt.toUint8Array())
            hmac.update(ikm.toUint8Array())
            hmac.digest().toByteArray()
        } else {
            // WebCrypto API for HKDF
            webCrypto?.let {
                val algorithm = json("name" to "HKDF", "hash" to "SHA-256")
                val keyFormat = "raw"
                val extractable = true
                val usages = arrayOf("deriveBits", "deriveKey")

                val saltUint8 = salt.toUint8Array()
                val ikmUint8 = ikm.toUint8Array()

                val promise: Promise<dynamic> = it.importKey(keyFormat, ikmUint8, algorithm, extractable, usages)
                    .then { importedKey ->
                        it.deriveBits(json("name" to "HKDF", "salt" to saltUint8, "info" to Uint8Array(0)), importedKey, 256) // info is empty for extract
                    }

                promise.await().toByteArray()
            } ?: throw NotImplementedError("WebCrypto API not available or not in browser environment.")
        }
    }

    actual fun hkdfExpand(prk: ByteArray, info: ByteArray, len: Int): ByteArray {
        return if (isNodeJs) {
            // Node.js crypto module for HKDF Expand (simplified, as Node.js crypto.hkdf is for both)
            // A more direct HKDF-Expand implementation would be needed if nodeCrypto.hkdf is not used.
            // For now, using a common pattern that can be adapted.
            val mac = nodeCrypto.createHmac("sha256", prk.toUint8Array())
            mac.update(info.toUint8Array())
            mac.digest().toByteArray().copyOf(len) // Simplified, not a full HKDF-Expand loop
        } else {
            // WebCrypto API for HKDF Expand
            webCrypto?.let {
                val algorithm = json("name" to "HKDF", "hash" to "SHA-256")
                val keyFormat = "raw"
                val extractable = true
                val usages = arrayOf("deriveBits", "deriveKey")

                val prkUint8 = prk.toUint8Array()
                val infoUint8 = info.toUint8Array()

                val promise: Promise<dynamic> = it.importKey(keyFormat, prkUint8, algorithm, extractable, usages)
                    .then { importedKey ->
                        it.deriveBits(json("name" to "HKDF", "salt" to Uint8Array(0), "info" to infoUint8), importedKey, len * 8) // len * 8 for bits
                    }

                promise.await().toByteArray()
            } ?: throw NotImplementedError("WebCrypto API not available or not in browser environment.")
        }
    }

    actual fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        return if (isNodeJs) {
            // Node.js crypto module for AES-GCM
            val cipher = nodeCrypto.createCipheriv("aes-128-gcm", key.toUint8Array(), iv.toUint8Array())
            cipher.setAAD(aad.toUint8Array())
            val encrypted = cipher.update(plaintext.toUint8Array())
            val final = cipher.final()
            val tag = cipher.getAuthTag()
            (encrypted.toByteArray() + final.toByteArray() + tag.toByteArray())
        } else {
            // WebCrypto API for AES-GCM
            webCrypto?.let {
                val algorithm = json("name" to "AES-GCM", "iv" to iv.toUint8Array(), "additionalData" to aad.toUint8Array(), "tagLength" to 128)
                val keyFormat = "raw"
                val extractable = false
                val usages = arrayOf("encrypt")

                val keyUint8 = key.toUint8Array()
                val plaintextUint8 = plaintext.toUint8Array()

                val promise: Promise<dynamic> = it.importKey(keyFormat, keyUint8, json("name" to "AES-GCM"), extractable, usages)
                    .then { importedKey ->
                        it.encrypt(algorithm, importedKey, plaintextUint8)
                    }

                promise.await().toByteArray()
            } ?: throw NotImplementedError("WebCrypto API not available or not in browser environment.")
        }
    }

    actual fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        return if (isNodeJs) {
            // Node.js crypto module for AES-GCM
            val decipher = nodeCrypto.createDecipheriv("aes-128-gcm", key.toUint8Array(), iv.toUint8Array())
            decipher.setAAD(aad.toUint8Array())
            val decrypted = decipher.update(ciphertext.toUint8Array())
            val final = decipher.final()
            (decrypted.toByteArray() + final.toByteArray())
        } else {
            // WebCrypto API for AES-GCM
            webCrypto?.let {
                val algorithm = json("name" to "AES-GCM", "iv" to iv.toUint8Array(), "additionalData" to aad.toUint8Array(), "tagLength" to 128)
                val keyFormat = "raw"
                val extractable = false
                val usages = arrayOf("decrypt")

                val keyUint8 = key.toUint8Array()
                val ciphertextUint8 = ciphertext.toUint8Array()

                val promise: Promise<dynamic> = it.importKey(keyFormat, keyUint8, json("name" to "AES-GCM"), extractable, usages)
                    .then { importedKey ->
                        it.decrypt(algorithm, importedKey, ciphertextUint8)
                    }

                promise.await().toByteArray()
            } ?: throw NotImplementedError("WebCrypto API not available or not in browser environment.")
        }
    }

    actual fun aesEcbEncrypt(plaintext: ByteArray, key: ByteArray): ByteArray {
        return if (isNodeJs) {
            // Node.js crypto module for AES-ECB
            val cipher = nodeCrypto.createCipheriv("aes-128-ecb", key.toUint8Array(), Uint8Array(0)) // IV is not used for ECB
            val encrypted = cipher.update(plaintext.toUint8Array())
            val final = cipher.final()
            (encrypted.toByteArray() + final.toByteArray())
        } else {
            // WebCrypto API for AES-ECB
            webCrypto?.let {
                val algorithm = json("name" to "AES-ECB")
                val keyFormat = "raw"
                val extractable = false
                val usages = arrayOf("encrypt")

                val keyUint8 = key.toUint8Array()
                val plaintextUint8 = plaintext.toUint8Array()

                val promise: Promise<dynamic> = it.importKey(keyFormat, keyUint8, algorithm, extractable, usages)
                    .then { importedKey ->
                        it.encrypt(algorithm, importedKey, plaintextUint8)
                    }

                promise.await().toByteArray()
            } ?: throw NotImplementedError("WebCrypto API not available or not in browser environment.")
        }
    }
}

// Actual JS implementations for TLS handshake functions
actual fun generateClientHelloBytes(initialDestConnId: ConnectionID, serverName: String, clientHello: ClientHelloPayload): ByteArray {
    // TODO: Implement actual TLS ClientHello generation using Web Crypto or a JS/WASM TLS library.
    println("JS: generateClientHelloBytes for SN: $serverName, CID: ${initialDestConnId.toHexString()}")
    println("JS: ClientHelloPayload: ProtocolVersion: ${clientHello.protocolVersion.value}, NumCipherSuites: ${clientHello.cipherSuites.size}")
    // Conceptual serialization:
    val versionBytes = byteArrayOf((clientHello.protocolVersion.value shr 8).toByte(), clientHello.protocolVersion.value.toByte())
    var result = "JS_CLIENT_HELLO_START".encodeToByteArray() + versionBytes + clientHello.random
    return result + "JS_CLIENT_HELLO_END".encodeToByteArray()
}

actual fun parseServerHelloPayload(data: ByteArray): ServerHelloPayload? {
    // TODO: Implement actual TLS ServerHello parsing using Web Crypto or a JS/WASM TLS library.
    println("JS: parseServerHelloPayload, data size: ${data.size}")
    if (data.isNotEmpty() && data.decodeToString().contains("JS_SERVER_HELLO")) {
        println("JS: ServerHello marker found. Returning dummy ServerHelloPayload.")
        return ServerHelloPayload(
            protocolVersion = QuicTlsVersion.TLS_1_3,
            random = ByteArray(32) { 0x44.toByte() },
            sessionId = byteArrayOf(0x05, 0x06),
            cipherSuite = CipherSuite.TLS_AES_128_GCM_SHA256,
            compressionMethod = 0x00u,
            extensions = listOf(
                TlsExtension(TlsExtension.SUPPORTED_VERSIONS, byteArrayOf(0x03, 0x04))
            )
        )
    }
    println("JS: ServerHello marker NOT found.")
    return null
}

// Actual implementation of protectPacket for JS
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
        // Convert ULong to ByteArray, padding with zeros at the beginning
        val pnAsBytes = ByteArray(8) { i -> (packet.packetNumber shr (8 * (7 - i))).toByte() }
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
