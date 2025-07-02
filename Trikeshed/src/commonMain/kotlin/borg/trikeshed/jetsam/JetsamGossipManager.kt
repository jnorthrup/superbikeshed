package borg.trikeshed.jetsam

// Typealiases for keys/values/entries
// (In real code, these would be more complex or use actual types)
typealias JetsamKey = String
typealias JetsamValue = Map<String, Any?>

data class JetsamEntry(val key: JetsamKey, val value: JetsamValue)

data class JetsamGossip(val entries: List<JetsamEntry>)

object JetsamGossipManager {
    // In-memory map to simulate DHT
    private val dht = mutableMapOf<JetsamKey, JetsamValue>()

    fun addEntry(key: JetsamKey, value: JetsamValue) {
        dht[key] = value
    }

    fun getEntry(key: JetsamKey): JetsamValue? = dht[key]

    fun gatherJetsam(): JetsamGossip = JetsamGossip(dht.map { (k, v) -> JetsamEntry(k, v) })

    // Simulate gossip/replication to another node
    fun replicateTo(target: JetsamGossipManager) {
        for ((key, value) in dht) {
            target.addEntry(key, value)
        }
    }
} 