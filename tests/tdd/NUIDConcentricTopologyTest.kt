package tests.tdd

import fiduciary.concentric.*
import borg.trikeshed.dht.kademlia.id.NUID
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/**
 * TDD Tests for NUID-Based Concentric Network Topology
 * 
 * Tests the core functionality where NUID proximity determines semantic scope.
 */

class NUIDConcentricTopologyTest {
    
    @Test
    fun `test NUID distance calculation`() = runTest {
        val topology = NUIDConcentricTopology()
        
        // Create two NUIDs with known distance
        val nuid1 = NUID.random()
        val nuid2 = NUID.random()
        
        val distance = topology.calculateNUIDDistance(nuid1, nuid2)
        
        assertTrue(distance >= 0, "NUID distance should be non-negative")
        assertTrue(distance <= 256, "NUID distance should not exceed 256 bits")
        
        // Distance to self should be 0
        val selfDistance = topology.calculateNUIDDistance(nuid1, nuid1)
        assertEquals(0, selfDistance, "NUID distance to self should be 0")
    }
    
    @Test
    fun `test agent registration and retrieval`() = runTest {
        val topology = NUIDConcentricTopology()
        
        val agent = NUIDAgent(
            id = NUID.random(),
            ring = ConcentricRing.CORE,
            capabilities = setOf(AgentCapability.CONSENSUS_BUILDING),
            semanticTags = setOf("test"),
            workload = 0.5
        )
        
        topology.registerAgent(agent)
        
        val retrievedAgents = topology.getAgentsByRing(ConcentricRing.CORE)
        assertEquals(1, retrievedAgents.size, "Should retrieve exactly one CORE agent")
        assertEquals(agent.id, retrievedAgents[0].id, "Retrieved agent should match registered agent")
    }
    
    @Test
    fun `test NUID-based visibility scope`() = runTest {
        val topology = NUIDConcentricTopology()
        
        // Create agents with different scopes
        val coreAgent = NUIDAgent(
            id = NUID.random(),
            ring = ConcentricRing.CORE, // 1-hop scope
            capabilities = setOf(AgentCapability.CONSENSUS_BUILDING),
            semanticTags = setOf("core")
        )
        
        val senateAgent = NUIDAgent(
            id = NUID.random(),
            ring = ConcentricRing.SENATE, // 32-hop scope
            capabilities = setOf(AgentCapability.DISTRIBUTION_COORDINATION),
            semanticTags = setOf("senate")
        )
        
        val congressAgent = NUIDAgent(
            id = NUID.random(),
            ring = ConcentricRing.CONGRESS, // 64-hop scope
            capabilities = setOf(AgentCapability.MASS_DISTRIBUTION),
            semanticTags = setOf("congress")
        )
        
        topology.registerAgent(coreAgent)
        topology.registerAgent(senateAgent)
        topology.registerAgent(congressAgent)
        
        // Test visibility scopes
        val visibleToCore = topology.getAgentsInSemanticScope(coreAgent.id)
        assertEquals(1, visibleToCore.size, "CORE agent should see only itself (1-hop scope)")
        
        val visibleToSenate = topology.getAgentsInSemanticScope(senateAgent.id)
        assertTrue(visibleToSenate.size >= 2, "SENATE agent should see multiple agents (32-hop scope)")
        
        val visibleToCongress = topology.getAgentsInSemanticScope(congressAgent.id)
        assertTrue(visibleToCongress.size >= 3, "CONGRESS agent should see all agents (64-hop scope)")
    }
    
