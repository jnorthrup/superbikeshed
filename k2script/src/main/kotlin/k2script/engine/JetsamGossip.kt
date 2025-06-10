package k2script.engine

import borg.trikeshed.jetsam.JetsamGossipManager
import borg.trikeshed.jetsam.JetsamGossip

// Re-export Jetsam types for convenience
typealias JetsamKey = borg.trikeshed.jetsam.JetsamKey
typealias JetsamValue = borg.trikeshed.jetsam.JetsamValue
typealias JetsamEntry = borg.trikeshed.jetsam.JetsamEntry
typealias JetsamPool = borg.trikeshed.jetsam.JetsamPool

object JetsamGossip {
    fun gatherJetsam(): JetsamGossip = JetsamGossipManager.gatherJetsam()
    
    fun gossipToCouch(jetsam: JetsamGossip) = JetsamGossipManager.gossipToCouch(jetsam)
    
    fun gossipToIPFS(jetsam: JetsamGossip) = JetsamGossipManager.gossipToIPFS(jetsam)
} 