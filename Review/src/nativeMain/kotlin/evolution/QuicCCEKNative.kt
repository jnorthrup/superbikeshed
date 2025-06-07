package evolution

import borg.trikeshed.reactor.UdpSocket
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.cinterop.*
import platform.posix.strerror
import platform.posix.errno
import platform.posix.socket
import platform.posix.AF_INET
import platform.posix.SOCK_DGRAM
import platform.posix.sendto
import platform.posix.recvfrom
import platform.posix.close
import platform.posix.sockaddr_in
import platform.posix.inet_addr
import platform.posix.htons
import platform.posix.sockaddr
import platform.posix.bind
import platform.posix.memcpy
import platform.posix.memset
import platform.posix.size_t
import platform.posix.read
import platform.posix.write
import platform.posix.fd_set
import platform.posix.FD_SET
import platform.posix.FD_ISSET
import platform.posix.FD_ZERO
import platform.posix.timeval
import platform.posix.select
import platform.posix.EAGAIN
import platform.posix.EWOULDBLOCK
import kotlin.random.Random

// Actual Native implementations for UdpSocket
actual fun createUdpSocket(): UdpSocket {
    return object : UdpSocket {
        private var fd: Int = -1

        init {
            memScoped {
                fd = socket(AF_INET, SOCK_DGRAM, 0)
                if (fd == -1) {
                    throw RuntimeException("Failed to create socket: ${strerror(errno)?.toKString()}")
                }
                val server_addr = alloc<sockaddr_in>()
                memset(server_addr.ptr, 0, sizeOf<sockaddr_in>().toULong())
                server_addr.sin_family = AF_INET.toUShort()
                server_addr.sin_addr.s_addr = 0u // INADDR_ANY
                server_addr.sin_port = htons(0u) // Let OS choose ephemeral port
                if (bind(fd, server_addr.ptr.reinterpret(), sizeOf<sockaddr_in>().toUInt()) == -1) {
                    close(fd)
                    throw RuntimeException("Failed to bind socket: ${strerror(errno)?.toKString()}")
                }
            }
        }

        override suspend fun send(data: ByteArray, host: String, port: Int): Boolean = withContext(Dispatchers.Default) {
            memScoped {
                val server_addr = alloc<sockaddr_in>()
                memset(server_addr.ptr, 0, sizeOf<sockaddr_in>().toULong())
                server_addr.sin_family = AF_INET.toUShort()
                server_addr.sin_addr.s_addr = inet_addr(host)
                server_addr.sin_port = htons(port.toUShort())

                val bytesSent = data.usePinned { pinned ->
                    sendto(fd, pinned.addressOf(0), data.size.toULong(), 0, server_addr.ptr.reinterpret(), sizeOf<sockaddr_in>().toUInt())
                }
                if (bytesSent == -1L) {
                    println("Native UDP Send failed: ${strerror(errno)?.toKString()}")
                    return@withContext false
                }
                return@withContext true
            }
        }

        override suspend fun receive(buffer: ByteArray): Int = withContext(Dispatchers.Default) {
            memScoped {
                val client_addr = alloc<sockaddr_in>()
                val client_addr_len = alloc<UIntVar>()
                client_addr_len.value = sizeOf<sockaddr_in>().toUInt()

                val bytesRead = buffer.usePinned { pinned ->
                    recvfrom(fd, pinned.addressOf(0), buffer.size.toULong(), 0, client_addr.ptr.reinterpret(), client_addr_len.ptr)
                }
                if (bytesRead == -1L) {
                    if (errno == EAGAIN || errno == EWOULDBLOCK) { // Non-blocking timeout
                        return@withContext 0
                    }
                    println("Native UDP Receive failed: ${strerror(errno)?.toKString()}")
                    return@withContext -1
                }
                return@withContext bytesRead.toInt()
            }
        }

        override fun close() {
            if (fd != -1) {
                platform.posix.close(fd)
                fd = -1
            }
        }
    }
}

