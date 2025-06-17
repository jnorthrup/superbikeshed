package gk.kademlia.agent

import gk.kademlia.id.NUID
import gk.kademlia.include.SubnetRoute
import gk.kademlia.net.NetMask

class DummyNetworkService<TNum : Comparable<TNum>, Sz : NetMask<TNum>> : NetworkService<TNum, Sz> {

    override suspend fun sendPing(route: SubnetRoute<TNum>): Boolean {
        println("DummyNetworkService: Faking sendPing to ${route.nuid.id} at ${route.address} on subnet ${route.subnetId}")
        // Simulate success, can be made configurable later
        return true
    }

    override suspend fun findNode(targetId: NUID<TNum>, count: Int): List<SubnetRoute<TNum>> {
        println("DummyNetworkService: Faking findNode for target ${targetId.id}, requesting $count nodes.")
        // Simulate finding no nodes
        return emptyList()
    }
}
