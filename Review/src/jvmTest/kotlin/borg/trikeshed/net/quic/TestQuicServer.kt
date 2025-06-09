package borg.trikeshed.net.quic

import borg.trikeshed.net.quic.crypto.*
import borg.trikeshed.net.quic.tls.*
import borg.trikeshed.net.quic.utils.*
import evolution.AesService
import evolution.HkdfService
import evolution.RealUdpSocketFactory
import evolution.UdpSocket
import evolution.getIODispatcher
import kotlinx.coroutines.*
import kotlin.random.Random

// MockQuicServerConnectionState: Manages state for one connection from server's perspective
class MockQuicServerConnectionState(
    val version: UInt,
    val clientInitialDcIdAsServerScid: ByteArray, // Client's Initial DCID (Server uses as its SCID)
    val clientInitialScidAsServerDcid: ByteArray, // Client's Initial SCID (Server uses as its DCID for replies)
    private val hkdfService: HkdfService,
    private val aesService: AesService
) {
    val serverScid: ByteArray = clientInitialDcIdAsServerScid
    val dcidForClientReplies: ByteArray = clientInitialScidAsServerDcid

    val connectionData: QuicConnection = QuicConnection.newClientConnectionDataOnly(
        scidOverride = serverScid,
        initialDcIdOverride = dcidForClientReplies
    )
    val manager: QuicConnectionManager = QuicConnectionManager(connectionData, QuicConnectionManager.ConnectionRole.SERVER)

    var clientHelloTlsPayload: ByteArray? = null
    var clientKeyShareFromHello: KeyShareEntry? = null

    var serverEphemeralPrivateKey: ByteArray? = null
    var serverEphemeralPublicKey: ByteArray? = null

    var sharedSecret: ByteArray? = null
    var handshakeSalt: ByteArray? = null
    var handshakeSecret: ByteArray? = null

    var serverHandshakeTrafficSecret: ByteArray? = null
    var clientHandshakeTrafficSecret: ByteArray? = null

    var serverInitialWriteSecrets: QuicSecrets? = null
    var clientInitialReadSecrets: QuicSecrets? = null

    // For 1-RTT
    var serverAppWriteSecrets: QuicSecrets? = null // Derived from server_application_traffic_secret_0
    var clientAppReadSecrets: QuicSecrets? = null  // Derived from client_application_traffic_secret_0

    var handshakeTranscript: ByteArray = byteArrayOf()

    init {
        runBlocking { // Simplification for test constructor
            val initialSalt = QuicConstants.QUIC_V1_INITIAL_SALT
            val initialSecretForServer = hkdfService.extract(initialSalt, clientInitialDcIdAsServerScid)

            val serverInitialWriteTrafficSecret = hkdfExpandLabel(hkdfService, initialSecretForServer, "server in", byteArrayOf(), 32)
            this.serverInitialWriteSecrets = QuicSecrets(
                key = hkdfExpandLabel(hkdfService, serverInitialWriteTrafficSecret, "quic key", byteArrayOf(), 16),
                iv = hkdfExpandLabel(hkdfService, serverInitialWriteTrafficSecret, "quic iv", byteArrayOf(), 12),
                hpKey = hkdfExpandLabel(hkdfService, serverInitialWriteTrafficSecret, "quic hp", byteArrayOf(), 16)
            )
            manager.updateSecrets(EncryptionLevel.INITIAL, this.serverInitialWriteSecrets!!)

            val clientInitialWriteTrafficSecret = hkdfExpandLabel(hkdfService, initialSecretForServer, "client in", byteArrayOf(), 32)
            this.clientInitialReadSecrets = QuicSecrets(
                key = hkdfExpandLabel(hkdfService, clientInitialWriteTrafficSecret, "quic key", byteArrayOf(), 16),
                iv = hkdfExpandLabel(hkdfService, clientInitialWriteTrafficSecret, "quic iv", byteArrayOf(), 12),
                hpKey = hkdfExpandLabel(hkdfService, clientInitialWriteTrafficSecret, "quic hp", byteArrayOf(), 16)
            )
        }
    }

    fun updateTranscript(message: ByteArray) {
        this.handshakeTranscript += message
    }

    suspend fun processClientHelloPayload(chTlsPayload: ByteArray, clientActualKeyShare: KeyShareEntry) {
        updateTranscript(chTlsPayload)
        this.clientHelloTlsPayload = chTlsPayload
        this.clientKeyShareFromHello = clientActualKeyShare

        val keyPair = generateEcdhKeyPair(clientActualKeyShare.group)
        this.serverEphemeralPrivateKey = keyPair.first
        this.serverEphemeralPublicKey = keyPair.second

        this.sharedSecret = computeEcdhSharedSecret(
            clientActualKeyShare.group, this.serverEphemeralPrivateKey!!, clientActualKeyShare.keyExchange
        )

        val earlySecret = ByteArray(32)
        this.handshakeSalt = hkdfExpandLabel(hkdfService, earlySecret, "derived", byteArrayOf(), 32)
        this.handshakeSecret = hkdfService.extract(this.handshakeSalt!!, this.sharedSecret!!)

        this.clientHandshakeTrafficSecret = hkdfExpandLabel(hkdfService, this.handshakeSecret!!, "c hs traffic", this.handshakeTranscript, 32)
        this.serverHandshakeTrafficSecret = hkdfExpandLabel(hkdfService, this.handshakeSecret!!, "s hs traffic", this.handshakeTranscript, 32)

        val shsKey = hkdfExpandLabel(hkdfService, this.serverHandshakeTrafficSecret!!, "quic key", byteArrayOf(), 16)
        val shsIv = hkdfExpandLabel(hkdfService, this.serverHandshakeTrafficSecret!!, "quic iv", byteArrayOf(), 12)
        val shsHpKey = hkdfExpandLabel(hkdfService, this.serverHandshakeTrafficSecret!!, "quic hp", byteArrayOf(), 16)
        val serverHandshakeWriteSecrets = QuicSecrets(shsKey, shsIv, shsHpKey)

        manager.updateSecrets(EncryptionLevel.HANDSHAKE, serverHandshakeWriteSecrets)
        connectionData.tlsHandshakeState = TlsHandshakeState.EXPECTING_ENCRYPTED_EXTENSIONS // Server ready for next phase
    }

    fun constructServerHello(): ServerHelloData = ServerHelloData(TLS_VERSION_1_2, Random.nextBytes(32), connectionData.clientId!!, TLS_AES_128_GCM_SHA256, 0u, KeyShareEntry(X25519_GROUP, serverEphemeralPublicKey!!), TLS_VERSION_1_3)
    fun constructEncryptedExtensions(): EncryptedExtensionsData = EncryptedExtensionsData(emptyList())
    fun constructCertificate(): CertificateData {
        val dummyCert = CertificateEntry("dummy_server_cert_bytes".encodeToByteArray(), emptyList())
        return CertificateData(byteArrayOf(), listOf(dummyCert))
    }
    suspend fun constructCertificateVerify(): CertificateVerifyData {
        val dummySig = sha256(handshakeTranscript).copyOfRange(0,32) // Dummy signature
        return CertificateVerifyData(TlsSignatureScheme.ECDSA_SECP256R1_SHA256, dummySig)
    }
    suspend fun constructFinished(): FinishedData {
        assertNotNull(serverHandshakeTrafficSecret, "Server handshake traffic secret not derived for Finished")
        val finishedKey = hkdfExpandLabel(hkdfService, serverHandshakeTrafficSecret!!, "finished", byteArrayOf(), 32)
        val transcriptHash = sha256(this.handshakeTranscript)
        val verifyData = hmacSha256(hkdfService, finishedKey, transcriptHash)
        return FinishedData(verifyData)
    }

    suspend fun deriveApplicationSecretsFromServerPerspective() {
        val fullTranscriptHash = sha256(this.handshakeTranscript)
        val ikmForMasterSecret = ByteArray(32)
        assertNotNull(this.handshakeSalt, "Handshake salt (derived secret) is null in server state")
        val masterSecret = hkdfService.extract(salt = this.handshakeSalt!!, ikm = ikmForMasterSecret)

        val srv_clientAppTrafficSecret = hkdfExpandLabel(hkdfService, masterSecret, "c ap traffic", fullTranscriptHash, 32)
        val srv_serverAppTrafficSecret = hkdfExpandLabel(hkdfService, masterSecret, "s ap traffic", fullTranscriptHash, 32)

        this.clientAppReadSecrets = QuicSecrets(
            key = hkdfExpandLabel(hkdfService, srv_clientAppTrafficSecret, "quic key", byteArrayOf(), 16),
            iv = hkdfExpandLabel(hkdfService, srv_clientAppTrafficSecret, "quic iv", byteArrayOf(), 12),
            hpKey = hkdfExpandLabel(hkdfService, srv_clientAppTrafficSecret, "quic hp", byteArrayOf(), 16)
        )
        this.serverAppWriteSecrets = QuicSecrets(
            key = hkdfExpandLabel(hkdfService, srv_serverAppTrafficSecret, "quic key", byteArrayOf(), 16),
            iv = hkdfExpandLabel(hkdfService, srv_serverAppTrafficSecret, "quic iv", byteArrayOf(), 12),
            hpKey = hkdfExpandLabel(hkdfService, srv_serverAppTrafficSecret, "quic hp", byteArrayOf(), 16)
        )
        manager.updateSecrets(EncryptionLevel.ONERTT, this.serverAppWriteSecrets!!)
        connectionData.tlsHandshakeState = TlsHandshakeState.HANDSHAKE_COMPLETE
         println("TestServer: Application secrets derived. State: ${connectionData.tlsHandshakeState}")
    }
}

