package gk.kademlia

object KademliaConfig {
    const val PING_TIMEOUT_MS: Long = 5000
    const val MAX_FAILED_PINGS: Int = 3
    const val BUCKET_REFRESH_INTERVAL_MS: Long = 3600000 // 1 hour
    const val NODE_DISCOVERY_COUNT: Int = 8 // Number of nodes to find for replenishment
    const val REPLACEMENT_CACHE_SIZE: Int = 5 // For nodes that couldn't be added to full buckets
}
