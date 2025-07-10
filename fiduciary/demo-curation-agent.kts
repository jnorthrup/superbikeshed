#!/usr/bin/env kotlin

@file:DependsOn("fiduciary")
@file:DependsOn("trikeshed-concentric")
@file:DependsOn("trikeshed-metaverse")

import fiduciary.agents.*
import fiduciary.concentric.*
import fiduciary.metaverse.*
import borg.trikeshed.lib.*
import borg.trikeshed.dht.kademlia.id.NUID
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Demo: Curation Agent in Fiduciary Subnets
 * 
 * This demo shows how to deploy and use the CurationAgent within
 * the fiduciary concentric subnet topology. The agent demonstrates:
 * 
 * 1. Integration with concentric network rings
 * 2. Content curation and quality assessment
 * 3. Trust verification and network building
 * 4. Collaborative curation with other agents
 * 5. Provenance tracking and audit trails
 */

suspend fun main() {
    println("🚀 Starting Curation Agent Demo in Fiduciary Subnets")
    println("=" * 60)
    
    // Initialize the concentric protocol
    val concentricProtocol = QuicConcentricProtocol()
    concentricProtocol.initialize()
    
    // Create a blackboard subspace for knowledge sharing
    val blackboardSubspace = BlackboardSubspace(
        id = "curation-demo-subspace",
        name = "Curation Demo Subspace",
        description = "Demo subspace for curation agent testing",
        creatorId = "demo-creator",
        privacyLevel = BlackboardSubspace.PrivacyLevel.PUBLIC,
        couchDatabase = "curation-demo-db",
        waveEngine = WaveCRDTEngine(),
        gitHistoryService = GitHistoryService()
    )
    
    // Create multiple curation agents with different capabilities
    val agents = createCurationAgents(concentricProtocol, blackboardSubspace)
    
    // Deploy agents to different rings
    deployAgentsToRings(agents, concentricProtocol)
    
    // Demonstrate content curation
    demonstrateContentCuration(agents)
    
    // Demonstrate trust verification
    demonstrateTrustVerification(agents)
    
    // Demonstrate collaborative curation
    demonstrateCollaborativeCuration(agents, concentricProtocol)
    
    // Show audit trails and provenance
    demonstrateAuditTrails(agents)
    
    // Cleanup
    cleanup(agents)
    
    println("\n✅ Curation Agent Demo Completed Successfully!")
}

suspend fun createCurationAgents(
    concentricProtocol: QuicConcentricProtocol,
    blackboardSubspace: BlackboardSubspace
): List<CurationAgent> {
    println("\n📋 Creating Curation Agents...")
    
    val agents = listOf(
        // Core validation agent (Ring 0)
        CurationAgent(
            agentId = NUID.generate(),
            subnetId = "fiduciary-core",
            trustLevel = 3,
            capabilities = setOf(
                CurationAgent.CurationCapability.CONTENT_VALIDATION,
                CurationAgent.CurationCapability.TRUST_VERIFICATION,
                CurationAgent.CurationCapability.COMPLIANCE_ENFORCEMENT
            ),
            concentricProtocol = concentricProtocol,
            blackboardSubspace = blackboardSubspace
        ),
        
        // Quality assessment agent (Ring 1)
        CurationAgent(
            agentId = NUID.generate(),
            subnetId = "fiduciary-quality",
            trustLevel = 2,
            capabilities = setOf(
                CurationAgent.CurationCapability.QUALITY_ASSESSMENT,
                CurationAgent.CurationCapability.PROVENANCE_TRACKING,
                CurationAgent.CurationCapability.COLLABORATION_COORDINATION
            ),
            concentricProtocol = concentricProtocol,
            blackboardSubspace = blackboardSubspace
        ),
        
        // Knowledge synthesis agent (Ring 2)
        CurationAgent(
            agentId = NUID.generate(),
            subnetId = "fiduciary-synthesis",
            trustLevel = 1,
            capabilities = setOf(
                CurationAgent.CurationCapability.KNOWLEDGE_SYNTHESIS,
                CurationAgent.CurationCapability.ATTENTION_OPTIMIZATION,
                CurationAgent.CurationCapability.COLLABORATION_COORDINATION
            ),
            concentricProtocol = concentricProtocol,
            blackboardSubspace = blackboardSubspace
        )
    )
    
    // Initialize all agents
    agents.forEachIndexed { index, agent ->
        val result = agent.initialize()
        if (result.isSuccess) {
            println("✅ Agent ${index + 1} initialized successfully")
        } else {
            println("❌ Agent ${index + 1} initialization failed: ${result.exceptionOrNull()}")
        }
    }
    
    return agents
}

