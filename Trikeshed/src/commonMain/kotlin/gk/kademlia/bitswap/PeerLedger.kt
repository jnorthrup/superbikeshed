package gk.kademlia.bitswap

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock // For Clock.System.now()

// Assuming PeerID is String for now. This should be a more specific type in a full libp2p integration.
typealias PeerID = String

class PeerLedger(val peerId: PeerID) {
    private val mutex = Mutex()

    // Blocks this peer wants from us (our node)
    private val peerWantList = mutableSetOf<CID>()

    // A simple accounting of blocks exchanged.
    // Positive means we sent more unique bytes to them than they sent to us.
    // Negative means they sent more unique bytes to us.
    private var bytesExchangedBalance: Long = 0

    // Timestamp of the last message received from this peer
    var lastMessageTime: Long = 0L // Initialize with current time on creation or first message?
                                   // Setting to 0L implies "never seen" or "not yet interacted".

    suspend fun updateLastMessageTime() {
        // For now, use a simple function. Could be integrated with message handling.
        lastMessageTime = Clock.System.now().toEpochMilliseconds()
        // println("PeerLedger[${peerId}]: Updated lastMessageTime to $lastMessageTime") // Optional: verbose logging
    }

    // --- Methods for managing what the peer wants from us ---
    suspend fun addWants(cids: Collection<CID>) {
        mutex.withLock {
            val newWants = cids.filter { peerWantList.add(it) }
            if (newWants.isNotEmpty()) {
                 println("PeerLedger[${peerId}]: Added ${newWants.size} new wants (total: ${peerWantList.size}). New: $newWants")
            }
        }
    }

    suspend fun removeWants(cids: Collection<CID>) {
        mutex.withLock {
            val removedWants = cids.filter { peerWantList.remove(it) }
            if (removedWants.isNotEmpty()) {
                println("PeerLedger[${peerId}]: Removed ${removedWants.size} wants (total: ${peerWantList.size}). Removed: $removedWants")
            }
        }
    }

    suspend fun getPeerWants(): Set<CID> {
        return mutex.withLock { peerWantList.toSet() }
    }

    suspend fun clearPeerWants() {
        mutex.withLock {
            if (peerWantList.isNotEmpty()) {
                peerWantList.clear()
                println("PeerLedger[${peerId}]: Cleared all wants.")
            }
        }
    }

    // --- Methods for accounting ---
    suspend fun blockSentToPeer(blockSize: Int) {
        mutex.withLock {
            bytesExchangedBalance += blockSize
            println("PeerLedger[${peerId}]: Sent block of size $blockSize. Balance: $bytesExchangedBalance")
        }
    }

    suspend fun blockReceivedFromPeer(blockSize: Int) {
        mutex.withLock {
            bytesExchangedBalance -= blockSize
            println("PeerLedger[${peerId}]: Received block of size $blockSize. Balance: $bytesExchangedBalance")
        }
    }

    suspend fun getBytesExchangedBalance(): Long {
        return mutex.withLock { bytesExchangedBalance }
    }

    override fun toString(): String {
        // Accessing these properties outside a lock is okay if toString is for informational/debug purposes
        // and slight inconsistencies are acceptable. For strict consistency, lock would be needed.
        return "PeerLedger(peerId='$peerId', wantsFromUs=${peerWantList.size}, balance=$bytesExchangedBalance, lastMsgTime=$lastMessageTime)"
    }
}
