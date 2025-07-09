package fiduciary.percolator

import borg.trikeshed.rest.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.*
import kotlinx.datetime.*
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Percolator Coordinator Server
 * 
 * Run this to start the coordinator that manages the volunteer network
 */
fun main() {
    val coordinator = PercolatorCoordinator()
    val server = PercolatorCoordinatorServer(coordinator)
    
    // Bootstrap with Patrick Devine archives
    server.bootstrapWork()
    
    // Start server
    server.start()
}

class PercolatorCoordinatorServer(
    private val coordinator: PercolatorCoordinator,
    private val port: Int = 8888
) {
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    fun start() {
        embeddedServer(Netty, port = port) {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                json(json)
            }
            
            install(io.ktor.server.plugins.cors.routing.CORS) {
                anyHost()
                allowMethod(HttpMethod.Options)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowHeader("*")
            }
            
            install(io.ktor.server.plugins.statuspages.StatusPages) {
                exception<Throwable> { call, cause ->
                    call.respondText(
                        "Error: ${cause.message}", 
                        status = HttpStatusCode.InternalServerError
                    )
                }
            }
            
            routing {
                // Node endpoints
                post("/api/v1/node/register") {
                    val registration = call.receive<RegisterNode>()
                    val nodeInfo = NodeInfo(
                        nodeId = registration.nodeId,
                        capabilities = registration.capabilities,
                        status = NodeStatus.ONLINE,
                        lastSeen = Clock.System.now()
                    )
                    coordinator.updateNodeStatus(NodeStatus(
                        nodeId = registration.nodeId,
                        timestamp = Clock.System.now(),
                        activeWork = 0,
                        completedWork = 0,
                        cpuUsage = 0.0,
                        memoryUsage = 0.0
                    ))
                    call.respond(HttpStatusCode.OK, mapOf("status" to "registered"))
                }
                
                post("/api/v1/work/claim") {
                    val nodeId = call.request.header("X-Node-Id") ?: "unknown"
                    val work = coordinator.claimWork(nodeId)
                    
                    if (work != null) {
                        call.respond(work)
                    } else {
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
                
                post("/api/v1/work/progress/{workId}") {
                    val progress = call.receive<WorkProgress>()
                    // Track progress
                    call.respond(HttpStatusCode.OK, mapOf("status" to "acknowledged"))
                }
                
                post("/api/v1/work/complete/{workId}") {
                    val result = call.receive<WorkResult>()
                    saveResults(result)
                    
                    // Calculate reward
                    val reward = calculateReward(result.stats)
                    call.respond(HttpStatusCode.OK, mapOf(
                        "status" to "completed",
                        "reward" to reward.points,
                        "reason" to reward.reason
                    ))
                }
                
                post("/api/v1/node/heartbeat") {
                    val status = call.receive<NodeStatus>()
                    coordinator.updateNodeStatus(status)
                    call.respond(HttpStatusCode.OK)
                }
                
                // Public endpoints
                get("/stats") {
                    val stats = coordinator.getNetworkStats()
                    call.respond(stats)
                }
                
                get("/") {
                    call.respondText("""
                        <html>
                        <head>
                            <title>Percolator Network</title>
                            <style>
                                body { font-family: monospace; padding: 40px; background: #1a1a1a; color: #0f0; }
                                h1 { color: #0ff; }
                                .stats { background: #222; padding: 20px; border-radius: 8px; }
                                .stat { margin: 10px 0; }
                                a { color: #0ff; }
                            </style>
                            <meta http-equiv="refresh" content="5">
                        </head>
                        <body>
                            <h1>🌊 Content Percolator Network</h1>
                            <div class="stats" id="stats">Loading...</div>
                            
                            <h2>Join the Network</h2>
                            <pre>java -jar percolator-node.jar --coordinator=http://localhost:$port</pre>
                            
                            <h2>Current Work</h2>
                            <div id="work">Loading...</div>
                            
                            <script>
                                async function updateStats() {
                                    const stats = await fetch('/stats').then(r => r.json());
                                    document.getElementById('stats').innerHTML = `
                                        <div class="stat">Active Nodes: ${'$'}{stats.activeNodes} / ${'$'}{stats.totalNodes}</div>
                                        <div class="stat">Pending Work: ${'$'}{stats.pendingWork} units</div>
                                        <div class="stat">In Progress: ${'$'}{stats.claimedWork} units</div>
                                        <div class="stat">Completed: ${'$'}{stats.completedWork} units</div>
                                    `;
                                }
                                
                                updateStats();
                                setInterval(updateStats, 5000);
                            </script>
                        </body>
                        </html>
                    """.trimIndent(), ContentType.Text.Html)
                }
            }
        }.start(wait = true)
        
        println("""
        ╔════════════════════════════════════════════╗
        ║       PERCOLATOR COORDINATOR RUNNING       ║
        ╚════════════════════════════════════════════╝
        
        Dashboard: http://localhost:$port
        API Base: http://localhost:$port/api/v1
        
        Volunteers can connect with:
        java -jar percolator-node.jar --coordinator=http://localhost:$port
        """.trimIndent())
    }
    
    /**
     * Bootstrap with Patrick Devine archives
     */
    fun bootstrapWork() {
        // Add work units from our previous extraction
        val patrickDevineWork = listOf(
            WorkUnit(
                id = "patrick_devine_transcripts",
                archiveUrl = "https://archive.org/download/patrickdevinefiles/Patrick%20Devine%20files.zip",
                entries = listOf(
                    FileEntry(
                        path = "Patrick Devine files/01-Transcripts/patrick_0720.txt",
                        offset = 3050270003L,
                        compressedSize = 9612,
                        uncompressedSize = 25389,
                        method = 8
                    )
                    // Add more transcript files here
                ),
                priority = 1.0
            ),
            WorkUnit(
                id = "patrick_devine_calls_batch1",
                archiveUrl = "https://archive.org/download/patrickdevinecalls/Patrick%20Devine%20Calls.zip",
                entries = listOf(
                    // Add audio file entries
                ),
                priority = 0.8
            )
        )
        
        patrickDevineWork.forEach { work ->
            coordinator.addWork(work.archiveUrl, work.entries)
        }
        
        println("📦 Bootstrapped ${patrickDevineWork.size} work units")
    }
    
    /**
     * Save processed results
     */
    private fun saveResults(result: WorkResult) {
        val resultsDir = File("./percolator-results/${result.workUnitId}")
        resultsDir.mkdirs()
        
        result.results.forEach { content ->
            val filename = content.fileEntry.path.substringAfterLast('/')
            val resultFile = File(resultsDir, "$filename.json")
            resultFile.writeText(json.encodeToString(content))
        }
        
        println("💾 Saved ${result.results.size} results for ${result.workUnitId}")
    }
}