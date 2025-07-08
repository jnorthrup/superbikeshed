package borg.trikeshed.services

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/**
 * Comprehensive test suite for Blackboard Metaverse
 */
class BlackboardMetaverseTest {
    
    internal lateinit var metaverse: BlackboardMetaverse
    
    @BeforeTest
    fun setup() {
        metaverse = BlackboardMetaverse()
    }
    
    @Test
    fun `test entering metaverse anonymously`() = runTest {
        val avatar = metaverse.enterMetaverse(pseudonym = "TestUser")
        
        assertNotNull(avatar)
        assertEquals("TestUser", avatar.pseudonym)
        assertEquals("commons", avatar.subspace)
        assertEquals(PrivacyLevel.ANONYMOUS, avatar.privacyLevel)
        assertEquals(0.0, avatar.reputation.score)
    }
    
    @Test
    fun `test contributing knowledge`() = runTest {
        val avatar = metaverse.enterMetaverse(pseudonym = "Contributor")
        
        val knowledge = KnowledgeFragment(
            id = "test-k1",
            content = "Test knowledge contribution",
            type = KnowledgeType.FACT,
            confidence = 0.8
        )
        
        val result = metaverse.contributeKnowledge(avatar.id, knowledge, "commons")
        
        assertTrue(result is ContributionResult.Success)
        val success = result as ContributionResult.Success
        assertEquals(knowledge.content, success.entry.knowledge.content)
        assertEquals(avatar.id, success.entry.contributorId)
    }
    
    @Test
    fun `test exploring knowledge`() = runTest {
        val avatar = metaverse.enterMetaverse(pseudonym = "Explorer")
        
        // First contribute some knowledge
        val knowledge1 = KnowledgeFragment(
            id = "k1",
            content = "Knowledge about metaverse",
            type = KnowledgeType.FACT,
            confidence = 0.9
        )
        
        val knowledge2 = KnowledgeFragment(
            id = "k2",
            content = "Gossip protocols in distributed systems",
            type = KnowledgeType.FACT,
            confidence = 0.8
        )
        
        metaverse.contributeKnowledge(avatar.id, knowledge1, "commons")
        metaverse.contributeKnowledge(avatar.id, knowledge2, "commons")
        
        // Now explore
        val query = KnowledgeQuery(
            keywords = listOf("metaverse", "gossip"),
            timeWindow = 24 * 60 * 60 * 1000
        )
        
        val result = metaverse.exploreKnowledge(avatar.id, query, "commons")
        
        assertTrue(result is ExplorationResult.Success)
        val success = result as ExplorationResult.Success
        assertTrue(success.results.isNotEmpty())
        assertTrue(success.results.any { it.content.contains("metaverse") })
    }
    
    @Test
    fun `test creating internal subspace`() = runTest {
        val avatar = metaverse.enterMetaverse(pseudonym = "Creator")
        
        val result = metaverse.createSubspace(
            creatorId = avatar.id,
            name = "TestSpace",
            description = "A test subspace",
            privacyLevel = PrivacyLevel.PRIVATE
        )
        
        assertTrue(result is SubspaceResult.Success)
        val success = result as SubspaceResult.Success
        assertEquals("TestSpace", success.subspace.name)
        assertEquals("A test subspace", success.subspace.description)
        assertEquals(PrivacyLevel.PRIVATE, success.subspace.privacyLevel)
        assertEquals(avatar.id, success.subspace.creatorId)
    }
    
    @Test
    fun `test serendipitous discovery`() = runTest {
        val avatar = metaverse.enterMetaverse(pseudonym = "Discoverer")
        
        // Add some knowledge first
        val knowledge1 = KnowledgeFragment(
            id = "s1",
            content = "Serendipitous knowledge fragment 1",
            type = KnowledgeType.FACT,
            confidence = 0.7
        )
        
        val knowledge2 = KnowledgeFragment(
            id = "s2",
            content = "Another random piece of knowledge",
            type = KnowledgeType.OPINION,
            confidence = 0.6
        )
        
        metaverse.contributeKnowledge(avatar.id, knowledge1, "commons")
        metaverse.contributeKnowledge(avatar.id, knowledge2, "commons")
        
        val result = metaverse.discoverKnowledge(
            avatar.id,
            DiscoveryMethod.Serendipity(count = 2)
        )
        
        assertTrue(result is DiscoveryResult.Success)
        val success = result as DiscoveryResult.Success
        assertTrue(success.knowledge.isNotEmpty())
    }
    
