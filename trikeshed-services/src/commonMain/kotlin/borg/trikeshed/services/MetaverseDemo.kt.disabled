@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.services

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Blackboard Metaverse Demo
 * 
 * Demonstrates the true metaverse - anonymous, private, gossip-based knowledge space
 */
class MetaverseDemo {
    
    internal val metaverse = BlackboardMetaverse()
    
    suspend fun runDemo() {
        println("🌌 Welcome to the Blackboard Metaverse")
        println("======================================")
        
        // Enter the metaverse anonymously
        val avatar1 = metaverse.enterMetaverse(pseudonym = "SnowCrash", subspace = "commons")
        val avatar2 = metaverse.enterMetaverse(pseudonym = "HiroProtagonist", subspace = "commons")
        val avatar3 = metaverse.enterMetaverse(pseudonym = "YTSamurai", subspace = "commons")
        
        println("👥 Avatars entered: ${avatar1.pseudonym}, ${avatar2.pseudonym}, ${avatar3.pseudonym}")
        
        // Contribute knowledge anonymously
        val knowledge1 = KnowledgeFragment(
            id = "k1",
            content = "The Metaverse is not a place, it's a shared hallucination",
            type = KnowledgeType.FACT,
            confidence = 0.9
        )
        
        val knowledge2 = KnowledgeFragment(
            id = "k2", 
            content = "Gossip protocols enable emergent knowledge discovery",
            type = KnowledgeType.FACT,
            confidence = 0.8
        )
        
        val knowledge3 = KnowledgeFragment(
            id = "k3",
            content = "Anonymous collaboration produces higher quality insights",
            type = KnowledgeType.OPINION,
            confidence = 0.7
        )
        
        // Contribute to blackboard
        metaverse.contributeKnowledge(avatar1.id, knowledge1, "commons")
        metaverse.contributeKnowledge(avatar2.id, knowledge2, "commons")
        metaverse.contributeKnowledge(avatar3.id, knowledge3, "commons")
        
        println("📝 Knowledge contributed to blackboard")
        
        // Explore knowledge through gossip
        val query = KnowledgeQuery(
            keywords = listOf("metaverse", "gossip"),
            timeWindow = 24 * 60 * 60 * 1000
        )
        
        val exploration = metaverse.exploreKnowledge(avatar1.id, query, "commons")
        when (exploration) {
            is ExplorationResult.Success -> {
                println("🔍 Knowledge discovered through gossip:")
                exploration.results.forEach { fragment ->
                    println("  - ${fragment.content} (confidence: ${fragment.confidence})")
                }
            }
            is ExplorationResult.Failure -> {
                println("❌ Exploration failed: ${exploration.error}")
            }
        }
        
        // Discover knowledge through serendipity
        val serendipity = metaverse.discoverKnowledge(
            avatar2.id,
            DiscoveryMethod.Serendipity(count = 2)
        )
        
        when (serendipity) {
            is DiscoveryResult.Success -> {
                println("🎲 Serendipitous discoveries:")
                serendipity.knowledge.forEach { fragment ->
                    println("  - ${fragment.content}")
                }
            }
            is DiscoveryResult.Failure -> {
                println("❌ Serendipity failed: ${serendipity.error}")
            }
        }
        
        // Synthesize emergent knowledge
        val synthesis = metaverse.synthesizeKnowledge(
            avatar3.id,
            SynthesisMethod.ConceptualBlending(listOf("metaverse", "gossip", "anonymous"))
        )
        
        when (synthesis) {
            is SynthesisResult.Success -> {
                println("🧠 Emergent knowledge synthesized:")
                println("  - ${synthesis.knowledge.content}")
                println("  - Confidence: ${synthesis.knowledge.confidence}")
            }
            is SynthesisResult.Failure -> {
                println("❌ Synthesis failed: ${synthesis.error}")
            }
        }
        
        // Create internal subspace
        val subspace = metaverse.createSubspace(
            creatorId = avatar1.id,
            name = "HackerSpace",
            description = "Private space for advanced knowledge sharing",
            privacyLevel = PrivacyLevel.PRIVATE
        )
        
        when (subspace) {
            is SubspaceResult.Success -> {
                println("🏠 Private subspace created: ${subspace.subspace.name}")
                
                // Enter the internal subspace
                val privateAvatar = metaverse.enterMetaverse(
                    pseudonym = "CryptoPunk",
                    subspace = subspace.subspace.id
                )
                
                val privateKnowledge = KnowledgeFragment(
                    id = "pk1",
                    content = "Private knowledge shared only in this subspace",
                    type = KnowledgeType.FACT,
                    confidence = 0.95
                )
                
                metaverse.contributeKnowledge(privateAvatar.id, privateKnowledge, subspace.subspace.id)
                println("🔒 Private knowledge contributed to ${subspace.subspace.name}")
            }
            is SubspaceResult.Failure -> {
                println("❌ Subspace creation failed: ${subspace.error}")
            }
        }
        
        println("\n🌌 Metaverse Demo Complete")
        println("=========================")
        println("This is the true metaverse - anonymous, private, emergent knowledge space")
        println("No corporate surveillance, no data mining, just pure knowledge sharing")
    }
}