    @Test
    fun `test semantic subnet creation`() = runTest {
        val topology = NUIDConcentricTopology()
        
        // Create agents with semantic tags
        val agents = listOf(
            NUIDAgent(
                id = NUID.random(),
                ring = ConcentricRing.TRIAD,
                capabilities = setOf(AgentCapability.NLP_PROCESSING),
                semanticTags = setOf("analysis", "nlp")
            ),
            NUIDAgent(
                id = NUID.random(),
                ring = ConcentricRing.PENTAD,
                capabilities = setOf(AgentCapability.CONTENT_INGESTION),
                semanticTags = setOf("analysis", "ingestion")
            ),
            NUIDAgent(
                id = NUID.random(),
                ring = ConcentricRing.DODECAD,
                capabilities = setOf(AgentCapability.ARCHIVE_PROCESSING),
                semanticTags = setOf("archive")
            )
        )
        
        agents.forEach { topology.registerAgent(it) }
        
        // Create semantic subnet
        val analysisAgentIds = agents.filter { "analysis" in it.semanticTags }.map { it.id }
        val subnetNUID = topology.createSemanticSubnet("patrick_analysis", analysisAgentIds)
        
        // Verify subnet creation
        val subnetAgents = topology.findAgentsBySemantic("patrick_analysis")
        assertEquals(2, subnetAgents.size, "Should find 2 agents in patrick_analysis subnet")
        
        assertTrue(subnetAgents.all { "analysis" in it.semanticTags }, 
                  "All subnet agents should have 'analysis' semantic tag")
    }
    
    @Test
    fun `test task routing to optimal rings`() = runTest {
        val topology = NUIDConcentricTopology()
        
        // Create agents across different rings
        val coreAgent = NUIDAgent(
            id = NUID.random(),
            ring = ConcentricRing.CORE,
            capabilities = setOf(AgentCapability.CONSENSUS_BUILDING),
            semanticTags = setOf("consensus")
        )
        
        val triadAgent = NUIDAgent(
            id = NUID.random(),
            ring = ConcentricRing.TRIAD,
            capabilities = setOf(AgentCapability.NLP_PROCESSING),
            semanticTags = setOf("analysis")
        )
        
        val senateAgent = NUIDAgent(
            id = NUID.random(),
            ring = ConcentricRing.SENATE,
            capabilities = setOf(AgentCapability.DISTRIBUTION_COORDINATION),
            semanticTags = setOf("distribution")
        )
        
        topology.registerAgent(coreAgent)
        topology.registerAgent(triadAgent)
        topology.registerAgent(senateAgent)
        
        // Test critical task routing
        val criticalTask = ConcentricTask(
            id = "test-critical",
            type = TaskType.CONSENSUS,
            requiredCapabilities = setOf(AgentCapability.CONSENSUS_BUILDING),
            priority = TaskPriority.CRITICAL,
            complexity = ComplexityLevel.SIMPLE,
            requiredAgents = 1
        )
        
        val optimalRing = topology.findOptimalRing(criticalTask)
        assertEquals(ConcentricRing.CORE, optimalRing, "Critical tasks should route to CORE ring")
        
        val selectedAgents = topology.routeTaskToRing(criticalTask, optimalRing)
        assertEquals(1, selectedAgents.size, "Should select exactly one agent for critical task")
        assertEquals(coreAgent.id, selectedAgents[0].id, "Should select CORE agent for consensus task")
    }
    
    @Test
    fun `test agent canSee method`() = runTest {
        val topology = NUIDConcentricTopology()
        
        val coreAgent = NUIDAgent(
            id = NUID.random(),
            ring = ConcentricRing.CORE, // 1-hop scope
            capabilities = setOf(AgentCapability.CONSENSUS_BUILDING),
            semanticTags = setOf("core")
        )
        
        val senateAgent = NUIDAgent(
            id = NUID.random(),
            ring = ConcentricRing.SENATE, // 32-hop scope
            capabilities = setOf(AgentCapability.DISTRIBUTION_COORDINATION),
            semanticTags = setOf("senate")
        )
        
        // Test visibility based on NUID scope
        val distance = topology.calculateNUIDDistance(coreAgent.id, senateAgent.id)
        val canSee = coreAgent.canSee(senateAgent)
        
        // CORE agent can only see agents within 1-hop scope
        assertEquals(canSee, distance <= 1, "CORE agent visibility should match 1-hop scope")
        
        // SENATE agent should be able to see CORE agent (32-hop scope > 1-hop distance)
        val senateCanSeeCore = senateAgent.canSee(coreAgent)
        assertTrue(senateCanSeeCore, "SENATE agent should be able to see CORE agent")
    }
    