suspend fun deployAgentsToRings(
    agents: List<CurationAgent>,
    concentricProtocol: QuicConcentricProtocol
) {
    println("\n🌐 Deploying Agents to Concentric Rings...")
    
    // Deploy to different rings based on trust level
    agents.forEachIndexed { index, agent ->
        val targetRing = 3 - agent.trustLevel // Higher trust = lower ring number
        val result = agent.moveToRing(targetRing)
        
        if (result.isSuccess) {
            println("✅ Agent ${index + 1} deployed to Ring $targetRing (Trust Level: ${agent.trustLevel})")
        } else {
            println("❌ Agent ${index + 1} deployment failed: ${result.exceptionOrNull()}")
        }
    }
}

suspend fun demonstrateContentCuration(agents: List<CurationAgent>) {
    println("\n📚 Demonstrating Content Curation...")
    
    // Create test content with varying quality
    val testContent = listOf(
        // High quality content
        CuratedContent(
            id = "high-quality-doc",
            type = ContentType.DOCUMENT,
            data = "This is a high-quality document with accurate information from a reliable source.",
            metadata = mapOf(
                "author" to "Dr. Jane Smith",
                "institution" to "MIT",
                "publication_date" to "2024-01-15",
                "peer_reviewed" to "true"
            ),
            source = "mit.edu/research",
            timestamp = Clock.System.now()
        ),
        
        // Medium quality content
        CuratedContent(
            id = "medium-quality-doc",
            type = ContentType.DOCUMENT,
            data = "This document contains some useful information but may need verification.",
            metadata = mapOf(
                "author" to "John Doe",
                "institution" to "Community College",
                "publication_date" to "2023-12-01",
                "peer_reviewed" to "false"
            ),
            source = "community-college.edu/blog",
            timestamp = Clock.System.now()
        ),
        
        // Low quality content
        CuratedContent(
            id = "low-quality-doc",
            type = ContentType.DOCUMENT,
            data = "This content contains questionable information and needs verification.",
            metadata = mapOf(
                "author" to "Anonymous",
                "institution" to "Unknown",
                "publication_date" to "2020-05-01",
                "peer_reviewed" to "false"
            ),
            source = "unreliable-blog.com",
            timestamp = Clock.System.now()
        )
    )
    
    // Curate content with each agent
    testContent.forEachIndexed { contentIndex, content ->
        println("\n--- Curating Content ${contentIndex + 1}: ${content.id} ---")
        
        agents.forEachIndexed { agentIndex, agent ->
            val result = agent.curateContent(content)
            
            println("Agent ${agentIndex + 1} (Ring ${3 - agent.trustLevel}):")
            println("  Validation: ${if (result.validationResult.isValid) "✅" else "❌"} (confidence: ${String.format("%.2f", result.validationResult.confidence)})")
            println("  Quality Score: ${String.format("%.2f", result.qualityScore.overallScore)}")
            println("  Recommendations: ${result.recommendations.size} items")
            
            if (result.recommendations.isNotEmpty()) {
                result.recommendations.forEach { recommendation ->
                    println("    - $recommendation")
                }
            }
        }
    }
}