/**
 * Interactive Metaverse Console
 */
class MetaverseConsole {
    
    internal val metaverse = BlackboardMetaverse()
    internal var currentAvatar: MetaverseAvatar? = null
    
    suspend fun start() {
        println("🌌 Blackboard Metaverse Console")
        println("===============================")
        println("Commands:")
        println("  enter <pseudonym> [subspace] - Enter metaverse")
        println("  contribute <content> - Contribute knowledge")
        println("  explore <keywords> - Explore knowledge")
        println("  discover <method> - Discover knowledge")
        println("  synthesize <concepts> - Synthesize knowledge")
        println("  create-subspace <name> <description> - Create internal subspace")
        println("  exit - Leave metaverse")
        println()
        
        while (true) {
            print("metaverse> ")
            val input = readLine() ?: break
            
            if (input.startsWith("exit")) {
                break
            }
            
            try {
                processCommand(input)
            } catch (e: Exception) {
                println("❌ Error: ${e.message}")
            }
        }
        
        println("👋 Farewell, metaverse traveler")
    }
    
    internal suspend fun processCommand(input: String) {
        val parts = input.split(" ")
        val command = parts[0]
        
        when (command) {
            "enter" -> {
                val pseudonym = parts.getOrNull(1) ?: "Anonymous"
                val subspace = parts.getOrNull(2) ?: "commons"
                
                currentAvatar = metaverse.enterMetaverse(pseudonym, subspace)
                println("👤 Entered as ${currentAvatar?.pseudonym} in $subspace")
            }
            "contribute" -> {
                val avatar = currentAvatar ?: throw Exception("Not in metaverse")
                val content = parts.drop(1).joinToString(" ")
                
                val knowledge = KnowledgeFragment(
                    id = "user-${System.currentTimeMillis()}",
                    content = content,
                    type = KnowledgeType.FACT,
                    confidence = 0.8
                )
                
                val result = metaverse.contributeKnowledge(avatar.id, knowledge, avatar.subspace)
                when (result) {
                    is ContributionResult.Success -> println("✅ Knowledge contributed")
                    is ContributionResult.Failure -> println("❌ Failed: ${result.error}")
                }
            }
            "explore" -> {
                val avatar = currentAvatar ?: throw Exception("Not in metaverse")
                val keywords = parts.drop(1)
                
                val query = KnowledgeQuery(keywords = keywords)
                val result = metaverse.exploreKnowledge(avatar.id, query, avatar.subspace)
                
                when (result) {
                    is ExplorationResult.Success -> {
                        println("🔍 Found ${result.results.size} knowledge fragments:")
                        result.results.forEach { fragment ->
                            println("  - ${fragment.content}")
                        }
                    }
                    is ExplorationResult.Failure -> println("❌ Failed: ${result.error}")
                }
            }
            "discover" -> {
                val avatar = currentAvatar ?: throw Exception("Not in metaverse")
                val method = parts.getOrNull(1) ?: "serendipity"
                
                val discoveryMethod = when (method) {
                    "serendipity" -> DiscoveryMethod.Serendipity(count = 3)
                    "gossip" -> DiscoveryMethod.GossipTrail(depth = 5)
                    "attention" -> DiscoveryMethod.AttentionFlow("default")
                    else -> DiscoveryMethod.Serendipity(count = 3)
                }
                
                val result = metaverse.discoverKnowledge(avatar.id, discoveryMethod)
                when (result) {
                    is DiscoveryResult.Success -> {
                        println("🎲 Discovered ${result.knowledge.size} fragments:")
                        result.knowledge.forEach { fragment ->
                            println("  - ${fragment.content}")
                        }
                    }
                    is DiscoveryResult.Failure -> println("❌ Failed: ${result.error}")
                }
            }
            "synthesize" -> {
                val avatar = currentAvatar ?: throw Exception("Not in metaverse")
                val concepts = parts.drop(1)
                
                val synthesisMethod = SynthesisMethod.ConceptualBlending(concepts)
                val result = metaverse.synthesizeKnowledge(avatar.id, synthesisMethod)
                
                when (result) {
                    is SynthesisResult.Success -> {
                        println("🧠 Synthesized knowledge:")
                        println("  - ${result.knowledge.content}")
                        println("  - Confidence: ${result.knowledge.confidence}")
                    }
                    is SynthesisResult.Failure -> println("❌ Failed: ${result.error}")
                }
            }
            "create-subspace" -> {
                val avatar = currentAvatar ?: throw Exception("Not in metaverse")
                val name = parts.getOrNull(1) ?: "PrivateSpace"
                val description = parts.drop(2).joinToString(" ")
                
                val result = metaverse.createSubspace(
                    creatorId = avatar.id,
                    name = name,
                    description = description,
                    privacyLevel = PrivacyLevel.PRIVATE
                )
                
                when (result) {
                    is SubspaceResult.Success -> println("🏠 Created subspace: ${result.subspace.name}")
                    is SubspaceResult.Failure -> println("❌ Failed: ${result.error}")
                }
            }
            else -> {
                println("❓ Unknown command: $command")
            }
        }
    }
} 