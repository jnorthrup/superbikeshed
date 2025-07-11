package fiduciary.agents

import fiduciary.concentric.*
import fiduciary.curator.*
import fiduciary.metaverse.*
import borg.trikeshed.lib.*
import borg.trikeshed.dht.kademlia.id.NUID
import kotlinx.coroutines.test.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.*

/**
 * TDD Test Suite for CurationAgent
 * 
 * Tests the curation agent's integration with fiduciary subnets,
 * content curation capabilities, trust verification, and collaborative features.
 */
class CurationAgentTest {
    
    private lateinit var mockConcentricProtocol: MockQuicConcentricProtocol
    private lateinit var mockBlackboardSubspace: MockBlackboardSubspace
    private lateinit var curationAgent: CurationAgent
    private val testAgentId = NUID.generate()
    private val testSubnetId = "test-fiduciary-subnet"
    
    @BeforeTest
    fun setup() {
        mockConcentricProtocol = MockQuicConcentricProtocol()
        mockBlackboardSubspace = MockBlackboardSubspace()
        
        curationAgent = CurationAgent(
            agentId = testAgentId,
            subnetId = testSubnetId,
            trustLevel = 2,
            capabilities = setOf(
                CurationAgent.CurationCapability.CONTENT_VALIDATION,
                CurationAgent.CurationCapability.PROVENANCE_TRACKING,
                CurationAgent.CurationCapability.QUALITY_ASSESSMENT,
                CurationAgent.CurationCapability.COLLABORATION_COORDINATION
            ),
            concentricProtocol = mockConcentricProtocol,
            blackboardSubspace = mockBlackboardSubspace
        )
    }
    
    @Test
    fun `test agent initialization`() = runTest {
        // Given: Agent is created
        
        // When: Agent is initialized
        val result = curationAgent.initialize()
        
        // Then: Initialization should succeed
        assertTrue(result.isSuccess)
        
        // And: Agent should be registered with concentric protocol
        assertTrue(mockConcentricProtocol.registeredAgents.contains(testAgentId))
        
        // And: Agent should join blackboard subspace
        assertTrue(mockBlackboardSubspace.joinedParticipants.contains(testAgentId.toString()))
    }
    
    @Test
    fun `test content curation with high quality content`() = runTest {
        // Given: Agent is initialized
        curationAgent.initialize()
        
        // And: High quality content
        val highQualityContent = CuratedContent(
            id = "test-content-1",
            type = ContentType.DOCUMENT,
            data = "High quality document content with accurate information",
            metadata = mapOf(
                "author" to "trusted-author",
                "source" to "reliable-source",
                "date" to "2024-01-01"
            ),
            source = "trusted-source.com",
            timestamp = Clock.System.now()
        )
        
        // When: Content is curated
        val result = curationAgent.curateContent(highQualityContent)
        
        // Then: Curation should succeed
        assertNotNull(result)
        assertEquals("test-content-1", result.contentId)
        
        // And: Validation should pass
        assertTrue(result.validationResult.isValid)
        assertTrue(result.validationResult.confidence >= 0.8)
        
        // And: Quality score should be high
        assertTrue(result.qualityScore.overallScore >= 0.8)
        assertTrue(result.qualityScore.accuracyScore >= 0.8)
        
        // And: No collaboration should be initiated for high quality content
        val collaborationEvents = curationAgent.getCurationHistory(
            eventType = CurationAgent.CurationEvent.CollaborationInitiated::class.java
        )
        assertEquals(0, collaborationEvents.size)
    }
    
    @Test
    fun `test content curation with low quality content triggers collaboration`() = runTest {
        // Given: Agent is initialized
        curationAgent.initialize()
        
        // And: Low quality content
        val lowQualityContent = CuratedContent(
            id = "test-content-2",
            type = ContentType.DOCUMENT,
            data = "Low quality content with questionable information",
            metadata = mapOf(
                "author" to "unknown-author",
                "source" to "unreliable-source",
                "date" to "2020-01-01"
            ),
            source = "unreliable-source.com",
            timestamp = Clock.System.now()
        )
        
        // When: Content is curated
        val result = curationAgent.curateContent(lowQualityContent)
        
        // Then: Curation should succeed but with lower scores
        assertNotNull(result)
        assertEquals("test-content-2", result.contentId)
        
        // And: Validation should have lower confidence
        assertTrue(result.validationResult.confidence < 0.8)
        
        // And: Quality score should be lower
        assertTrue(result.qualityScore.overallScore < 0.7)
        
        // And: Collaboration should be initiated
        val collaborationEvents = curationAgent.getCurationHistory(
            eventType = CurationAgent.CurationEvent.CollaborationInitiated::class.java
        )
        assertEquals(1, collaborationEvents.size)
        
        val collaborationEvent = collaborationEvents[0] as CurationAgent.CurationEvent.CollaborationInitiated
        assertEquals(CurationAgent.CollaborationType.PEER_REVIEW, collaborationEvent.collaborationType)
    }
    
