import kotlin.test.*
import kotlinx.coroutines.*
import borg.trikeshed.jetsam.*

class JetsamDaemonTest {
    @Test
    fun testJetsamDaemonGossipPropagation() = runBlocking {
        val daemonA = JetsamDaemon("A")
        val daemonB = JetsamDaemon("B")
        daemonA.addPeer(daemonB)
        daemonB.addPeer(daemonA)

        val key = "shared-key"
        val value = mapOf("ipfs" to "cid999", "couch" to "rev888")
        daemonA.addEntry(key, value)

        daemonA.startGossiping(50)
        daemonB.startGossiping(50)

        // Wait for gossip to propagate
        delay(200)

        val received = daemonB.getEntry(key)
        assertNotNull(received)
        assertEquals("cid999", received["ipfs"])
        assertEquals("rev888", received["couch"])

        daemonA.stopGossiping()
        daemonB.stopGossiping()
    }
} 