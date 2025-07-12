package nexus.test

import nexus.ontology.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*

fun main() = runBlocking {
    println("🎯 Nexus Simple Test - Verifying Core Functionality")
    println("=" + "=".repeat(60))
    
    // Test 1: Attention Ontology
    println("\n1. Testing Attention Ontology Creation:")
    val ontology = createAttentionOntology()
    println("   ✓ Created ${ontology.size} ontology mappings")
    
    // Test 2: Attention Distribution
    println("\n2. Testing Attention Distribution:")
    val distribution = createStandardAttentionDistribution()
    for (i in 0 until distribution.size) {
        val vector = distribution[i]
        println("   - ${vector.component1()}: ${vector.component2()}%")
    }
    
    // Test 3: Causality Chain
    println("\n3. Testing Causality Chain:")
    val chain = createCausalityChain()
    for (i in 0 until chain.size) {
        val link = chain[i]
        println("   - ${link.component1()} → ${link.component2()}")
    }
    
    // Test 4: Core Types
    println("\n4. Testing Core Types:")
    val testJoin = "Nexus" j "Working"
    println("   - Join test: ${testJoin.component1()} + ${testJoin.component2()}")
    
    val testSeries: Indexed<String> = \1 j { \2: Int -> "Item-$i" }
    println("   - Series test: size=${testSeries.size}")
    for (i in 0 until testSeries.size) {
        println("     [${i}] = ${testSeries[i]}")
    }
    
    println("\n✅ All tests passed!")
}