package fiduciary.demo

import fiduciary.concentric.*
import borg.trikeshed.dht.kademlia.id.NUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock

/**
 * NUID-Based Concentric Network Demo
 * 
 * Demonstrates how NUID proximity determines semantic scope and visibility
 * in the concentric network architecture.
 */

suspend fun runNUIDConcentricDemo() {
    println("🌐 NUID-Based Concentric Network Demo")
    println("=====================================")
    
    val topology = NUIDConcentricTopology()
    
    // Start listening to agent events
    launch {
        topology.agentEvents.collect { event ->
            when (event) {
                is AgentEvent.AgentRegistered -> {
                    println("✅ Agent registered: ${event.agent.id} in ${event.agent.ring} (scope: ${event.agent.getSemanticScope()}-hop)")
                }
                is AgentEvent.SemanticSubnetCreated -> {
                    println("🔗 Semantic subnet created: '${event.semanticTag}' with NUID ${event.subnetNUID}")
                }
                is AgentEvent.AgentUnregistered -> {
                    println("❌ Agent unregistered: ${event.agent.id}")
                }
            }
        }
    }
    
    // Create agents across different rings
    println("\n📋 Creating agents across concentric rings...")
    
    val agents = createDemoAgents()
    agents.forEach { agent ->
        topology.registerAgent(agent)
        delay(100) // Simulate registration delay
    }
    
    // Demonstrate NUID-based visibility
    println("\n👁️  Demonstrating NUID-based visibility...")
    demonstrateNUIDVisibility(topology, agents)
    
    // Create semantic subnets
    println("\n🏷️  Creating semantic subnets...")
    createSemanticSubnets(topology, agents)
    
    // Demonstrate task routing
    println("\n🎯 Demonstrating task routing...")
    demonstrateTaskRouting(topology)
    
    // Show topology statistics
    println("\n📊 Topology Statistics:")
    val stats = topology.getTopologyStats()
    printTopologyStats(stats)
    
    println("\n✅ NUID-Based Concentric Network Demo Complete!")
}

private fun createDemoAgents(): List<NUIDAgent> {
    val agents = mutableListOf<NUIDAgent>()
    
    // CORE agent (1-hop scope)
    agents.add(NUIDAgent(
        id = NUID.random(),
        ring = ConcentricRing.CORE,
        capabilities = setOf(AgentCapability.CONSENSUS_BUILDING, AgentCapability.SECURITY_ENFORCEMENT),
        semanticTags = setOf("governance", "consensus"),
        workload = 0.1
    ))
    
    // DYAD agents (2-hop scope)
    repeat(2) { i ->
        agents.add(NUIDAgent(
            id = NUID.random(),
            ring = ConcentricRing.DYAD,
            capabilities = setOf(AgentCapability.QUORUM_FORMATION, AgentCapability.TASK_COORDINATION),
            semanticTags = setOf("validation", "coordination"),
            workload = 0.2 + (i * 0.1)
        ))
    }
    
    // TRIAD agents (4-hop scope)
    repeat(3) { i ->
        agents.add(NUIDAgent(
            id = NUID.random(),
            ring = ConcentricRing.TRIAD,
            capabilities = setOf(AgentCapability.NLP_PROCESSING, AgentCapability.ENTITY_EXTRACTION),
            semanticTags = setOf("analysis", "nlp"),
            workload = 0.3 + (i * 0.1)
        ))
    }
    
    // PENTAD agents (8-hop scope)
    repeat(5) { i ->
        agents.add(NUIDAgent(
            id = NUID.random(),
            ring = ConcentricRing.PENTAD,
            capabilities = setOf(AgentCapability.CONTENT_INGESTION, AgentCapability.TRANSFORMATION),
            semanticTags = setOf("ingestion", "processing"),
            workload = 0.4 + (i * 0.1)
        ))
    }
    
    // DODECAD agents (16-hop scope)
    repeat(12) { i ->
        agents.add(NUIDAgent(
            id = NUID.random(),
            ring = ConcentricRing.DODECAD,
            capabilities = setOf(AgentCapability.ARCHIVE_PROCESSING, AgentCapability.RETRIEVAL_OPTIMIZATION),
            semanticTags = setOf("archive", "retrieval"),
            workload = 0.5 + (i * 0.05)
        ))
    }
    
    // SENATE agents (32-hop scope)
    repeat(24) { i ->
        agents.add(NUIDAgent(
            id = NUID.random(),
            ring = ConcentricRing.SENATE,
            capabilities = setOf(AgentCapability.DISTRIBUTION_COORDINATION, AgentCapability.REPLICATION_MANAGEMENT),
            semanticTags = setOf("distribution", "replication"),
            workload = 0.6 + (i * 0.02)
        ))
    }
    
    // CONGRESS agents (64-hop scope)
    repeat(100) { i ->
        agents.add(NUIDAgent(
            id = NUID.random(),
            ring = ConcentricRing.CONGRESS,
            capabilities = setOf(AgentCapability.MASS_DISTRIBUTION, AgentCapability.GLOBAL_SYNC),
            semanticTags = setOf("mass_distribution", "global_sync"),
            workload = 0.7 + (i * 0.01)
        ))
    }
    
    return agents
}

