package borg.trikeshed.jetsam

import kotlinx.coroutines.*

class JetsamDaemon(
    private val name: String,
    private val gossipManager: JetsamGossipManager = JetsamGossipManager(),
    private val peers: MutableList<JetsamDaemon> = mutableListOf()
) {
    private var job: Job? = null

    fun startGossiping(intervalMs: Long = 100) {
        job = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                for (peer in peers) {
                    gossipManager.replicateTo(peer.gossipManager)
                }
                delay(intervalMs)
            }
        }
    }

    fun stopGossiping() {
        job?.cancel()
    }

    fun addPeer(peer: JetsamDaemon) {
        peers.add(peer)
    }

    fun addEntry(key: JetsamKey, value: JetsamValue) {
        gossipManager.addEntry(key, value)
    }

    fun getEntry(key: JetsamKey): JetsamValue? = gossipManager.getEntry(key)
} 