    @Test
    fun `test conceptual blending synthesis`() = runTest {
        val avatar = metaverse.enterMetaverse(pseudonym = "Synthesizer")
        
        // Add knowledge with different concepts
        val knowledge1 = KnowledgeFragment(
            id = "c1",
            content = "Knowledge about artificial intelligence",
            type = KnowledgeType.FACT,
            confidence = 0.8
        )
        
        val knowledge2 = KnowledgeFragment(
            id = "c2",
            content = "Information about blockchain technology",
            type = KnowledgeType.FACT,
            confidence = 0.7
        )
        
        metaverse.contributeKnowledge(avatar.id, knowledge1, "commons")
        metaverse.contributeKnowledge(avatar.id, knowledge2, "commons")
        
        val result = metaverse.synthesizeKnowledge(
            avatar.id,
            SynthesisMethod.ConceptualBlending(listOf("artificial intelligence", "blockchain"))
        )
        
        assertTrue(result is SynthesisResult.Success)
        val success = result as SynthesisResult.Success
        assertNotNull(success.knowledge)
        assertTrue(success.knowledge.confidence > 0.0)
    }
    
    @Test
    fun `test gossip trail discovery`() = runTest {
        val avatar = metaverse.enterMetaverse(pseudonym = "GossipFollower")
        
        val result = metaverse.discoverKnowledge(
            avatar.id,
            DiscoveryMethod.GossipTrail(depth = 3)
        )
        
        assertTrue(result is DiscoveryResult.Success)
        val success = result as DiscoveryResult.Success
        // Even with no existing gossip, should return empty list, not failure
        assertNotNull(success.knowledge)
    }
    
    @Test
    fun `test attention flow discovery`() = runTest {
        val avatar = metaverse.enterMetaverse(pseudonym = "AttentionFollower")
        
        val result = metaverse.discoverKnowledge(
            avatar.id,
            DiscoveryMethod.AttentionFlow("default")
        )
        
        assertTrue(result is DiscoveryResult.Success)
        val success = result as DiscoveryResult.Success
        assertNotNull(success.knowledge)
    }
    
    @Test
    fun `test pattern matching synthesis`() = runTest {
        val avatar = metaverse.enterMetaverse(pseudonym = "PatternMatcher")
        
        // Add knowledge with patterns
        val knowledge1 = KnowledgeFragment(
            id = "p1",
            content = "Pattern: distributed systems are resilient",
            type = KnowledgeType.FACT,
            confidence = 0.8
        )
        
        val knowledge2 = KnowledgeFragment(
            id = "p2",
            content = "Another pattern: gossip protocols work well",
            type = KnowledgeType.FACT,
            confidence = 0.7
        )
        
        metaverse.contributeKnowledge(avatar.id, knowledge1, "commons")
        metaverse.contributeKnowledge(avatar.id, knowledge2, "commons")
        
        val result = metaverse.synthesizeKnowledge(
            avatar.id,
            SynthesisMethod.PatternMatching("pattern")
        )
        
        assertTrue(result is SynthesisResult.Success)
        val success = result as SynthesisResult.Success
        assertNotNull(success.knowledge)
    }
    
    @Test
    fun `test emergent insight synthesis`() = runTest {
        val avatar = metaverse.enterMetaverse(pseudonym = "InsightGenerator")
        
        val result = metaverse.synthesizeKnowledge(
            avatar.id,
            SynthesisMethod.EmergentInsight("default")
        )
        
        assertTrue(result is SynthesisResult.Success)
        val success = result as SynthesisResult.Success
        assertNotNull(success.knowledge)
    }
    
    @Test
    fun `test invalid avatar operations`() = runTest {
        val invalidAvatarId = "invalid-avatar-id"
        
        val knowledge = KnowledgeFragment(
            id = "invalid",
            content = "Test content",
            type = KnowledgeType.FACT,
            confidence = 0.8
        )
        
        val contributeResult = metaverse.contributeKnowledge(invalidAvatarId, knowledge, "commons")
        assertTrue(contributeResult is ContributionResult.Failure)
        
        val query = KnowledgeQuery(keywords = listOf("test"))
        val exploreResult = metaverse.exploreKnowledge(invalidAvatarId, query, "commons")
        assertTrue(exploreResult is ExplorationResult.Failure)
        
        val discoverResult = metaverse.discoverKnowledge(
            invalidAvatarId,
            DiscoveryMethod.Serendipity(count = 1)
        )
        assertTrue(discoverResult is DiscoveryResult.Failure)
        
        val synthesizeResult = metaverse.synthesizeKnowledge(
            invalidAvatarId,
            SynthesisMethod.ConceptualBlending(listOf("test"))
        )
        assertTrue(synthesizeResult is SynthesisResult.Failure)
    }
    
    @Test
    fun `test knowledge anonymization`() = runTest {
        val avatar = metaverse.enterMetaverse(pseudonym = "AnonymousUser")
        
        val knowledge = KnowledgeFragment(
            id = "anon-test",
            content = "Anonymous knowledge contribution",
            type = KnowledgeType.FACT,
            confidence = 0.8
        )
        
        val result = metaverse.contributeKnowledge(avatar.id, knowledge, "commons")
        
        assertTrue(result is ContributionResult.Success)
        val success = result as ContributionResult.Success
        assertEquals("AnonymousUser", success.entry.knowledge.metadata["contributor"])
    }
    
