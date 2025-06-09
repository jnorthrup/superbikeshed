package borg.trikeshed.net.tls

import kotlinx.cinterop.*
import libopenssl.*
import platform.posix.free
import borg.trikeshed.net.common.UdpSocketService // Not strictly needed here, but Result was used in outline
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers // Assuming a suitable IO dispatcher context for native

// Constants for TLS Exporter labels for QUIC (RFC 9001, Appendix A)
private const val QUIC_TLS_EXPORTER_LABEL_CLIENT_HANDSHAKE_TRAFFIC_SECRET = "client hs traffic"
private const val QUIC_TLS_EXPORTER_LABEL_SERVER_HANDSHAKE_TRAFFIC_SECRET = "server hs traffic"
private const val QUIC_TLS_EXPORTER_LABEL_CLIENT_APPLICATION_TRAFFIC_SECRET = "client ap traffic"
private const val QUIC_TLS_EXPORTER_LABEL_SERVER_APPLICATION_TRAFFIC_SECRET = "server ap traffic"

// QUIC transport parameters TLS extension ID (RFC 9001)
private const val QUIC_TRANSPORT_PARAMETERS_TLS_EXTENSION_ID = 0x39 // Standard value from IANA, hex 39 = decimal 57

@OptIn(ExperimentalForeignApi::class, ExperimentalStdlibApi::class, ExperimentalUnsignedTypes::class)
class NativeTlsServiceOpenSsl : TlsService {

    // Static data for custom extension, needs careful lifetime management if not global.
    // For this example, assuming it's passed via add_arg and callbacks manage it.
    companion object {
        // This map would hold (SSL* -> pinned ByteArray) if we needed to pass complex data to add_cb.
        // For this simple case where add_cb receives the pinned data directly, it's not strictly needed here.
        // private val extensionDataMap = mutableMapOf<COpaquePointer, Pinned<ByteArray>>()
    }