    @Test
    fun `test trust verification`() = runTest {
        // Given: Agent is initialized
        curationAgent.initialize()
        
        // And: Another agent to verify
        val otherAgentId = NUID.generate()
        
        // When: Trust is verified
        val trustScore = curationAgent.verifyTrust(otherAgentId)
        
        // Then: Trust score should be calculated
        assertNotNull(trustScore)
        assertEquals(otherAgentId, trustScore.agentId)
        assertTrue(trustScore.score >= 0.0 && trustScore.score <= 1.0)
        assertTrue(trustScore.verificationCount >= 1)
        
        // And: Trust verification event should be recorded
        val trustEvents = curationAgent.getCurationHistory(
            eventType = CurationAgent.CurationEvent.TrustVerified::class.java
        )
        assertEquals(1, trustEvents.size)
        
        val trustEvent = trustEvents[0] as CurationAgent.CurationEvent.TrustVerified
        assertEquals(otherAgentId, trustEvent.agentId)
        assertEquals("multi-factor-assessment", trustEvent.verificationMethod)
    }
    
    @Test
    fun `test moving between rings`() = runTest {
        // Given: Agent is initialized
        curationAgent.initialize()
        
        // When: Agent moves to a different ring
        val result = curationAgent.moveToRing(2)
        
        // Then: Move should succeed
        assertTrue(result.isSuccess)
        
        // And: Agent should be moved in concentric protocol
        assertTrue(mockConcentricProtocol.movedAgents.contains(testAgentId))
        assertEquals(2, mockConcentricProtocol.getAgentRing(testAgentId))
    }
    
    @Test
    fun `test curation history filtering`() = runTest {
        // Given: Agent is initialized and has performed various actions
        curationAgent.initialize()
        
        val content = CuratedContent(
            id = "test-content-3",
            type = ContentType.DOCUMENT,
            data = "Test content",
            metadata = emptyMap(),
            source = "test-source",
            timestamp = Clock.System.now()
        )
        
        curationAgent.curateContent(content)
        curationAgent.verifyTrust(NUID.generate())
        
        // When: History is filtered by event type
        val validationEvents = curationAgent.getCurationHistory(
            eventType = CurationAgent.CurationEvent.ContentValidated::class.java
        )
        
        val provenanceEvents = curationAgent.getCurationHistory(
            eventType = CurationAgent.CurationEvent.ProvenanceTracked::class.java
        )
        
        val trustEvents = curationAgent.getCurationHistory(
            eventType = CurationAgent.CurationEvent.TrustVerified::class.java
        )
        
        // Then: Correct events should be returned
        assertEquals(1, validationEvents.size)
        assertEquals(1, provenanceEvents.size)
        assertEquals(1, trustEvents.size)
        
        // And: Events should be of correct types
        assertTrue(validationEvents[0] is CurationAgent.CurationEvent.ContentValidated)
        assertTrue(provenanceEvents[0] is CurationAgent.CurationEvent.ProvenanceTracked)
        assertTrue(trustEvents[0] is CurationAgent.CurationEvent.TrustVerified)
    }
    
    @Test
    fun `test agent shutdown`() = runTest {
        // Given: Agent is initialized
        curationAgent.initialize()
        
        // When: Agent is shut down
        val result = curationAgent.shutdown()
        
        // Then: Shutdown should succeed
        assertTrue(result.isSuccess)
        
        // And: Agent should be unregistered from concentric protocol
        assertTrue(mockConcentricProtocol.unregisteredAgents.contains(testAgentId))
        
        // And: Agent should leave blackboard subspace
        assertTrue(mockBlackboardSubspace.leftParticipants.contains(testAgentId.toString()))
    }
    
    @Test
    fun `test trust network management`() = runTest {
        // Given: Agent is initialized
        curationAgent.initialize()
        
        // When: Multiple agents are verified
        val agent1 = NUID.generate()
        val agent2 = NUID.generate()
        val agent3 = NUID.generate()
        
        curationAgent.verifyTrust(agent1)
        curationAgent.verifyTrust(agent2)
        curationAgent.verifyTrust(agent3)
        
        // Then: Trust network should contain all agents
        val trustNetwork = curationAgent.getTrustNetwork()
        assertEquals(3, trustNetwork.size)
        assertTrue(trustNetwork.containsKey(agent1))
        assertTrue(trustNetwork.containsKey(agent2))
        assertTrue(trustNetwork.containsKey(agent3))
        
        // And: All trust scores should be valid
        trustNetwork.values.forEach { trustScore ->
            assertTrue(trustScore.score >= 0.0 && trustScore.score <= 1.0)
            assertTrue(trustScore.verificationCount >= 1)
        }
    }
    
