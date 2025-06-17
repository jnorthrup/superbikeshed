package gk.kademlia.agent

import gk.kademlia.id.NUID
import gk.kademlia.include.SubnetRoute
import gk.kademlia.net.NetMask

interface NetworkService<TNum : Comparable<TNum>, Sz : NetMask<TNum>> {
    suspend fun sendPing(route: SubnetRoute<TNum>): Boolean
    suspend fun findNode(targetId: NUID<TNum>, count: Int): List<SubnetRoute<TNum>>
}