actual fun getIODispatcher(): CoroutineDispatcher = Dispatchers.Default // Consider a specific Native dispatcher if available/needed

// Actual Native implementations for Crypto expect object
@OptIn(ExperimentalForeignApi::class) // For CPointer, nativeHeap, etc.
actual object Crypto {
    // Assuming OpenSSL cinterop bindings are available under 'openssl.*'
    // These are illustrative and would need actual OpenSSL bindings and error handling.

    actual fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        // Conceptual OpenSSL HKDF-Extract (using EVP_PKEY for HKDF)
        // Actual implementation would involve EVP_PKEY_CTX, EVP_PKEY_derive_init, etc.
        // For HKDF, PRK length is usually hash output length (e.g., SHA256 -> 32 bytes)
        println("Native HKDF-Extract with OpenSSL (conceptual)")
        memScoped {
            val saltPtr = salt.toCValues().ptr
            val ikmPtr = ikm.toCValues().ptr
            val prkLen = 32 // Assuming SHA-256, output length for PRK
            val prkBuffer = nativeHeap.allocArray<UByteVar>(prkLen)

            // Hypothetical: val result = openssl.HKDF_extract(saltPtr, salt.size, ikmPtr, ikm.size, prkBuffer, prkLen.toULong(), openssl.EVP_sha256())
            // if (result != 1) throw Exception("HKDF_Extract failed")

            // Placeholder: Fill with some data for now
            for(i in 0 until prkLen) prkBuffer[i] = ((ikm.getOrElse(i % ikm.size) xor salt.getOrElse(i % salt.size)).toByte()).toUByte()

            return ByteArray(prkLen) { prkBuffer[it].toByte() }
        }
    }

    actual fun hkdfExpand(prk: ByteArray, info: ByteArray, len: Int): ByteArray {
        println("Native HKDF-Expand with OpenSSL (conceptual), len: $len")
        memScoped {
            val prkPtr = prk.toCValues().ptr
            val infoPtr = info.toCValues().ptr
            val okmBuffer = nativeHeap.allocArray<UByteVar>(len)

            // Hypothetical: val result = openssl.HKDF_expand(prkPtr, prk.size, infoPtr, info.size, okmBuffer, len.toULong(), openssl.EVP_sha256())
            // if (result != 1) throw Exception("HKDF_Expand failed")

            // Placeholder:
            for (i in 0 until len) {
                okmBuffer[i] = (prk.getOrElse(i % prk.size) xor info.getOrElse(i % info.size) xor (i % 256).toByte()).toUByte()
            }
            return ByteArray(len) { okmBuffer[it].toByte() }
        }
    }

    actual fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        println("Native AES-GCM Encrypt with OpenSSL (conceptual)")
        memScoped {
            val keyPtr = key.toCValues().ptr
            val ivPtr = iv.toCValues().ptr
            val plaintextPtr = plaintext.toCValues().ptr
            val aadPtr = aad.toCValues().ptr

            val ciphertextLen = plaintext.size
            val tagLen = 16 // Standard GCM tag length
            val outBuffer = nativeHeap.allocArray<UByteVar>(ciphertextLen + tagLen)
            val tagBuffer = nativeHeap.allocArray<UByteVar>(tagLen) // Or get it from end of outBuffer

            // Hypothetical EVP_AEAD API usage:
            // val aead = openssl.EVP_aes_128_gcm() // Or 256 based on key.size
            // val ctx = openssl.EVP_AEAD_CTX_new(aead, keyPtr, key.size.toULong(), tagLen.toULong())
            // if (ctx == null) throw Exception("Failed to create EVP_AEAD_CTX")
            // val outLen = alloc<ULongVar>()
            // val result = openssl.EVP_AEAD_CTX_seal(ctx, outBuffer, outLen.ptr, (ciphertextLen + tagLen).toULong(),
            //                                        ivPtr, iv.size.toULong(),
            //                                        plaintextPtr, plaintext.size.toULong(),
            //                                        aadPtr, aad.size.toULong())
            // if (result != 1) throw Exception("AES-GCM encryption failed. OutLen: ${outLen.value}")
            // openssl.EVP_AEAD_CTX_free(ctx)
            // For tag: often seal places it at the end of the ciphertext buffer or a separate get_tag call

            // Placeholder:
            plaintext.forEachIndexed { i, byte -> outBuffer[i] = (byte xor key[i % key.size]).toUByte() }
            (0 until tagLen).forEach { outBuffer[ciphertextLen + it] = it.toUByte() }


            return ByteArray(ciphertextLen + tagLen) { outBuffer[it].toByte() }
        }
    }

    actual fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertextWithTag: ByteArray, aad: ByteArray): ByteArray {
        println("Native AES-GCM Decrypt with OpenSSL (conceptual)")
        memScoped {
            val keyPtr = key.toCValues().ptr
            val ivPtr = iv.toCValues().ptr
            val aadPtr = aad.toCValues().ptr

            val tagLen = 16
            if (ciphertextWithTag.size < tagLen) throw Exception("Ciphertext too short to contain a tag")

            val ciphertextLen = ciphertextWithTag.size - tagLen
            val ciphertextAndTagPtr = ciphertextWithTag.toCValues().ptr

            val plaintextBuffer = nativeHeap.allocArray<UByteVar>(ciphertextLen)

            // Hypothetical EVP_AEAD API usage:
            // val aead = openssl.EVP_aes_128_gcm() // Or 256
            // val ctx = openssl.EVP_AEAD_CTX_new(aead, keyPtr, key.size.toULong(), tagLen.toULong())
            // if (ctx == null) throw Exception("Failed to create EVP_AEAD_CTX for decrypt")
            // val outLen = alloc<ULongVar>()
            // val result = openssl.EVP_AEAD_CTX_open(ctx, plaintextBuffer, outLen.ptr, ciphertextLen.toULong(),
            //                                       ivPtr, iv.size.toULong(),
            //                                       ciphertextAndTagPtr, ciphertextWithTag.size.toULong(), // Ciphertext + Tag
            //                                       aadPtr, aad.size.toULong())
            // if (result != 1) throw Exception("AES-GCM decryption failed (authentication or decryption error). OutLen: ${outLen.value}")
            // openssl.EVP_AEAD_CTX_free(ctx)

            // Placeholder:
            (0 until ciphertextLen).forEach { i ->
                plaintextBuffer[i] = (ciphertextWithTag[i] xor key[i % key.size]).toUByte()
            }
            // Placeholder: Tag verification would happen here in a real scenario.

            return ByteArray(ciphertextLen) { plaintextBuffer[it].toByte() }
        }
    }

    // Signature already harmonized: plaintext: ByteArray, key: ByteArray
    actual fun aesEcbEncrypt(plaintext: ByteArray, key: ByteArray): ByteArray {
        println("Native AES-ECB Encrypt with OpenSSL (conceptual)")
        memScoped {
            val keyPtr = key.toCValues().ptr
            val plaintextPtr = plaintext.toCValues().ptr

            // ECB output size can be up to plaintext.size + block_size - 1
            // For AES, block size is 16 bytes.
            val outBufferLen = plaintext.size + 16
            val outBuffer = nativeHeap.allocArray<UByteVar>(outBufferLen)
            val outLen = alloc<IntVar>() // To store actual length of output
            val finalLen = alloc<IntVar>()

            // Hypothetical EVP API for ECB:
            // val cipher = openssl.EVP_aes_128_ecb() // Or 256
            // val ctx = openssl.EVP_CIPHER_CTX_new()
            // if (ctx == null) throw Exception("Failed to create EVP_CIPHER_CTX for ECB encrypt")
            // if (openssl.EVP_EncryptInit_ex(ctx, cipher, null, keyPtr, null) != 1) throw Exception("EVP_EncryptInit_ex failed")
            // if (openssl.EVP_EncryptUpdate(ctx, outBuffer, outLen.ptr, plaintextPtr, plaintext.size) != 1) throw Exception("EVP_EncryptUpdate failed")
            // if (openssl.EVP_EncryptFinal_ex(ctx, outBuffer?.plus(outLen.value), finalLen.ptr) != 1) throw Exception("EVP_EncryptFinal_ex failed")
            // val totalLen = outLen.value + finalLen.value
            // openssl.EVP_CIPHER_CTX_free(ctx)

            // Placeholder:
            plaintext.forEachIndexed { i, byte -> outBuffer[i] = (byte xor key[i % key.size]).toUByte() }
            val totalLen = plaintext.size


            return ByteArray(totalLen) { outBuffer[it].toByte() }
        }
    }
}

