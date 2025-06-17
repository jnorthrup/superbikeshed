package gk.kademlia.agent

import gk.kademlia.KademliaConfig
import gk.kademlia.id.NUID
import gk.kademlia.include.SubnetRoute
import gk.kademlia.net.NetMask
import gk.kademlia.routing.RoutingTable
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex // For evictionMutex
import kotlinx.coroutines.sync.withLock // For evictionMutex
import kotlinx.datetime.Clock

class RoutingManager<TNum : Comparable<TNum>, Sz : NetMask<TNum>>(
    private val agentNUID: NUID<TNum>,
    private val routingTable: RoutingTable<TNum, Sz>,
    private val networkService: NetworkService<TNum, Sz>,
    private val coroutineScope: CoroutineScope
) {
    private var refreshJob: Job? = null
    private var currentRefreshIntervalMs: Long = KademliaConfig.BUCKET_REFRESH_INTERVAL_MS
    private val nodeEvictionTimestamps = mutableListOf<Long>()
    private val evictionMutex = Mutex() // For thread-safe access to nodeEvictionTimestamps


    fun start() {
        if (refreshJob?.isActive == true) {
            println("RoutingManager already started.")
            return
        }
        println("Starting RoutingManager with initial interval: $currentRefreshIntervalMs ms")
        refreshJob = coroutineScope.launch {
            // Initial delay before the first refresh cycle starts
            delay(currentRefreshIntervalMs)
            while (isActive) {
                try {
                    refreshBuckets()
                    adaptRefreshInterval() // Adapt interval AFTER a refresh cycle
                    delay(currentRefreshIntervalMs)
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
                val removedRoute = routingTable.rmRoute(nuid)
                if (removedRoute != null) {
                    evictionMutex.withLock {
                        nodeEvictionTimestamps.add(Clock.System.now().toEpochMilliseconds())
                    }
                    println("RoutingManager: Evicted node ${nuid.id} from bucket $i due to failed pings.")
                } else {
                    println("RoutingManager: Attempted to evict node ${nuid.id} from bucket $i, but it was already removed.")
                }
            }

            // Replenish bucket if needed
            val currentBucketSize = routingTable.buckets[i].size // Re-check size after removals
            if (currentBucketSize < routingTable.bucketSize) {
                println("Bucket $i is below target size ($currentBucketSize/${routingTable.bucketSize}). Attempting targeted discovery.")

                var nodesToDiscover: List<SubnetRoute<TNum>> = emptyList()
                try {
                    // Note: agentNUID is the NUID<TNum> object. generateTargetNUIDForBucketRefresh is its method.
                    val targetPrimitive = agentNUID.generateTargetNUIDForBucketRefresh(i)
                    println("RoutingManager: Bucket $i is sparse. Ideal discovery target ID (primitive): $targetPrimitive (Agent NUID: ${agentNUID.id}).")

                    // Actual call still uses agentNUID as the primary argument for findNode due to NetworkService.findNode signature
                    // and the difficulty of creating a NUID<TNum> from just Primitive without concrete type knowledge here.
                    // DummyNetworkService will ignore the targetId's actual value for now.
                    // A real implementation would require NUID.generateTargetNUIDForBucketRefresh to return NUID<TNum>
                    // or NetworkService.findNode to accept a Primitive.
                    nodesToDiscover = networkService.findNode(agentNUID, KademliaConfig.NODE_DISCOVERY_COUNT)
                    // If findNode could take a primitive:
                    // nodesToDiscover = networkService.findNode(targetPrimitive, KademliaConfig.NODE_DISCOVERY_COUNT)
                    // Or if generateTargetNUIDForBucketRefresh returned NUID<TNum>:
                    // val targetNUIDForDiscovery = agentNUID.generateTargetNUIDForBucketRefresh(i)
                    // nodesToDiscover = networkService.findNode(targetNUIDForDiscovery, KademliaConfig.NODE_DISCOVERY_COUNT)

                } catch (e: Exception) {
                    println("RoutingManager: Error generating target NUID or during findNode for bucket $i refresh: ${e.message}")
                    // Fallback to old method: query around agent's NUID if targeted failed.
                    // This path might be taken if generateTargetNUIDForBucketRefresh throws for some reason.
                    println("RoutingManager: Falling back to discovery around agent's NUID for bucket $i.")
                    nodesToDiscover = networkService.findNode(agentNUID, KademliaConfig.NODE_DISCOVERY_COUNT)
                }

                println("Discovered ${nodesToDiscover.size} potential nodes for bucket $i.")
                for (discoveredNode in nodesToDiscover) {
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
        val lruNode = bucket.values.minByOrNull { it.lastSeen }

        if (lruNode == null) {
            routingTable.addRoute(newNode) // Should not happen if bucket is full, but good practice
            println("Bucket $bucketIndex was marked full but no LRU node found (unexpected). Added new node ${newNode.nuid.id}.")
            return
        }

        println("Pinging LRU node ${lruNode.nuid.id} (lastSeen: ${lruNode.lastSeen}) in bucket $bucketIndex.")
        if (networkService.sendPing(lruNode)) {
            lruNode.lastSeen = Clock.System.now().toEpochMilliseconds()
            lruNode.failedPings = 0
            println("LRU node ${lruNode.nuid.id} responded. New node ${newNode.nuid.id} is discarded (or cached).")
        } else {
            println("LRU node ${lruNode.nuid.id} did not respond. Removing it and adding new node ${newNode.nuid.id}.")
            val removedNode = routingTable.rmRoute(lruNode.nuid) // This is an eviction
            if (removedNode != null) {
                 evictionMutex.withLock {
                    nodeEvictionTimestamps.add(Clock.System.now().toEpochMilliseconds())
                }
                println("Evicted LRU node ${lruNode.nuid.id} from full bucket $bucketIndex.")
            }
            routingTable.addRoute(newNode)
        }
    }

    private suspend fun adaptRefreshInterval() {
        val windowStartTime = Clock.System.now().toEpochMilliseconds() - KademliaConfig.EVICTION_OBSERVATION_WINDOW_MS

        val recentEvictions = evictionMutex.withLock {
            // Prune old eviction timestamps
            val initialSize = nodeEvictionTimestamps.size
            nodeEvictionTimestamps.removeAll { it < windowStartTime }
            val prunedCount = initialSize - nodeEvictionTimestamps.size
            if (prunedCount > 0) {
                println("RoutingManager: Pruned $prunedCount old eviction timestamps.")
            }
            nodeEvictionTimestamps.size
        }

        println("RoutingManager: Recent evictions in window (${KademliaConfig.EVICTION_OBSERVATION_WINDOW_MS / 1000}s): $recentEvictions")

        val oldInterval = currentRefreshIntervalMs
        if (recentEvictions > KademliaConfig.HIGH_EVICTION_THRESHOLD) {
            currentRefreshIntervalMs = (currentRefreshIntervalMs * 0.8).toLong()
                .coerceAtLeast(KademliaConfig.MIN_REFRESH_INTERVAL_MS)
            if (currentRefreshIntervalMs != oldInterval) {
                 println("RoutingManager: High churn detected. Refresh interval reduced from $oldInterval to $currentRefreshIntervalMs ms")
            } else {
                 println("RoutingManager: High churn detected, but interval already at MIN ($currentRefreshIntervalMs ms).")
            }
        } else if (recentEvictions < KademliaConfig.LOW_EVICTION_THRESHOLD) {
            currentRefreshIntervalMs = (currentRefreshIntervalMs * 1.2).toLong()
                .coerceAtMost(KademliaConfig.MAX_REFRESH_INTERVAL_MS)
             if (currentRefreshIntervalMs != oldInterval) {
                println("RoutingManager: Low churn detected. Refresh interval increased from $oldInterval to $currentRefreshIntervalMs ms")
            } else {
                println("RoutingManager: Low churn detected, but interval already at MAX ($currentRefreshIntervalMs ms).")
            }
        } else {
            // Moderate churn, gradually adjust towards default BUCKET_REFRESH_INTERVAL_MS
            val adjustmentFactor = if (currentRefreshIntervalMs < KademliaConfig.BUCKET_REFRESH_INTERVAL_MS) 1.05 else 0.95
            val targetInterval = if (currentRefreshIntervalMs < KademliaConfig.BUCKET_REFRESH_INTERVAL_MS) {
                (currentRefreshIntervalMs * adjustmentFactor).toLong().coerceAtMost(KademliaConfig.BUCKET_REFRESH_INTERVAL_MS)
            } else {
                (currentRefreshIntervalMs * adjustmentFactor).toLong().coerceAtLeast(KademliaConfig.BUCKET_REFRESH_INTERVAL_MS)
            }
            if (currentRefreshIntervalMs != targetInterval) {
                currentRefreshIntervalMs = targetInterval
                println("RoutingManager: Moderate churn. Refresh interval adjusted from $oldInterval towards default, now $currentRefreshIntervalMs ms")
            } else {
                 println("RoutingManager: Moderate churn. Refresh interval stable at $currentRefreshIntervalMs ms (already at/near default or bounds).")
            }
        }
    }
}