    @Test
    fun `test topology statistics`() = runTest {
        val topology = NUIDConcentricTopology()
        
        // Create agents across multiple rings
        repeat(5) { i ->
            val agent = NUIDAgent(
                id = NUID.random(),
                ring = ConcentricRing.values()[i % ConcentricRing.values().size],
                capabilities = setOf(AgentCapability.CONSENSUS_BUILDING),
                semanticTags = setOf("test"),
                workload = i * 0.1
            )
            topology.registerAgent(agent)
        }
        
        val stats = topology.getTopologyStats()
        
        assertEquals(5, stats.totalAgents, "Should have 5 total agents")
        assertTrue(stats.avgNUIDDistance >= 0, "Average NUID distance should be non-negative")
        
        // Verify ring statistics
        ConcentricRing.values().forEach { ring ->
            val ringStats = stats.ringStats[ring]
            assertNotNull(ringStats, "Should have statistics for each ring")
            assertTrue(ringStats.agentCount >= 0, "Agent count should be non-negative")
            assertTrue(ringStats.avgWorkload >= 0, "Average workload should be non-negative")
        }
    }
    
    @Test
    fun `test NUID fromSemantic extension`() {
        val semantic1 = "patrick_devine_analysis"
        val semantic2 = "global_sync"
        
        val nuid1 = NUID.fromSemantic(semantic1)
        val nuid2 = NUID.fromSemantic(semantic2)
        val nuid1Again = NUID.fromSemantic(semantic1)
        
        // Same semantic should produce same NUID
        assertEquals(nuid1, nuid1Again, "Same semantic should produce same NUID")
        
        // Different semantics should produce different NUIDs
        assertNotEquals(nuid1, nuid2, "Different semantics should produce different NUIDs")
    }
    
    @Test
    fun `test trust level assignment`() {
        assertEquals(TrustLevel.FULL, ConcentricRing.CORE.getTrustLevel(), "CORE should have FULL trust")
        assertEquals(TrustLevel.HIGH, ConcentricRing.DYAD.getTrustLevel(), "DYAD should have HIGH trust")
        assertEquals(TrustLevel.HIGH, ConcentricRing.TRIAD.getTrustLevel(), "TRIAD should have HIGH trust")
        assertEquals(TrustLevel.MEDIUM, ConcentricRing.PENTAD.getTrustLevel(), "PENTAD should have MEDIUM trust")
        assertEquals(TrustLevel.MEDIUM, ConcentricRing.DODECAD.getTrustLevel(), "DODECAD should have MEDIUM trust")
        assertEquals(TrustLevel.LOW, ConcentricRing.SENATE.getTrustLevel(), "SENATE should have LOW trust")
        assertEquals(TrustLevel.MINIMAL, ConcentricRing.CONGRESS.getTrustLevel(), "CONGRESS should have MINIMAL trust")
    }
    
    @Test
    fun `test ring progression and scope`() {
        // Verify the hop-based scope progression
        assertEquals(1, ConcentricRing.CORE.nuidScope, "CORE should have 1-hop scope")
        assertEquals(2, ConcentricRing.DYAD.nuidScope, "DYAD should have 2-hop scope")
        assertEquals(4, ConcentricRing.TRIAD.nuidScope, "TRIAD should have 4-hop scope")
        assertEquals(8, ConcentricRing.PENTAD.nuidScope, "PENTAD should have 8-hop scope")
        assertEquals(16, ConcentricRing.DODECAD.nuidScope, "DODECAD should have 16-hop scope")
        assertEquals(32, ConcentricRing.SENATE.nuidScope, "SENATE should have 32-hop scope")
        assertEquals(64, ConcentricRing.CONGRESS.nuidScope, "CONGRESS should have 64-hop scope")
        
        // Verify scope doubles with each level (except CORE)
        assertEquals(ConcentricRing.DYAD.nuidScope * 2, ConcentricRing.TRIAD.nuidScope)
        assertEquals(ConcentricRing.TRIAD.nuidScope * 2, ConcentricRing.PENTAD.nuidScope)
        assertEquals(ConcentricRing.PENTAD.nuidScope * 2, ConcentricRing.DODECAD.nuidScope)
        assertEquals(ConcentricRing.DODECAD.nuidScope * 2, ConcentricRing.SENATE.nuidScope)
        assertEquals(ConcentricRing.SENATE.nuidScope * 2, ConcentricRing.CONGRESS.nuidScope)
    }
} 