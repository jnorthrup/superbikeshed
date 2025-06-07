package borg.trikeshed.net.tls

/**
 * Defines encryption levels relevant to the TLS handshake phases for QUIC key derivation.
 * Note: QUIC's "Initial" keys are derived from Connection ID and are not part of TLS directly.
 * This enum refers to keys derived *by* the TLS handshake.
 */
enum class TlsEncryptionLevel {
    /** Secrets derived for Handshake messages (e.g., client_handshake_traffic_secret, server_handshake_traffic_secret). */
    HANDSHAKE,
    /** Secrets for Application Data (1-RTT keys, e.g., client_application_traffic_secret_0, server_application_traffic_secret_0). */
    APPLICATION_DATA
    // 0-RTT keys (early_exporter_secret, client_early_traffic_secret) are also possible but handled differently.
}

/**
 * Represents a TLS alert.
 * @property level The alert level (1 for warning, 2 for fatal).
 * @property description The alert description code (as per TLS Alert Protocol).
 * @property message An optional descriptive message.
 */
data class TlsAlert(val level: Byte, val description: Byte, val message: String? = null)

/**
 * Callbacks used by a [TlsConnection] to communicate events and data back to the QUIC layer.
 */
interface TlsHandshakeCallbacks {
    /**
     * Called when the TLS stack receives an alert from the peer.
     * @param alert The received TLS alert.
     */
    fun onTlsAlertReceived(alert: TlsAlert)

    /**
     * Called when the TLS stack needs to send an alert to the peer.
     * The QUIC layer should package this into a CONNECTION_CLOSE frame if fatal.
     * @param alert The TLS alert to send.
     */
    fun onTlsAlertToSend(alert: TlsAlert)

    /**
     * Called when the TLS stack has handshake data to send to the peer.
     * This data should be wrapped in QUIC CRYPTO frames at the specified encryption level.
     * @param data The handshake data bytes.
     * @param level The encryption level at which this data should be sent.
     */
    fun onHandshakeDataToSend(data: ByteArray, level: TlsEncryptionLevel)

    /**
     * Called when new encryption secrets are available for a specific encryption level.
     * QUIC will use these secrets with HKDF-Expand-Label to derive packet protection keys and IVs.
     *
     * @param level The [TlsEncryptionLevel] for which these secrets are intended.
     * @param readSecret The secret used for decrypting packets from the peer at this level.
     * @param writeSecret The secret used for encrypting packets to the peer at this level.
     * @param cipherSuite The IANA TLS Cipher Suite ID (e.g., 0x1301 for TLS_AES_128_GCM_SHA256).
     */
    fun onNewEncryptionSecretsReady(level: TlsEncryptionLevel, readSecret: ByteArray, writeSecret: ByteArray, cipherSuite: Int)

    /**
     * Called when the TLS handshake is successfully completed.
     * @param negotiatedAlpn The ALPN protocol selected by the server (e.g., "h3"), or null if none was negotiated.
     */
    fun onHandshakeComplete(negotiatedAlpn: String?)

    /**
     * Called when the peer's certificate chain is received.
     * This callback is optional and provides the raw DER-encoded certificate for custom verification
     * or inspection by the application if needed beyond standard TLS validation.
     *
     * @param derEncodedCertificate The first certificate in the peer's chain (the end-entity certificate),
     *                              DER-encoded. Null if no certificate was provided or applicable.
     *                              Further certificates in a chain would require more complex handling.
     */
    fun onPeerCertificateReceived(derEncodedCertificate: ByteArray?) // Simplified to first cert
}

/**
 * Represents an active TLS handshake context.
 * Allows feeding received handshake data and managing the TLS state machine.
 */
interface TlsConnection {
    /**
     * Processes handshake data received from the peer (e.g., from QUIC CRYPTO frames).
     * This drives the TLS handshake state machine.
     *
     * @param data The handshake data bytes received from the peer.
     * @param level The [TlsEncryptionLevel] at which this data was received.
     * @return A [Result] indicating success (Unit) or failure (Exception) of processing.
     *         Failure might indicate a fatal TLS error, requiring connection termination.
     */
    fun processHandshakeData(data: ByteArray, level: TlsEncryptionLevel): Result<Unit>

    /**
     * Provides a Pre-Shared Key (PSK) ticket for session resumption.
     * This is optional and used if the client wishes to attempt session resumption.
     * @param ticket The PSK ticket received from a previous session.
     */
    fun providePskTicket(ticket: ByteArray) // Optional for session resumption

    /**
     * Closes this specific TLS connection context and releases associated resources.
     * May trigger sending of a `close_notify` alert depending on the TLS stack and state.
     */
    fun close()
}

/**
 * Service interface for platform-specific TLS operations, focused on the client role for QUIC.
 */
expect interface TlsService { // Changed to expect interface
    /**
     * Starts a new TLS client handshake.
     *
     * @param hostname The hostname to connect to, used for SNI and certificate verification.
     * @param alpnProtocols A list of ALPN protocols to offer (e.g., "h3", "h3-29").
     * @param quicTransportParams The client's QUIC transport parameters, DER-encoded or in a format
     *                            suitable for embedding in the QUIC Transport Parameters TLS extension.
     * @param callbacks A [TlsHandshakeCallbacks] implementation to handle events and data from the TLS stack.
     * @return A [Result] containing a [TlsConnection] object on success, allowing interaction with the
     *         ongoing handshake, or an [Exception] on failure to initialize.
     */
    suspend fun startClientHandshake(
        hostname: String,
        alpnProtocols: List<String>,
        quicTransportParams: ByteArray, // Bytes of the QUIC TP TLS extension payload
        callbacks: TlsHandshakeCallbacks
    ): Result<TlsConnection>

    // Consider adding a close() method to TlsService if the service itself holds resources
    // that need explicit cleanup beyond individual TlsConnection objects.
    // fun close()
}