    @Test
    fun `test content curation with different content types`() = runTest {
        // Given: Agent is initialized
        curationAgent.initialize()
        
        // When: Different content types are curated
        val documentContent = CuratedContent(
            id = "doc-1",
            type = ContentType.DOCUMENT,
            data = "Document content",
            metadata = emptyMap(),
            source = "doc-source",
            timestamp = Clock.System.now()
        )
        
        val imageContent = CuratedContent(
            id = "img-1",
            type = ContentType.IMAGE,
            data = "Image data",
            metadata = emptyMap(),
            source = "img-source",
            timestamp = Clock.System.now()
        )
        
        val audioContent = CuratedContent(
            id = "audio-1",
            type = ContentType.AUDIO,
            data = "Audio data",
            metadata = emptyMap(),
            source = "audio-source",
            timestamp = Clock.System.now()
        )
        
        val docResult = curationAgent.curateContent(documentContent)
        val imgResult = curationAgent.curateContent(imageContent)
        val audioResult = curationAgent.curateContent(audioContent)
        
        // Then: All content types should be curated successfully
        assertNotNull(docResult)
        assertNotNull(imgResult)
        assertNotNull(audioResult)
        
        assertEquals("doc-1", docResult.contentId)
        assertEquals("img-1", imgResult.contentId)
        assertEquals("audio-1", audioResult.contentId)
        
        // And: All should have validation and quality scores
        assertTrue(docResult.validationResult.isValid)
        assertTrue(imgResult.validationResult.isValid)
        assertTrue(audioResult.validationResult.isValid)
        
        assertTrue(docResult.qualityScore.overallScore > 0)
        assertTrue(imgResult.qualityScore.overallScore > 0)
        assertTrue(audioResult.qualityScore.overallScore > 0)
    }
    
    @Test
    fun `test provenance tracking`() = runTest {
        // Given: Agent is initialized
        curationAgent.initialize()
        
        // When: Content is curated
        val content = CuratedContent(
            id = "provenance-test",
            type = ContentType.DOCUMENT,
            data = "Test content for provenance",
            metadata = emptyMap(),
            source = "provenance-source",
            timestamp = Clock.System.now()
        )
        
        val result = curationAgent.curateContent(content)
        
        // Then: Provenance should be tracked
        assertNotNull(result.provenance)
        assertEquals("provenance-test", result.provenance.contentId)
        assertEquals("provenance-source", result.provenance.origin)
        assertEquals(1, result.provenance.contributors.size)
        assertEquals(testAgentId, result.provenance.contributors[0])
        
        // And: Transformation should be recorded
        assertEquals(1, result.provenance.transformations.size)
        val transformation = result.provenance.transformations[0]
        assertEquals("curation", transformation.type)
        assertEquals("Content curated by agent", transformation.description)
        assertEquals(testAgentId, transformation.agentId)
    }
}

// Mock implementations for testing

class MockQuicConcentricProtocol : QuicConcentricProtocol {
    val registeredAgents = mutableSetOf<NUID>()
    val unregisteredAgents = mutableSetOf<NUID>()
    val movedAgents = mutableSetOf<NUID>()
    private val agentRings = mutableMapOf<NUID, Int>()
    
    override suspend fun registerAgent(agentId: NUID, ringLevel: Int, capabilities: List<String>) {
        registeredAgents.add(agentId)
        agentRings[agentId] = ringLevel
    }
    
    override suspend fun unregisterAgent(agentId: NUID) {
        unregisteredAgents.add(agentId)
        agentRings.remove(agentId)
    }
    
    override suspend fun moveAgentToRing(agentId: NUID, targetRing: Int) {
        movedAgents.add(agentId)
        agentRings[agentId] = targetRing
    }
    
    override suspend fun getAgentsInRing(ring: Int): List<NUID> {
        return agentRings.filterValues { it == ring }.keys.toList()
    }
    
    fun getAgentRing(agentId: NUID): Int = agentRings[agentId] ?: 0
}

class MockBlackboardSubspace : BlackboardSubspace(
    id = "test-subspace",
    name = "Test Subspace",
    description = "Test subspace for unit testing",
    creatorId = "test-creator",
    privacyLevel = BlackboardSubspace.PrivacyLevel.PUBLIC,
    couchDatabase = "test-db",
    waveEngine = MockWaveCRDTEngine(),
    gitHistoryService = MockGitHistoryService()
) {
    val joinedParticipants = mutableSetOf<String>()
    val leftParticipants = mutableSetOf<String>()
    
    override suspend fun join(participantId: String, invitationToken: String?) {
        joinedParticipants.add(participantId)
    }
    
    override suspend fun leave(participantId: String) {
        leftParticipants.add(participantId)
    }
}

class MockWaveCRDTEngine : WaveCRDTEngine {
    override suspend fun merge(operationSet: OperationSet): WaveDocument {
        return WaveDocument("test-doc", emptyList())
    }
    
    override suspend fun transform(operation: Operation): Operation {
        return operation
    }
}

class MockGitHistoryService : GitHistoryService {
    override suspend fun commit(document: WaveDocument): String {
        return "test-commit-hash"
    }
    
    override suspend fun checkout(commitHash: String): WaveDocument? {
        return null
    }
} 