suspend fun demonstrateTrustVerification(agents: List<CurationAgent>) {
    println("\n🤝 Demonstrating Trust Verification...")
    
    // Create some test agent IDs
    val testAgentIds = List(5) { NUID.generate() }
    
    // Have each agent verify trust of other agents
    agents.forEachIndexed { agentIndex, agent ->
        println("\nAgent ${agentIndex + 1} verifying trust of other agents:")
        
        testAgentIds.forEach { testAgentId ->
            val trustScore = agent.verifyTrust(testAgentId)
            
            println("  Agent ${testAgentId.toString().take(8)}...: ${String.format("%.2f", trustScore.score)}")
            println("    Factors: ${trustScore.factors.keys.joinToString(", ")}")
            println("    Verifications: ${trustScore.verificationCount}")
        }
    }
    
    // Show trust network for first agent
    val firstAgent = agents.first()
    val trustNetwork = firstAgent.getTrustNetwork()
    println("\n📊 Trust Network for Agent 1:")
    trustNetwork.forEach { (agentId, trustScore) ->
        println("  ${agentId.toString().take(8)}...: ${String.format("%.2f", trustScore.score)}")
    }
}

suspend fun demonstrateCollaborativeCuration(
    agents: List<CurationAgent>,
    concentricProtocol: QuicConcentricProtocol
) {
    println("\n👥 Demonstrating Collaborative Curation...")
    
    // Create content that requires collaboration
    val collaborativeContent = CuratedContent(
        id = "collaborative-doc",
        type = ContentType.DOCUMENT,
        data = "This document contains complex information that requires multiple perspectives for proper curation.",
        metadata = mapOf(
            "author" to "Research Team",
            "institution" to "Multi-University Consortium",
            "publication_date" to "2024-01-20",
            "complexity" to "high",
            "requires_collaboration" to "true"
        ),
        source = "research-consortium.org",
        timestamp = Clock.System.now()
    )
    
    // Curate with each agent and show collaboration events
    agents.forEachIndexed { agentIndex, agent ->
        println("\nAgent ${agentIndex + 1} curating collaborative content:")
        
        val result = agent.curateContent(collaborativeContent)
        
        // Check for collaboration events
        val collaborationEvents = agent.getCurationHistory(
            eventType = CurationAgent.CurationEvent.CollaborationInitiated::class.java
        )
        
        if (collaborationEvents.isNotEmpty()) {
            println("  🤝 Collaboration initiated!")
            collaborationEvents.forEach { event ->
                val collabEvent = event as CurationAgent.CurationEvent.CollaborationInitiated
                println("    Type: ${collabEvent.collaborationType}")
                println("    Partner: ${collabEvent.partnerAgentId.toString().take(8)}...")
            }
        } else {
            println("  ✅ No collaboration needed")
        }
    }
}

suspend fun demonstrateAuditTrails(agents: List<CurationAgent>) {
    println("\n📋 Demonstrating Audit Trails...")
    
    agents.forEachIndexed { agentIndex, agent ->
        println("\nAgent ${agentIndex + 1} Audit Trail:")
        
        val allEvents = agent.getCurationHistory()
        println("  Total Events: ${allEvents.size}")
        
        // Group events by type
        val eventTypes = allEvents.groupBy { it::class.simpleName }
        eventTypes.forEach { (eventType, events) ->
            println("  $eventType: ${events.size} events")
        }
        
        // Show recent events
        val recentEvents = agent.getCurationHistory(since = Clock.System.now() - 1.hours)
        println("  Recent Events (last hour): ${recentEvents.size}")
        
        if (recentEvents.isNotEmpty()) {
            recentEvents.take(3).forEach { event ->
                println("    - ${event::class.simpleName} at ${event.timestamp}")
            }
        }
    }
}

suspend fun cleanup(agents: List<CurationAgent>) {
    println("\n🧹 Cleaning up...")
    
    agents.forEachIndexed { index, agent ->
        val result = agent.shutdown()
        if (result.isSuccess) {
            println("✅ Agent ${index + 1} shut down successfully")
        } else {
            println("❌ Agent ${index + 1} shutdown failed: ${result.exceptionOrNull()}")
        }
    }
}

// Extension function for string repetition
operator fun String.times(n: Int): String = repeat(n)

// Extension property for Duration
val Int.hours: kotlin.time.Duration get() = kotlin.time.Duration.hours(this) 