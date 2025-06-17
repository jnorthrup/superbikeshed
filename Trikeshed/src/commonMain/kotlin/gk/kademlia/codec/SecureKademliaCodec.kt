package gk.kademlia.codec

import gk.kademlia.messages.FindNodeRequest
import gk.kademlia.messages.KademliaPayload
import gk.kademlia.messages.NodesResponse
import gk.kademlia.messages.PingRequest
import gk.kademlia.messages.PongResponse
import gk.kademlia.messages.BitswapEnvelope // Import BitswapEnvelope

import gk.kademlia.security.CryptoService
import gk.kademlia.security.KeyPair
import gk.kademlia.security.PrivateKey // Potentially unused if agentKeyPair.privateKey is directly accessed
import gk.kademlia.security.PublicKey   // Potentially unused if agentKeyPair.publicKey is directly accessed
import gk.kademlia.security.SecureMessage

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.datetime.Clock // For timestamps

class SecureKademliaCodec(
    private val cryptoService: CryptoService,
    private val agentKeyPair: KeyPair // Agent's own keypair for signing
) : Codec<KademliaPayload, String> { // Assuming JSON String for Serde

    private val json = Json {
        prettyPrint = false // For network efficiency
        ignoreUnknownKeys = true
        serializersModule = SerializersModule {
            polymorphic(KademliaPayload::class) {
                subclass(PingRequest::class, PingRequest.serializer())
                subclass(PongResponse::class, PongResponse.serializer())
                subclass(FindNodeRequest::class, FindNodeRequest.serializer())
                subclass(NodesResponse::class, NodesResponse.serializer())
                subclass(BitswapEnvelope::class, BitswapEnvelope.serializer()) // Add BitswapEnvelope
                // Add other KademliaPayload subtypes here as they are defined
            }
            // No explicit context needed for SerializableNUID/SerializableSubnetRoute here
            // as they are concrete types used within NodesResponse, which is serializable.
        }
    }

    override fun send(event: KademliaPayload): String? {
        try {
            // 1. Serialize payload to JSON string, then to bytes for signing
            val payloadJsonString = json.encodeToString(event) // Polymorphic Aware
            val payloadBytes = payloadJsonString.encodeToByteArray()

            // 2. Create timestamp
            val timestamp = Clock.System.now().toEpochMilliseconds()
            // For signing, include timestamp bytes as well to prevent replay of old signature with new timestamp
            val timestampBytes = timestamp.toString().encodeToByteArray() // Simple way to include in hash

            // 3. Data to sign = hash(payloadBytes + timestampBytes)
            // A more robust approach might involve a structured format or specific encoding for the elements.
            val dataToSignInput = payloadBytes + timestampBytes
            val hashedDataToSign = cryptoService.hash(dataToSignInput)


            // 4. Sign the hashed data
            val signature = cryptoService.sign(hashedDataToSign, agentKeyPair.privateKey)

            // 5. Encode agent's public key
            val senderPublicKeyEncoded = cryptoService.encodePublicKey(agentKeyPair.publicKey)

            // 6. Create SecureMessage
            val secureMessage = SecureMessage(
                payload = event, // The original, non-serialized payload object
                senderPublicKeyEncoded = senderPublicKeyEncoded,
                signature = signature,
                timestamp = timestamp
            )

            // 7. Serialize SecureMessage to JSON String
            return json.encodeToString(secureMessage) // Polymorphic aware for payload within SecureMessage

        } catch (e: Exception) {
            println("SecureKademliaCodec.send error: ${e.message}")
            e.printStackTrace() // Basic error handling for now
            return null
        }
    }

    override fun recv(ser: String): KademliaPayload? {
        try {
            // 1. Deserialize JSON String to SecureMessage
            // Type argument for SecureMessage is KademliaPayload because KademliaPayload is our sealed interface.
            val secureMessage = json.decodeFromString<SecureMessage<KademliaPayload>>(ser)

            // 2. Verify timestamp (e.g., within a 5-minute window)
            val currentTime = Clock.System.now().toEpochMilliseconds()
            val timeDifference = kotlin.math.abs(currentTime - secureMessage.timestamp)
            if (timeDifference > 300_000) { // 5 minutes tolerance
                println("SecureKademliaCodec.recv error: Timestamp validation failed. Difference: ${timeDifference}ms. Current: $currentTime, Msg ts: ${secureMessage.timestamp}")
                return null
            }

            // 3. Decode sender's public key
            val senderPublicKey = cryptoService.decodePublicKey(secureMessage.senderPublicKeyEncoded)

            // 4. Reconstruct the data that was signed
            //    This MUST match how it was constructed in send()
            val payloadJsonString = json.encodeToString(secureMessage.payload) // Polymorphic Aware
            val payloadBytes = payloadJsonString.encodeToByteArray()
            val timestampBytes = secureMessage.timestamp.toString().encodeToByteArray() // Must be same format as in send

            val dataThatWasSignedInput = payloadBytes + timestampBytes
            val hashedDataThatWasSigned = cryptoService.hash(dataThatWasSignedInput)


            // 5. Verify signature against the reconstructed hashed data
            val signatureValid = cryptoService.verifySignature(
                hashedDataThatWasSigned,
                secureMessage.signature,
                senderPublicKey
            )

            if (!signatureValid) {
                 println("SecureKademliaCodec.recv error: Signature verification failed.")
                return null
            }

            // All checks passed, return the payload
            return secureMessage.payload

        } catch (e: Exception) {
            println("SecureKademliaCodec.recv error: ${e.message}")
            e.printStackTrace() // Basic error handling
            return null
        }
    }
}
