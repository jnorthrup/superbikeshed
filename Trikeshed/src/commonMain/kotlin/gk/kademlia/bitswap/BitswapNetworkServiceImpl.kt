package gk.kademlia.bitswap

import gk.kademlia.agent.NetworkService // Kademlia's NetworkService
import gk.kademlia.messages.BitswapEnvelope
// KademliaPayload, NUID, NetMask are needed for the generic types of NetworkService
import gk.kademlia.messages.KademliaPayload
import gk.kademlia.id.NUID
import gk.kademlia.net.NetMask
import gk.kademlia.include.SubnetRoute // Import SubnetRoute for the updated method signature

import kotlinx.serialization.encodeToString // For JSON serialization
// import kotlinx.serialization.encodeToByteArray // Not directly used if going String -> ByteArray
// import kotlinx.serialization.decodeFromByteArray // Not used here
import kotlinx.serialization.json.Json // Using Json for BitswapMessage serialization as decided


class BitswapNetworkServiceImpl<TNum : Comparable<TNum>, Sz : NetMask<TNum>>(
    private val localPeerIdString: String,
    private val kademliaNetworkService: NetworkService<TNum, Sz>,
    private val jsonSerializer: Json = Json
) : BitswapNetworkSender<TNum, Sz> { // Implement generic BitswapNetworkSender

    override suspend fun sendMessage(targetRoute: SubnetRoute<TNum>, message: BitswapMessage) {
        val targetNuidStr = targetRoute.nuid.id?.toString() ?: "UNKNOWN_NUID_IN_ROUTE"
        println("BitswapNetworkService: Sending Bitswap message from $localPeerIdString to $targetNuidStr (via SubnetRoute)")
        try {
            // Serialize BitswapMessage to ByteArray using JSON
            val bitswapMessageBytes = jsonSerializer.encodeToString(message).encodeToByteArray()

            val envelope = BitswapEnvelope(
                sourcePeerIdString = localPeerIdString,
                bitswapMessageBytes = bitswapMessageBytes
            )

            // Now directly use the provided targetRoute to send via Kademlia's NetworkService
            val sent = kademliaNetworkService.sendMessage(targetRoute, envelope)
            if (sent) {
                println("BitswapNetworkService: BitswapEnvelope sent to Kademlia layer for target $targetNuidStr.")
            } else {
                println("BitswapNetworkService: Kademlia layer rejected BitswapEnvelope for target $targetNuidStr.")
                // Optionally, throw an exception or handle failure to propagate it
            }

        } catch (e: Exception) {
            println("BitswapNetworkService: Error serializing or sending Bitswap message: ${e.message}")
            e.printStackTrace()
        }
    }
}