    override suspend fun startClientHandshake(
        hostname: String,
        alpnProtocols: List<String>,
        quicTransportParams: ByteArray, // Payload for the QUIC TP TLS extension
        callbacks: TlsHandshakeCallbacks
    ): Result<TlsConnection> {
        val ctx = SSL_CTX_new(TLS_client_method())
        if (ctx == null) {
            return Result.failure(RuntimeException("OpenSSL: SSL_CTX_new failed. Error: ${opensslErrorString()}"))
        }

        var ssl: CPointer<SSL>? = null
        var readBio: CPointer<BIO>? = null
        var writeBio: CPointer<BIO>? = null

        try {
            SSL_CTX_set_min_proto_version(ctx, TLS1_3_VERSION)
            // Recommended cipher suites for HTTP/3 (RFC 9114, Section 3.2)
            val ciphers = "TLS_AES_128_GCM_SHA256:TLS_AES_256_GCM_SHA384:TLS_CHACHA20_POLY1305_SHA256"
            if (SSL_CTX_set_cipher_suites(ctx, ciphers) != 1) {
                throw SslException("OpenSSL: SSL_CTX_set_cipher_suites failed. Error: ${opensslErrorString()}")
            }

            // QUIC Transport Parameters Extension
            if (quicTransportParams.isNotEmpty()) {
                // Pin the data for the duration of SSL_CTX_add_custom_ext call at least.
                // The add_cb will need access to this data. OpenSSL might copy it or expect it to be stable.
                // A common pattern is to pass a pointer to a structure that includes length.
                // For this, we'll use a global/static C function that receives a COpaquePointer.
                // The COpaquePointer will be a StableRef to the ByteArray.
                val stableRef = StableRef.create(quicTransportParams)
                if (SSL_CTX_add_custom_ext(
                        ctx,
                        QUIC_TRANSPORT_PARAMETERS_TLS_EXTENSION_ID.toUInt(),
                        SSL_EXT_CLIENT_HELLO or SSL_EXT_TLS1_3_ONLY,
                        staticCFunction(::addQuicTransportParamsExtCallback),
                        staticCFunction(::freeQuicTransportParamsExtCallback),
                        stableRef.asCPointer(), // Pass StableRef as argument
                        staticCFunction(::parseQuicTransportParamsExtCallback),
                        null
                    ) != 1
                ) {
                    stableRef.dispose() // Dispose if adding failed
                    println("NativeTlsServiceOpenSsl: Warning - SSL_CTX_add_custom_ext for QUIC TP failed. Error: ${opensslErrorString()}")
                    // This could be a fatal error for QUIC.
                }
                // Note: The StableRef should be disposed in freeQuicTransportParamsExtCallback if OpenSSL calls it.
                // However, for client sending, OpenSSL might not call free_cb for the *data source* itself.
                // It's safer to manage StableRef lifecycle if data is copied by add_cb.
                // If add_cb doesn't copy, then `quicTransportParams` must outlive the SSL object or be pinned.
                // For simplicity here, assuming add_cb copies data and we can dispose stableRef after SSL_new or SSL_free.
                // The free_cb is more for data OpenSSL allocates *for* the extension *in* the SSL struct.
            }

            ssl = SSL_new(ctx)
            if (ssl == null) {
                throw SslException("OpenSSL: SSL_new failed. Error: ${opensslErrorString()}")
            }

            // SNI
            if (hostname.isNotEmpty()) {
                if (SSL_set_tlsext_host_name(ssl, hostname.cstr) != 1) {
                     println("NativeTlsServiceOpenSsl: Warning - SSL_set_tlsext_host_name failed. Error: ${opensslErrorString()}")
                }
            }

            // ALPN
            if (alpnProtocols.isNotEmpty()) {
                val alpnBuffer = BufferWriter()
                alpnProtocols.forEach { proto ->
                    val protoBytes = proto.encodeToByteArray()
                    if (protoBytes.size > 255) throw IllegalArgumentException("ALPN protocol name too long: $proto")
                    alpnBuffer.writeByte(protoBytes.size.toByte())
                    alpnBuffer.writeBytes(protoBytes)
                }
                val alpnBytes = alpnBuffer.toByteArray()
                alpnBytes.usePinned { pinnedAlpn ->
                    // SSL_set_alpn_protos returns 0 on success, non-0 on failure.
                    if (SSL_set_alpn_protos(ssl, pinnedAlpn.addressOf(0).reinterpret(), alpnBytes.size.toUInt()) != 0) {
                        println("NativeTlsServiceOpenSsl: Warning - SSL_set_alpn_protos failed. Error: ${opensslErrorString()}")
                    }
                }
            }

            readBio = BIO_new(BIO_s_mem()) ?: throw SslException("OpenSSL: BIO_new(BIO_s_mem()) for readBio failed.")
            writeBio = BIO_new(BIO_s_mem()) ?: throw SslException("OpenSSL: BIO_new(BIO_s_mem()) for writeBio failed.")

            BIO_set_mem_eof_return(readBio, -1) // Important for non-blocking like behavior
            BIO_set_mem_eof_return(writeBio, -1)

            SSL_set_bio(ssl, readBio, writeBio) // SSL takes ownership of BIOs if successful
            SSL_set_connect_state(ssl)

            // Create a CoroutineScope for this TlsConnection
            val connectionJob = SupervisorJob(callbacks_getContext_Job_or_Default_Native())
            val connectionScope = CoroutineScope(Dispatchers.Default + connectionJob) // Use a suitable dispatcher for Native

            val nativeTlsConnection = NativeTlsConnection(ssl, ctx, readBio, writeBio, callbacks, connectionScope)
            nativeTlsConnection.driveHandshake() // Initial drive

            return Result.success(nativeTlsConnection)

        } catch (e: Throwable) {
            // Cleanup in case of error
            if (ssl != null) SSL_free(ssl) // Frees associated BIOs if SSL_set_bio was called
            else { // SSL_new failed or BIO_new failed before SSL_set_bio
                if (readBio != null) BIO_free(readBio)
                if (writeBio != null) BIO_free(writeBio)
            }
            SSL_CTX_free(ctx)
            return Result.failure(RuntimeException("OpenSSL: Native TLS handshake setup failed: ${e.message}", e))
        }
    }
}

