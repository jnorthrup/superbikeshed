package borg.trikeshed.net.quic.tls

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TlsMessagesTest {

    // Helper to create a somewhat realistic ClientHelloData for testing
    private fun createSampleClientHelloData(): ClientHelloData {
        return ClientHelloData(
            clientRandom = Random.nextBytes(32),
            cipherSuites = listOf(TLS_AES_128_GCM_SHA256, TLS_CHACHA20_POLY1305_SHA256),
            keyShareEntries = listOf(
                KeyShareEntry(X25519_GROUP, Random.nextBytes(32))
            ),
            quicTransportParameters = Random.nextBytes(20), // Dummy transport params
            supportedVersions = listOf(TLS_VERSION_1_3),
            supportedGroups = listOf(X25519_GROUP, P256_GROUP),
            serverName = "example.com"
        )
    }

    @Test
    fun testSerializeClientHello() {
        val clientHello = createSampleClientHelloData()
        val serialized = serializeClientHello(clientHello)

        assertNotNull(serialized)
        assertTrue(serialized.isNotEmpty())

        // Check Handshake Type (0x01 for ClientHello)
        assertEquals(0x01.toByte(), serialized[0], "Handshake type should be ClientHello (0x01)")

        // Check legacy version (should be 0x0303 for TLS 1.3)
        val legacyVersion = (serialized[4].toInt() and 0xFF shl 8) or (serialized[5].toInt() and 0xFF)
        assertEquals(TLS_VERSION_1_2.toInt(), legacyVersion, "Legacy version should be 0x0303 for TLS 1.3")

        // Further checks could include length fields, session ID, cipher suites list, etc.
        // For example, check if client random is present
        val randomOffset = 4 + 2 // type + length + legacy_version
        assertContentEquals(
            clientHello.clientRandom,
            serialized.sliceArray(randomOffset until randomOffset + 32),
            "Client random mismatch in serialized data"
        )
    }

    // ClientHello deserialization is not explicitly implemented as a single function `deserializeClientHello`
    // in TlsMessages.kt. It's implicitly handled by the TLS client/server state machines that would
    // parse incoming byte streams. parseHandshakeMessage is a generic helper.
    // For a dedicated test, one would need to either:
    // 1. Expose more internal parsing logic from a state machine, or
    // 2. Craft a byte stream and use parseHandshakeMessage, then manually verify fields.
    // For now, focusing on round-trip test.

    @Test
    fun testClientHelloRoundTrip() {
        val originalClientHello = createSampleClientHelloData()
        val serialized = serializeClientHello(originalClientHello)

        // To deserialize, we first need to parse the handshake wrapper
        val parsedHandshake = parseHandshakeMessage(serialized)
        assertNotNull(parsedHandshake, "Failed to parse handshake message wrapper")
        assertEquals(0x01.toByte(), parsedHandshake.first, "Handshake type should be ClientHello")

        val payload = parsedHandshake.third
        var offset = 0

        // Deserialize step-by-step to reconstruct ClientHelloData for comparison
        val dVersion = payload.readShort(offset); offset += 2 // legacy_version
        val dRandom = payload.sliceArray(offset until offset + 32); offset += 32

        val dSessionIdLen = payload[offset++].toInt() and 0xFF
        val dSessionId = payload.sliceArray(offset until offset + dSessionIdLen); offset += dSessionIdLen

        val dCipherSuitesLen = payload.readShort(offset).toInt(); offset += 2
        val dCipherSuitesList = mutableListOf<UShort>()
        var csOffset = 0
        while(csOffset < dCipherSuitesLen) {
            dCipherSuitesList.add(payload.readShort(offset + csOffset))
            csOffset += 2
        }
        offset += dCipherSuitesLen

        val dCompressionMethodsLen = payload[offset++].toInt() and 0xFF
        val dCompressionMethods = payload.sliceArray(offset until offset + dCompressionMethodsLen); offset += dCompressionMethodsLen

        val dExtensionsTotalLen = payload.readShort(offset).toInt(); offset += 2
        // For a full round trip, we'd need to deserialize all extensions as well.
        // This part is complex due to multiple extension types.
        // For this test, we will compare the most critical parts that were serialized.

        assertEquals(originalClientHello.version, if (dVersion == TLS_VERSION_1_2) TLS_VERSION_1_3 else dVersion, "Version mismatch (adjusting for legacy field)")
        assertContentEquals(originalClientHello.clientRandom, dRandom, "Client random mismatch")
        assertContentEquals(originalClientHello.sessionId, dSessionId, "Session ID mismatch")
        assertEquals(originalClientHello.cipherSuites, dCipherSuitesList, "Cipher suites mismatch")
        assertContentEquals(originalClientHello.compressionMethods, dCompressionMethods, "Compression methods mismatch")

        // A full round-trip test would involve re-serializing the deserialized data
        // or fully deserializing all extensions and comparing each field.
        // The current `ClientHelloData` includes fields not directly in the main block but in extensions
        // (keyShareEntries, quicTransportParameters, supportedVersions, supportedGroups, serverName)
        // so a direct field-by-field comparison after partial deserialization is limited.
        // The most robust test is often to serialize, then deserialize, then *re-serialize* the deserialized object
        // and check if the second serialization matches the first. Or, fully deserialize all fields.
    }

    // Helper to create a sample ServerHelloData
    private fun createSampleServerHelloData(): ServerHelloData {
         return ServerHelloData(
            version = TLS_VERSION_1_2, // Legacy version field
            serverRandom = Random.nextBytes(32),
            sessionId = Random.nextBytes(16), // Echoed or new session ID
            cipherSuite = TLS_AES_128_GCM_SHA256,
            compressionMethod = 0x00u,
            keyShareEntry = KeyShareEntry(X25519_GROUP, Random.nextBytes(32)),
            supportedVersion = TLS_VERSION_1_3 // Actual version in extension
        )
    }

    @Test
    fun testDeserializeServerHello() {
        // Construct a mock ServerHello byte array (simplified from QuicCurl for focus)
        // Handshake header (type 0x02, length) + payload
        // Payload: legacy_version, random, session_id, cipher_suite, compression_method, extensions_len, extensions...
        val legacyVersion = TLS_VERSION_1_2.writeShort()
        val random = Random.nextBytes(32)
        val sessionId = Random.nextBytes(16).writeByteLengthPrefixed() // Example session ID
        val cipherSuite = TLS_AES_128_GCM_SHA256.writeShort()
        val compMethod = byteArrayOf(0x00)

        var extensionsBytes = byteArrayOf()
        // KeyShare extension
        val ksGroup = X25519_GROUP.writeShort()
        val ksKey = Random.nextBytes(32)
        val ksKeyLen = ksKey.size.toUShort().writeShort()
        val ksData = ksGroup + ksKeyLen + ksKey
        extensionsBytes += TlsExtensionType.KEY_SHARE.writeShort() + ksData.size.toUShort().writeShort() + ksData

        // SupportedVersions extension
        val svData = TLS_VERSION_1_3.writeShort()
        extensionsBytes += TlsExtensionType.SUPPORTED_VERSIONS.writeShort() + svData.size.toUShort().writeShort() + svData

        val extensionsLen = extensionsBytes.size.toUShort().writeShort()

        val payload = legacyVersion + random + sessionId + cipherSuite + compMethod + extensionsLen + extensionsBytes
        val handshakeMessage = byteArrayOf(0x02.toByte()) + // ServerHello type
                               byteArrayOf(0x00.toByte()) + payload.size.toUShort().writeShort() + // Length
                               payload

        val deserialized = deserializeServerHello(handshakeMessage)
        assertNotNull(deserialized, "deserializeServerHello returned null")
        assertEquals(TLS_VERSION_1_2, deserialized.version, "Legacy version mismatch")
        assertContentEquals(random, deserialized.serverRandom, "Server random mismatch")
        assertContentEquals(sessionId.drop(1).toByteArray(), deserialized.sessionId, "Session ID mismatch") // Drop length prefix for comparison
        assertEquals(TLS_AES_128_GCM_SHA256, deserialized.cipherSuite, "Cipher suite mismatch")
        assertEquals(0x00.toUByte(), deserialized.compressionMethod, "Compression method mismatch")
        assertNotNull(deserialized.keyShareEntry, "KeyShareEntry should not be null")
        assertEquals(X25519_GROUP, deserialized.keyShareEntry!!.group, "KeyShare group mismatch")
        assertContentEquals(ksKey, deserialized.keyShareEntry!!.keyExchange, "KeyShare key_exchange mismatch")
        assertNotNull(deserialized.supportedVersion, "SupportedVersion should not be null")
        assertEquals(TLS_VERSION_1_3, deserialized.supportedVersion, "SupportedVersion mismatch")
    }

    // No serializeServerHello exists yet. A round-trip test would require it.


    @Test
    fun testDeserializeEncryptedExtensions() {
        // Handshake Type 0x08, Length, Extensions (overall length, then list)
        val ext1Data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val ext1 = TlsExtension(TlsExtensionType.SERVER_NAME, ext1Data) // Using SERVER_NAME just as an example type

        val ext2Data = byteArrayOf(0x0A, 0x0B)
        val ext2 = TlsExtension(TlsExtensionType.SUPPORTED_GROUPS, ext2Data)

        var extensionsPayloadBytes = byteArrayOf()
        // Serialize ext1
        extensionsPayloadBytes += ext1.type.writeShort()
        extensionsPayloadBytes += ext1.data.size.toUShort().writeShort()
        extensionsPayloadBytes += ext1.data
        // Serialize ext2
        extensionsPayloadBytes += ext2.type.writeShort()
        extensionsPayloadBytes += ext2.data.size.toUShort().writeShort()
        extensionsPayloadBytes += ext2.data

        // Overall extensions length prefix
        val extensionsStructureBytes = extensionsPayloadBytes.size.toUShort().writeShort() + extensionsPayloadBytes

        val handshakeMessage = byteArrayOf(0x08.toByte()) + // EncryptedExtensions type
                               byteArrayOf(0x00.toByte()) + extensionsStructureBytes.size.toUShort().writeShort() + // Handshake message length
                               extensionsStructureBytes

        val deserialized = deserializeEncryptedExtensions(handshakeMessage)
        assertNotNull(deserialized, "deserializeEncryptedExtensions returned null")
        assertEquals(2, deserialized.extensions.size, "Expected 2 extensions")

        val dExt1 = deserialized.extensions.find { it.type == TlsExtensionType.SERVER_NAME }
        assertNotNull(dExt1, "SERVER_NAME extension not found")
        assertContentEquals(ext1Data, dExt1.data, "SERVER_NAME data mismatch")

        val dExt2 = deserialized.extensions.find { it.type == TlsExtensionType.SUPPORTED_GROUPS }
        assertNotNull(dExt2, "SUPPORTED_GROUPS extension not found")
        assertContentEquals(ext2Data, dExt2.data, "SUPPORTED_GROUPS data mismatch")
    }

    @Test
    fun testFinishedDataRoundTrip() {
        val originalFinishedData = FinishedData(Random.nextBytes(32))

        val serialized = serializeFinished(originalFinishedData)
        assertNotNull(serialized, "serializeFinished returned null")
        assertTrue(serialized.isNotEmpty(), "Serialized finished data is empty")
        assertEquals(0x14.toByte(), serialized[0], "Handshake type should be Finished (0x14)")

        // Check length field (3 bytes)
        val payloadLength = (serialized[1].toInt() and 0xFF shl 16) or
                            (serialized[2].toInt() and 0xFF shl 8) or
                            (serialized[3].toInt() and 0xFF)
        assertEquals(originalFinishedData.verifyData.size, payloadLength, "Payload length mismatch in header")
        assertEquals(4 + originalFinishedData.verifyData.size, serialized.size, "Total serialized length mismatch")

        val deserialized = deserializeServerFinished(serialized) // Using deserializeServerFinished as it's compatible
        assertNotNull(deserialized, "deserializeServerFinished returned null for round trip")
        assertContentEquals(originalFinishedData.verifyData, deserialized.verifyData, "verifyData mismatch in round trip")
    }

    @Test
    fun testDeserializeCertificate_Basic() {
        val dummyCertBytes = byteArrayOf(0xDE, 0xAD, 0xBE, 0xEF)
        val emptyExtensions = byteArrayOf(0x00, 0x00) // Length 0 for extensions list

        var certEntryBytes = byteArrayOf()
        certEntryBytes += byteArrayOf(0x00, 0x00, dummyCertBytes.size.toByte()) // cert_data length (3 bytes)
        certEntryBytes += dummyCertBytes
        certEntryBytes += emptyExtensions

        val certificateRequestContext = byteArrayOf(0x00) // Length 0 for context
        var certificateListBytes = byteArrayOf()
        certificateListBytes += byteArrayOf(0x00, 0x00, certEntryBytes.size.toByte()) // certificate_list length (3 bytes)
        certificateListBytes += certEntryBytes

        val payload = certificateRequestContext + certificateListBytes
        val handshakeMessage = byteArrayOf(0x0B.toByte()) + // Certificate Type
                               byteArrayOf(0x00.toByte()) + payload.size.toUShort().writeShort() + // Handshake length
                               payload

        val deserialized = deserializeCertificate(handshakeMessage)
        assertNotNull(deserialized, "deserializeCertificate returned null")
        assertTrue(deserialized.certificateRequestContext.isEmpty(), "Certificate request context should be empty")
        assertEquals(1, deserialized.certificateList.size, "Should be one certificate entry")
        assertContentEquals(dummyCertBytes, deserialized.certificateList[0].certificateData, "Certificate data mismatch")
        assertTrue(deserialized.certificateList[0].extensions.isEmpty(), "Certificate entry extensions should be empty")
    }

    @Test
    fun testDeserializeCertificateVerify_Basic() {
        val dummySigScheme = TlsSignatureScheme.ECDSA_SECP256R1_SHA256
        val dummySignature = byteArrayOf(0xCA, 0xFE, 0xBA, 0xBE)

        var payload = byteArrayOf()
        payload += dummySigScheme.writeShort()
        payload += dummySignature.size.toUShort().writeShort()
        payload += dummySignature

        val handshakeMessage = byteArrayOf(0x0F.toByte()) + // CertificateVerify type
                               byteArrayOf(0x00.toByte()) + payload.size.toUShort().writeShort() + // Handshake length
                               payload

        val deserialized = deserializeCertificateVerify(handshakeMessage)
        assertNotNull(deserialized, "deserializeCertificateVerify returned null")
        assertEquals(dummySigScheme, deserialized.algorithm, "Signature scheme mismatch")
        assertContentEquals(dummySignature, deserialized.signature, "Signature data mismatch")
    }
}