private suspend fun demonstrateNUIDVisibility(topology: NUIDConcentricTopology, agents: List<NUIDAgent>) {
    // Pick a CORE agent and show what it can see
    val coreAgent = agents.first { it.ring == ConcentricRing.CORE }
    val visibleToCore = topology.getAgentsInSemanticScope(coreAgent.id)
    println("🔍 CORE agent ${coreAgent.id} can see ${visibleToCore.size} agents (1-hop scope)")
    
    // Pick a SENATE agent and show what it can see
    val senateAgent = agents.first { it.ring == ConcentricRing.SENATE }
    val visibleToSenate = topology.getAgentsInSemanticScope(senateAgent.id)
    println("🔍 SENATE agent ${senateAgent.id} can see ${visibleToSenate.size} agents (32-hop scope)")
    
    // Pick a CONGRESS agent and show what it can see
    val congressAgent = agents.first { it.ring == ConcentricRing.CONGRESS }
    val visibleToCongress = topology.getAgentsInSemanticScope(congressAgent.id)
    println("🔍 CONGRESS agent ${congressAgent.id} can see ${visibleToCongress.size} agents (64-hop scope)")
    
    // Show NUID distance calculations
    val distance = topology.calculateNUIDDistance(coreAgent.id, senateAgent.id)
    println("📏 NUID distance between CORE and SENATE agents: $distance bits")
    
    val canSee = coreAgent.canSee(senateAgent)
    println("👁️  Can CORE agent see SENATE agent? $canSee")
}

private suspend fun createSemanticSubnets(topology: NUIDConcentricTopology, agents: List<NUIDAgent>) {
    // Create semantic subnet for "patrick_devine_analysis"
    val patrickAgents = agents.filter { 
        "analysis" in it.semanticTags || "nlp" in it.semanticTags 
    }.take(10).map { it.id }
    
    val patrickSubnetNUID = topology.createSemanticSubnet("patrick_devine_analysis", patrickAgents)
    println("🏷️  Created 'patrick_devine_analysis' subnet with NUID: $patrickSubnetNUID")
    
    // Create semantic subnet for "global_sync"
    val syncAgents = agents.filter { 
        "global_sync" in it.semanticTags 
    }.take(20).map { it.id }
    
    val syncSubnetNUID = topology.createSemanticSubnet("global_sync", syncAgents)
    println("🏷️  Created 'global_sync' subnet with NUID: $syncSubnetNUID")
    
    // Find agents by semantic tags
    val patrickAnalysisAgents = topology.findAgentsBySemantic("patrick_devine_analysis")
    println("🔍 Found ${patrickAnalysisAgents.size} agents in 'patrick_devine_analysis' subnet")
    
    val globalSyncAgents = topology.findAgentsBySemantic("global_sync")
    println("🔍 Found ${globalSyncAgents.size} agents in 'global_sync' subnet")
}

private suspend fun demonstrateTaskRouting(topology: NUIDConcentricTopology) {
    // Create different types of tasks
    val criticalTask = ConcentricTask(
        id = "critical-consensus-001",
        type = TaskType.CONSENSUS,
        requiredCapabilities = setOf(AgentCapability.CONSENSUS_BUILDING),
        priority = TaskPriority.CRITICAL,
        complexity = ComplexityLevel.SIMPLE,
        requiredAgents = 1,
        semanticTags = setOf("governance")
    )
    
    val analysisTask = ConcentricTask(
        id = "patrick-analysis-001",
        type = TaskType.ANALYSIS,
        requiredCapabilities = setOf(AgentCapability.NLP_PROCESSING, AgentCapability.ENTITY_EXTRACTION),
        priority = TaskPriority.HIGH,
        complexity = ComplexityLevel.COMPLEX,
        requiredAgents = 5,
        semanticTags = setOf("patrick_devine_analysis")
    )
    
    val distributionTask = ConcentricTask(
        id = "mass-distribution-001",
        type = TaskType.DISTRIBUTION,
        requiredCapabilities = setOf(AgentCapability.MASS_DISTRIBUTION),
        priority = TaskPriority.LOW,
        complexity = ComplexityLevel.MASSIVE,
        requiredAgents = 50,
        semanticTags = setOf("global_sync")
    )
    
    // Route tasks to optimal rings
    val criticalRing = topology.findOptimalRing(criticalTask)
    val criticalAgents = topology.routeTaskToRing(criticalTask, criticalRing)
    println("🎯 Critical task routed to ${criticalRing} ring: ${criticalAgents.size} agents selected")
    
    val analysisRing = topology.findOptimalRing(analysisTask)
    val analysisAgents = topology.routeTaskToRing(analysisTask, analysisRing)
    println("🎯 Analysis task routed to ${analysisRing} ring: ${analysisAgents.size} agents selected")
    
    val distributionRing = topology.findOptimalRing(distributionTask)
    val distributionAgents = topology.routeTaskToRing(distributionTask, distributionRing)
    println("🎯 Distribution task routed to ${distributionRing} ring: ${distributionAgents.size} agents selected")
}

private fun printTopologyStats(stats: TopologyStats) {
    println("📈 Total Agents: ${stats.totalAgents}")
    println("📈 Semantic Subnets: ${stats.semanticSubnetCount}")
    println("📈 Average NUID Distance: ${"%.2f".format(stats.avgNUIDDistance)} bits")
    println()
    
    println("📊 Ring Statistics:")
    stats.ringStats.forEach { (ring, ringStats) ->
        println("  ${ring.name}: ${ringStats.agentCount} agents, " +
                "avg workload: ${"%.2f".format(ringStats.avgWorkload)}, " +
                "scope: ${ring.nuidScope}-hop, " +
                "semantic subnets: ${ringStats.semanticSubnetCount}")
    }
}

// Main function for standalone demo
fun main() = runBlocking {
    runNUIDConcentricDemo()
} 