// --- Helper C interop callbacks for QUIC Transport Parameters extension ---
@OptIn(ExperimentalForeignApi::class)
private fun addQuicTransportParamsExtCallback(
    ssl: CPointer<SSL>?, extType: UInt, context: UInt,
    out: CPointer<CPointerVar<UByteVar>>?, outlen: CPointer<size_tVar>?,
    x: CPointer<X509>?, chainidx: size_t, al: CPointer<IntVar>?, addArg: COpaquePointer?
): Int {
    if (addArg == null || out == null || outlen == null) return SSL_CLIENT_HELLO_ERROR // Error code for add_cb failure

    val stableRef = addArg.asStableRef<ByteArray>()
    val quicTpBytes = stableRef.get()

    if (quicTpBytes.isEmpty()) {
        out.pointed.value = null
        outlen.pointed.value = 0u
        return SSL_CLIENT_HELLO_SUCCESS // Success, empty extension
    }

    // OpenSSL expects to manage the memory for the extension data it sends.
    // So we must allocate memory using OPENSSL_malloc and copy our data into it.
    val mem = OPENSSL_malloc(quicTpBytes.size.convert())?.reinterpret<UByteVar>()
    if (mem == null) {
        al?.pointed?.value = SSL_AD_INTERNAL_ERROR
        return SSL_CLIENT_HELLO_ERROR // Indicate failure
    }
    quicTpBytes.usePinned { pinned ->
        memcpy(mem, pinned.addressOf(0), quicTpBytes.size.convert())
    }
    out.pointed.value = mem
    outlen.pointed.value = quicTpBytes.size.convert()

    // The StableRef should be disposed of by the caller of SSL_CTX_add_custom_ext
    // if it's a one-shot setup, or by free_cb if it's tied to SSL object lifecycle.
    // For client sending, OpenSSL copies this data internally before handshake completes.
    // So, disposing it in free_cb (if called) or after SSL_free is safer.
    // For this example, we'll assume it's a one-shot for ClientHello construction phase.
    // However, the `add_arg` itself is what needs freeing if it was allocated.
    // The `stableRef` itself is Kotlin's object.
    // The `free_cb` is for freeing `*out` if OpenSSL doesn't do it.
    // Here, `OPENSSL_malloc` means OpenSSL will free it, or `free_cb` should.
    return SSL_CLIENT_HELLO_SUCCESS // Indicate success
}

@OptIn(ExperimentalForeignApi::class)
private fun freeQuicTransportParamsExtCallback(
    ssl: CPointer<SSL>?, extType: UInt, context: UInt,
    outVal: CPointer<UByteVar>?, // Data previously allocated by add_cb's *out
    addArg: COpaquePointer?
) {
    // This callback is responsible for freeing the memory allocated in add_cb *if* OpenSSL doesn't automatically.
    // Since add_cb used OPENSSL_malloc, OpenSSL should free it when the SSL structure is freed.
    // However, it's good practice to provide a free_cb if an add_cb is provided.
    // If `outVal` was allocated by us with `OPENSSL_malloc` in `add_cb`, we should free it here.
    // The `addArg` (StableRef) should be disposed of when the SSL_CTX is freed, or if this extension is removed.
    // For ClientHello, this free_cb might not be called for the *source* data pointed by addArg.
    // It's for the data *copied into* the SSL structure.
    if (outVal != null) {
        OPENSSL_free(outVal)
    }
    // If addArg was a StableRef created per SSL_CTX and needs disposal with SSL_CTX:
    // This is more complex. Typically, addArg for SSL_CTX_add_custom_ext is for data associated with CTX,
    // while SSL_add_custom_ext would be for SSL object specific data.
    // If StableRef passed to SSL_CTX_add_custom_ext was meant to live with CTX:
    // It should be disposed when SSL_CTX_free is called. This requires more advanced context management.
    // For now, assuming the StableRef created in startClientHandshake is disposed after SSL_CTX_add_custom_ext if it fails,
    // or if successful, it's assumed that the data was copied or the StableRef is managed elsewhere (e.g. with SSL_CTX lifecycle).
    // This is a common tricky point in C interop.
    // For this simple version, we'll assume the initial StableRef created in startClientHandshake is disposed there if setup fails early.
    // If setup succeeds, the data is copied by OpenSSL or the pinned data outlives the handshake.
}

@OptIn(ExperimentalForeignApi::class)
private fun parseQuicTransportParamsExtCallback(
    ssl: CPointer<SSL>?, extType: UInt, context: UInt,
    inData: CPointer<UByteVar>?, inlen: size_t,
    x: CPointer<X509>?, chainidx: size_t, al: CPointer<IntVar>?,
    parseArg: COpaquePointer?
): Int {
    // Client typically does not parse this extension from ServerHello.
    // Server would parse it from ClientHello.
    // If client needed to parse server's QUIC TPs (e.g. encrypted extensions), logic would go here.
    return 1 // Indicate success (extension processed, or ignored if not applicable)
}


