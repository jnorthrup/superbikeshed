/**
 * Ultra-Minimal WASM Kademlia Agent
 * 
 * Focus: What WASM does best - fast, sandboxed micro-computations
 * Let JVM handle the heavy coordination and context management
 */

/**
 * Simple WASM micro-agent for Kademlia bus
 */
class WASMAgent(val id: String, val keysize: Int = 2) {
    
    fun processMessage(type: String, data: String): String {
        return when (type) {
            "PING" -> "PONG:$id"
            "ROUTE" -> routeMessage(data)  
            "COMPUTE" -> computeTensor(data)
            else -> "UNKNOWN:$type"
        }
    }
    
    private fun routeMessage(targetId: String): String {
        val distance = targetId.hashCode() xor id.hashCode()
        return "ROUTE_RESULT:distance=$distance,keysize=$keysize"
    }
    
    private fun computeTensor(data: String): String {
        val values = data.split(",").mapNotNull { it.toDoubleOrNull() }
        val result = values.map { it * 2.0 } // Simple tensor op
        return "COMPUTE_RESULT:${result.joinToString(",")}"
    }
}

/**
 * WASM Micro-Swarm - manages lightweight agents
 */
object WASMSwarm {
    private val agents = mutableMapOf<String, WASMAgent>()
    
    fun spawn(id: String, keysize: Int = 2): WASMAgent {
        val agent = WASMAgent(id, keysize)
        agents[id] = agent
        console.log("🧩 WASM micro-agent spawned: $id")
        return agent
    }
    
    fun route(agentId: String, type: String, data: String): String? {
        return agents[agentId]?.processMessage(type, data)
    }
    
    fun getAgentCount(): Int = agents.size
}

/**
 * Main entry point - demonstrates WASM micro-agents
 */
fun main() {
    console.log("🚀 WASM Micro-Agents on Kademlia Bus")
    
    // Spawn micro-agents for 2-bit subnet (3 max agents)
    val alpha = WASMSwarm.spawn("micro-alpha", 2)
    val beta = WASMSwarm.spawn("micro-beta", 2) 
    val gamma = WASMSwarm.spawn("micro-gamma", 2)
    
    // Test routing
    val routeResult = alpha.processMessage("ROUTE", "target-node-xyz")
    console.log("📡 Route: $routeResult")
    
    // Test computation
    val computeResult = beta.processMessage("COMPUTE", "1.0,2.0,3.0,4.0")
    console.log("🔢 Compute: $computeResult")
    
    // Test ping
    val pingResult = gamma.processMessage("PING", "")
    console.log("🏓 Ping: $pingResult")
    
    console.log("✅ WASM micro-agents operational (${WASMSwarm.getAgentCount()} agents)")
}

// Browser console
external object console {
    fun log(message: String)
}