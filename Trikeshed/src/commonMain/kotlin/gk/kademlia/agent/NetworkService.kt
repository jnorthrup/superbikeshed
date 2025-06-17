package gk.kademlia.agent

import gk.kademlia.id.NUID
import gk.kademlia.include.SubnetRoute
import gk.kademlia.net.NetMask
import gk.kademlia.messages.KademliaPayload // Import KademliaPayload

interface NetworkService<TNum : Comparable<TNum>, Sz : NetMask<TNum>> {
    suspend fun sendPing(route: SubnetRoute<TNum>): Boolean
    suspend fun findNode(targetId: NUID<TNum>, count: Int): List<SubnetRoute<TNum>>

    /**
     * Sends a generic KademliaPayload to a target route.
     * @param targetRoute The route information (NUID, address) of the target.
     * @param payload The KademliaPayload to send (e.g., PingRequest, BitswapEnvelope).
     * @return True if the message was accepted for sending, false otherwise.
     *         Note: True does not guarantee delivery, only acceptance by the network layer.
     */
    suspend fun sendMessage(targetRoute: SubnetRoute<TNum>, payload: KademliaPayload): Boolean
}
