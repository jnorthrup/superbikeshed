package borg.trikeshed.gossip

import borg.trikeshed.core.*
import borg.trikeshed.net.quic.*
import borg.trikeshed.orchestration.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import borg.trikeshed.lib.Series
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.html.*
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLElement
import kotlinx.browser.document
import kotlinx.browser.window

// Core Types
@Serializable
data class GossipEvent(
    val timestamp: Long,
    val source: String,
    val topic: String,
    val content: String,
    val confidence: Double = 1.0
)

@JvmInline
value class SubnetToken(val value: String)

// Visualizer Component
class GossipVisualizer(private val agent: ConcentricSubnetAgent) {
    private val container: HTMLElement = document.getElementById("gossip-container") as HTMLElement

    init {
        setupUI()
        observeGossipEvents()
    }

    private fun setupUI() {
        container.innerHTML = """
            <div class="gossip-visualizer">
                <div class="header">
                    <h2>Gossip Events</h2>
                    <div class="controls">
                        <button id="clear-events">Clear</button>
                        <button id="export-events">Export</button>
                    </div>
                </div>
                <div class="events-container" id="events-list"></div>
            </div>
        """.trimIndent()

        // Add event listeners
        document.getElementById("clear-events")?.addEventListener("click", { clearEvents() })
        document.getElementById("export-events")?.addEventListener("click", { exportEvents() })
    }

    private fun observeGossipEvents() {
        agent.gossipEventsFlow.map { events ->
            events.materialize().sortedByDescending { it.timestamp }
        }.collect { events ->
            updateEventsList(events)
        }
    }

    private fun updateEventsList(events: List<GossipEvent>) {
        val eventsList = document.getElementById("events-list") as HTMLElement
        eventsList.innerHTML = events.joinToString("\n") { event ->
            """
            <div class="gossip-event ${if (event.confidence > 0.8) "high-confidence" else ""}">
                <div class="event-header">
                    <span class="timestamp">${formatTimestamp(event.timestamp)}</span>
                    <span class="source">${event.source}</span>
                    <span class="confidence">${(event.confidence * 100).toInt()}%</span>
                </div>
                <div class="event-content">
                    <div class="topic">${event.topic}</div>
                    <div class="content">${event.content}</div>
                </div>
            </div>
            """.trimIndent()
        }
    }

    private fun clearEvents() {
        val eventsList = document.getElementById("events-list") as HTMLElement
        eventsList.innerHTML = ""
    }

    private fun exportEvents() {
        val events = agent.gossipEventsFlow.value.materialize()
        val json = JSON.stringify(events)
        val blob = Blob(arrayOf(json), BlobPropertyBag("application/json"))
        val url = URL.createObjectURL(blob)
        
        val a = document.createElement("a")
        a.href = url
        a.download = "gossip-events-${System.currentTimeMillis()}.json"
        a.click()
        
        URL.revokeObjectURL(url)
    }

    private fun formatTimestamp(timestamp: Long): String {
        return window.Date(timestamp).toLocaleString()
    }
}

// Add some basic styles
document.head?.appendChild(document.createElement("style").apply {
    textContent = """
        .gossip-visualizer {
            font-family: system-ui, -apple-system, sans-serif;
            max-width: 800px;
            margin: 0 auto;
            padding: 20px;
        }
        
        .header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 20px;
        }
        
        .controls button {
            margin-left: 10px;
            padding: 8px 16px;
            border: none;
            border-radius: 4px;
            background: #007bff;
            color: white;
            cursor: pointer;
        }
        
        .gossip-event {
            border: 1px solid #ddd;
            border-radius: 4px;
            padding: 12px;
            margin-bottom: 12px;
            background: white;
        }
        
        .high-confidence {
            border-left: 4px solid #28a745;
        }
        
        .event-header {
            display: flex;
            justify-content: space-between;
            margin-bottom: 8px;
            color: #666;
            font-size: 0.9em;
        }
        
        .event-content {
            color: #333;
        }
        
        .topic {
            font-weight: bold;
            margin-bottom: 4px;
        }
        
        .content {
            white-space: pre-wrap;
        }
    """.trimIndent()
})

// Main entry point
fun main() = runBlocking {
    // Create minimal services
    val ipfsService = object : IpfsPubSubService {
        override suspend fun publish(topic: String, data: String) {
            println("Published to $topic: $data")
        }
        
        override suspend fun subscribe(topic: String, handler: (String) -> Unit): Job? {
            println("Subscribed to $topic")
            return null
        }
        
        override fun unsubscribe(topic: String) {
            println("Unsubscribed from $topic")
        }
    }
    
    val quicService = object : QuicNetworkService {
        override val key: CoroutineContext.Key<*> get() = QuicNetworkServiceKey
        override val isBound: Boolean get() = true
        override val localAddress: NetworkAddress? get() = Join("localhost", 8080)
        
        override suspend fun bind(localAddress: NetworkAddress?): NetworkAddress {
            return Join("localhost", 8080)
        }
        
        override suspend fun send(packet: DatagramPacket) {
            println("Sent packet to ${packet.address}: ${String(packet.data)}")
        }
        
        override suspend fun receive(bufferSize: Int): DatagramPacket {
            return Join(ByteArray(0), Join("localhost", 8080))
        }
        
        override suspend fun resolve(hostname: String, port: Int): Series<NetworkAddress> {
            return seriesOf(Join(hostname, port))
        }
        
        override suspend fun close() {
            println("Closed QUIC service")
        }
    }
    
    // Create and start visualizer
    val visualizer = GossipVisualizer(
        agent = ConcentricSubnetAgent(
            scope = this,
            ipfsPubSubService = ipfsService,
            quicNetworkService = quicService,
            subnetToken = SubnetToken("test-token")
        )
    )
    
    visualizer.start()
    
    // Keep running
    while (true) {
        delay(1000)
    }
} 