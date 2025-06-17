package gk.kademlia.include

import kotlinx.datetime.Clock

typealias SubnetID = String

data class SubnetRoute<TNum : Comparable<TNum>>(
    val nuid: NUID<TNum>,
    val address: Address,
    val subnetId: SubnetID,
    var lastSeen: Long = Clock.System.now().toEpochMilliseconds(),
    var failedPings: Int = 0
)