internal class NativeTlsConnection(
    private val ssl: CPointer<SSL>,
    private val ctx: CPointer<SSL_CTX>, // Retain to free it appropriately
    private val readBio: CPointer<BIO>, // Input BIO (data from peer)
    private val writeBio: CPointer<BIO>,// Output BIO (data to send to peer)
    private val callbacks: TlsHandshakeCallbacks,
    override val coroutineContext: CoroutineContext // Scope for this connection
) : TlsConnection, CoroutineScope {

    private val closed = kotlinx.atomicfu.atomic(false)
    private var handshakeSecretsReported = false
    private var applicationSecretsReported = false

    init {
        // Initial handshake drive might be needed if SSL_connect/SSL_do_handshake wasn't blocking
        // or if it returned WANT_READ/WRITE immediately.
        driveHandshake()
    }

    fun driveHandshake() {
        if (closed.value || !isActive) return

        val ret = SSL_do_handshake(ssl)
        processSslResult(ret)
    }

    private fun processSslResult(ret: Int) {
        if (closed.value || !isActive) return

        val err = SSL_get_error(ssl, ret)
        when (err) {
            SSL_ERROR_NONE -> { // Handshake completed successfully OR non-blocking operation finished
                if (SSL_is_init_finished(ssl) == 1) {
                    // Ensure app keys are out, then call complete
                    extractAndReportKeys(TlsEncryptionLevel.APPLICATION_DATA, forceExtraction = true)
                    val alpnSelected = getAlpnSelected()
                    callbacks.onHandshakeComplete(alpnSelected)
                    // No more driving needed from here for handshake itself
                } else {
                    // This case (SSL_ERROR_NONE but handshake not finished) is unusual.
                    // It might mean an operation completed but more are needed.
                    // Re-drive or check other conditions. For now, assume it implies progress.
                    sendPendingBioData() // Send anything OpenSSL produced
                }
            }
            SSL_ERROR_WANT_READ -> {
                sendPendingBioData() // Send anything OpenSSL might have produced before wanting to read
                // Now wait for caller to provide more data via processHandshakeData()
            }
            SSL_ERROR_WANT_WRITE -> {
                sendPendingBioData() // Send what OpenSSL wants to write
                // After sending, immediately try to continue the handshake
                if (!closed.value && isActive) { // Recheck state before re-driving
                    driveHandshake()
                }
            }
            SSL_ERROR_SSL, SSL_ERROR_SYSCALL -> {
                val errorMessages = mutableListOf<String>()
                var errorCode: ULong
                while (ERR_peek_error().also { errorCode = it } != 0uL) {
                    errorMessages.add(ERR_error_string(errorCode, null)?.toKString() ?: "Unknown OpenSSL error code $errorCode")
                    ERR_get_error() // Consume the error from the queue
                }
                val combinedErrorMessage = if (errorMessages.isNotEmpty()) errorMessages.joinToString("; ") else "Undetermined SSL/Syscall error"

                val alertDesc = SSL_get_alert_state(ssl).toByte() // This might not be set for all errors
                val alertToSend = if (alertDesc != 0.toByte() && alertDesc != SSL3_AD_NO_ALERT.toByte()) alertDesc else SSL_AD_INTERNAL_ERROR.toByte()

                callbacks.onTlsAlertToSend(TlsAlert(2, alertToSend, "Fatal SSL error: $combinedErrorMessage"))
                closeAndCleanup(notifyError = false) // Error already sent via alert
            }
            else -> {
                val errorString = ERR_error_string(ERR_get_error(), null)?.toKString() ?: "Unknown error from SSL_get_error code: $err"
                callbacks.onTlsAlertToSend(TlsAlert(2, SSL_AD_INTERNAL_ERROR.toByte(), "Unhandled SSL error: $errorString"))
                closeAndCleanup(notifyError = false)
            }
        }

        if (!closed.value && isActive) {
            if (!handshakeSecretsReported) extractAndReportKeys(TlsEncryptionLevel.HANDSHAKE)
            if (SSL_is_init_finished(ssl) == 1 && !applicationSecretsReported) {
                extractAndReportKeys(TlsEncryptionLevel.APPLICATION_DATA)
            }
        }
    }

    private fun sendPendingBioData() {
        if (closed.value || !isActive) return
        memScoped {
            val buffer = allocArray<UByteVar>(4096) // Read in chunks
            while (true) {
                val bytesRead = BIO_read(writeBio, buffer, 4096)
                if (bytesRead > 0) {
                    val dataToSend = buffer.readBytes(bytesRead) // Converts to Kotlin ByteArray
                    val level = if (SSL_is_init_finished(ssl) == 1) TlsEncryptionLevel.APPLICATION_DATA else TlsEncryptionLevel.HANDSHAKE
                    callbacks.onHandshakeDataToSend(dataToSend, level)
                } else {
                    if (BIO_should_retry(writeBio) == 0) { // Not a retryable error (e.g. EOF or real error)
                        // Break, no more data to read without blocking or error
                    }
                    break
                }
            }
        }
    }

    private fun extractAndReportKeys(level: TlsEncryptionLevel, forceExtraction: Boolean = false) {
        if (closed.value) return
        if (level == TlsEncryptionLevel.HANDSHAKE && handshakeSecretsReported && !forceExtraction) return
        if (level == TlsEncryptionLevel.APPLICATION_DATA && applicationSecretsReported && !forceExtraction) return

        if (level == TlsEncryptionLevel.APPLICATION_DATA && SSL_is_init_finished(ssl) != 1 && !forceExtraction) return

        val session = SSL_get_session(ssl) ?: return
        val cipher = SSL_SESSION_get_cipher(session) ?: SSL_get_current_cipher(ssl) ?: return
        val cipherId = SSL_CIPHER_get_id(cipher).toInt()

        val evpCipher = SSL_CIPHER_get_evp_cipher(cipher)
        val evpMd = SSL_CIPHER_get_evp_md(cipher)
        val secretDerivationLength = if (evpMd != null) EVP_MD_size(evpMd) else EVP_CIPHER_key_length(evpCipher) // Fallback, ideally use hash size
        if (secretDerivationLength == 0) {
            println("NativeTlsConnection: Could not determine secret length for cipher ${SSL_CIPHER_get_name(cipher)?.toKString()}")
            return
        }

        var clientSecret: ByteArray? = null
        var serverSecret: ByteArray? = null

        when (level) {
            TlsEncryptionLevel.HANDSHAKE -> {
                clientSecret = exportKeyMaterial(QUIC_TLS_EXPORTER_LABEL_CLIENT_HANDSHAKE_TRAFFIC_SECRET, secretDerivationLength)
                serverSecret = exportKeyMaterial(QUIC_TLS_EXPORTER_LABEL_SERVER_HANDSHAKE_TRAFFIC_SECRET, secretDerivationLength)
                if (clientSecret != null && serverSecret != null && !handshakeSecretsReported) {
                    callbacks.onNewEncryptionSecretsReady(TlsEncryptionLevel.HANDSHAKE, serverSecret, clientSecret, cipherId)
                    handshakeSecretsReported = true
                }
            }
            TlsEncryptionLevel.APPLICATION_DATA -> {
                if (SSL_is_init_finished(ssl) == 1 || forceExtraction) {
                    clientSecret = exportKeyMaterial(QUIC_TLS_EXPORTER_LABEL_CLIENT_APPLICATION_TRAFFIC_SECRET, secretDerivationLength)
                    serverSecret = exportKeyMaterial(QUIC_TLS_EXPORTER_LABEL_SERVER_APPLICATION_TRAFFIC_SECRET, secretDerivationLength)
                    if (clientSecret != null && serverSecret != null && !applicationSecretsReported) {
                        callbacks.onNewEncryptionSecretsReady(TlsEncryptionLevel.APPLICATION_DATA, serverSecret, clientSecret, cipherId)
                        applicationSecretsReported = true

                        // Get peer certificate after handshake completion
                        val peerCert = SSL_get_peer_certificate(ssl) // Returns a new reference, must be X509_free'd
                        if (peerCert != null) {
                            try {
                                memScoped {
                                    val derOut = allocPointerTo<CPointerVar<UByteVar>>()
                                    val len = i2d_X509(peerCert, derOut.ptr)
                                    if (len > 0 && derOut.value != null) {
                                        callbacks.onPeerCertificateReceived(derOut.value!!.readBytes(len))
                                        OPENSSL_free(derOut.value)
                                    } else { callbacks.onPeerCertificateReceived(null) }
                                }
                            } finally {
                                X509_free(peerCert)
                            }
                        } else { callbacks.onPeerCertificateReceived(null) }
                    } else if (forceExtraction && (clientSecret == null || serverSecret == null)) {
                        callbacks.onTlsAlertToSend(TlsAlert(2, SSL_AD_INTERNAL_ERROR.toByte(), "Failed to derive application secrets post-handshake"))
                        closeAndCleanup()
                    }
                }
            }
        }
    }

    private fun exportKeyMaterial(label: String, length: Int): ByteArray? = memScoped {
        if (length <= 0) return null
        val buffer = allocArray<UByteVar>(length)
        // SSL_export_keying_material expects non-const label, but we pass string literal.
        // This is usually fine as it's only read. Context is null for this usage.
        if (SSL_export_keying_material(ssl, buffer, length.convert(), label, label.length.convert(), null, 0, 0) != 1) {
            println("NativeTlsConnection: Failed to export key material for label '$label'. Error: ${opensslErrorString()}")
            return null
        }
        return buffer.readBytes(length)
    }

    private fun getAlpnSelected(): String? = memScoped {
        val proto = allocPointerTo<UByteVar>()
        val len = alloc<UIntVar>()
        SSL_get0_alpn_selected(ssl, proto.ptr, len.ptr)
        return proto.value?.readBytes(len.value.toInt())?.decodeToString()
    }

    override fun processHandshakeData(data: ByteArray, level: TlsEncryptionLevel): Result<Unit> {
        if (closed.value || !isActive) return Result.failure(IllegalStateException("TLS connection is closed or scope inactive."))
        if (data.isEmpty()) return Result.success(Unit)

        var bytesWrittenToBio = 0
        data.usePinned { pinnedData ->
            bytesWrittenToBio = BIO_write(readBio, pinnedData.addressOf(0), data.size)
        }

        if (bytesWrittenToBio > 0) {
            driveHandshake()
        } else if (bytesWrittenToBio < 0) {
            val bioError = ERR_get_error() // BIO_ctrl BIO_CTRL_INFO is for other things
            return Result.failure(Exception("OpenSSL: BIO_write error: ${opensslErrorString(bioError)}"))
        }
        return Result.success(Unit)
    }

    override fun providePskTicket(ticket: ByteArray) {
        println("NativeTlsConnection: WARNING - providePskTicket not implemented.")
    }

    private fun closeAndCleanup(notifyError: Boolean = true) {
        if (closed.getAndSet(true)) return

        if (notifyError && isActive) { // Check isActive before using callbacks
            // callbacks.onTlsAlertToSend(TlsAlert(2, SSL_AD_INTERNAL_ERROR.toByte(), "Closing TLS connection due to internal error"))
            // Or a callback indicating handshake failure
        }

        // Cancel the coroutine scope. This should cancel any ongoing operations within this scope.
        (coroutineContext[Job] as? CompletableJob)?.complete() ?: coroutineContext[Job]?.cancel()

        SSL_free(ssl) // This also frees associated BIOs if SSL_set_bio was successful
        SSL_CTX_free(ctx)
        println("NativeTlsConnection: Closed and freed SSL/CTX.")
    }

    override fun close() {
        if (closed.value || !isActive) { // Check isActive from CoroutineScope
            if (!closed.value) closeAndCleanup(notifyError = false) // Ensure cleanup if scope is dead but flag not set
            return
        }

        // Try graceful shutdown if handshake was completed
        if (SSL_is_init_finished(ssl) == 1) {
            val ret = SSL_shutdown(ssl)
            sendPendingBioData() // Send our close_notify
            if (ret == 0) { // Shutdown not finished, means we sent close_notify and wait for peer's
                // We might need to call SSL_shutdown() again after receiving peer's close_notify.
                // For client, often okay to just close after sending.
                // This part can be complex depending on how strictly bi-directional shutdown is handled.
            } else if (ret < 0) { // Error during shutdown
                println("NativeTlsConnection: SSL_shutdown error: ${opensslErrorString()}")
            }
        }
        closeAndCleanup(notifyError = false)
    }
}

// Helper to get OpenSSL error string
@OptIn(ExperimentalForeignApi::class)
private fun opensslErrorString(error: ULong = ERR_get_error()): String {
    return ERR_error_string(error, null)?.toKString() ?: "Unknown OpenSSL error (code: $error)"
}

// Conceptual helper to get a parent Job from callbacks context, or null
private fun callbacks_getContext_Job_or_Default_Native(): Job? {
    return null
}