// Actual Native implementations for TLS handshake functions
actual fun generateClientHelloBytes(initialDestConnId: ConnectionID, serverName: String, clientHello: ClientHelloPayload): ByteArray {
    // Placeholder for Native. A real implementation would use a native TLS library (e.g., OpenSSL's libssl)
    // to serialize the ClientHelloPayload into bytes.
    println("Native: generateClientHelloBytes for SN: $serverName, CID: ${initialDestConnId.toHexString()}")
    println("Native: ClientHelloPayload: ProtocolVersion: ${clientHello.protocolVersion.value}, Random: ${clientHello.random.take(4).joinToString()}, NumCipherSuites: ${clientHello.cipherSuites.size}")
    // Conceptual serialization:
    val versionBytes = byteArrayOf((clientHello.protocolVersion.value shr 8).toByte(), clientHello.protocolVersion.value.toByte())
    var result = "NATIVE_CLIENT_HELLO_START".encodeToByteArray() + versionBytes + clientHello.random
    // In a real scenario, proper TLS encoding of each field and extension is needed.
    return result + "NATIVE_CLIENT_HELLO_END".encodeToByteArray()
}

actual fun parseServerHelloPayload(data: ByteArray): ServerHelloPayload? {
    // Placeholder for Native. A real implementation would use a native TLS library.
    println("Native: parseServerHelloPayload, data size: ${data.size}")
    // Simple check for a marker, not real parsing.
    if (data.isNotEmpty() && data.decodeToString().contains("NATIVE_SERVER_HELLO")) {
        println("Native: ServerHello marker found. Returning dummy ServerHelloPayload.")
        return ServerHelloPayload(
            protocolVersion = QuicTlsVersion.TLS_1_3,
            random = ByteArray(32) { 0x33.toByte() },
            sessionId = byteArrayOf(0x03, 0x04),
            cipherSuite = CipherSuite.TLS_AES_128_GCM_SHA256,
            compressionMethod = 0x00u,
            extensions = listOf(
                TlsExtension(TlsExtension.SUPPORTED_VERSIONS, byteArrayOf(0x03, 0x04)),
                TlsExtension(TlsExtension.KEY_SHARE, Random.nextBytes(32)) // Dummy key share
            )
        )
    }
    println("Native: ServerHello marker NOT found.")
    return null
}

// Actual implementation of protectPacket for Native
internal actual fun protectPacket(packet: QuicPacket, keys: QuicInitialKeys, connection: QuicConnection): ByteArray {
    println("Native QUIC packet protection (placeholder)")
    // A simplified protection that just concatenates header, PN, and payload.
    // Doesn't implement actual header protection or AEAD.
    val headerPart = (packet.packetType?.typeValue?.toByte() ?: 0x00) // Simplified
    val pnBytes = ByteArray(4) { i -> (packet.packetNumber shr (8 * (3 - i))).toByte() }

    return byteArrayOf(headerPart) + packet.connectionId + pnBytes + packet.payload
}
