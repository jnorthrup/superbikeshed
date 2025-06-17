package gk.kademlia.bitswap

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Represents a want entry with additional tracking
data class WantEntry(
    val cid: CID,
    val priority: Int,
    // Peers we've sent this want to and are awaiting response/block
    val sentToPeers: MutableSet<String> = mutableSetOf() // Assuming PeerID is String for now
)

class WantManager {
    private val mutex = Mutex()
    // Active wants: CID mapped to its WantEntry details
    private val activeWants = mutableMapOf<CID, WantEntry>()

    // Flow to emit CIDs that have been successfully received and processed by the client of WantManager
    private val _blockReceivedFlow = MutableSharedFlow<CID>(replay = 0, extraBufferCapacity = 1) // Use tryEmit
    val blockReceivedFlow = _blockReceivedFlow.asSharedFlow()

    // Flow to emit wantlist changes (CID, add/cancel, priority) for network broadcast
    // Wantlist.Entry directly represents the change to be broadcast.
    private val _wantlistChangesFlow = MutableSharedFlow<Wantlist.Entry>(replay = 0, extraBufferCapacity = 5) // Use tryEmit
    val wantlistChangesFlow = _wantlistChangesFlow.asSharedFlow()

    suspend fun wantBlock(cid: CID, priority: Int = 1) {
        mutex.withLock {
            val existingWant = activeWants[cid]
            if (existingWant == null) {
                activeWants[cid] = WantEntry(cid, priority)
                val emitted = _wantlistChangesFlow.tryEmit(Wantlist.Entry(cid, priority, cancel = false, wantType = Wantlist.WantType.Block, sendDontHave = true))
                println("WantManager: Added want for $cid with priority $priority. Emit success: $emitted")
            } else if (existingWant.priority != priority) {
                // Priority has changed, update and re-broadcast
                activeWants[cid] = existingWant.copy(priority = priority)
                val emitted = _wantlistChangesFlow.tryEmit(Wantlist.Entry(cid, priority, cancel = false, wantType = Wantlist.WantType.Block, sendDontHave = true))
                 println("WantManager: Updated want for $cid to priority $priority. Emit success: $emitted")
            }
             else {
                // Want already exists with same priority
                println("WantManager: Want for $cid already exists with priority $priority. No change.")
            }
        }
    }

    suspend fun cancelWant(cid: CID) {
        mutex.withLock {
            if (activeWants.containsKey(cid)) {
                activeWants.remove(cid)
                // Emit change for network layer to broadcast a cancel
                val emitted = _wantlistChangesFlow.tryEmit(Wantlist.Entry(cid, priority = 0 /*priority irrelevant for cancel*/, cancel = true))
                println("WantManager: Cancelled want for $cid. Emit success: $emitted")
            } else {
                 println("WantManager: No active want for $cid to cancel.")
            }
        }
    }

    /**
     * Called when a block corresponding to a CID is successfully received and stored.
     * This removes the CID from active wants and notifies listeners.
     */
    suspend fun blockReceived(cid: CID) {
        mutex.withLock {
            if (activeWants.containsKey(cid)) {
                activeWants.remove(cid)
                val emitted = _blockReceivedFlow.tryEmit(cid)
                // The engine part would typically send CANCEL messages to peers it asked.
                // WantManager itself does not send network messages.
                println("WantManager: Block received for $cid, removed from active wants. Emit success: $emitted")
            } else {
                println("WantManager: Block received for $cid, but it was not in active wants (or already removed).")
            }
        }
    }

    suspend fun getActiveWants(): List<Wantlist.Entry> {
        return mutex.withLock {
            activeWants.values.map {
                Wantlist.Entry(
                    block = it.cid,
                    priority = it.priority,
                    cancel = false, // Active wants are never cancels themselves
                    wantType = Wantlist.WantType.Block, // Assuming active wants are for Blocks
                    sendDontHave = true // Typically true for active wants until first HAVE/block received
                )
            }
        }
    }

    suspend fun getWantEntry(cid: CID): WantEntry? {
        return mutex.withLock { activeWants[cid] }
    }

    suspend fun addSentToPeer(cid: CID, peerId: String) {
        mutex.withLock {
            activeWants[cid]?.sentToPeers?.add(peerId)
            println("WantManager: Marked want for $cid as sent to peer $peerId.")
        }
    }

    suspend fun removeSentToPeer(cid: CID, peerId: String): Boolean {
       return mutex.withLock {
            val removed = activeWants[cid]?.sentToPeers?.remove(peerId) ?: false
            if(removed) println("WantManager: Unmarked want for $cid as sent to peer $peerId (e.g. due to response).")
            removed
        }
    }

    // For testing or specific scenarios
    suspend fun hasActiveWant(cid: CID): Boolean {
        return mutex.withLock { activeWants.containsKey(cid) }
    }
}