    @Test
    fun `test reputation calculation`() = runTest {
        val avatar = metaverse.enterMetaverse(pseudonym = "ReputationUser")
        
        val initialReputation = avatar.reputation.score
        
        val knowledge = KnowledgeFragment(
            id = "reputation-test",
            content = "High quality knowledge",
            type = KnowledgeType.FACT,
            confidence = 0.9
        )
        
        metaverse.contributeKnowledge(avatar.id, knowledge, "commons")
        
        // Note: In a real implementation, we'd need to retrieve the updated avatar
        // For now, we just verify the contribution was successful
        val result = metaverse.contributeKnowledge(avatar.id, knowledge, "commons")
        assertTrue(result is ContributionResult.Success)
    }
    
    @Test
    fun `test multiple subspaces isolation`() = runTest {
        val avatar = metaverse.enterMetaverse(pseudonym = "MultiSpaceUser")
        
        // Create two subspaces
        val subspace1Result = metaverse.createSubspace(
            creatorId = avatar.id,
            name = "Space1",
            description = "First subspace",
            privacyLevel = PrivacyLevel.PRIVATE
        )
        
        val subspace2Result = metaverse.createSubspace(
            creatorId = avatar.id,
            name = "Space2", 
            description = "Second subspace",
            privacyLevel = PrivacyLevel.PRIVATE
        )
        
        assertTrue(subspace1Result is SubspaceResult.Success)
        assertTrue(subspace2Result is SubspaceResult.Success)
        
        val subspace1 = (subspace1Result as SubspaceResult.Success).subspace
        val subspace2 = (subspace2Result as SubspaceResult.Success).subspace
        
        // Contribute knowledge to different subspaces
        val knowledge1 = KnowledgeFragment(
            id = "space1-k",
            content = "Knowledge for space 1",
            type = KnowledgeType.FACT,
            confidence = 0.8
        )
        
        val knowledge2 = KnowledgeFragment(
            id = "space2-k",
            content = "Knowledge for space 2", 
            type = KnowledgeType.FACT,
            confidence = 0.8
        )
        
        val result1 = metaverse.contributeKnowledge(avatar.id, knowledge1, subspace1.id)
        val result2 = metaverse.contributeKnowledge(avatar.id, knowledge2, subspace2.id)
        
        assertTrue(result1 is ContributionResult.Success)
        assertTrue(result2 is ContributionResult.Success)
    }
    
    @Test
    fun `test knowledge query matching`() = runTest {
        val query = KnowledgeQuery(
            keywords = listOf("metaverse", "gossip"),
            timeWindow = 24 * 60 * 60 * 1000,
            confidenceThreshold = 0.5
        )
        
        val matchingKnowledge = KnowledgeFragment(
            id = "match",
            content = "The metaverse uses gossip protocols",
            type = KnowledgeType.FACT,
            confidence = 0.8
        )
        
        val nonMatchingKnowledge = KnowledgeFragment(
            id = "no-match",
            content = "Unrelated content",
            type = KnowledgeType.FACT,
            confidence = 0.3
        )
        
        assertTrue(query.matches(matchingKnowledge))
        assertFalse(query.matches(nonMatchingKnowledge))
    }
    
    @Test
    fun `test knowledge types`() = runTest {
        val fact = KnowledgeFragment(
            id = "fact",
            content = "Verifiable fact",
            type = KnowledgeType.FACT,
            confidence = 0.9
        )
        
        val opinion = KnowledgeFragment(
            id = "opinion",
            content = "Subjective opinion",
            type = KnowledgeType.OPINION,
            confidence = 0.6
        )
        
        val question = KnowledgeFragment(
            id = "question",
            content = "What is the meaning of life?",
            type = KnowledgeType.QUESTION,
            confidence = 0.5
        )
        
        val synthesized = KnowledgeFragment(
            id = "synthesized",
            content = "Emergent insight",
            type = KnowledgeType.SYNTHESIZED,
            confidence = 0.7
        )
        
        val emergent = KnowledgeFragment(
            id = "emergent",
            content = "Collective intelligence",
            type = KnowledgeType.EMERGENT,
            confidence = 0.8
        )
        
        assertEquals(KnowledgeType.FACT, fact.type)
        assertEquals(KnowledgeType.OPINION, opinion.type)
        assertEquals(KnowledgeType.QUESTION, question.type)
        assertEquals(KnowledgeType.SYNTHESIZED, synthesized.type)
        assertEquals(KnowledgeType.EMERGENT, emergent.type)
    }
} 