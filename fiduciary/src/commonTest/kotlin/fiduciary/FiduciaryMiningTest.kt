package fiduciary

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import fiduciary.workers.WorkerPoolEvolution
import fiduciary.agents.FiduciaryAgentSystem
import fiduciary.concentric.QuicConcentricProtocol
import borg.trikeshed.dht.kademlia.id.NUID
import kotlinx.coroutines.test.runTest

class FiduciaryMiningTest {
    
    @Test
    fun testWorkerPoolCanInitialize() = runTest {
        // GIVEN: Required components
        val agentSystem = FiduciaryAgentSystem()
        val protocol = QuicConcentricProtocol()
        val subnetId = NUID.random()
        
        // WHEN: Create worker pool
        val workerPool = WorkerPoolEvolution(agentSystem, protocol, subnetId)
        
        // THEN: It exists
        assertNotNull(workerPool)
    }
    
    @Test
    fun testWorkerPoolInitializesAllTypes() = runTest {
        // GIVEN: Worker pool system
        val agentSystem = FiduciaryAgentSystem()
        val protocol = QuicConcentricProtocol()
        val subnetId = NUID.random()
        val workerPool = WorkerPoolEvolution(agentSystem, protocol, subnetId)
        
        // WHEN: Initialize
        workerPool.initialize()
        
        // THEN: All pool types are created
        val status = workerPool.getSystemStatus()
        assertTrue(status.totalPools == 8, "Expected 8 worker pool types")
    }
    
    @Test
    fun testAgentSystemCanProcessDecisions() = runTest {
        // GIVEN: Agent system with registered codec
        val agentSystem = FiduciaryAgentSystem()
        val codecRegistry = agentSystem.codecRegistry
        val defaultCodec = codecRegistry.createDefaultCodec()
        codecRegistry.registerCodec(defaultCodec)
        
        // AND: Create a team
        val teamId = "test-team"
        val team = agentSystem.createTeam(
            teamId = teamId,
            name = "Test Mining Team",
            teamTraits = setOf(
                FiduciaryAgentSystem.SemanticTrait.ANALYTICAL,
                FiduciaryAgentSystem.SemanticTrait.COLLABORATIVE
            ),
            coordinationPattern = FiduciaryAgentSystem.CoordinationPattern.COLLABORATIVE
        )
        
        // AND: Add an agent
        val agent = FiduciaryAgentSystem.Agent(
            id = "test-agent",
            teamId = teamId,
            semanticTraits = setOf(FiduciaryAgentSystem.SemanticTrait.ANALYTICAL),
            capabilities = setOf(FiduciaryAgentSystem.Capability.PATTERN_RECOGNITION),
            priorityRank = 1,
            registeredCodecs = setOf(defaultCodec.id)
        )
        agentSystem.addAgentToTeam(teamId, agent)
        
        // WHEN: Process a decision
        val request = FiduciaryAgentSystem.DecisionRequest(
            agentId = agent.id,
            codecId = defaultCodec.id,
            trigger = "ANALYZE",
            inputs = mapOf("data" to "test"),
            priority = 1
        )
        val responses = agentSystem.processTeamDecision(teamId, request)
        
        // THEN: We get responses
        assertTrue(responses.isNotEmpty(), "Should have decision responses")
    }
}