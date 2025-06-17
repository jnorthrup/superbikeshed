package gk.kademlia.bitswap

interface BlockStore {
    suspend fun get(cid: CID): ByteArray?
    suspend fun put(cid: CID, data: ByteArray): Boolean // Returns true on success
    suspend fun has(cid: CID): Boolean
}

// Dummy implementation for testing/early integration
class InMemoryBlockStore : BlockStore {
    private val store = mutableMapOf<CID, ByteArray>()

    override suspend fun get(cid: CID): ByteArray? {
        println("InMemoryBlockStore: GET request for CID: $cid")
        val data = store[cid]
        if (data != null) {
            println("InMemoryBlockStore: Found CID: $cid, Data size: ${data.size}")
        } else {
            println("InMemoryBlockStore: CID not found: $cid")
        }
        return data
    }

    override suspend fun put(cid: CID, data: ByteArray): Boolean {
        println("InMemoryBlockStore: PUT request for CID: $cid, Data size: ${data.size}")
        store[cid] = data
        // In a real store, one might verify if the data matches the CID's hash.
        // For InMemory, we assume valid data is given.
        return true
    }

    override suspend fun has(cid: CID): Boolean {
        val exists = store.containsKey(cid)
        println("InMemoryBlockStore: HAS check for CID: $cid, Exists: $exists")
        return exists
    }

    fun allCids(): Set<CID> = store.keys.toSet()

    fun clear() {
        println("InMemoryBlockStore: Clearing all blocks.")
        store.clear()
    }
}
