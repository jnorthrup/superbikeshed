package gk.kademlia.agent

import gk.kademlia.KademliaConfig
import gk.kademlia.id.NUID
import gk.kademlia.include.SubnetRoute
import gk.kademlia.net.NetMask
import gk.kademlia.routing.RoutingTable
import kotlinx.coroutines.*
import kotlinx.datetime.Clock

class RoutingManager<TNum : Comparable<TNum>, Sz : NetMask<TNum>>(
    private val agentNUID: NUID<TNum>,
    private val routingTable: RoutingTable<TNum, Sz>,
    private val networkService: NetworkService<TNum, Sz>,
    private val coroutineScope: CoroutineScope
) {
    private var refreshJob: Job? = null

    fun start() {
        if (refreshJob?.isActive == true) {
            println("RoutingManager already started.")
            return
        }
        println("Starting RoutingManager...")
        refreshJob = coroutineScope.launch {
            while (isActive) {
                try {
                    refreshBuckets()
                    delay(KademliaConfig.BUCKET_REFRESH_INTERVAL_MS) // Initial delay before first refresh might be long
                } catch (e: CancellationException) {
                    println("RoutingManager refresh job cancelled.")
                    break
                } catch (e: Exception) {
                    println("Error during bucket refresh: ${e.message}")
                    // Optionally add a shorter delay before retrying after an error
                    delay(60000L) // e.g., 1 minute delay after error
                }
            }
        }
        println("RoutingManager started.")
    }

    fun stop() {
        println("Stopping RoutingManager...")
        refreshJob?.cancel()
        refreshJob = null
        println("RoutingManager stopped.")
    }

    private suspend fun refreshBuckets() {
        println("Refreshing buckets...")
        val currentTime = Clock.System.now().toEpochMilliseconds()

        for (i in routingTable.buckets.indices) {
            val bucket = routingTable.buckets[i]
            val nodesToRemove = mutableListOf<NUID<TNum>>() // To avoid concurrent modification

            // Check existing nodes
            for (route in ArrayList(bucket.values)) { // Iterate over a copy
                if ((currentTime - route.lastSeen) > KademliaConfig.BUCKET_REFRESH_INTERVAL_MS) {
                    println("Node ${route.nuid.id} in bucket $i requires ping check (lastSeen: ${route.lastSeen}).")
                    if (networkService.sendPing(route)) {
                        route.lastSeen = Clock.System.now().toEpochMilliseconds()
                        route.failedPings = 0
                        println("Node ${route.nuid.id} responded to ping. Updated lastSeen.")
                    } else {
                        route.failedPings++
                        println("Node ${route.nuid.id} failed ping. Failed pings: ${route.failedPings}.")
                        if (route.failedPings >= KademliaConfig.MAX_FAILED_PINGS) {
                            nodesToRemove.add(route.nuid)
                            println("Node ${route.nuid.id} exceeded max failed pings. Marking for removal.")
                        }
                    }
                }
            }

            nodesToRemove.forEach { nuid ->
                routingTable.rmRoute(nuid)
                println("Removed node ${nuid.id} from bucket $i.")
            }

            // Replenish bucket if needed
            if (bucket.size < routingTable.bucketSize) {
                println("Bucket $i is below target size (${bucket.size}/${routingTable.bucketSize}). Attempting to discover new nodes.")
                // A simple strategy: query for nodes around agent's NUID.
                // A more targeted strategy would be to generate an ID within the bucket's range.
                val discoveredNodes = networkService.findNode(agentNUID, KademliaConfig.NODE_DISCOVERY_COUNT)
                println("Discovered ${discoveredNodes.size} potential nodes.")
                for (discoveredNode in discoveredNodes) {
                    if (discoveredNode.nuid.id == agentNUID.id) {
                        println("Skipping own NUID ${agentNUID.id} from discovered nodes.")
                        continue
                    }
                    // Check if the target bucket for this specific node is still not full.
                    val targetBucketIndex = routingTable.bucketFor(discoveredNode.nuid)
                    if (routingTable.buckets[targetBucketIndex].size < routingTable.bucketSize) {
                        val addedNode = routingTable.addRoute(discoveredNode)
                        if (addedNode == null) { // addRoute returns previous value, null if new
                             println("Added discovered node ${discoveredNode.nuid.id} to bucket $targetBucketIndex.")
                        } else {
                             println("Discovered node ${discoveredNode.nuid.id} already in bucket $targetBucketIndex or replaced existing.")
                        }
                    } else {
                        println("Bucket $targetBucketIndex for node ${discoveredNode.nuid.id} is full. Skipping.")
                        // Optionally, could call handleFullBucket here if that's desired behavior during refresh
                    }
                     // Ensure we don't overfill the *current* bucket in this loop if discovery is broad
                    if (routingTable.buckets[i].size >= routingTable.bucketSize) break
                }
            }
        }
        println("Bucket refresh cycle finished.")
    }

    suspend fun handleFullBucket(bucketIndex: Int, newNode: SubnetRoute<TNum>) {
        val bucket = routingTable.buckets[bucketIndex]
        if (bucket.size < routingTable.bucketSize) {
            routingTable.addRoute(newNode)
            println("Added node ${newNode.nuid.id} to bucket $bucketIndex as it was not full.")
            return
        }

        println("Bucket $bucketIndex is full. Handling conflict for new node ${newNode.nuid.id}.")
        // Find the least recently seen node (or oldest if lastSeen is similar)
        // For simplicity, if using LinkedHashMap, the first entry is often the oldest / LRU
        // However, relying on insertion order for LRU might be tricky if nodes are updated.
        // Explicitly finding by lastSeen is safer.
        val lruNode = bucket.values.minByOrNull { it.lastSeen }

        if (lruNode == null) { // Should not happen if bucket is full, but good practice
            routingTable.addRoute(newNode)
            println("Bucket $bucketIndex was marked full but no LRU node found. Added new node ${newNode.nuid.id}.")
            return
        }

        println("Pinging LRU node ${lruNode.nuid.id} in bucket $bucketIndex.")
        if (networkService.sendPing(lruNode)) {
            lruNode.lastSeen = Clock.System.now().toEpochMilliseconds()
            lruNode.failedPings = 0 // Reset on successful ping
            // Bucket remains full, new node is discarded (or could be cached)
            println("LRU node ${lruNode.nuid.id} responded. New node ${newNode.nuid.id} is discarded.")
            // Consider adding newNode to a replacement cache here if implementing that feature
        } else {
            println("LRU node ${lruNode.nuid.id} did not respond. Removing it and adding new node ${newNode.nuid.id}.")
            routingTable.rmRoute(lruNode.nuid)
            routingTable.addRoute(newNode)
        }
    }
}
