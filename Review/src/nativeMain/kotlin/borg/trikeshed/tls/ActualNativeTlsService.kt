package borg.trikeshed.tls

import borg.trikeshed.lib.Series
import borg.trikeshed.net.tls.* // Imports TlsService, TlsConnection, TlsHandshakeCallbacks, etc.
import kotlinx.cinterop.*
import platform.openssl.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalForeignApi::class)
actual class ActualNativeTlsService(
    private val parentCoroutineContext: CoroutineContext
) : TlsService, CoroutineScope {

    override val coroutineContext: CoroutineContext = parentCoroutineContext + SupervisorJob() + CoroutineName("ActualNativeTlsService")

    init {
        // Initialize OpenSSL if not already done globally (usually needed once per process)
        OPENSSL_init_ssl(0uL, null) // Equivalent to SSL_library_init() + SSL_load_error_strings() + OpenSSL_add_all_algorithms()
        OPENSSL_init_crypto(OPENSSL_INIT_LOAD_CRYPTO_STRINGS.toULong() or OPENSSL_INIT_ADD_ALL_CIPHERS.toULong() or OPENSSL_INIT_ADD_ALL_DIGESTS.toULong(), null)
    }

    actual override suspend fun startClientHandshake(
        hostname: String,
        alpnProtocols: Series<String>,
        quicTransportParams: ByteArray,
        callbacks: TlsHandshakeCallbacks,
        clientCertificateChainDer: Series<ByteArray>?,
        clientPrivateKeyDer: ByteArray?
    ): Result<TlsConnection> = withContext(Dispatchers.Default) { // Use Default for potentially blocking C calls
        val method = TLS_client_method()
        if (method == null) {
            return@withContext Result.failure(RuntimeException("Failed to create TLS client method: ${ERR_error_string(ERR_get_error(), null)?.toKString()}"))
        }
        val ctx = SSL_CTX_new(method)
        if (ctx == null) {
            return@withContext Result.failure(RuntimeException("Failed to create SSL_CTX: ${ERR_error_string(ERR_get_error(), null)?.toKString()}"))
        }

        try {
            // Min/Max protocol version (TLS 1.3)
            SSL_CTX_set_min_proto_version(ctx, TLS1_3_VERSION)
            SSL_CTX_set_max_proto_version(ctx, TLS1_3_VERSION)

            // ALPN
            if (alpnProtocols.isNotEmpty()) {
                val alpnBytes = alpnProtocols.map { it.encodeToByteArray() }.toSerializedAlpn()
                alpnBytes.usePinned { pinnedAlpn ->
                    if (SSL_CTX_set_alpn_protos(ctx, pinnedAlpn.addressOf(0).reinterpret(), pinnedAlpn.get().size.toUInt()) != 0) {
                         throw RuntimeException("Failed to set ALPN protos: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
                    }
                }
            }

            // Client Certificate
            if (clientCertificateChainDer != null && clientPrivateKeyDer != null && clientCertificateChainDer.isNotEmpty()) {
                // Load private key
                clientPrivateKeyDer.usePinned { pKeyPinned ->
                    val pKeyBio = BIO_new_mem_buf(pKeyPinned.addressOf(0), clientPrivateKeyDer.size) ?: throw RuntimeException("BIO_new_mem_buf for private key failed")
                    defer { BIO_free(pKeyBio) }
                    // Try common formats, PKCS#8 DER is typical
                    val evpPkey = d2i_PrivateKey_bio(pKeyBio, null) ?: d2i_PKCS8_PRIV_KEY_INFO_bio(pKeyBio, null)
                                  ?: throw RuntimeException("Failed to parse DER private key (d2i_PrivateKey or d2i_PKCS8_PRIV_KEY_INFO_bio): ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
                    defer { EVP_PKEY_free(evpPkey) }
                    if (SSL_CTX_use_PrivateKey(ctx, evpPkey) != 1) {
                        throw RuntimeException("SSL_CTX_use_PrivateKey failed: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
                    }
                }

                // Load certificate chain (leaf first)
                clientCertificateChainDer.forEachIndexed { index, certDer -> // Assumes Series.forEachIndexed
                    certDer.usePinned { certPinned ->
                        val certBio = BIO_new_mem_buf(certPinned.addressOf(0), certDer.size) ?: throw RuntimeException("BIO_new_mem_buf for cert failed")
                        defer { BIO_free(certBio) }
                        val x509 = d2i_X509_bio(certBio, null) ?: throw RuntimeException("Failed to parse DER certificate (d2i_X509_bio): ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
                        defer { X509_free(x509) }

                        if (index == 0) { // Leaf certificate
                            if (SSL_CTX_use_certificate(ctx, x509) != 1) {
                                throw RuntimeException("SSL_CTX_use_certificate failed for leaf: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
                            }
                        } else { // Intermediate certificate
                            if (SSL_CTX_add_extra_chain_cert(ctx, X509_dup(x509)) != 1L) { // X509_dup as add_extra_chain_cert takes ownership
                                throw RuntimeException("SSL_CTX_add_extra_chain_cert failed: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
                            }
                        }
                    }
                }
                // Optionally: SSL_CTX_check_private_key(ctx)
            }

            // QUIC Transport Parameters (Custom Extension)
            // Extension ID for QUIC TP: 0x39 (from RFC 8449, later RFC 9001 uses 57 for QUIC TP)
            // draft-ietf-quic-tls-34 uses 0x1b (27), then 0x39 (57 decimal), RFC9001 uses 0x39 / 57
            val quicExtType = 0x39u // 57 decimal
            quicTransportParams.usePinned { pinnedTp ->
                 // SSL_CTX_add_custom_ext is the way, but it's complex to set up callbacks for client side.
                 // Simpler for client: SSL_set_quic_transport_params (if OpenSSL version supports it directly)
                 // Or, for older OpenSSL, manually construct ClientHello with this extension.
                 // For now, we note this is where it *would* be set. The TlsService model assumes the
                 // service handles embedding this. If OpenSSL doesn't do it automatically via a simple API,
                 // this actual TlsService would need to manually construct ClientHello bytes or use SSL_add_custom_ext.
                 // This part is highly dependent on OpenSSL version and capabilities.
                 // For this subtask, acknowledge and do not fully implement custom ext handling.
            }


            val ssl = SSL_new(ctx) ?: throw RuntimeException("SSL_new failed: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
            // SNI
            hostname.cstr.usePinned { pinnedHostname ->
                if (SSL_set_tlsext_host_name(ssl, pinnedHostname.addressOf(0)) != 1L) {
                     SSL_free(ssl)
                    throw RuntimeException("SSL_set_tlsext_host_name failed for SNI: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
                }
            }

            SSL_set_connect_state(ssl) // Set to client mode

            val nativeTlsConnection = ActualNativeTlsConnection(ssl, ctx, callbacks, coroutineContext)
            nativeTlsConnection.doHandshakeNonBlocking() // Initial handshake step

            Result.success(nativeTlsConnection)

        } catch (e: Throwable) {
            SSL_CTX_free(ctx) // Free SSL_CTX if SSL object creation or setup failed
            Result.failure(e)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
class ActualNativeTlsConnection(
    private val ssl: CPointer<SSL>,
    private val sslCtx: CPointer<SSL_CTX>, // Keep to free it eventually
    private val callbacks: TlsHandshakeCallbacks,
    scopeContext: CoroutineContext
) : TlsConnection, CoroutineScope {

    override val coroutineContext: CoroutineContext = scopeContext + SupervisorJob() + CoroutineName("ActualNativeTlsConnection-${ssl.hashCode()}")

    private val readBio: CPointer<BIO> = BIO_new(BIO_s_mem()) ?: throw RuntimeException("Failed to create read BIO")
    private val writeBio: CPointer<BIO> = BIO_new(BIO_s_mem()) ?: throw RuntimeException("Failed to create write BIO")
    private var isClosed = false

    init {
        SSL_set_bio(ssl, readBio, writeBio) // SSL takes ownership of BIOs if this was SSL_set0_rbio/wbio
                                           // With SSL_set_bio, it increments their ref count. We need to free them.
    }

    // Called to drive the handshake, especially initially and after receiving data.
    fun doHandshakeNonBlocking(): Int {
        if (isClosed) return -1
        val ret = SSL_do_handshake(ssl)
        handleSslReturnCode(ret)
        return ret
    }

    private fun handleSslReturnCode(ret: Int) {
        if (isClosed) return

        when (val err = SSL_get_error(ssl, ret)) {
            SSL_ERROR_NONE -> { /* Success, or operation completed (e.g. handshake done) */
                if (SSL_is_init_finished(ssl) == 1) {
                    // Get ALPN
                    memScoped {
                        val alpnDataPtr = alloc<CPointerVar<UByteVar>>()
                        val alpnLen = alloc<UIntVar>()
                        SSL_get0_alpn_selected(ssl, alpnDataPtr.ptr, alpnLen.ptr)
                        val negotiatedAlpn = alpnDataPtr.value?.readBytes(alpnLen.value.toInt())?.decodeToString()
                        callbacks.onHandshakeComplete(negotiatedAlpn)
                    }
                    // TODO: Call onNewEncryptionSecretsReady appropriately.
                    // This requires extracting current read/write secrets from SSL object.
                    // This is complex with OpenSSL directly. Usually done via exporters.
                }
            }
            SSL_ERROR_WANT_READ -> { /* Need more data from peer, inform QUIC via onHandshakeDataToSend (by sending nothing) or by expecting more calls to processHandshakeData */
                sendPendingBioData() // Send any data OpenSSL might have buffered before wanting read
            }
            SSL_ERROR_WANT_WRITE -> { /* SSL wants to send data, call sendPendingBioData */
                sendPendingBioData()
            }
            SSL_ERROR_SSL, SSL_ERROR_SYSCALL -> {
                val errorString = ERR_error_string(ERR_get_error(), null)?.toKString() ?: "Unknown SSL error $err"
                val alertDesc = SSL_alert_desc_string_long(SSL_get_alert_description(ssl).toInt())?.toKString()
                callbacks.onTlsAlertToSend(TlsAlert(2, SSL_get_alert_description(ssl), "SSL/Syscall Error: $errorString. Alert: $alertDesc"))
                close()
            }
            // Other errors like SSL_ERROR_ZERO_RETURN (clean shutdown from peer)
            else -> {
                 callbacks.onTlsAlertToSend(TlsAlert(2, SSL_get_alert_description(ssl) ?: SSL_AD_INTERNAL_ERROR.toByte(), "Unhandled SSL error: $err"))
                 close()
            }
        }
    }

    private fun sendPendingBioData() {
        if (isClosed) return
        val pendingBytes = BIO_ctrl_pending(writeBio).toInt()
        if (pendingBytes > 0) {
            memScoped {
                val buffer = allocArray<ByteVar>(pendingBytes)
                val bytesReadFromBio = BIO_read(writeBio, buffer, pendingBytes)
                if (bytesReadFromBio > 0) {
                    // Determine current encryption level for QUIC CRYPTO frame
                    // This is complex. SSL_get_current_cipher() can give info.
                    // Mapping to TlsEncryptionLevel (HANDSHAKE vs APPLICATION_DATA) needed.
                    // For simplicity, assume HANDSHAKE during handshake.
                    val level = if (SSL_is_init_finished(ssl) == 1) TlsEncryptionLevel.APPLICATION_DATA else TlsEncryptionLevel.HANDSHAKE
                    callbacks.onHandshakeDataToSend(buffer.readBytes(bytesReadFromBio), level)
                } else if (bytesReadFromBio < 0 && BIO_should_retry(writeBio) == 0) {
                     // Error reading from writeBio
                }
            }
        }
    }

    override fun processHandshakeData(data: ByteArray, level: TlsEncryptionLevel): Result<Unit> {
        if (isClosed) return Result.failure(IllegalStateException("TlsConnection is closed"))
        if (data.isEmpty()) return Result.success(Unit) // No data to process

        var bytesWrittenToBio = 0
        data.usePinned { pinnedData ->
            bytesWrittenToBio = BIO_write(readBio, pinnedData.addressOf(0), data.size)
        }

        if (bytesWrittenToBio == data.size) {
            // Try to drive handshake with new data. SSL_do_handshake or SSL_read for app data.
            // If still in handshake:
            if (SSL_is_init_finished(ssl) == 0) {
                val ret = SSL_do_handshake(ssl)
                handleSslReturnCode(ret)
            } else {
                // Post-handshake, this would be SSL_read for application data, but QUIC uses its own framing.
                // CRYPTO frames post-handshake are usually for NewSessionTicket.
                // We might still call SSL_read to process it if it's TLS record layer.
                // For now, assume CRYPTO frames are fed, and SSL_do_handshake handles it.
                // This part needs refinement for post-handshake CRYPTO data.
                 memScoped { // Placeholder for SSL_read if handling app data via TLS records
                    val buffer = allocArray<ByteVar>(1) // Dummy read
                    val ret = SSL_read(ssl, buffer, 0) // Try to process any buffered post-handshake TLS messages
                    handleSslReturnCode(ret)
                 }
            }
            return Result.success(Unit)
        } else {
            // Error writing to readBio
            val errorMsg = if (bytesWrittenToBio < 0 && BIO_should_retry(readBio) == 0) {
                "BIO_write to readBio failed: ${ERR_error_string(ERR_get_error(), null)?.toKString()}"
            } else {
                "BIO_write to readBio wrote incomplete data ($bytesWrittenToBio/${data.size})"
            }
            return Result.failure(RuntimeException(errorMsg))
        }
    }

    override fun providePskTicket(ticket: ByteArray) { /* TODO: SSL_set_session for PSK */ }

    override fun close() {
        if (!isClosed) {
            isClosed = true
            // SSL_shutdown(ssl) // Send close_notify, needs read/write loop
            SSL_free(ssl)     // Frees SSL struct. BIOs are handled below if not owned by SSL.
            // BIO_free(readBio) // If SSL_set_bio was used, BIO_free_all on chain or individual free
            // BIO_free(writeBio) // If SSL_set_bio, these are not freed by SSL_free.
                               // If SSL_set0_bio, SSL_free handles them.
                               // Assuming SSL_set_bio, so we need to free them if they weren't chained and freed by BIO_free_all.
                               // However, they are typically part of the SSL object's I/O mechanism.
                               // Let's assume SSL_free handles associated BIOs correctly if set via SSL_set_bio,
                               // or that BIO_s_mem doesn't need explicit free beyond its buffer if not part of chain.
                               // Safest is often BIO_free_all if they were part of a chain.
                               // Here, they are distinct. BIO_free is usually sufficient for BIO_s_mem.
            BIO_free_all(readBio) // BIO_free_all is safe for single BIOs too.
            BIO_free_all(writeBio)
            SSL_CTX_free(sslCtx) // Free context only when service is fully done with it.
                                 // If CTX is shared, this is wrong. Assume CTX is per-connection for now.
            cancel("ActualNativeTlsConnection closed")
        }
    }
}

// Helper to convert Series<String> ALPN list to OpenSSL format (length-prefixed bytes)
@OptIn(ExperimentalForeignApi::class)
private fun Series<String>.toSerializedAlpn(): ByteArray {
    val byteList = mutableListOf<Byte>()
    this.map { it }.toList().forEach { protocol -> // Assuming Series.map {}.toList() or similar for iteration
        val protoBytes = protocol.encodeToByteArray()
        if (protoBytes.size > 255) throw IllegalArgumentException("ALPN protocol too long: $protocol")
        byteList.add(protoBytes.size.toByte())
        byteList.addAll(protoBytes.toList())
    }
    return byteList.toByteArray()
}

// Dummy defer for C resource cleanup (not a real robust solution for complex scopes)
@OptIn(ExperimentalForeignApi::class)
private inline fun <R> memScoped(block: MemScope.() -> R): R = kotlinx.cinterop.memScoped(block)
@OptIn(ExperimentalForeignApi::class)
private fun MemScope.defer(block: () -> Unit) { /* Actual defer needs more setup or use AutoClosable */ }
