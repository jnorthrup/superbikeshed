package gk.kademlia.codec

import gk.kademlia.messages.FindNodeRequest
import gk.kademlia.messages.KademliaPayload
import gk.kademlia.messages.NodesResponse
import gk.kademlia.messages.PingRequest
import gk.kademlia.messages.PongResponse
import gk.kademlia.security.CryptoService
import gk.kademlia.security.DummyCryptoService
import gk.kademlia.security.DummyPrivateKey
import gk.kademlia.security.DummyPublicKey
import gk.kademlia.security.KeyPair
import gk.kademlia.security.SecureMessage
import kotlinx.datetime.Clock





import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecureKademliaCodecTest {

    private lateinit var cryptoService: CryptoService
    private lateinit var agentKeyPair: KeyPair
    private lateinit var codec: SecureKademliaCodec

    // JSON instance for manual serialization/deserialization in tests if needed
    private val testJson = Json {
        serializersModule = SerializersModule {
            polymorphic(KademliaPayload::class) {
                subclass(PingRequest::class, PingRequest.serializer())
                subclass(PongResponse::class, PongResponse.serializer())
                subclass(FindNodeRequest::class, FindNodeRequest.serializer())
                subclass(NodesResponse::class, NodesResponse.serializer())
            }
        }
        ignoreUnknownKeys = true // Match codec's Json settings
    }


    @BeforeTest
    fun setup() {
        cryptoService = DummyCryptoService() // Use the dummy crypto service
        agentKeyPair = cryptoService.generateKeyPair()
        codec = SecureKademliaCodec(cryptoService, agentKeyPair)
    }

    private fun createDummyKeyPair(id: String): KeyPair {
        return KeyPair(
            DummyPublicKey("public_$id".encodeToByteArray()),
            DummyPrivateKey("private_$id".encodeToByteArray())
        )
    }

    @Test
    fun `sendAndRecv_SuccessfulPingPong`() {
        val originalPingRequest = PingRequest("test-ping-123")

        val serializedOutput = codec.send(originalPingRequest)
        assertNotNull(serializedOutput, "Serialized output should not be null.")

        val receivedPayload = codec.recv(serializedOutput)
        assertNotNull(receivedPayload, "Received payload should not be null.")
        assertIs<PingRequest>(receivedPayload, "Received payload should be PingRequest.")
        assertEquals(originalPingRequest.uniqueId, receivedPayload.uniqueId, "PingRequest uniqueId should match.")
    }

    @Test
    fun `sendAndRecv_SuccessfulFindNodeNodesResponseCycle`() {
        // Test with FindNodeRequest and an empty NodesResponse
        val findNodeRequest = FindNodeRequest("target_node_id_bytes".encodeToByteArray())

        // Agent sends FindNodeRequest
        val serializedFindNode = codec.send(findNodeRequest)
        assertNotNull(serializedFindNode)

        // Peer receives, decodes (simulated by our codec.recv for now)
        val decodedByPeer = codec.recv(serializedFindNode)
        assertNotNull(decodedByPeer)
        assertIs<FindNodeRequest>(decodedByPeer)
        assertTrue(decodedByPeer.targetNUIDProto.contentEquals(findNodeRequest.targetNUIDProto))

        // Peer prepares NodesResponse (empty for this test)
        val nodesResponseByPeer = NodesResponse(emptyList())
        // Peer sends (signs) the NodesResponse.
        // For this test, we use the same 'codec' instance, meaning it's signed with agentKeys.
        // In a real scenario, a peer would use its own keys.
        val serializedNodesResponse = codec.send(nodesResponseByPeer)
        assertNotNull(serializedNodesResponse)

        // Agent receives and decodes NodesResponse
        val finalNodesResponse = codec.recv(serializedNodesResponse)
        assertNotNull(finalNodesResponse)
        assertIs<NodesResponse>(finalNodesResponse)
        assertTrue(finalNodesResponse.nodes.isEmpty())
    }


    @Test
    fun `recv_TamperedSignature_FailsVerification`() {
        val pingRequest = PingRequest("tamper-sig-test")
        val originalSerialized = codec.send(pingRequest)
        assertNotNull(originalSerialized)

        // Manually deserialize to SecureMessage to tamper
        val secureMessage = testJson.decodeFromString<SecureMessage<KademliaPayload>>(originalSerialized)

        // Tamper with the signature
        val tamperedSignature = secureMessage.signature.clone()
        if (tamperedSignature.isNotEmpty()) {
            tamperedSignature[0] = tamperedSignature[0].inc() // Flip a bit
        } else {
            // Signature was empty, make it non-empty and wrong
            tamperedSignature = "wrongsig".encodeToByteArray()
        }

        val tamperedSecureMessage = secureMessage.copy(signature = tamperedSignature)
        val tamperedSerialized = testJson.encodeToString(tamperedSecureMessage)

        val receivedPayload = codec.recv(tamperedSerialized)
        assertNull(receivedPayload, "Payload should be null due to tampered signature.")
    }

    @Test
    fun `recv_TamperedPayload_FailsVerification`() {
        val pingRequest = PingRequest("tamper-payload-test")
        val originalSerialized = codec.send(pingRequest)
        assertNotNull(originalSerialized)

        // Manually deserialize to SecureMessage
        val secureMessage = testJson.decodeFromString<SecureMessage<KademliaPayload>>(originalSerialized)

        // Tamper with the payload
        val originalPayload = secureMessage.payload
        assertTrue(originalPayload is PingRequest, "Payload should be PingRequest")
        val tamperedPayload = originalPayload.copy(uniqueId = "tampered-id")

        val tamperedSecureMessage = secureMessage.copy(payload = tamperedPayload)
        val tamperedSerialized = testJson.encodeToString(tamperedSecureMessage)

        val receivedPayload = codec.recv(tamperedSerialized)
        assertNull(receivedPayload, "Payload should be null due to tampered payload with original signature.")
    }

    @Test
    fun `recv_ExpiredTimestamp_FailsVerification`() {
        val pingRequest = PingRequest("expired-ts-test")

        // Manually construct SecureMessage with an old timestamp
        val payloadJsonString = testJson.encodeToString(pingRequest as KademliaPayload) // Cast for polymorphic encoding
        val payloadBytes = payloadJsonString.encodeToByteArray()

        // Timestamp from 10 minutes ago (600,000 ms)
        val expiredTimestamp = Clock.System.now().toEpochMilliseconds() - 600_000L
        val timestampBytes = expiredTimestamp.toString().encodeToByteArray()

        val dataToSignInput = payloadBytes + timestampBytes
        val hashedDataToSign = cryptoService.hash(dataToSignInput)
        val signature = cryptoService.sign(hashedDataToSign, agentKeyPair.privateKey)
        val senderPublicKeyEncoded = cryptoService.encodePublicKey(agentKeyPair.publicKey)

        val expiredSecureMessage = SecureMessage(
            payload = pingRequest,
            senderPublicKeyEncoded = senderPublicKeyEncoded,
            signature = signature,
            timestamp = expiredTimestamp
        )

        val serializedExpiredMessage = testJson.encodeToString(expiredSecureMessage)

        val receivedPayload = codec.recv(serializedExpiredMessage)
        assertNull(receivedPayload, "Payload should be null due to expired timestamp.")
    }

    @Test
    fun `recv_TimestampTooFarInFuture_FailsVerification`() {
        val pingRequest = PingRequest("future-ts-test")

        val payloadJsonString = testJson.encodeToString(pingRequest as KademliaPayload)
        val payloadBytes = payloadJsonString.encodeToByteArray()

        // Timestamp 10 minutes in the future
        val futureTimestamp = Clock.System.now().toEpochMilliseconds() + 600_000L
        val timestampBytes = futureTimestamp.toString().encodeToByteArray()

        val dataToSignInput = payloadBytes + timestampBytes
        val hashedDataToSign = cryptoService.hash(dataToSignInput)
        val signature = cryptoService.sign(hashedDataToSign, agentKeyPair.privateKey)
        val senderPublicKeyEncoded = cryptoService.encodePublicKey(agentKeyPair.publicKey)

        val futureSecureMessage = SecureMessage(
            payload = pingRequest,
            senderPublicKeyEncoded = senderPublicKeyEncoded,
            signature = signature,
            timestamp = futureTimestamp
        )

        val serializedFutureMessage = testJson.encodeToString(futureSecureMessage)

        val receivedPayload = codec.recv(serializedFutureMessage)
        assertNull(receivedPayload, "Payload should be null due to timestamp too far in the future.")
    }
}
