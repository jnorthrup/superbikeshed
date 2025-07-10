#!/usr/bin/env kotlin

@file:DependsOn("io.ktor:ktor-server-netty:2.3.7")
@file:DependsOn("io.ktor:ktor-server-content-negotiation:2.3.7")
@file:DependsOn("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
@file:DependsOn("io.ktor:ktor-server-cors:2.3.7")
@file:DependsOn("io.ktor:ktor-server-websockets:2.3.7")

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.*
import java.time.Instant
import kotlin.random.Random

@Serializable
data class WorkerStatus(
    val id: String,
    val type: String,
    val hashRate: Int,
    val status: String,
    val tokensMinedTotal: Int,
    val lastUpdate: String
)

@Serializable
data class MiningStats(
    val totalHashRate: Int,
    val totalTokens: Int,
    val activeWorkers: Int,
    val miningCycles: Int,
    val timestamp: String
)

@Serializable
data class DashboardData(
    val workers: List<WorkerStatus>,
    val stats: MiningStats
)

val workers = mutableListOf(
    WorkerStatus("NEXUS-001", "NEXUS_PROCESS_ANALYSIS", 0, "IDLE", 0, Instant.now().toString()),
    WorkerStatus("GOAL-001", "GOAL_STRUCTURING_CONSULTANT", 0, "IDLE", 0, Instant.now().toString()),
    WorkerStatus("ATTN-001", "ATTENTION_AGGREGATION", 0, "IDLE", 0, Instant.now().toString()),
    WorkerStatus("RSRC-001", "RESOURCE_ALLOCATION", 0, "IDLE", 0, Instant.now().toString()),
    WorkerStatus("CMPL-001", "COMPLIANCE_VALIDATION", 0, "IDLE", 0, Instant.now().toString()),
    WorkerStatus("RISK-001", "RISK_ASSESSMENT", 0, "IDLE", 0, Instant.now().toString()),
    WorkerStatus("PTRN-001", "PATTERN_RECOGNITION", 0, "IDLE", 0, Instant.now().toString()),
    WorkerStatus("DCSN-001", "DECISION_SYNTHESIS", 0, "IDLE", 0, Instant.now().toString())
)

var totalTokens = 0
var miningCycles = 0

fun main() {
    // Start mining simulation in background
    GlobalScope.launch {
        while (true) {
            miningCycles++
            workers.forEach { worker ->
                worker.hashRate = Random.nextInt(100, 500)
                worker.status = if (Random.nextBoolean()) "MINING" else "PROCESSING"
                val newTokens = worker.hashRate / 10
                worker.tokensMinedTotal += newTokens
                totalTokens += newTokens
                worker.lastUpdate = Instant.now().toString()
            }
            delay(2000)
        }
    }
    
    embeddedServer(Netty, port = 8080) {
        install(ContentNegotiation) {
            json(Json { prettyPrint = true })
        }
        install(CORS) {
            anyHost()
        }
        install(WebSockets)
        
        routing {
            get("/") {
                call.respondText(getDashboardHtml(), io.ktor.http.ContentType.Text.Html)
            }
            
            get("/api/status") {
                val stats = MiningStats(
                    totalHashRate = workers.sumOf { it.hashRate },
                    totalTokens = totalTokens,
                    activeWorkers = workers.count { it.status == "MINING" },
                    miningCycles = miningCycles,
                    timestamp = Instant.now().toString()
                )
                call.respond(DashboardData(workers, stats))
            }
            
            webSocket("/ws") {
                while (true) {
                    val stats = MiningStats(
                        totalHashRate = workers.sumOf { it.hashRate },
                        totalTokens = totalTokens,
                        activeWorkers = workers.count { it.status == "MINING" },
                        miningCycles = miningCycles,
                        timestamp = Instant.now().toString()
                    )
                    val data = Json.encodeToString(DashboardData.serializer(), DashboardData(workers, stats))
                    send(Frame.Text(data))
                    delay(1000)
                }
            }
        }
    }.start(wait = true)
}

fun getDashboardHtml() = """
<!DOCTYPE html>
<html>
<head>
    <title>Fiduciary Mining RPC Dashboard</title>
    <style>
        body { 
            font-family: 'Courier New', monospace; 
            background: #0a0a0a; 
            color: #00ff00; 
            padding: 20px;
        }
        h1 { 
            text-align: center; 
            color: #00ff00;
            text-shadow: 0 0 10px #00ff00;
        }
        .stats {
            display: grid;
            grid-template-columns: repeat(4, 1fr);
            gap: 20px;
            margin: 20px 0;
        }
        .stat-box {
            background: #1a1a1a;
            border: 2px solid #00ff00;
            padding: 20px;
            text-align: center;
            box-shadow: 0 0 20px rgba(0,255,0,0.3);
        }
        .stat-value {
            font-size: 2em;
            font-weight: bold;
            color: #00ff00;
        }
        .workers {
            margin-top: 30px;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            background: #1a1a1a;
        }
        th, td {
            padding: 10px;
            text-align: left;
            border: 1px solid #00ff00;
        }
        th {
            background: #0f0f0f;
            color: #00ff00;
        }
        .mining { color: #ffff00; }
        .processing { color: #00ffff; }
        .idle { color: #666; }
    </style>
</head>
<body>
    <h1>⛏️ FIDUCIARY MINING RPC DASHBOARD ⛏️</h1>
    
    <div class="stats">
        <div class="stat-box">
            <div>Total Hash Rate</div>
            <div class="stat-value" id="hashRate">0</div>
            <div>ops/sec</div>
        </div>
        <div class="stat-box">
            <div>Total Tokens</div>
            <div class="stat-value" id="tokens">0</div>
            <div>FID</div>
        </div>
        <div class="stat-box">
            <div>Active Workers</div>
            <div class="stat-value" id="activeWorkers">0</div>
            <div>mining</div>
        </div>
        <div class="stat-box">
            <div>Mining Cycles</div>
            <div class="stat-value" id="cycles">0</div>
            <div>completed</div>
        </div>
    </div>
    
    <div class="workers">
        <h2>Worker Pool Status</h2>
        <table id="workersTable">
            <thead>
                <tr>
                    <th>Worker ID</th>
                    <th>Type</th>
                    <th>Status</th>
                    <th>Hash Rate</th>
                    <th>Tokens Mined</th>
                    <th>Last Update</th>
                </tr>
            </thead>
            <tbody id="workersBody">
            </tbody>
        </table>
    </div>
    
    <script>
        const ws = new WebSocket('ws://localhost:8080/ws');
        
        ws.onmessage = (event) => {
            const data = JSON.parse(event.data);
            
            // Update stats
            document.getElementById('hashRate').textContent = data.stats.totalHashRate;
            document.getElementById('tokens').textContent = data.stats.totalTokens;
            document.getElementById('activeWorkers').textContent = data.stats.activeWorkers;
            document.getElementById('cycles').textContent = data.stats.miningCycles;
            
            // Update workers table
            const tbody = document.getElementById('workersBody');
            tbody.innerHTML = '';
            
            data.workers.forEach(worker => {
                const row = tbody.insertRow();
                row.innerHTML = `
                    <td>$\{worker.id}</td>
                    <td>$\{worker.type}</td>
                    <td class="$\{worker.status.toLowerCase()}">$\{worker.status}</td>
                    <td>$\{worker.hashRate} ops/sec</td>
                    <td>$\{worker.tokensMinedTotal} FID</td>
                    <td>$\{new Date(worker.lastUpdate).toLocaleTimeString()}</td>
                `;
            });
        };
        
        // Fallback polling if WebSocket fails
        setInterval(async () => {
            if (ws.readyState !== WebSocket.OPEN) {
                const response = await fetch('/api/status');
                const data = await response.json();
                // Update UI same as WebSocket
            }
        }, 2000);
    </script>
</body>
</html>
""".trimIndent()

main()