class TestQuicServer(
    private val hkdfService: HkdfService,
    private val aesService: AesService,
    private val port: Int = 12345
) {
    private var udpSocket: UdpSocket? = null
    private val serverDispatcher = getIODispatcher()
    private var job: Job? = null
    private val activeConnections = mutableMapOf<String, MockQuicServerConnectionState>() // Keyed by Server's SCID (Client's Initial DCID)
    val handshakeSuccessfullyCompleted = CompletableDeferred<Boolean>()

    private suspend fun getOrCreateConnectionState(clientInitialDcId: ByteArray, clientInitialScid: ByteArray): MockQuicServerConnectionState {
        val serverScidString = clientInitialDcId.toHex() // Server uses client's DCID as its SCID
        return activeConnections.getOrPut(serverScidString) {
            println("TestServer: New connection for ServerSCID (Client's Initial DCID): $serverScidString")
            MockQuicServerConnectionState(QuicConstants.QUIC_VERSION_1, clientInitialDcId, clientInitialScid, hkdfService, aesService)
        }
    }

    suspend fun start() {
        if (job != null) { println("TestServer already running."); return }
        job = CoroutineScope(serverDispatcher + SupervisorJob()).launch {
            try {
                udpSocket = RealUdpSocketFactory().create(port, serverDispatcher)
                println("TestQuicServer listening on port $port")
                val buffer = ByteArray(4096)

                while (isActive) {
                    println("TestServer: Waiting for packet...")
                    val receivedPacketInfo = udpSocket?.receive(buffer) ?: break
                    val (size, clientHost, clientPort) = receivedPacketInfo
                    val rawPacket = buffer.copyOfRange(0, size)
                    println("TestServer: Received ${rawPacket.size} bytes from $clientHost:$clientPort")

                    val minimalHeader = QuicPacketUtils.parseMinimalHeaderFields(rawPacket) ?: continue

                    if (!minimalHeader.isShortHeader && minimalHeader.longHeaderType == QuicPacketType.INITIAL) {
                        val serverConnState = getOrCreateConnectionState(minimalHeader.dcid, minimalHeader.scid!!)
                        assertNotNull(serverConnState.clientInitialReadSecrets, "Server's client_initial_read_secrets not derived")

                        val pnOffsetClientInitial = minimalHeader.packetNumberOffset
                        val sampleOffsetClientInitial = pnOffsetClientInitial + QuicPacketUtils.parsePacketNumberLengthFromFirstByte(minimalHeader.firstByte, false) + 4

                        val unprotectResult = removeHeaderProtection(aesService, serverConnState.clientInitialReadSecrets!!.hpKey, rawPacket, sampleOffsetClientInitial, pnOffsetClientInitial)
                        if (unprotectResult == null) { println("TestServer: Client Initial HP removal failed."); continue }
                        val (unprotectedClientInitialHeader, clientInitialPn) = unprotectResult
                        val unprotectedHeaderLen = pnOffsetClientInitial + QuicPacketUtils.parsePacketNumberLengthFromFirstByte(unprotectedClientInitialHeader[0], false)

                        val encryptedPayload = rawPacket.sliceArray(unprotectedHeaderLen until rawPacket.size)
                        val decryptedPayload = aesService.gcmDecrypt(serverConnState.clientInitialReadSecrets!!.key, serverConnState.clientInitialReadSecrets!!.iv, encryptedPayload, unprotectedClientInitialHeader.sliceArray(0 until unprotectedHeaderLen)) // TODO: Correct IV
                        if (decryptedPayload == null) { println("TestServer: Client Initial decryption failed."); continue }

                        val clientPnLen = QuicPacketUtils.parsePacketNumberLengthFromFirstByte(unprotectedClientInitialHeader[0], false)
                        val clientCryptoFrameData = parseCryptoFrame(decryptedPayload.sliceArray(clientPnLen until decryptedPayload.size))?.first
                        if (clientCryptoFrameData == null) { println("TestServer: Failed to parse crypto frame from client Initial"); continue }

                        println("TestServer: Successfully unprotected and decrypted client Initial. Processing ClientHello...")
                        // A real server would parse ClientHelloData to get the actual KeyShareEntry
                        val clientActualKeyShare = TlsMessagesTestUtil.extractClientKeyShare(clientCryptoFrameData) ?: KeyShareEntry(X25519_GROUP, Random.nextBytes(32)) // Fallback for test
                        serverConnState.processClientHelloPayload(clientCryptoFrameData, clientActualKeyShare)

                        sendServerInitialFlight(serverConnState, clientHost, clientPort)
                        sendServerHandshakeFlight(serverConnState, clientHost, clientPort)

                    } else if (!minimalHeader.isShortHeader && minimalHeader.longHeaderType == QuicPacketType.HANDSHAKE) {
                        val serverConnState = activeConnections[minimalHeader.dcid.toHex()] // Client's DCID is server's SCID
                        if (serverConnState == null) { println("TestServer: No connection for Handshake packet DCID ${minimalHeader.dcid.toHex()}"); continue }
                        assertNotNull(serverConnState.clientHandshakeTrafficSecret, "Server's view of client HS traffic secret is null")
                        val clientHsReadSecrets = QuicSecrets(
                            key = hkdfExpandLabel(hkdfService, serverConnState.clientHandshakeTrafficSecret!!, "quic key", byteArrayOf(), 16),
                            iv = hkdfExpandLabel(hkdfService, serverConnState.clientHandshakeTrafficSecret!!, "quic iv", byteArrayOf(), 12),
                            hpKey = hkdfExpandLabel(hkdfService, serverConnState.clientHandshakeTrafficSecret!!, "quic hp", byteArrayOf(), 16)
                        )
                        val pnOffsetClientHs = minimalHeader.packetNumberOffset
                        val sampleOffsetClientHs = pnOffsetClientHs + QuicPacketUtils.parsePacketNumberLengthFromFirstByte(minimalHeader.firstByte, false) + 4
                        val unprotectHsResult = removeHeaderProtection(aesService, clientHsReadSecrets.hpKey, rawPacket, sampleOffsetClientHs, pnOffsetClientHs)
                        if (unprotectHsResult == null) { println("TestServer: Client Handshake HP removal failed."); continue }
                        val (unprotectedClientHsHeader, clientHsPn) = unprotectHsResult
                        val unprotectedHsHeaderLen = pnOffsetClientHs + QuicPacketUtils.parsePacketNumberLengthFromFirstByte(unprotectedClientHsHeader[0], false)
                        val encryptedHsPayload = rawPacket.sliceArray(unprotectedHsHeaderLen until rawPacket.size)
                        val decryptedHsPayload = aesService.gcmDecrypt(clientHsReadSecrets.key, clientHsReadSecrets.iv, encryptedHsPayload, unprotectedClientHsHeader.sliceArray(0 until unprotectedHsHeaderLen)) // TODO: Correct IV
                        if (decryptedHsPayload == null) { println("TestServer: Client Handshake decryption failed."); continue }

                        val clientFinFrameData = parseCryptoFrame(decryptedHsPayload.sliceArray(QuicPacketUtils.parsePacketNumberLengthFromFirstByte(unprotectedClientHsHeader[0],false) until decryptedHsPayload.size))?.first
                        if (clientFinFrameData == null) { println("TestServer: Failed to parse ClientFinished CRYPTO frame."); continue }

                        val clientFinished = deserializeClientFinished(clientFinFrameData)
                        if (clientFinished == null) { println("TestServer: Failed to deserialize ClientFinished."); continue }

                        val expectedClientVerify = serverConnState.constructFinished().verifyData // Server calculates what it expects client to send based on its transcript
                        // This is not quite right. Server needs to verify client's Finished against *server's* transcript *before* client's Finished.
                        // And use client_handshake_traffic_secret.
                        val finishedKey = hkdfExpandLabel(hkdfService, serverConnState.clientHandshakeTrafficSecret!!, "finished", byteArrayOf(), 32)
                        val transcriptHash = sha256(serverConnState.handshakeTranscript) // Transcript server has up to *its own* Finished
                        val expectedClientVerifyData = hmacSha256(hkdfService, finishedKey, transcriptHash)

                        if (expectedClientVerifyData.contentEquals(clientFinished.verifyData)) {
                            println("TestServer: Client Finished VERIFIED.")
                            serverConnState.updateTranscript(clientFinFrameData) // Add client finished to transcript
                            serverConnState.deriveApplicationSecretsFromServerPerspective()
                            if(serverConnState.connectionData.tlsHandshakeState == TlsHandshakeState.HANDSHAKE_COMPLETE) {
                                handshakeSuccessfullyCompleted.complete(true)
                            }
                        } else {
                            println("TestServer: Client Finished verification FAILED.")
                            handshakeSuccessfullyCompleted.complete(false)
                        }

                    } else if (minimalHeader.isShortHeader) {
                        val connStateKey = minimalHeader.dcid.toHex() // Client's DCID is server's SCID
                        val serverConnState = activeConnections[connStateKey]
                        if (serverConnState == null) { println("TestServer: No connection for Short Header DCID ${connStateKey}"); continue }
                        assertNotNull(serverConnState.clientAppReadSecrets, "Server's client_app_read_keys are null")

                        val pnOffsetShort = minimalHeader.packetNumberOffset
                        val sampleOffsetShort = pnOffsetShort + QuicPacketUtils.parsePacketNumberLengthFromFirstByte(minimalHeader.firstByte, true) + 4
                        val unprotectShortResult = removeHeaderProtection(aesService, serverConnState.clientAppReadSecrets!!.hpKey, rawPacket, sampleOffsetShort, pnOffsetShort)
                        if (unprotectShortResult == null) { println("TestServer: Client 1-RTT HP removal failed."); continue }
                        val (unprotectedShortHeader, client1RttPn) = unprotectShortResult
                        val unprotectedShortHeaderLen = pnOffsetShort + QuicPacketUtils.parsePacketNumberLengthFromFirstByte(unprotectedShortHeader[0], true)
                        val encryptedShortPayload = rawPacket.sliceArray(unprotectedShortHeaderLen until rawPacket.size)
                        val decryptedShortPayload = aesService.gcmDecrypt(serverConnState.clientAppReadSecrets!!.key, serverConnState.clientAppReadSecrets!!.iv, encryptedShortPayload, unprotectedShortHeader.sliceArray(0 until unprotectedShortHeaderLen)) // TODO: Correct IV
                        if (decryptedShortPayload == null) { println("TestServer: Client 1-RTT decryption failed."); continue }

                        val streamFrame = parseStreamFrame(decryptedShortPayload)
                        if (streamFrame != null && streamFrame.third.decodeToString() == "PING") {
                            println("TestServer: Received PING. Sending PONG.")
                            // Send ACK for PING packet
                            val ackFrame = createAckFrame(client1RttPn.toLong(), 0uL, emptyList(), 0uL)
                            val ackHeader = QuicPacketUtils.serializeShortHeader(serverConnState.dcidForClient, serverConnState.manager.getNextPacketNumberForEncryptionLevel(EncryptionLevel.ONERTT), 1, false)
                            val ackEncPayload = aesService.gcmEncrypt(serverConnState.serverAppWriteSecrets!!.key, serverConnState.serverAppWriteSecrets!!.iv, ackFrame, ackHeader)!!
                            val ackPnOffset = QuicPacketUtils.calculatePacketNumberOffsetForShortHeader(serverConnState.dcidForClient.size)
                            val ackProtectedHeader = applyHeaderProtection(aesService, serverConnState.serverAppWriteSecrets!!.hpKey, ackHeader, ackEncPayload, ackPnOffset, 1, true)
                            udpSocket!!.send(ackProtectedHeader + ackEncPayload, clientHost, clientPort)

                            // Send PONG
                            val pongFrame = createStreamFrame(0uL, 0uL, "PONG".encodeToByteArray(), true)
                            val pongHeader = QuicPacketUtils.serializeShortHeader(serverConnState.dcidForClient, serverConnState.manager.getNextPacketNumberForEncryptionLevel(EncryptionLevel.ONERTT), 1, false)
                            val pongEncPayload = aesService.gcmEncrypt(serverConnState.serverAppWriteSecrets!!.key, serverConnState.serverAppWriteSecrets!!.iv, pongFrame, pongHeader)!!
                            val pongPnOffset = QuicPacketUtils.calculatePacketNumberOffsetForShortHeader(serverConnState.dcidForClient.size)
                            val pongProtectedHeader = applyHeaderProtection(aesService, serverConnState.serverAppWriteSecrets!!.hpKey, pongHeader, pongEncPayload, pongPnOffset, 1, true)
                            udpSocket!!.send(pongProtectedHeader + pongEncPayload, clientHost, clientPort)
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive && e !is CancellationException) {
                    println("TestQuicServer error in main loop: ${e.message}"); e.printStackTrace()
                    if (!handshakeSuccessfullyCompleted.isCompleted) handshakeSuccessfullyCompleted.completeExceptionally(e)
                }
            } finally {
                println("TestQuicServer receive loop ended.")
                udpSocket?.close()
            }
        }
    }

    private suspend fun sendServerInitialFlight(serverConnState: MockQuicServerConnectionState, clientHost: String, clientPort: Int) {
        val serializedSh = serializeServerHello(serverConnState.constructServerHello())
        serverConnState.updateTranscript(serializedSh)
        // Per RFC 9001, ServerHello is typically followed by EE, Cert, CV, Finished in subsequent packet(s)
        // For simplicity of test, we can bundle SH + EE in Initial, then Cert + CV + Finished in Handshake.
        val serializedEe = serializeEncryptedExtensions(serverConnState.constructEncryptedExtensions())
        serverConnState.updateTranscript(serializedEe)

        val cryptoPayload = createCryptoFrame(0uL, serializedSh + serializedEe)
        val pn = serverConnState.manager.getNextPacketNumberForEncryptionLevel(EncryptionLevel.INITIAL)
        val pnLen = 1
        val payloadLen = pnLen + cryptoPayload.size + QuicConstants.AEAD_TAG_LENGTH

        val (headerBytes, pnOffset) = QuicPacketUtils.serializeInitialHeader(
            QuicConstants.QUIC_VERSION_1, serverConnState.dcidForClient, serverConnState.serverScid,
            byteArrayOf(), pn, pnLen, payloadLen
        )
        assertNotNull(serverConnState.serverInitialWriteSecrets, "Server Initial Write Secrets are null")
        // Encrypt PN + Crypto Frame
        val payloadToEncrypt = byteArrayOf(pn.toByte()) + cryptoPayload
        val encrypted = aesService.gcmEncrypt(
            serverConnState.serverInitialWriteSecrets!!.key, serverConnState.serverInitialWriteSecrets!!.iv, // TODO: Construct IV with PN
            payloadToEncrypt,
            headerBytes // AAD is the unprotected header (without PN for this encryption)
        )!!
        val protectedHeader = applyHeaderProtection(
            aesService, serverConnState.serverInitialWriteSecrets!!.hpKey, headerBytes,
            encrypted, // Sample from encrypted (PN+Crypto)
            pnOffset, pnLen, false
        )
        udpSocket!!.send(protectedHeader + encrypted, clientHost, clientPort)
        println("TestServer: Sent Initial packet (SH+EE) to $clientHost:$clientPort")
    }

    private suspend fun sendServerHandshakeFlight(serverConnState: MockQuicServerConnectionState, clientHost: String, clientPort: Int) {
        val serializedCert = serializeCertificate(serverConnState.constructCertificate())
        serverConnState.updateTranscript(serializedCert)
        val serializedCv = serializeCertificateVerify(serverConnState.constructCertificateVerify())
        serverConnState.updateTranscript(serializedCv)
        val serializedFin = serializeFinished(serverConnState.constructFinished())
        // Server's Finished is NOT added to its own transcript for MAC calculation by client

        var offset = 0uL
        val frame1 = createCryptoFrame(offset, serializedCert); offset += serializedCert.size.toULong()
        val frame2 = createCryptoFrame(offset, serializedCv);   offset += serializedCv.size.toULong()
        val frame3 = createCryptoFrame(offset, serializedFin)
        val cryptoPayloadBundle = frame1 + frame2 + frame3

        val pn = serverConnState.manager.getNextPacketNumberForEncryptionLevel(EncryptionLevel.HANDSHAKE)
        val pnLen = 1
        val payloadLen = pnLen + cryptoPayloadBundle.size + QuicConstants.AEAD_TAG_LENGTH

        val (headerBytes, pnOffset) = QuicPacketUtils.serializeHandshakeHeader(
            QuicConstants.QUIC_VERSION_1, serverConnState.dcidForClient, serverConnState.serverScid,
            pn, pnLen, payloadLen
        )
        val handshakeSecrets = serverConnState.manager.getCurrentSecretsForSend(EncryptionLevel.HANDSHAKE)
        assertNotNull(handshakeSecrets, "Server Handshake Write Secrets are null")

        // Encrypt PN + Crypto Frames
        val payloadToEncrypt = byteArrayOf(pn.toByte()) + cryptoPayloadBundle
        val encrypted = aesService.gcmEncrypt(
            handshakeSecrets.key, handshakeSecrets.iv, // TODO: Construct IV with PN
            payloadToEncrypt,
            headerBytes // AAD is unprotected header
        )!!
        val protectedHeader = applyHeaderProtection(
            aesService, handshakeSecrets.hpKey, headerBytes,
            encrypted, // Sample from encrypted (PN+Crypto bundle)
            pnOffset, pnLen, false // isShortHeader = false for Handshake packet
        )
        udpSocket!!.send(protectedHeader + encrypted, clientHost, clientPort)
        println("TestServer: Sent Handshake packet (Cert+CV+Fin) to $clientHost:$clientPort")
    }


    fun stop() {
        println("TestQuicServer.stop() called")
        job?.cancel() // Cancel the coroutine
        try {
            udpSocket?.close() // Close the socket
        } catch (e: Exception) {
            println("Exception closing server socket: ${e.message}")
        } finally {
            udpSocket = null
            job = null
            println("TestQuicServer stopped and socket closed.")
        }
    }
     fun awaitHandshakeCompletion(timeout: Long = 10000L): Boolean = runBlocking {
        withTimeoutOrNull(timeout) {
            handshakeSuccessfullyCompleted.await()
        } ?: false
    }
}

// Helper to allow runBlocking in init, not ideal but makes test setup contained
private fun runBlocking(block: suspend CoroutineScope.() -> Unit) = kotlinx.coroutines.runBlocking(Dispatchers.Default, block)

internal object TlsMessagesTestUtil { // Helper to access parts of TLS messages for test setup
    fun extractClientKeyShare(clientHelloTlsPayload: ByteArray): KeyShareEntry? {
        // Simplified parsing of ClientHello to get KeyShare (assuming it's the first extension for this util)
        // This is very basic and fragile, only for test.
        try {
            // Skip: type(1), len(3), legacy_ver(2), random(32), sess_id_len(1)+sess_id, cipher_suites_len(2)+suites, comp_meth_len(1)+meth
            var offset = 1 + 3 + 2 + 32
            val sessIdLen = clientHelloTlsPayload[offset++].toInt() and 0xFF
            offset += sessIdLen
            val csLen = ((clientHelloTlsPayload[offset].toInt() and 0xFF) shl 8) or (clientHelloTlsPayload[offset+1].toInt() and 0xFF)
            offset += 2 + csLen
            val compLen = clientHelloTlsPayload[offset++].toInt() and 0xFF
            offset += compLen

            val extsTotalLen = ((clientHelloTlsPayload[offset].toInt() and 0xFF) shl 8) or (clientHelloTlsPayload[offset+1].toInt() and 0xFF)
            offset += 2
            // Now at extensions. Find KeyShare.
            var extOffset = offset
            while(extOffset < offset + extsTotalLen) {
                val extType = ((clientHelloTlsPayload[extOffset].toInt() and 0xFF) shl 8) or (clientHelloTlsPayload[extOffset+1].toInt() and 0xFF)
                extOffset += 2
                val extLen = ((clientHelloTlsPayload[extOffset].toInt() and 0xFF) shl 8) or (clientHelloTlsPayload[extOffset+1].toInt() and 0xFF)
                extOffset += 2
                if (extType.toUShort() == TlsExtensionType.KEY_SHARE) {
                    // KeyShare ClientHello: list_len(2), group(2), key_len(2), key_data
                    val ksListLen = ((clientHelloTlsPayload[extOffset].toInt() and 0xFF) shl 8) or (clientHelloTlsPayload[extOffset+1].toInt() and 0xFF)
                    // Assuming one entry for simplicity
                    val group = ((clientHelloTlsPayload[extOffset+2].toInt() and 0xFF) shl 8) or (clientHelloTlsPayload[extOffset+3].toInt() and 0xFF)
                    val keyExLen = ((clientHelloTlsPayload[extOffset+4].toInt() and 0xFF) shl 8) or (clientHelloTlsPayload[extOffset+5].toInt() and 0xFF)
                    val keyEx = clientHelloTlsPayload.sliceArray(extOffset+6 until extOffset+6+keyExLen)
                    return KeyShareEntry(group.toUShort(), keyEx)
                }
                extOffset += extLen
            }
        } catch (e: Exception) { /* ignore, return null */ }
        return null
    }
}
internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
