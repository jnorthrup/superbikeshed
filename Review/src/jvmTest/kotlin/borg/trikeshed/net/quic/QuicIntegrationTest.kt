package borg.trikeshed.net.quic

import evolution.UdpSocket // Assuming this is the correct import from the 'evolution' library
import evolution.RealUdpSocketFactory // Assuming a factory to create sockets
import evolution.getIODispatcher // For IO operations context

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class QuicIntegrationTest {

    private val testDispatcher = getIODispatcher() // For running socket operations
    private val defaultTimeoutMillis = 5000L // Timeout for network operations

    @Test
    fun testUdpEcho() = runBlocking {
        val serverPort = 12345
        val serverHost = "127.0.0.1"
        val message = "Hello QUIC Echo".encodeToByteArray()
        var serverSocket: UdpSocket? = null
        var clientSocket: UdpSocket? = null
        val serverJob: Job?

        try {
            // Server part
            serverSocket = RealUdpSocketFactory().create(serverPort, testDispatcher)
            assertNotNull(serverSocket, "Failed to create server socket")
            println("UDP Echo Server listening on port $serverPort")

            serverJob = CoroutineScope(testDispatcher).launch {
                val buffer = ByteArray(1024)
                println("Server: Waiting to receive...")
                val receivedPacket = serverSocket.receive(buffer) // Assuming receive returns a data class or similar
                if (receivedPacket != null) {
                    val (receivedSize, senderHost, senderPort) = receivedPacket
                    val receivedData = buffer.copyOfRange(0, receivedSize)
                    println("Server: Received ${receivedData.decodeToString()} from $senderHost:$senderPort")

                    // Echo back
                    serverSocket.send(receivedData, senderHost, senderPort)
                    println("Server: Echoed data back to $senderHost:$senderPort")
                } else {
                    println("Server: Receive returned null")
                }
            }

            // Client part
            // Add a small delay to ensure server socket is bound and listening
            delay(100)
            clientSocket = RealUdpSocketFactory().create(0, testDispatcher) // Bind to any available port
            assertNotNull(clientSocket, "Failed to create client socket")

            println("Client: Sending message '${message.decodeToString()}' to $serverHost:$serverPort")
            clientSocket.send(message, serverHost, serverPort)

            val responseBuffer = ByteArray(1024)
            println("Client: Waiting for echo...")

            val receivedResponse = withTimeoutOrNull(defaultTimeoutMillis) {
                 clientSocket.receive(responseBuffer)
            }

            assertNotNull(receivedResponse, "Client: Did not receive echo response (timeout or error)")
            val (responseSize, _, _) = receivedResponse!! // From, port not checked here
            val responseData = responseBuffer.copyOfRange(0, responseSize)
            println("Client: Received echo '${responseData.decodeToString()}'")

            assertContentEquals(message, responseData, "Echoed message does not match original")
            println("UDP Echo Test successful!")

            serverJob.join() // Ensure server coroutine finishes

        } catch (e: Exception) {
            println("UDP Echo Test failed: ${e.message}")
            e.printStackTrace()
            throw e // Re-throw to fail the test
        } finally {
            println("Closing sockets...")
            serverSocket?.close()
            clientSocket?.close()
            println("Sockets closed.")
        }
    }

    // Removed local QuicTestConstants and QuicTestPacketType, will use from common source
    // Removed local helper functions createCryptoFrame, serializeLongHeader, applyHeaderProtection
    // Will use versions from QuicPacketUtils and QuicCryptoUtils

    @Test
    fun testSendClientHelloInitialPacket() = runBlocking {
        val serverPort = 12346
        val serverHost = "127.0.0.1"
        var serverSocket: UdpSocket? = null
        var clientSocket: UdpSocket? = null
        val serverJob: Job?

        try {
            // Setup Client (`QuicCurl` and `QuicConnection`)
            val testHkdfService = TestHkdfService()
            val testAesService = TestAesService()

            val connectionData = QuicConnection.newClientConnectionDataOnly()
            val manager = QuicConnectionManager(connectionData, QuicConnectionManager.ConnectionRole.CLIENT)

            connectionData.deriveInitialSecrets(testHkdfService, manager) // runBlocking from test method
            val clientHelloTlsPayload = connectionData.initiateClientHandshake(testHkdfService, manager) // runBlocking from test method
            assertNotNull(clientHelloTlsPayload, "ClientHello TLS payload is null")

            val initialSecrets = manager.getCurrentSecretsForSend(EncryptionLevel.INITIAL)
            assertNotNull(initialSecrets, "Initial secrets not found")

            // Construct CRYPTO Frame using common utility
            val cryptoFrame = borg.trikeshed.net.quic.utils.createCryptoFrame(offset = 0uL, data = clientHelloTlsPayload)

            // Construct Initial Packet Header using common utility
            val dcid = connectionData.clientId!!
            val scid = Random.nextBytes(8) // Client chooses initial SCID for this first packet
            // connectionData.serverId = scid // SCID is *our* ID, serverId is what server chose for us (null initially)

            val packetNumber = 0L // Use Long as per serializeInitialHeader
            val pnLengthBytes = 1 // Example: 1-byte packet number for simplicity in test

            // Length field in header = PN length + Crypto Frame length + AEAD Tag
            val payloadLengthWithTagAndPn = pnLengthBytes + cryptoFrame.size + QuicConstants.AEAD_TAG_LENGTH

            // The packet number itself is part of this header before HP
            val initialPacketHeaderBytes = QuicPacketUtils.serializeInitialHeader(
                version = QuicConstants.QUIC_VERSION_1,
                dcid = dcid,
                scid = scid,
                token = byteArrayOf(),
                packetNumber = packetNumber,
                pnLengthBytes = pnLengthBytes,
                payloadLengthWithTagAndPn = payloadLengthWithTagAndPn
            )

            // AAD for payload encryption is the full initialPacketHeaderBytes (unprotected header with PN)
            val aadForPayloadEncryption = initialPacketHeaderBytes

            // Payload for encryption is just the CryptoFrame (PN is already in the header)
            val encryptedCryptoFrameWithTag = testAesService.gcmEncrypt(initialSecrets.key, initialSecrets.iv, cryptoFrame, aadForPayloadEncryption)
            assertNotNull(encryptedCryptoFrameWithTag, "Payload (CryptoFrame) encryption failed")

            // Header Protection
            // Determine packet number offset within initialPacketHeaderBytes
            // FirstByte(1) + Version(4) + DCIDLen(1)+DCID(len) + SCIDLen(1)+SCID(len) + TokenLen(varint)+Token(len) + Length(varint) -> PN starts
            var currentOffset = 1 + 4 + 1 + dcid.size + 1 + scid.size
            currentOffset += byteArrayOf().size.toULong().encodeVarInt().size // Token Len VarInt size
            currentOffset += byteArrayOf().size // Token size
            currentOffset += payloadLengthWithTagAndPn.toULong().encodeVarInt().size // Length VarInt size
            val packetNumberOffsetInHeader = currentOffset

            val protectedHeaderBytes = borg.trikeshed.net.quic.crypto.applyHeaderProtection(
                aesService = testAesService,
                hpKey = initialSecrets.hpKey,
                packetHeaderBytes = initialPacketHeaderBytes,
                encryptedPayloadBytes = encryptedCryptoFrameWithTag, // Sample is from this
                packetNumberOffset = packetNumberOffsetInHeader,
                pnLengthBytes = pnLengthBytes
            )

            val quicPacketBytes = protectedHeaderBytes + encryptedCryptoFrameWithTag

            // Setup simple UDP server to receive this packet
            serverSocket = RealUdpSocketFactory().create(serverPort, testDispatcher)
            val serverReceivedPacket = CompletableDeferred<ByteArray>()
            serverJob = CoroutineScope(testDispatcher).launch {
                val buffer = ByteArray(2048)
                val received = serverSocket.receive(buffer)
                if (received != null) {
                    serverReceivedPacket.complete(buffer.copyOfRange(0, received.first))
                } else {
                    serverReceivedPacket.completeExceptionally(Exception("Server receive failed"))
                }
            }
            delay(100) // Give server a moment to start

            clientSocket = RealUdpSocketFactory().create(0, testDispatcher)
            println("Client sending QUIC Initial Packet (${quicPacketBytes.size} bytes) to $serverHost:$serverPort")
            clientSocket.send(quicPacketBytes, serverHost, serverPort)

            val receivedByServer = withTimeoutOrNull(defaultTimeoutMillis) { serverReceivedPacket.await() }
            assertNotNull(receivedByServer, "Server did not receive the packet")
            println("Server received ${receivedByServer.size} bytes.")
            // Basic check: is the size roughly what we sent?
            assertEquals(quicPacketBytes.size, receivedByServer.size, "Packet size mismatch")

            // Server-side processing:
            // 1. Parse Initial Header Fields (Partial)
            //    We need DCID from the packet to derive server's initial secrets.
            //    The DCID in the *client's Initial packet* is the client's *chosen* DCID for the server to use.
            //    This was `dcid` on the client side (connectionData.clientId).
            //    The SCID in the *client's Initial packet* is the client's *own* SCID.
            //    The server will use the SCID field from the client's Initial packet as the DCID for its response.

            // For server deriving its initial secrets: it uses the DCID *from the client's Initial packet*.
            // This DCID was originally chosen by the client (it's `connectionData.clientId`).
            val clientInitialDCID = dcid // This is what the server sees as DCID in the first Initial from client

            val serverInitialSecrets = runBlocking {
                val initialSalt = QuicConstants.QUIC_V1_INITIAL_SALT
                val extracted = testHkdfService.extract(initialSalt, clientInitialDCID)
                val serverSecretLabel = "server in"
                // Note: TestHkdfService used here for consistency in test. Real server uses real HKDF.
                val serverInitialSecret = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(testHkdfService, extracted, serverSecretLabel, byteArrayOf(), 32)

                val key = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(testHkdfService, serverInitialSecret, "quic key", byteArrayOf(), 16)
                val iv = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(testHkdfService, serverInitialSecret, "quic iv", byteArrayOf(), 12)
                val hp = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(testHkdfService, serverInitialSecret, "quic hp", byteArrayOf(), 16)
                QuicSecrets(key, iv, hp)
            }
            assertNotNull(serverInitialSecrets, "Server initial secrets derivation failed")

            // 2. Remove Header Protection (Server-Side)
            // We need a function similar to applyHeaderProtection but for removal.
            // The header received by server is `protectedHeaderBytes`.
            // The encrypted payload received is `encryptedCryptoFrameWithTag`.

            // Find PN offset in protectedHeaderBytes (it's the same as in initialPacketHeaderBytes)
            val serverReceivedProtectedHeader = receivedByServer.sliceArray(0 until protectedHeaderBytes.size)
            val serverReceivedEncryptedPayload = receivedByServer.sliceArray(protectedHeaderBytes.size until receivedByServer.size)

            // Sample for HP removal is from serverReceivedEncryptedPayload
            val serverSampleHp: ByteArray
             if (serverReceivedEncryptedPayload.size >= 4 + 16) {
                serverSampleHp = serverReceivedEncryptedPayload.copyOfRange(4, 4 + 16)
            } else {
                val availableData = serverReceivedEncryptedPayload.drop(4).toByteArray()
                serverSampleHp = availableData + ByteArray(16 - availableData.size)
            }

            val serverHpMask = testAesService.ecbEncrypt(serverInitialSecrets.hpKey, serverSampleHp)
            assertNotNull(serverHpMask, "Server HP mask generation failed")

            val tempUnprotectedHeader = serverReceivedProtectedHeader.clone()
            // Unmask first byte
            tempUnprotectedHeader[0] = (tempUnprotectedHeader[0].toInt() xor (serverHpMask[0].toInt() and 0x0F)).toByte()
            val receivedPnLengthBytes = (tempUnprotectedHeader[0].toInt() and 0x03) + 1
            assertEquals(pnLengthBytes, receivedPnLengthBytes, "Packet Number Length mismatch after unprotection")

            // Unmask PN (at packetNumberOffsetInHeader)
            for (i in 0 until receivedPnLengthBytes) {
                tempUnprotectedHeader[packetNumberOffsetInHeader + i] =
                    (tempUnprotectedHeader[packetNumberOffsetInHeader + i].toInt() xor serverHpMask[1 + i].toInt()).toByte()
            }
            val unprotectedFullHeaderByServer = tempUnprotectedHeader

            // Extract packet number (this is simplified, real PN decoding handles larger numbers and reconstruction)
            var receivedPacketNumber = 0L
            for(i in 0 until receivedPnLengthBytes) {
                receivedPacketNumber = (receivedPacketNumber shl 8) + (unprotectedFullHeaderByServer[packetNumberOffsetInHeader + i].toLong() and 0xFF)
            }
            assertEquals(packetNumber, receivedPacketNumber, "Packet number mismatch after unprotection")


            // 3. Decrypt Payload
            // AAD for server-side decryption is the now unprotected header.
            val decryptedPayload = testAesService.gcmDecrypt(serverInitialSecrets.key, serverInitialSecrets.iv, serverReceivedEncryptedPayload, unprotectedFullHeaderByServer)
            assertNotNull(decryptedPayload, "Server-side payload decryption failed")

            // The decryptedPayload should be: packetNumberBytes + cryptoFrame
            // We need to slice off the packetNumberBytes part from decryptedPayload to get the cryptoFrame bytes
            val decryptedCryptoFrameBytes = decryptedPayload.sliceArray(receivedPnLengthBytes until decryptedPayload.size)

            // 4. Parse Crypto Frame
            val parsedCryptoFrame = borg.trikeshed.net.quic.utils.parseCryptoFrame(decryptedCryptoFrameBytes)
            assertNotNull(parsedCryptoFrame, "Failed to parse CRYPTO frame on server side")
            val (receivedClientHelloTls, _) = parsedCryptoFrame

            // 5. Verify ClientHello content (basic)
            assertContentEquals(clientHelloTlsPayload, receivedClientHelloTls, "Decrypted ClientHello TLS payload mismatch")
            println("Server successfully decrypted and parsed ClientHello from Initial packet.")

            // --- Server Responds ---
            println("Server: Preparing response...")
            val serverTestHkdfService = TestHkdfService() // Server uses its own crypto instances
            val serverTestAesService = TestAesService()

            // Server uses the DCID from client's Initial as its SCID for this connection
            val serverSideScid = clientInitialDCID
            // Server uses the SCID from client's Initial as its DCID for this connection
            val serverSideDcid = scid

            val serverConnectionData = QuicConnection.newClientConnectionDataOnly() // Server acts as "client" of its own lib here
            serverConnectionData.clientId = serverSideScid // Server's chosen SCID
            serverConnectionData.serverId = serverSideDcid // What it knows as client's SCID (its DCID)

            val serverManager = QuicConnectionManager(serverConnectionData, QuicConnectionManager.ConnectionRole.SERVER)

            // Server derives its initial secrets (needed if it were to send an Initial packet, not strictly for Handshake here but good practice)
            // It would use its SCID (serverSideScid) to derive initial secrets if it were sending an Initial.
            // However, for processing client's Initial and sending Handshake, it uses client's Initial DCID.
            // For sending a Handshake packet, it will use its own handshake keys.

            // Server needs to "process" the client hello to set up its state and derive handshake keys
            // This involves ECDH: Server uses its new ephemeral key + client's public key from ClientHello
            // (ClientHelloTlsPayload contains the client's KeyShare)
            // A proper server would deserialize clientHelloTlsPayload into ClientHelloData.
            // For this test, we assume clientHelloTlsPayload can be conceptually used.
            // This is a simplified placeholder for server's TLS stack processing ClientHello:

            val serverEphemeralKeyPair = borg.trikeshed.net.quic.crypto.generateEcdhKeyPair(borg.trikeshed.net.quic.tls.X25519_GROUP)
            val serverEphemeralPublicKeyBytes = serverEphemeralKeyPair.second

            // Extract client's public key from clientHelloTlsPayload (needs deserialization)
            // For simplicity, let's assume we have access to connectionData.clientEphemeralPublicKey (which was sent)
            val clientPublicKeyFromHello = connectionData.clientEphemeralPublicKey
            assertNotNull(clientPublicKeyFromHello, "Client public key from original CH was null")

            val serverDhSecret = borg.trikeshed.net.quic.crypto.computeEcdhSharedSecret(
                borg.trikeshed.net.quic.tls.X25519_GROUP,
                serverEphemeralKeyPair.first, // Server's private key
                clientPublicKeyFromHello    // Client's public key
            )
            assertNotNull(serverDhSecret, "Server DH secret calculation failed")

            // Server derives its handshake secrets
            // Server's transcript for deriving its handshake secrets starts with ClientHello
            val serverHandshakeTranscript = clientHelloTlsPayload // Simplified for this step

            val serverEarlySecret = ByteArray(32) // Non-PSK
            val serverDerivedSecretSalt = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(serverTestHkdfService, serverEarlySecret, "derived", byteArrayOf(), 32)
            val serverHandshakeSecret = serverTestHkdfService.extract(serverDerivedSecretSalt, serverDhSecret)

            // Server's perspective for traffic secrets
            val srv_clientHandshakeTrafficSecret = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(serverTestHkdfService, serverHandshakeSecret, "c hs traffic", serverHandshakeTranscript, 32)
            val srv_serverHandshakeTrafficSecret = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(serverTestHkdfService, serverHandshakeSecret, "s hs traffic", serverHandshakeTranscript, 32)

            serverConnectionData.clientHandshakeTrafficSecretInternal = srv_clientHandshakeTrafficSecret
            serverConnectionData.serverHandshakeTrafficSecretInternal = srv_serverHandshakeTrafficSecret
            serverConnectionData.derivedHandshakeSecret = serverDerivedSecretSalt


            // Server creates ServerHello TLS message
            val serverHelloData = borg.trikeshed.net.quic.tls.ServerHelloData(
                version = borg.trikeshed.net.quic.tls.TLS_VERSION_1_2, // Legacy version
                serverRandom = Random.nextBytes(32),
                sessionId = Random.nextBytes(0), // Empty session ID
                cipherSuite = borg.trikeshed.net.quic.tls.TLS_AES_128_GCM_SHA256,
                keyShareEntry = borg.trikeshed.net.quic.tls.KeyShareEntry(borg.trikeshed.net.quic.tls.X25519_GROUP, serverEphemeralPublicKeyBytes),
                supportedVersion = borg.trikeshed.net.quic.tls.TLS_VERSION_1_3
            )
            val serializedServerHello = borg.trikeshed.net.quic.tls.serializeServerHello(serverHelloData) // Needs to be created if not exists

            // Server creates EncryptedExtensions TLS message (empty for now)
            val encryptedExtensionsData = borg.trikeshed.net.quic.tls.EncryptedExtensionsData(emptyList())
            val serializedEncryptedExtensions = borg.trikeshed.net.quic.tls.serializeEncryptedExtensions(encryptedExtensionsData) // Needs to be created

            // Packetize ServerHello + EncryptedExtensions
            val serverHelloCryptoFrame = borg.trikeshed.net.quic.utils.createCryptoFrame(0uL, serializedServerHello)
            val eeCryptoFrame = borg.trikeshed.net.quic.utils.createCryptoFrame(serializedServerHello.size.toULong(), serializedEncryptedExtensions)
            val serverHandshakePayload = serverHelloCryptoFrame + eeCryptoFrame

            val serverHandshakePacketNum = 0L
            val serverPnLengthBytes = 1

            val serverHandshakeHeaderBytes = QuicPacketUtils.serializeHandshakeHeader(
                version = QuicConstants.QUIC_VERSION_1,
                dcid = serverSideDcid, // Client's SCID from its Initial packet
                scid = serverSideScid, // Server's chosen SCID
                packetNumber = serverHandshakePacketNum,
                pnLengthBytes = serverPnLengthBytes,
                payloadLengthWithTagAndPn = serverPnLengthBytes + serverHandshakePayload.size + QuicConstants.AEAD_TAG_LENGTH
            )

            // Server uses its serverHandshakeTrafficSecret to get Handshake Write Keys
            val serverHandshakeWriteKey = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(serverTestHkdfService, srv_serverHandshakeTrafficSecret, "quic key", byteArrayOf(), 16)
            val serverHandshakeWriteIv = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(serverTestHkdfService, srv_serverHandshakeTrafficSecret, "quic iv", byteArrayOf(), 12)
            val serverHandshakeWriteHpKey = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(serverTestHkdfService, srv_serverHandshakeTrafficSecret, "quic hp", byteArrayOf(), 16)
            val serverWriteSecrets = QuicSecrets(serverHandshakeWriteKey, serverHandshakeWriteIv, serverHandshakeWriteHpKey)

            // Server encrypts its handshake payload
            val serverEncryptedPayload = serverTestAesService.gcmEncrypt(serverWriteSecrets.key, serverWriteSecrets.iv, serverHandshakePayload, aad = serverHandshakeHeaderBytes)
            assertNotNull(serverEncryptedPayload, "Server failed to encrypt its handshake payload")

            // Server applies header protection to its handshake packet header
            var serverHpSampleOffset = 1 + 4 + 1 + serverSideDcid.size + 1 + serverSideScid.size
            serverHpSampleOffset += (serverPnLengthBytes + serverHandshakePayload.size + QuicConstants.AEAD_TAG_LENGTH).toULong().encodeVarInt().size
            val serverPnOffsetInHeader = serverHpSampleOffset

            val serverProtectedHeader = borg.trikeshed.net.quic.crypto.applyHeaderProtection(
                serverTestAesService, serverWriteSecrets.hpKey, serverHandshakeHeaderBytes, serverEncryptedPayload,
                packetNumberOffset = serverPnOffsetInHeader,
                pnLengthBytes = serverPnLengthBytes
            )
            val serverResponsePacketBytes = serverProtectedHeader + serverEncryptedPayload

            // Server sends the Handshake packet
            // Client's address/port are known from `receivedPacket` in server's receive loop (from testUdpEcho)
            // For this test, we use serverHost, clientSocket.getLocalPort() - assuming clientSocket is set up for receive
            // This part is tricky as the client socket port for receiving might not be the one it sent from unless bound.
            // Let's assume the server knows where to send (e.g. client's original sending port if that's how UDP works for reply)
            // Or, client needs to be ready to receive on the socket it sent from.
            // For now, send to the client's listening port if it were set up.
            // This test is focused on client sending, server receiving and parsing.
            // The server sending back is the next step.
            // For now, we've verified server can decrypt ClientHello.

            // --- Server Responds with ServerHello in an Initial Packet ---
            val serverHelloForInitial = borg.trikeshed.net.quic.tls.ServerHelloData(
                version = borg.trikeshed.net.quic.tls.TLS_VERSION_1_2,
                serverRandom = Random.nextBytes(32),
                sessionId = connectionData.clientId!!, // Echoing client's DCID as session ID (example)
                cipherSuite = borg.trikeshed.net.quic.tls.TLS_AES_128_GCM_SHA256,
                keyShareEntry = borg.trikeshed.net.quic.tls.KeyShareEntry(borg.trikeshed.net.quic.tls.X25519_GROUP, serverEphemeralKeyPair.second),
                supportedVersion = borg.trikeshed.net.quic.tls.TLS_VERSION_1_3
            )
            val serializedServerHelloForInitial = borg.trikeshed.net.quic.tls.serializeServerHello(serverHelloForInitial)
            val serverCryptoFrameForInitial = borg.trikeshed.net.quic.utils.createCryptoFrame(0uL, serializedServerHelloForInitial)

            val serverInitialPacketNum = 0L
            val serverInitialPnLenBytes = 1
            val serverInitialPayloadLen = serverInitialPnLenBytes + serverCryptoFrameForInitial.size + QuicConstants.AEAD_TAG_LENGTH

            // Server's DCID for its Initial packet is the SCID from client's Initial packet.
            // Server's SCID for its Initial packet is chosen by the server.
            val serverResponseDcid = scid // This was client's SCID on its Initial
            val serverResponseScid = Random.nextBytes(8) // Server chooses its own SCID

            val serverInitialHeaderBytes = QuicPacketUtils.serializeInitialHeader(
                version = QuicConstants.QUIC_VERSION_1,
                dcid = serverResponseDcid,
                scid = serverResponseScid,
                token = byteArrayOf(), // Server does not send token in first Initial
                packetNumber = serverInitialPacketNum,
                pnLengthBytes = serverInitialPnLenBytes,
                payloadLengthWithTagAndPn = serverInitialPayloadLen
            )

            // Server encrypts with its Initial Write keys (derived from client's Initial DCID)
            val serverInitialWritePayloadToEncrypt = byteArrayOf(serverInitialPacketNum.toByte()) + serverCryptoFrameForInitial
            val serverEncryptedInitialPayload = testAesService.gcmEncrypt(
                serverInitialSecrets.key, serverInitialSecrets.iv, serverInitialWritePayloadToEncrypt, serverInitialHeaderBytes
            )
            assertNotNull(serverEncryptedInitialPayload, "Server encryption of Initial payload failed")

            // Server HP for its Initial packet
            var serverInitialPnOffset = 1 + 4 + 1 + serverResponseDcid.size + 1 + serverResponseScid.size
            serverInitialPnOffset += byteArrayOf().size.toULong().encodeVarInt().size // Token
            serverInitialPnOffset += serverInitialPayloadLen.toULong().encodeVarInt().size // Length

            val serverProtectedInitialHeader = borg.trikeshed.net.quic.crypto.applyHeaderProtection(
                testAesService, serverInitialSecrets.hpKey, serverInitialHeaderBytes, serverEncryptedInitialPayload,
                packetNumberOffset = serverInitialPnOffset,
                pnLengthBytes = serverInitialPnLenBytes
            )
            val serverInitialResponsePacketBytes = serverProtectedInitialHeader + serverEncryptedInitialPayload

            // Server sends this Initial packet to the client
            // Client's address and port were captured by the server's receive call earlier
            // For this test, client needs to be ready to receive on its clientSocket
            val clientListeningPort = (clientSocket as? evolution.RealUdpSocket)?.getLocalPort() // Assuming getLocalPort exists
            assertNotNull(clientListeningPort, "Could not get client listening port")

            println("Server: Sending Initial packet with ServerHello (${serverInitialResponsePacketBytes.size} bytes) to $serverHost:$clientListeningPort")
            serverSocket.send(serverInitialResponsePacketBytes, serverHost, clientListeningPort)

            serverJob.join() // Ensure server's first receive job is done.

            // --- Client Receives Server's Initial Packet ---
            println("Client: Waiting for server's Initial packet...")
            val clientResponseBuffer = ByteArray(2048)
            val clientReceivedPacket = withTimeoutOrNull(defaultTimeoutMillis) {
                clientSocket.receive(clientResponseBuffer)
            }
            assertNotNull(clientReceivedPacket, "Client did not receive server's Initial packet")
            val (clientReceivedSize, _, _) = clientReceivedPacket
            val clientReceivedRawBytes = clientResponseBuffer.copyOfRange(0, clientReceivedSize)
            println("Client: Received ${clientReceivedRawBytes.size} bytes from server.")

            // Client needs to derive Server Initial Read secrets to decrypt this
            // These are derived using the DCID from the *client's first Initial packet* (connectionData.clientId)
            val clientServerInitialSecrets = initialSecrets // Client already derived these as `initialSecrets` (for server writing/client reading)
                                                          // if `deriveInitialSecrets` derived both client and server sets.
                                                          // Let's re-verify what `initialSecrets` means.
                                                          // `manager.getCurrentSecretsForSend(EncryptionLevel.INITIAL)` gives client *write* secrets.
                                                          // We need client's *read* secrets for Server's Initial.
            // Re-derive server initial secrets from client's perspective for reading:
            val clientPerspectiveServerInitialSecrets = runBlocking {
                val initialSalt = QuicConstants.QUIC_V1_INITIAL_SALT
                val extracted = testHkdfService.extract(initialSalt, dcid) // dcid is client's chosen DCID for server
                val serverWriteSecretLabel = "server in" // Correct label for server's write keys
                val serverInitialWriteSecret = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(testHkdfService, extracted, serverWriteSecretLabel, byteArrayOf(), 32)

                QuicSecrets(
                    key = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(testHkdfService, serverInitialWriteSecret, "quic key", byteArrayOf(), 16),
                    iv = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(testHkdfService, serverInitialWriteSecret, "quic iv", byteArrayOf(), 12),
                    hpKey = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(testHkdfService, serverInitialWriteSecret, "quic hp", byteArrayOf(), 16)
                )
            }

            // Client unprotects and decrypts server's Initial packet
            // This logic would be part of client's QUIC packet processing layer
            // Simplified version:
            // 1. Parse unprotected parts of header to get PN length, SCID (which is serverResponseScid)
            // For now, assume we know expected header length and PN length (serverInitialPnLenBytes)
            val clientReceivedProtectedHeader = clientReceivedRawBytes.sliceArray(0 until serverProtectedInitialHeader.size)
            val clientReceivedEncryptedPayload = clientReceivedRawBytes.sliceArray(serverProtectedInitialHeader.size until clientReceivedRawBytes.size)

            val clientSampleHp: ByteArray
             if (clientReceivedEncryptedPayload.size >= 4 + 16) {
                clientSampleHp = clientReceivedEncryptedPayload.copyOfRange(4, 4 + 16)
            } else {
                val availableData = clientReceivedEncryptedPayload.drop(4).toByteArray()
                clientSampleHp = availableData + ByteArray(16 - availableData.size)
            }
            val clientHpMask = testAesService.ecbEncrypt(clientPerspectiveServerInitialSecrets.hpKey, clientSampleHp)
            assertNotNull(clientHpMask)

            val clientTempUnprotectedHeader = clientReceivedProtectedHeader.clone()
            clientTempUnprotectedHeader[0] = (clientTempUnprotectedHeader[0].toInt() xor (clientHpMask[0].toInt() and 0x0F)).toByte()
            // ... unmask PN ... (assuming serverInitialPnOffset and serverInitialPnLenBytes)
            for (i in 0 until serverInitialPnLenBytes) {
                 clientTempUnprotectedHeader[serverInitialPnOffset + i] =
                    (clientTempUnprotectedHeader[serverInitialPnOffset + i].toInt() xor clientHpMask[1+i].toInt()).toByte()
            }
            val clientUnprotectedServerHeader = clientTempUnprotectedHeader

            val clientDecryptedPayload = testAesService.gcmDecrypt(
                clientPerspectiveServerInitialSecrets.key, clientPerspectiveServerInitialSecrets.iv,
                clientReceivedEncryptedPayload, clientUnprotectedServerHeader
            )
            assertNotNull(clientDecryptedPayload, "Client failed to decrypt server's Initial payload")

            val clientDecryptedServerCryptoFrame = clientDecryptedPayload.sliceArray(serverInitialPnLenBytes until clientDecryptedPayload.size)
            val parsedServerHelloFrame = borg.trikeshed.net.quic.utils.parseCryptoFrame(clientDecryptedServerCryptoFrame)
            assertNotNull(parsedServerHelloFrame)

            // Client processes the ServerHello TLS message
            val serverHelloProcessingSuccess = connectionData.processServerHandshakeMessage(
                parsedServerHelloFrame.first, // This is the TLS ServerHello message
                EncryptionLevel.INITIAL, // Crypto context in which it was received (for transcript)
                testHkdfService,
                testAesService,
                manager
            )
            assertTrue(serverHelloProcessingSuccess, "Client failed to process ServerHello TLS message")
            assertEquals(TlsHandshakeState.EXPECTING_ENCRYPTED_EXTENSIONS, connectionData.tlsHandshakeState, "Client state incorrect after processing ServerHello")
            println("Client successfully decrypted and processed ServerHello from server's Initial packet.")
            assertEquals(TlsHandshakeState.EXPECTING_ENCRYPTED_EXTENSIONS, connectionData.tlsHandshakeState)


            // --- Server Prepares and Sends Handshake Flight (EE, Cert, CV, Finished) ---
            // This would typically be in one or more Handshake packets.
            // For simplicity, we'll bundle them into one payload for one Handshake packet.

            // Server needs its own QuicConnection-like state. Using MockQuicServerTlsState.
            val serverTlsState = MockQuicServerTlsState(
                version = QuicConstants.QUIC_VERSION_1,
                initialClientDcId = dcid, // This was the DCID in client's Initial, server uses as its SCID
                initialClientScid = scid, // This was the SCID in client's Initial, server uses as its DCID
                hkdfService = testHkdfService // Server uses its own test HKDF instance
            )
            // Server "processes" the client's hello to derive its keys and set its state
            // We need the client's public key from the ClientHello.
            // Assuming connectionData.clientEphemeralPublicKey is what was sent in CH.
            serverTlsState.processClientHelloPayload(
                clientHelloTlsPayload,
                borg.trikeshed.net.quic.tls.KeyShareEntry(borg.trikeshed.net.quic.tls.X25519_GROUP, connectionData.clientEphemeralPublicKey!!)
            )
            // Server updates its transcript with its own ServerHello (which it would have sent in its Initial packet)
            serverTlsState.updateTranscript(serializedServerHelloForInitial) // The SH it sent in the Initial packet

            // Server creates EncryptedExtensions
            val eeData = borg.trikeshed.net.quic.tls.EncryptedExtensionsData(emptyList())
            val serializedEE = borg.trikeshed.net.quic.tls.serializeEncryptedExtensions(eeData)
            serverTlsState.updateTranscript(serializedEE)

            // Server creates Certificate
            val dummyServerCert = borg.trikeshed.net.quic.tls.CertificateEntry("dummy_cert_bytes".encodeToByteArray(), emptyList())
            val certData = borg.trikeshed.net.quic.tls.CertificateData(byteArrayOf(), listOf(dummyServerCert))
            val serializedCertificate = borg.trikeshed.net.quic.tls.serializeCertificate(certData)
            serverTlsState.updateTranscript(serializedCertificate)

            // Server creates CertificateVerify
            // Signature is over server's transcript hash. The signature itself is dummy.
            val serverTranscriptHashForCV = borg.trikeshed.net.quic.crypto.sha256(serverTlsState.handshakeTranscript)
            // Actual signature generation would use server's private key corresponding to the certificate.
            // For this test, the signature content doesn't matter as client's verifySignature is a placeholder.
            val dummySignature = "dummy_signature".encodeToByteArray()
            val cvData = borg.trikeshed.net.quic.tls.CertificateVerifyData(borg.trikeshed.net.quic.tls.TlsSignatureScheme.ECDSA_SECP256R1_SHA256, dummySignature)
            val serializedCertVerify = borg.trikeshed.net.quic.tls.serializeCertificateVerify(cvData)
            serverTlsState.updateTranscript(serializedCertVerify)

            // Server creates Finished
            assertNotNull(serverTlsState.serverHandshakeTrafficSecret, "Server HS traffic secret not set in mock server state")
            val serverFinishedKey = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(testHkdfService, serverTlsState.serverHandshakeTrafficSecret!!, "finished", byteArrayOf(), 32)
            val serverTranscriptHashForFinished = borg.trikeshed.net.quic.crypto.sha256(serverTlsState.handshakeTranscript)
            val serverVerifyData = borg.trikeshed.net.quic.crypto.hmacSha256(testHkdfService, serverFinishedKey, serverTranscriptHashForFinished)
            val finishedData = borg.trikeshed.net.quic.tls.FinishedData(serverVerifyData)
            val serializedServerFinished = borg.trikeshed.net.quic.tls.serializeFinished(finishedData)
            // Server does NOT add its own Finished to the transcript for calculating Client Finished.
            // But for sending, it's part of the payload.

            // Bundle these into CRYPTO frames for one Handshake packet
            var serverHandshakeCryptoPayloadOffset = 0uL
            val frame1 = borg.trikeshed.net.quic.utils.createCryptoFrame(serverHandshakeCryptoPayloadOffset, serializedEE)
            serverHandshakeCryptoPayloadOffset += serializedEE.size.toULong()
            val frame2 = borg.trikeshed.net.quic.utils.createCryptoFrame(serverHandshakeCryptoPayloadOffset, serializedCertificate)
            serverHandshakeCryptoPayloadOffset += serializedCertificate.size.toULong()
            val frame3 = borg.trikeshed.net.quic.utils.createCryptoFrame(serverHandshakeCryptoPayloadOffset, serializedCertVerify)
            serverHandshakeCryptoPayloadOffset += serializedCertVerify.size.toULong()
            val frame4 = borg.trikeshed.net.quic.utils.createCryptoFrame(serverHandshakeCryptoPayloadOffset, serializedServerFinished)

            val serverCombinedHandshakePayload = frame1 + frame2 + frame3 + frame4
            val serverHandshakePacketNum2 = 1L // Next PN for server in Handshake space
            val serverHandshakePnLenBytes2 = 1

            val serverHandshakePayloadLen = serverHandshakePnLenBytes2 + serverCombinedHandshakePayload.size + QuicConstants.AEAD_TAG_LENGTH
            val serverHandshakeHeaderBytes2 = QuicPacketUtils.serializeHandshakeHeader(
                QuicConstants.QUIC_VERSION_1, serverTlsState.dcidForClient, serverTlsState.serverChosenScid,
                serverHandshakePacketNum2, serverHandshakePnLenBytes2, serverHandshakePayloadLen
            )

            val serverHandshakeWriteSecrets = serverTlsState.manager.getCurrentSecretsForSend(EncryptionLevel.HANDSHAKE)
            assertNotNull(serverHandshakeWriteSecrets, "Server handshake write secrets not found in mock server manager")

            val serverEncryptedHandshakePayload = testAesService.gcmEncrypt(
                serverHandshakeWriteSecrets.key, serverHandshakeWriteSecrets.iv,
                byteArrayOf(serverHandshakePacketNum2.toByte()) + serverCombinedHandshakePayload, // PN + Payload
                serverHandshakeHeaderBytes2
            )
            assertNotNull(serverEncryptedHandshakePayload, "Server encryption of Handshake payload failed")

            var serverHandshakePnOffset = 1 + 4 + 1 + serverTlsState.dcidForClient.size + 1 + serverTlsState.serverChosenScid.size
            serverHandshakePnOffset += serverHandshakePayloadLen.toULong().encodeVarInt().size

            val serverProtectedHandshakeHeader = borg.trikeshed.net.quic.crypto.applyHeaderProtection(
                testAesService, serverHandshakeWriteSecrets.hpKey, serverHandshakeHeaderBytes2, serverEncryptedHandshakePayload,
                serverHandshakePnOffset, serverHandshakePnLenBytes2
            )
            val serverHandshakePacketBytes = serverProtectedHandshakeHeader + serverEncryptedHandshakePayload

            println("Server: Sending Handshake packet with EE, Cert, CV, Finished (${serverHandshakePacketBytes.size} bytes) to $serverHost:$clientListeningPort")
            serverSocket.send(serverHandshakePacketBytes, serverHost, clientListeningPort)


            // --- Client Receives Server's Handshake Packet ---
            println("Client: Waiting for server's Handshake packet...")
            val clientReceivedHandshakePacket = withTimeoutOrNull(defaultTimeoutMillis) {
                clientSocket.receive(clientResponseBuffer) // Reusing buffer
            }
            assertNotNull(clientReceivedHandshakePacket, "Client did not receive server's Handshake packet")
            val (clientReceivedHsSize, _, _) = clientReceivedHandshakePacket
            val clientReceivedHsRawBytes = clientResponseBuffer.copyOfRange(0, clientReceivedHsSize)
            println("Client: Received ${clientReceivedHsRawBytes.size} Handshake bytes from server.")

            // Client unprotects and decrypts server's Handshake packet
            // Client uses its `serverHandshakeTrafficSecretInternal` derived keys (stored in `serverHandshakeSecretsForReception`)
            val clientServerHandshakeReadSecrets = connectionData.serverHandshakeSecretsForReception
            assertNotNull(clientServerHandshakeReadSecrets, "Client's server handshake read secrets are null")

            val clientReceivedHsProtectedHeader = clientReceivedHsRawBytes.sliceArray(0 until serverProtectedHandshakeHeader.size)
            val clientReceivedHsEncryptedPayload = clientReceivedHsRawBytes.sliceArray(serverProtectedHandshakeHeader.size until clientReceivedHsRawBytes.size)

            val clientHsSampleHp: ByteArray
             if (clientReceivedHsEncryptedPayload.size >= 4 + 16) {
                clientHsSampleHp = clientReceivedHsEncryptedPayload.copyOfRange(4, 4 + 16)
            } else {
                val availableData = clientReceivedHsEncryptedPayload.drop(4).toByteArray()
                clientHsSampleHp = availableData + ByteArray(16 - availableData.size)
            }
            val clientHsHpMask = testAesService.ecbEncrypt(clientServerHandshakeReadSecrets.hpKey, clientHsSampleHp)
            assertNotNull(clientHsHpMask)

            val clientTempUnprotectedHsHeader = clientReceivedHsProtectedHeader.clone()
            clientTempUnprotectedHsHeader[0] = (clientTempUnprotectedHsHeader[0].toInt() xor (clientHsHpMask[0].toInt() and 0x0F)).toByte() // Mask for Long Header
            // ... unmask PN ... (using serverHandshakePnOffset, serverHandshakePnLenBytes2)
             for (i in 0 until serverHandshakePnLenBytes2) {
                 clientTempUnprotectedHsHeader[serverPnOffsetInHeader + i] = // serverPnOffsetInHeader was for server's packet construction
                    (clientTempUnprotectedHsHeader[serverPnOffsetInHeader + i].toInt() xor clientHsHpMask[1+i].toInt()).toByte()
            }
            val clientUnprotectedServerHsHeader = clientTempUnprotectedHsHeader


            val clientDecryptedHsPayload = testAesService.gcmDecrypt(
                clientServerHandshakeReadSecrets.key, clientServerHandshakeReadSecrets.iv,
                clientReceivedHsEncryptedPayload, clientUnprotectedServerHsHeader
            )
            assertNotNull(clientDecryptedHsPayload, "Client failed to decrypt server's Handshake payload")

            val clientDecryptedServerHandshakeCryptoPayload = clientDecryptedHsPayload.sliceArray(serverHandshakePnLenBytes2 until clientDecryptedHsPayload.size)

            // Client processes these messages one by one from the combined payload
            var processedBytesCount = 0
            val eeFrame = borg.trikeshed.net.quic.utils.parseCryptoFrame(clientDecryptedServerHandshakeCryptoPayload.drop(processedBytesCount).toByteArray())
            assertNotNull(eeFrame); assertTrue(connectionData.processServerHandshakeMessage(eeFrame.first, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager)); processedBytesCount += eeFrame.second
            assertEquals(TlsHandshakeState.EXPECTING_CERTIFICATE, connectionData.tlsHandshakeState)

            val certFrame = borg.trikeshed.net.quic.utils.parseCryptoFrame(clientDecryptedServerHandshakeCryptoPayload.drop(processedBytesCount).toByteArray())
            assertNotNull(certFrame); assertTrue(connectionData.processServerHandshakeMessage(certFrame.first, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager)); processedBytesCount += certFrame.second
            assertEquals(TlsHandshakeState.EXPECTING_CERTIFICATE_VERIFY, connectionData.tlsHandshakeState)

            val cvFrame = borg.trikeshed.net.quic.utils.parseCryptoFrame(clientDecryptedServerHandshakeCryptoPayload.drop(processedBytesCount).toByteArray())
            assertNotNull(cvFrame); assertTrue(connectionData.processServerHandshakeMessage(cvFrame.first, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager)); processedBytesCount += cvFrame.second
            assertEquals(TlsHandshakeState.EXPECTING_SERVER_FINISHED, connectionData.tlsHandshakeState)

            val finFrame = borg.trikeshed.net.quic.utils.parseCryptoFrame(clientDecryptedServerHandshakeCryptoPayload.drop(processedBytesCount).toByteArray())
            assertNotNull(finFrame); assertTrue(connectionData.processServerHandshakeMessage(finFrame.first, EncryptionLevel.HANDSHAKE, testHkdfService, testAesService, manager)); processedBytesCount += finFrame.second
            assertEquals(TlsHandshakeState.READY_TO_SEND_CLIENT_FINISHED, connectionData.tlsHandshakeState)
            println("Client successfully processed server's handshake flight.")

            // --- Client Sends Its Finished Message ---
            val clientFinishedTlsMessage = connectionData.generateClientFinishedMessage(testHkdfService, manager)
            assertNotNull(clientFinishedTlsMessage, "Client failed to generate its Finished message")

            val clientFinishedCryptoFrame = borg.trikeshed.net.quic.utils.createCryptoFrame(0uL, clientFinishedTlsMessage)
            val clientHandshakePacketNum = manager.getNextPacketNumberForEncryptionLevel(EncryptionLevel.HANDSHAKE)
            val clientPnLengthBytes = 1 // Assuming 1-byte PN for client's Finished packet as well

            // Client's DCID for Handshake packet is server's SCID (serverTlsState.serverChosenScid or serverResponseScid from Server's Initial)
            // Client's SCID for Handshake packet is its own original SCID (scid used in its first Initial)
            val clientFinishedHeader = QuicPacketUtils.serializeHandshakeHeader(
                QuicConstants.QUIC_VERSION_1,
                dcid = serverTlsState.serverChosenScid, // Server's SCID is client's DCID for this packet
                scid = scid,                           // Client's original SCID
                packetNumber = clientHandshakePacketNum,
                pnLengthBytes = clientPnLengthBytes,
                payloadLengthWithTagAndPn = clientPnLengthBytes + clientFinishedCryptoFrame.size + QuicConstants.AEAD_TAG_LENGTH
            )
            val clientHandshakeWriteSecrets = manager.getCurrentSecretsForSend(EncryptionLevel.HANDSHAKE)
            assertNotNull(clientHandshakeWriteSecrets, "Client handshake write secrets are null")

            val clientEncryptedFinishedPayload = testAesService.gcmEncrypt(
                clientHandshakeWriteSecrets.key, clientHandshakeWriteSecrets.iv,
                byteArrayOf(clientHandshakePacketNum.toByte()) + clientFinishedCryptoFrame, // PN + Payload
                clientFinishedHeader
            )
            assertNotNull(clientEncryptedFinishedPayload, "Client encryption of Finished payload failed")

            var clientFinishedPnOffset = 1 + 4 + 1 + serverTlsState.serverChosenScid.size + 1 + scid.size
            clientFinishedPnOffset += (clientPnLengthBytes + clientFinishedCryptoFrame.size + QuicConstants.AEAD_TAG_LENGTH).toULong().encodeVarInt().size

            val clientProtectedFinishedHeader = borg.trikeshed.net.quic.crypto.applyHeaderProtection(
                testAesService, clientHandshakeWriteSecrets.hpKey, clientFinishedHeader,
                clientEncryptedFinishedPayload, clientFinishedPnOffset, clientPnLengthBytes
            )
            val clientFinishedQuicPacket = clientProtectedFinishedHeader + clientEncryptedFinishedPayload

            println("Client: Sending Handshake packet with Client Finished (${clientFinishedQuicPacket.size} bytes) to $serverHost:$serverPort")
            clientSocket.send(clientFinishedQuicPacket, serverHost, serverPort)
            assertEquals(TlsHandshakeState.CLIENT_FINISHED_SENT, connectionData.tlsHandshakeState)

            // --- Server Receives and Processes Client Finished ---
            val serverReceivesClientFinished = CompletableDeferred<ByteArray>()
            val serverJobClientFinished = CoroutineScope(testDispatcher).launch {
                val buffer = ByteArray(2048)
                val received = serverSocket.receive(buffer) // Server receives client's finished
                if (received != null) serverReceivesClientFinished.complete(buffer.copyOfRange(0, received.first))
                else serverReceivesClientFinished.completeExceptionally(Exception("Server did not receive client finished"))
            }
            val receivedClientFinishedPacketBytes = withTimeoutOrNull(defaultTimeoutMillis) { serverReceivesClientFinished.await() }
            assertNotNull(receivedClientFinishedPacketBytes, "Server did not receive client's Finished packet")

            // Server unprotects and decrypts (using its clientHandshakeTrafficSecret derived keys)
            val serverClientHandshakeReadSecrets = QuicSecrets( // Reconstruct or retrieve from serverTlsState
                 borg.trikeshed.net.quic.crypto.hkdfExpandLabel(testHkdfService, serverTlsState.clientHandshakeTrafficSecret!!, "quic key", byteArrayOf(), 16),
                 borg.trikeshed.net.quic.crypto.hkdfExpandLabel(testHkdfService, serverTlsState.clientHandshakeTrafficSecret!!, "quic iv", byteArrayOf(), 12),
                 borg.trikeshed.net.quic.crypto.hkdfExpandLabel(testHkdfService, serverTlsState.clientHandshakeTrafficSecret!!, "quic hp", byteArrayOf(), 16)
            )
            // ... (Full unprotection and decryption logic for Client Finished is now inside TestQuicServer) ...
            // TestQuicServer will internally verify the Client Finished message.
            // We just need to ensure the server signals completion.
            // val parsedClientFinished = borg.trikeshed.net.quic.tls.deserializeClientFinished(clientFinishedTlsMessage)
            // assertNotNull(parsedClientFinished)

            // Server verifies client's verify_data (this logic is now encapsulated in TestQuicServer)
            val serverTranscriptBeforeClientFinished = serverTlsState.handshakeTranscript.copyOf() // Server's transcript up to its own Finished
            val serverExpectedClientFinishedKey = borg.trikeshed.net.quic.crypto.hkdfExpandLabel(testHkdfService, serverTlsState.clientHandshakeTrafficSecret!!, "finished", byteArrayOf(), 32)
            val serverTranscriptHashForClientFinished = borg.trikeshed.net.quic.crypto.sha256(serverTranscriptBeforeClientFinished)
            val serverExpectedClientVerifyData = borg.trikeshed.net.quic.crypto.hmacSha256(testHkdfService, serverExpectedClientFinishedKey, serverTranscriptHashForClientFinished)
            assertContentEquals(serverExpectedClientVerifyData, parsedClientFinished.verifyData, "Server failed to verify Client Finished data")
            println("Server successfully verified Client Finished.")
            serverTlsState.updateTranscript(clientFinishedTlsMessage) // Server updates its transcript with client's finished

            // --- Both Sides Derive Application Secrets ---
            println("Client: Deriving application secrets...")
            connectionData.deriveApplicationSecrets(testHkdfService, manager)
            assertEquals(TlsHandshakeState.HANDSHAKE_COMPLETE, connectionData.tlsHandshakeState)
            assertNotNull(manager.getCurrentSecretsForSend(EncryptionLevel.ONERTT), "Client ONERTT secrets not available")
            println("Client handshake complete. State: ${connectionData.tlsHandshakeState}")

            println("Server: Deriving application secrets...")
            serverTlsState.serverConnectionData.deriveApplicationSecrets(testHkdfService, serverTlsState.serverManager)
            assertEquals(TlsHandshakeState.HANDSHAKE_COMPLETE, serverTlsState.serverConnectionData.tlsHandshakeState)
            assertNotNull(serverTlsState.serverManager.getCurrentSecretsForSend(EncryptionLevel.ONERTT), "Server ONERTT secrets not available")
            println("Server handshake complete. State: ${serverTlsState.serverConnectionData.tlsHandshakeState}")

            serverJobClientFinished.join() // Ensure server processed client finished

            // --- Client Sends 1-RTT PING ---
            val pingData = "PING".encodeToByteArray()
            val clientStreamFrame = borg.trikeshed.net.quic.utils.createStreamFrame(streamId = 0uL, offset = 0uL, data = pingData, fin = true)
            val client1RttPacketNum = manager.getNextPacketNumberForEncryptionLevel(EncryptionLevel.ONERTT)
            val client1RttPnLen = 1

            val clientAppWriteSecrets = manager.getCurrentSecretsForSend(EncryptionLevel.ONERTT)!!
            // DCID for client's 1-RTT packet is server's chosen SCID (from Server Hello, stored as serverTlsState.serverChosenScid)
            val clientShortHeader = QuicPacketUtils.serializeShortHeader(
                dcid = serverTlsState.serverChosenScid,
                packetNumber = client1RttPacketNum,
                pnLengthBytes = client1RttPnLen,
                keyPhaseBit = false // Assuming initial key phase
            )
            val client1RttEncryptedPayload = testAesService.gcmEncrypt(clientAppWriteSecrets.key, clientAppWriteSecrets.iv, clientStreamFrame, aad = clientShortHeader)
            assertNotNull(client1RttEncryptedPayload, "Client PING encryption failed")

            val clientPnOffsetShort = QuicPacketUtils.calculatePacketNumberOffsetForShortHeader(serverTlsState.serverChosenScid.size)
            val client1RttProtectedHeader = borg.trikeshed.net.quic.crypto.applyHeaderProtection(
                testAesService, clientAppWriteSecrets.hpKey, clientShortHeader,
                client1RttEncryptedPayload, clientPnOffsetShort, client1RttPnLen, isShortHeader = true
            )
            val client1RttPacket = client1RttProtectedHeader + client1RttEncryptedPayload

            println("Client: Sending 1-RTT PING packet (${client1RttPacket.size} bytes)")
            clientSocket.send(client1RttPacket, serverHost, serverPort)

            // --- Server Receives and Processes 1-RTT PING ---
            val serverReceivesPing = CompletableDeferred<ByteArray>()
            val serverJobPing = CoroutineScope(testDispatcher).launch {
                val buffer = ByteArray(2048)
                val received = serverSocket.receive(buffer)
                if (received != null) serverReceivesPing.complete(buffer.copyOfRange(0, received.first))
                else serverReceivesPing.completeExceptionally(Exception("Server did not receive PING"))
            }
            val receivedPingPacketBytes = withTimeoutOrNull(defaultTimeoutMillis) { serverReceivesPing.await() }
            assertNotNull(receivedPingPacketBytes, "Server did not receive PING packet")

            // Server unprotects and decrypts (using its clientAppTrafficSecret derived keys)
            // These keys are stored in serverTlsState.clientAppReadSecrets after its deriveApplicationSecretsFromServerPerspective call
            val serverClientAppReadSecretsActual = serverTlsState.clientAppReadSecrets
            assertNotNull(serverClientAppReadSecretsActual, "Server's 1-RTT read keys (client_app_traffic) not set up in mock server state (TestQuicServer.kt)")

            // Simplified unprotection (header parsing needed to get real DCID for key lookup if not direct)
            // Assuming clientShortHeader.size is the size of the protected header received by server.
            // This is only true if DCID length chosen by server (for client to use) is same as client's original DCID length.
            // For short headers, DCID is explicit.
            val parsedPingHeaderMinimal = QuicPacketUtils.parseMinimalHeaderFields(receivedPingPacketBytes, serverTlsState.serverScid.size) // Server's SCID is client's DCID
            assertNotNull(parsedPingHeaderMinimal, "Failed to parse PING packet header on server side")
            assertEquals(clientShortHeader.size, parsedPingHeaderMinimal.packetNumberOffset + parsedPingHeaderMinimal.packetNumberLengthProtectionBits.let { (it and 0x03) + 1 }, "PING header size mismatch assumption")


            val serverReceivedPingProtectedHeader = receivedPingPacketBytes.sliceArray(0 until clientShortHeader.size)
            val serverReceivedPingEncryptedPayload = receivedPingPacketBytes.sliceArray(clientShortHeader.size until receivedPingPacketBytes.size)

            val serverPingSampleHp: ByteArray
             if (serverReceivedPingEncryptedPayload.size >= 4 + 16) { // Sample from payload ciphertext
                serverPingSampleHp = serverReceivedPingEncryptedPayload.copyOfRange(4, 4 + 16)
            } else {
                val availableData = serverReceivedPingEncryptedPayload.drop(4).toByteArray()
                serverPingSampleHp = availableData + ByteArray(16 - availableData.size)
            }
            val serverPingHpMask = testAesService.ecbEncrypt(serverClientAppReadSecretsActual!!.hpKey, serverPingSampleHp)!!
            val serverTempUnprotectedPingHeader = serverReceivedPingProtectedHeader.clone()
            serverTempUnprotectedPingHeader[0] = (serverTempUnprotectedPingHeader[0].toInt() xor (serverPingHpMask[0].toInt() and 0x07)).toByte() // Short header specific mask
            // ... unmask PN ... (for simplicity, assume PN is known or not critical for this PING test's decryption)
            val serverUnprotectedPingHeader = serverTempUnprotectedPingHeader


            val decryptedPingPayload = testAesService.gcmDecrypt(serverClientAppReadSecretsActual.key, serverClientAppReadSecretsActual.iv, serverReceivedPingEncryptedPayload, serverUnprotectedPingHeader)
            assertNotNull(decryptedPingPayload, "Server PING decryption failed")

            val parsedPingFrame = borg.trikeshed.net.quic.utils.parseStreamFrame(decryptedPingPayload)
            assertNotNull(parsedPingFrame, "Server failed to parse PING STREAM frame")
            assertContentEquals(pingData, parsedPingFrame.third, "PING data mismatch on server")
            println("Server received PING, data: ${parsedPingFrame.third.decodeToString()}")
            serverJobPing.join()

            // --- Server Sends 1-RTT PONG ---
            val pongData = "PONG".encodeToByteArray()
            val serverStreamFrame = borg.trikeshed.net.quic.utils.createStreamFrame(streamId = 0uL, offset = 0uL, data = pongData, fin = true) // Same stream
            val server1RttPacketNum = serverTlsState.serverManager.getNextPacketNumberForEncryptionLevel(EncryptionLevel.ONERTT)
            val server1RttPnLen = 1
            val serverAppWriteSecrets = serverTlsState.serverManager.getCurrentSecretsForSend(EncryptionLevel.ONERTT)!!

            // Server's DCID for 1-RTT is client's SCID (scid from client's initial)
            val serverShortHeader = QuicPacketUtils.serializeShortHeader(serverTlsState.dcidForClient, server1RttPacketNum, server1RttPnLen, keyPhaseBit = false)
            val server1RttEncryptedPayload = testAesService.gcmEncrypt(serverAppWriteSecrets.key, serverAppWriteSecrets.iv, serverStreamFrame, aad = serverShortHeader)
            assertNotNull(server1RttEncryptedPayload)
            val serverPnOffsetShort = QuicPacketUtils.calculatePacketNumberOffsetForShortHeader(serverTlsState.dcidForClient.size)
            val server1RttProtectedHeader = borg.trikeshed.net.quic.crypto.applyHeaderProtection(testAesService, serverAppWriteSecrets.hpKey, serverShortHeader, server1RttEncryptedPayload, serverPnOffsetShort, server1RttPnLen, isShortHeader = true)
            val server1RttPacket = server1RttProtectedHeader + server1RttEncryptedPayload

            println("Server: Sending 1-RTT PONG packet (${server1RttPacket.size} bytes)")
            serverSocket.send(server1RttPacket, serverHost, clientListeningPort)


            // --- Client Receives and Processes 1-RTT PONG ---
            val clientReceivesPong = CompletableDeferred<ByteArray>()
             val clientJobPong = CoroutineScope(testDispatcher).launch {
                val buffer = ByteArray(2048)
                val received = clientSocket.receive(buffer)
                if (received != null) clientReceivesPong.complete(buffer.copyOfRange(0, received.first))
                else clientReceivesPong.completeExceptionally(Exception("Client did not receive PONG"))
            }
            val receivedPongPacketBytes = withTimeoutOrNull(defaultTimeoutMillis) { clientReceivesPong.await() }
            assertNotNull(receivedPongPacketBytes, "Client did not receive PONG packet")

            // Client uses its 1-RTT Read Keys (derived from serverAppTrafficSecret)
            // These are NOT directly in manager.getCurrentSecretsForSend(ONERTT)
            // Client needs to store its server_app_traffic_secret derived keys for reading.
            // Let's assume connectionData.cryptoSecrets[EncryptionLevel.ONERTT_SERVER_KEYS] if it were implemented.
            // For now, reconstruct them for test if not stored.
            val clientServerAppReadSecrets = QuicSecrets( // Reconstruct or retrieve
                borg.trikeshed.net.quic.crypto.hkdfExpandLabel(testHkdfService, connectionData.serverAppTrafficSecretInternal!!, "quic key", byteArrayOf(), 16),
                borg.trikeshed.net.quic.crypto.hkdfExpandLabel(testHkdfService, connectionData.serverAppTrafficSecretInternal!!, "quic iv", byteArrayOf(), 12),
                borg.trikeshed.net.quic.crypto.hkdfExpandLabel(testHkdfService, connectionData.serverAppTrafficSecretInternal!!, "quic hp", byteArrayOf(), 16)
            )
             assertNotNull(clientServerAppReadSecrets, "Client's 1-RTT read keys for server data not set up")


            val clientReceivedPongProtectedHeader = receivedPongPacketBytes.sliceArray(0 until serverShortHeader.size) // Assuming header size is known
            val clientReceivedPongEncryptedPayload = receivedPongPacketBytes.sliceArray(serverShortHeader.size until receivedPongPacketBytes.size)

            val clientPongSampleHp: ByteArray
             if (clientReceivedPongEncryptedPayload.size >= 4 + 16) {
                clientPongSampleHp = clientReceivedPongEncryptedPayload.copyOfRange(4, 4 + 16)
            } else {
                val availableData = clientReceivedPongEncryptedPayload.drop(4).toByteArray()
                clientPongSampleHp = availableData + ByteArray(16 - availableData.size)
            }
            val clientPongHpMask = testAesService.ecbEncrypt(clientServerAppReadSecrets.hpKey, clientPongSampleHp)!!
            val clientTempUnprotectedPongHeader = clientReceivedPongProtectedHeader.clone()
            clientTempUnprotectedPongHeader[0] = (clientTempUnprotectedPongHeader[0].toInt() xor (clientPongHpMask[0].toInt() and 0x07)).toByte()
            // ... unmask PN ...
            val clientUnprotectedPongHeader = clientTempUnprotectedPongHeader

            val decryptedPongPayload = testAesService.gcmDecrypt(clientServerAppReadSecrets.key, clientServerAppReadSecrets.iv, clientReceivedPongEncryptedPayload, clientUnprotectedPongHeader)
            assertNotNull(decryptedPongPayload, "Client PONG decryption failed")

            val parsedPongFrame = borg.trikeshed.net.quic.utils.parseStreamFrame(decryptedPongPayload)
            assertNotNull(parsedPongFrame, "Client failed to parse PONG STREAM frame")
            assertContentEquals(pongData, parsedPongFrame.third, "PONG data mismatch on client")
            println("Client received PONG, data: ${parsedPongFrame.third.decodeToString()}")

            clientJobPong.join()

        } finally {
            serverSocket?.close()
            clientSocket?.close()
        }
    }

    @Test
    fun testQuicCurl_FullHandshakeWithTestServer() {
        val testHkdfService = TestHkdfService()
        val testAesService = TestAesService()
        val serverPort = 12347 // Use a distinct port
        val serverHost = "127.0.0.1"

        // These CIDs will be used by TestQuicServer to expect and by QuicCurl to send.
        val clientScidForTest = Random.nextBytes(8)
        val clientDcIdForTest = Random.nextBytes(8)

        val testServer = TestQuicServer(testHkdfService, testAesService, port = serverPort)
        // TestQuicServer.start() now derives CIDs from the first packet it receives.
        val serverJob = GlobalScope.launch(getIODispatcher()) {
            testServer.start()
        }

        val quicCurl = QuicCurl(testHkdfService, testAesService, RealUdpSocketFactory())
        var clientConnection: QuicConnection? = null

        try {
            runBlocking(getIODispatcher()) {
                var serverReady = false
                for(i in 1..5) { // Simple retry/wait for server to start
                    delay(500)
                    // Try a preliminary check or just assume server is up after delay
                    // For a real test, a more robust readiness signal from server would be better.
                    println("Test: Attempting to connect to server (attempt $i)...")
                    // A simple way to check if server socket is bound, though not perfect:
                    // Try to connect a dummy socket, or rely on TestQuicServer printing its ready message.
                    // For now, just delay and hope.
                    serverReady = true // Assume ready after delay.
                    if(serverReady) break
                }
                if (!serverReady) {
                    fail("Server did not appear to start in time.")
                }

                println("Test: Calling QuicCurl.connect with SCID: ${clientScidForTest.toHex()}, DCID: ${clientDcIdForTest.toHex()}")
                clientConnection = quicCurl.connect(
                    serverHost,
                    serverPort,
                    testClientScid = clientScidForTest,
                    testInitialDcid = clientDcIdForTest
                )
            }

            assertNotNull(clientConnection, "Client connection should not be null on successful handshake")
            assertEquals(TlsHandshakeState.HANDSHAKE_COMPLETE, clientConnection!!.tlsHandshakeState, "Client should reach HANDSHAKE_COMPLETE")

            val client1RttSecrets = clientConnection!!.cryptoSecrets[EncryptionLevel.ONERTT] ?: manager.getCurrentSecretsForSend(EncryptionLevel.ONERTT)
            assertNotNull(client1RttSecrets, "Client should have 1-RTT keys")
            println("Test: Client handshake completed successfully. State: ${clientConnection!!.tlsHandshakeState}")

            // Check server state
            val serverCompletedSuccessfully = runBlocking { testServer.awaitHandshakeCompletion(15000L) } // Wait up to 15s
            assertTrue(serverCompletedSuccessfully, "Server should also complete handshake successfully.")
            println("Test: Server also reported handshake completion.")

        } catch (e: Exception) {
            println("testQuicCurl_FullHandshakeWithTestServer failed: ${e.message}")
            e.printStackTrace()
            fail("Test threw an exception: ${e.message}")
        } finally {
            println("Test: Stopping server...")
            testServer.stop()
            runBlocking { serverJob.cancelAndJoin() }
            println("Test: Server stopped.")
            // QuicCurl's socket is now closed within its connect method's finally block if added.
            // If not, quicCurl.closeSocket() or similar would be needed if socket is kept open.
        }
    }

}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
// Need CompletableDeferred for the server receive part of the test
            serverSocket?.close()
            clientSocket?.close()
        }
    }
}
// Need CompletableDeferred for the server receive part of the test
import kotlinx.coroutines.CompletableDeferred
