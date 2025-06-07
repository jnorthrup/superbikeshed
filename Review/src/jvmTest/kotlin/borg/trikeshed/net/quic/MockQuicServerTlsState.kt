package borg.trikeshed.net.quic // Placing it in the same package as QuicIntegrationTest for simplicity

import borg.trikeshed.net.quic.crypto.QuicSecrets
import borg.trikeshed.net.quic.tls.KeyShareEntry
import evolution.HkdfService

/**
 * A simplified state holder for the server side of a QUIC TLS handshake in tests.
 * This is NOT a complete QUIC server implementation.
 */
class MockQuicServerTlsState(
    val version: UInt,
    initialClientDcId: ByteArray, // DCID from client's first Initial (server will use as its SCID)
    initialClientScid: ByteArray, // SCID from client's first Initial (server will use as its DCID)
    private val hkdfService: HkdfService // Test HKDF service
) {
    val serverChosenScid: ByteArray = initialClientDcId // Server's SCID is client's DCID
    val dcidForClient: ByteArray = initialClientScid   // Server's DCID (for packets to client) is client's SCID

    val connectionData: QuicConnection = QuicConnection.newClientConnectionDataOnly().apply {
        // Server's perspective: its "client ID" is its own SCID, "server ID" is what it uses as DCID to client
        this.clientId = serverChosenScid
        this.serverId = dcidForClient
    }
    val manager: QuicConnectionManager = QuicConnectionManager(connectionData, QuicConnectionManager.ConnectionRole.SERVER)

    var clientHelloTlsPayload: ByteArray? = null // Store the raw ClientHello for transcript
    var clientKeyShare: KeyShareEntry? = null // Extracted from ClientHello

    var serverEphemeralPrivateKey: ByteArray? = null
    var serverEphemeralPublicKey: ByteArray? = null

    var sharedSecret: ByteArray? = null
    var handshakeSalt: ByteArray? = null // This is the "derived_secret" from early_secret + "derived"
    var handshakeSecret: ByteArray? = null

    var serverHandshakeTrafficSecret: ByteArray? = null
    var clientHandshakeTrafficSecret: ByteArray? = null // Server's view of client's hs traffic secret

    var handshakeTranscript: ByteArray = byteArrayOf()

    // Server's Initial keys (derived from client's Initial DCID)
    var serverInitialSecrets: QuicSecrets? = null

    init {
        runBlocking { // Keep it simple for test setup
            deriveServerInitialSecrets(initialClientDcId)
        }
    }

    private suspend fun deriveServerInitialSecrets(dcidFromClientInitial: ByteArray) {
        val initialSalt = QuicConstants.QUIC_V1_INITIAL_SALT
        val extracted = hkdfService.extract(initialSalt, dcidFromClientInitial)
        // These are server's *write* keys for its Initial packets.
        val serverWriteSecretLabel = "server in"
        val serverInitialWriteSecret = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(hkdfService, extracted, serverWriteSecretLabel, byteArrayOf(), 32)

        serverInitialSecrets = QuicSecrets(
            key = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(hkdfService, serverInitialWriteSecret, "quic key", byteArrayOf(), 16),
            iv = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(hkdfService, serverInitialWriteSecret, "quic iv", byteArrayOf(), 12),
            hpKey = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(hkdfService, serverInitialWriteSecret, "quic hp", byteArrayOf(), 16)
        )
        // Server would also derive client initial read keys using "client in" label if it needed to send something other than Initial
    }


    // Simplified processing of ClientHello to extract key share and update transcript
    // A real server would fully deserialize and validate ClientHelloData
    suspend fun processClientHelloPayload(chPayload: ByteArray, clientEphemeralPubKey: KeyShareEntry) {
        this.clientHelloTlsPayload = chPayload
        this.handshakeTranscript += chPayload
        this.clientKeyShare = clientEphemeralPubKey // Assume this is passed in after parsing on test side

        // Generate server's ephemeral keys
        val keyPair = borg.trikeshed.net.quic.crypto.generateEcdhKeyPair(clientEphemeralPubKey.group)
        this.serverEphemeralPrivateKey = keyPair.first
        this.serverEphemeralPublicKey = keyPair.second

        // Compute shared secret
        this.sharedSecret = borg.trikeshed.net.quic.crypto.computeEcdhSharedSecret(
            clientEphemeralPubKey.group,
            this.serverEphemeralPrivateKey!!,
            clientEphemeralPubKey.keyExchange
        )

        // Derive handshake secrets (server's perspective)
        val earlySecret = ByteArray(32) // Non-PSK
        this.handshakeSalt = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(hkdfService, earlySecret, "derived", byteArrayOf(), 32)
        this.handshakeSecret = hkdfService.extract(this.handshakeSalt!!, this.sharedSecret!!)

        // Server's view of traffic secrets
        this.clientHandshakeTrafficSecret = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(
            hkdfService, this.handshakeSecret!!, "c hs traffic", this.handshakeTranscript, 32
        )
        this.serverHandshakeTrafficSecret = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(
            hkdfService, this.handshakeSecret!!, "s hs traffic", this.handshakeTranscript, 32
        )

        // Server sets its HANDSHAKE send keys (derived from serverHandshakeTrafficSecret)
        val shsKey = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(hkdfService, this.serverHandshakeTrafficSecret!!, "quic key", byteArrayOf(), 16)
        val shsIv = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(hkdfService, this.serverHandshakeTrafficSecret!!, "quic iv", byteArrayOf(), 12)
        val shsHpKey = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(hkdfService, this.serverHandshakeTrafficSecret!!, "quic hp", byteArrayOf(), 16)
        val serverHandshakeWriteSecrets = QuicSecrets(shsKey, shsIv, shsHpKey)

        connectionData.cryptoSecrets[EncryptionLevel.HANDSHAKE] = serverHandshakeWriteSecrets // Server's write keys
        manager.updateSecrets(EncryptionLevel.HANDSHAKE, serverHandshakeWriteSecrets)
    }

    fun updateTranscript(message: ByteArray) {
        this.handshakeTranscript += message
    }
}

// Helper to allow runBlocking in init, not ideal but makes test setup contained
private fun runBlocking(block: suspend CoroutineScope.() -> Unit) = kotlinx.coroutines.runBlocking(Dispatchers.Default, block)
