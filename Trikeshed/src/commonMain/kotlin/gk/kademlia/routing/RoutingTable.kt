package gk.kademlia.routing

import gk.kademlia.id.NUID
import gk.kademlia.include.Address
import gk.kademlia.include.SubnetID
import gk.kademlia.include.SubnetRoute
import gk.kademlia.net.NetMask
import kotlin.math.min

/**
 * once an agent knows its network id it can create a routeTable, the agent will also be
 * responsible for assigning new GUIDS on all routes
 * before touching the route table.
 *
 */
open class RoutingTable<TNum : Comparable<TNum>, Sz : NetMask<TNum>>(
    val agentNUID: NUID<TNum>, val optimal: Boolean = false,
) {
    private val bitOps = agentNUID.ops

    /**
     * contract is to have the route guid id fully realized in agent first
     */
    fun addRoute(other: SubnetRoute<TNum>): SubnetRoute<TNum>? = other.let { (g: NUID<TNum>) ->
        min(agentNUID.netmask.distance(agentNUID.id!!, g.id!!), bucketCount).let {
            if (it > 0)
                buckets[it.dec()].put(g.id!!, other)
            else null
        }
    }

    fun rmRoute(nuidToRemove: NUID<TNum>): SubnetRoute<TNum>? =
        agentNUID.netmask.distance(agentNUID.id!!, nuidToRemove.id!!).let { origDistance ->
            if (origDistance > 0)
                buckets.takeIf { it.isNotEmpty() }?.get(min(bucketCount, origDistance.dec()))?.remove(nuidToRemove.id!!)
            else null
        }

    fun getNearestNodes(targetNUID: NUID<TNum>, subnetIdFilter: SubnetID? = null, count: Int = bucketSize): List<SubnetRoute<TNum>> {
        val candidates = mutableListOf<SubnetRoute<TNum>>()
        val targetBucket = bucketFor(targetNUID)

        // Iterate outwards from the target bucket
        for (offset in 0 until bucketCount) {
            // Check buckets on both sides of the target bucket
            listOf(targetBucket - offset, targetBucket + offset).forEach { bucketIndex ->
                if (bucketIndex >= 0 && bucketIndex < bucketCount) {
                    buckets[bucketIndex].values.forEach { route ->
                        if (subnetIdFilter == null || route.subnetId == subnetIdFilter) {
                            candidates.add(route)
                        }
                    }
                }
            }
            if (candidates.size >= count && subnetIdFilter != null) break // Optimization: if filter is active and we have enough, break early
        }

        // Sort by distance to targetNUID and take the closest 'count' nodes.
        // Note: The distance here is the NUID distance, not bucket index distance.
        return candidates
            .distinctBy { it.nuid.id } // Ensure unique nodes
            .sortedBy { agentNUID.netmask.distance(targetNUID.id!!, it.nuid.id!!) }
            .take(count)
    }


    fun bucketFor(g: NUID<TNum>): Int =
        min(agentNUID.netmask.distance(agentNUID.id!!, g.id!!), bucketCount).let {
            // Ensure bucket index is always non-negative, distance 0 means same node, not a bucket
            if (it > 0) it.dec() else 0
        }


    open val bucketCount: Int = agentNUID.netmask.bits.let { if (optimal) it else it / 2 + 1 }
    open val bucketSize: Int = agentNUID.netmask.bits.let { if (optimal) it else it / 2 + 1 }

    val buckets: Array<MutableMap<TNum, SubnetRoute<TNum>>> = Array(bucketCount) { linkedMapOf() }

}