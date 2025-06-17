package gk.kademlia.agent

import gk.kademlia.codec.SecureKademliaCodec
import gk.kademlia.id.NUID
import gk.kademlia.include.SubnetRoute
import gk.kademlia.messages.FindNodeRequest
import gk.kademlia.messages.NodesResponse
import gk.kademlia.messages.PingRequest
import gk.kademlia.messages.PongResponse
import gk.kademlia.messages.KademliaPayload // Import KademliaPayload for the new method
import gk.kademlia.net.NetMask // Still needed for Sz type parameter if service methods use it.

// DummyNetworkService now uses the codec to simulate sending/receiving secure messages.
class DummyNetworkService<TNum : Comparable<TNum>, Sz : NetMask<TNum>>(
    private val codec: SecureKademliaCodec
    // No agentNUID needed directly here if codec handles keying via its configured agentKeyPair
) : NetworkService<TNum, Sz> {

    override suspend fun sendPing(route: SubnetRoute<TNum>): Boolean {
        val pingId = "ping_${kotlin.random.Random.nextInt()}"
        val pingRequest = PingRequest(pingId)

        println("DummyNetworkService: Simulating sendPing to NUID ${route.nuid.id} at ${route.address} on subnet ${route.subnetId}")
        val serializedPing = codec.send(pingRequest)
        println("DummyNetworkService: Secured PingRequest: ${serializedPing?.take(70)}...")

        if (serializedPing == null) {
            println("DummyNetworkService: Failed to serialize PingRequest.")
            return false
        }

        // Simulate peer receiving, processing, and responding
        println("DummyNetworkService: Simulating peer receiving PingRequest...")
        val decodedRequestByPeer = codec.recv(serializedPing)

        if (decodedRequestByPeer !is PingRequest || decodedRequestByPeer.uniqueId != pingId) {
            println("DummyNetworkService: Peer failed to decode ping or ID mismatch. Decoded: $decodedRequestByPeer")
            return false
        }
        println("DummyNetworkService: Peer successfully decoded PingRequest: $decodedRequestByPeer")

        val pongResponseByPeer = PongResponse(pingId)
        // Peer would sign with ITS OWN key. Dummy uses the "agent's" codec (which uses agent's keypair) to simulate this.
        println("DummyNetworkService: Simulating peer sending PongResponse: $pongResponseByPeer")
        val serializedPongFromPeer = codec.send(pongResponseByPeer)
        println("DummyNetworkService: Peer sends secured PongResponse: ${serializedPongFromPeer?.take(70)}...")

        if (serializedPongFromPeer == null) {
            println("DummyNetworkService: Peer failed to serialize PongResponse.")
            return false
        }

        // Agent receives and decodes pong
        println("DummyNetworkService: Agent simulating receiving PongResponse...")
        val finalPong = codec.recv(serializedPongFromPeer)
        val success = finalPong is PongResponse && finalPong.uniqueId == pingId

        if (success) {
            println("DummyNetworkService: Successfully received and verified Pong: $finalPong")
        } else {
            println("DummyNetworkService: Failed to verify Pong or type mismatch. Received: $finalPong")
        }
        return success
    }

    override suspend fun findNode(targetId: NUID<TNum>, count: Int): List<SubnetRoute<TNum>> {
        // For NUID<TNum>.toString(), ensure it provides a reasonable representation.
        // Using hash of NUID.id as targetNUIDProto for FindNodeRequest might be more robust
        // if NUID.id itself is complex or very large. But for dummy, toString() is simpler.
        // The actual conversion of NUID to bytes for FindNodeRequest would ideally use a
        // method on NUID or a dedicated NUID serializer if NUID itself was part of the payload.
        // Since FindNodeRequest takes ByteArray, this conversion happens before calling it.
        val targetIdBytes = targetId.id?.toString()?.encodeToByteArray() ?: byteArrayOf()
        // This is a placeholder for actual NUID serialization for the request.
        // In a real scenario, targetId.toByteArray() or similar would be preferred.

        val request = FindNodeRequest(targetNUIDProto = targetIdBytes)

        println("DummyNetworkService: Simulating findNode for target NUID ${targetId.id}, requesting $count nodes.")
        val serializedRequest = codec.send(request)
        println("DummyNetworkService: Secured FindNodeRequest: ${serializedRequest?.take(70)}...")

        if (serializedRequest == null) {
            println("DummyNetworkService: Failed to serialize FindNodeRequest.")
            return emptyList()
        }

        // Simulate peer receiving and processing
        println("DummyNetworkService: Simulating peer receiving FindNodeRequest...")
        val decodedRequestByPeer = codec.recv(serializedRequest)
        if (decodedRequestByPeer !is FindNodeRequest) {
             println("DummyNetworkService: Peer failed to decode FindNodeRequest. Decoded: $decodedRequestByPeer")
            return emptyList()
        }
        println("DummyNetworkService: Peer successfully decoded FindNodeRequest targeting (bytes): ${decodedRequestByPeer.targetNUIDProto.contentToString().take(20)}...")

        // Simulate peer responding with empty list
        val response = NodesResponse(nodes = emptyList()) // Uses SerializableSubnetRoute internally if KademliaMessages was updated
        println("DummyNetworkService: Simulating peer sending NodesResponse (empty list).")
        val serializedResponse = codec.send(response)
        println("DummyNetworkService: Peer sends secured NodesResponse: ${serializedResponse?.take(70)}...")

        if (serializedResponse == null) {
            println("DummyNetworkService: Peer failed to serialize NodesResponse.")
            return emptyList()
        }

        // Agent receives and decodes response
        println("DummyNetworkService: Agent simulating receiving NodesResponse...")
        val finalResponse = codec.recv(serializedResponse)
        if (finalResponse is NodesResponse) {
            println("DummyNetworkService: Successfully received and decoded NodesResponse. Node count: ${finalResponse.nodes.size}")
            // Cannot convert SerializableSubnetRoute back to SubnetRoute<TNum> easily here
            // without full NUID deserialization logic and BitOps. Returning emptyList is fine for dummy.
            return emptyList()
        } else {
            println("DummyNetworkService: Failed to decode NodesResponse or type mismatch. Received: $finalResponse")
            return emptyList()
        }
    }

    override suspend fun sendMessage(targetRoute: SubnetRoute<TNum>, payload: KademliaPayload): Boolean {
        val serializedPayload = codec.send(payload) // Securely serialize the KademliaPayload
        val targetNuidString = targetRoute.nuid.id?.toString() ?: "UNKNOWN_NUID"

        println("DummyNetworkService: sendMessage to NUID $targetNuidString (type: ${payload::class.simpleName}). Secured: ${serializedPayload?.take(70)}...")

        if (serializedPayload == null) {
            println("DummyNetworkService: Failed to serialize payload for sendMessage.")
            return false
        }

        // Simulate the message being "received" by the target peer and processed.
        // For this dummy service, we'll just log that it was "sent" (encoded successfully).
        // A more complex dummy might try to simulate the peer decoding it,
        // especially if it's a BitswapEnvelope, to test further interactions.
        // However, for this subtask, successful encoding is sufficient.

        // Example of further simulation (optional, can be expanded if needed for testing):
        /*
        println("DummyNetworkService: Simulating peer at $targetNuidString receiving the generic message...")
        val receivedByPeer = codec.recv(serializedPayload)
        if (receivedByPeer != null) {
            println("DummyNetworkService: Peer at $targetNuidString conceptually decoded payload: ${receivedByPeer::class.simpleName}")
            if (receivedByPeer is gk.kademlia.messages.BitswapEnvelope) {
                 println("DummyNetworkService: Peer received BitswapEnvelope, from: ${receivedByPeer.sourcePeerIdString}, bs_msg_size: ${receivedByPeer.bitswapMessageBytes.size}")
                // Here, a test setup might have a way to route this back to a local BitswapEngine
                // associated with targetNUID for loopback testing.
            }
        } else {
            println("DummyNetworkService: Peer at $targetNuidString failed to decode the generic message.")
            // This would indicate an issue with codec or data if it happens.
        }
        */
        return true // Indicate successful "sending" (encoding and conceptual dispatch)
    }
}
