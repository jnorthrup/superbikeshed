package gk.kademlia

object KademliaConfig {
    const val PING_TIMEOUT_MS: Long = 5000
    const val MAX_FAILED_PINGS: Int = 3
    const val BUCKET_REFRESH_INTERVAL_MS: Long = 3600000 // 1 hour - Default/initial refresh
    const val NODE_DISCOVERY_COUNT: Int = 8 // Number of nodes to find for replenishment
    const val REPLACEMENT_CACHE_SIZE: Int = 5 // For nodes that couldn't be added to full buckets

    // Constants for adaptive refresh logic
    const val MIN_REFRESH_INTERVAL_MS: Long = 5 * 60 * 1000 // 5 minutes
    const val MAX_REFRESH_INTERVAL_MS: Long = 3 * 60 * 60 * 1000 // 3 hours
    const val EVICTION_OBSERVATION_WINDOW_MS: Long = 4 * 60 * 60 * 1000 // 4 hours
    const val HIGH_EVICTION_THRESHOLD: Int = 20 // If >20 nodes evicted in window from a bucket
    const val LOW_EVICTION_THRESHOLD: Int = 2   // If <2 nodes evicted in window from a bucket
}
