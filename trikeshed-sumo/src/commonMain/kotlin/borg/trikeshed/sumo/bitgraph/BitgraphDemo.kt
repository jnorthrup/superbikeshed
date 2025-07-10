2package borg.trikeshed.sumo.bitgraph

import borg.trikeshed.lib.*

/**
 * Bitgraph Demo
 * 
 * Demonstrates efficient ontology operations using bit-level representations
 */
object BitgraphDemo {
    
    fun main() {
        println("=== SUMO & Yamato Bitgraph Demo ===\n")
        
        demonstrateSUMOBitgraph()
        println("\n" + "=".repeat(50) + "\n")
        demonstrateYamatoBitgraph()
        println("\n" + "=".repeat(50) + "\n")
        demonstrateCrossOntologyAlignment()
        println("\n" + "=".repeat(50) + "\n")
        demonstratePerformance()
    }
    
    private fun demonstrateSUMOBitgraph() {
        println("1. SUMO Bitgraph Operations")
        println("-".repeat(30))
        
        // Create SUMO hierarchy
        val entity = SumoBitgraph.createConcept("Entity")
        val physical = SumoBitgraph.createConcept("Physical")
        val abstract = SumoBitgraph.createConcept("Abstract")
        val object_ = SumoBitgraph.createConcept("Object")
        val process = SumoBitgraph.createConcept("Process")
        
        // Build subsumption hierarchy
        SumoBitgraph.createSubsumption(entity, physical)
        SumoBitgraph.createSubsumption(entity, abstract)
        SumoBitgraph.createSubsumption(physical, object_)
        SumoBitgraph.createSubsumption(physical, process)
        
        println("Created SUMO concepts with bit patterns:")
        println("  Entity:   ${entity.bitPattern.toString(2).padStart(8, '0')}")
        println("  Physical: ${physical.bitPattern.toString(2).padStart(8, '0')}")
        println("  Object:   ${object_.bitPattern.toString(2).padStart(8, '0')}")
        
        // Test transitive closure
        val closure = SumoBitgraph.computeTransitiveClosure(object_)
        println("\nTransitive closure of Object: ${closure.size} concepts")
        
        // Create and test axiom
        val axiom = SumoBitgraph.createAxiom(
            type = AxiomType.IMPLICATION,
            antecedent = physical.bitPattern,
            consequent = entity.bitPattern
        )
        println("\nAxiom validation for Physical→Entity: ${axiom.validate(physical.bitPattern)}")
    }
    
    private fun demonstrateYamatoBitgraph() {
        println("2. Yamato Bitgraph Operations")
        println("-".repeat(30))
        
        // Create Yamato categories
        val endurant = YamatoBitgraph.createCategory("Endurant")
        val perdurant = YamatoBitgraph.createCategory("Perdurant")
        val physicalEnd = YamatoBitgraph.createCategory("PhysicalEndurant")
        
        println("Created Yamato categories:")
        println("  Endurant:  ${endurant.bitSignature.toString(16)}")
        println("  Perdurant: ${perdurant.bitSignature.toString(16)}")
        
        // Create roles
        val studentRole = YamatoBitgraph.createRole("Student")
        val teacherRole = YamatoBitgraph.createRole("Teacher")
        
        // Create dependence
        val person = YamatoBitgraph.createCategory("Person")
        val brain = YamatoBitgraph.createCategory("Brain")
        val dep = YamatoBitgraph.createDependence(person, brain, DependenceType.EXISTENTIAL)
        
        println("\nCreated existential dependence: Person → Brain")
        
        // Create quality space
        val color = YamatoBitgraph.createQualityDimension("Color")
        val weight = YamatoBitgraph.createQualityDimension("Weight")
        val qualitySpace = YamatoBitgraph.createQualitySpace(listOf(color, weight))
        
        println("\nQuality space with ${qualitySpace.dimensionCount} dimensions")
        println("  Bit representation: ${qualitySpace.bitRepresentation.toString(16)}")
    }
    
    private fun demonstrateCrossOntologyAlignment() {
        println("3. Cross-Ontology Alignment")
        println("-".repeat(30))
        
        // Create aligned concepts
        val sumoEntity = SumoBitgraph.createConcept("Entity")
        val yamatoEntity = YamatoBitgraph.createCategory("Entity")
        
        val alignment = BitgraphAlignmentFactory.create(
            source = sumoEntity,
            target = yamatoEntity,
            relation = AlignmentRelation.EQUIVALENT
        )
        
        println("Alignment created:")
        println("  SUMO Entity (${sumoEntity.bitPattern}) ≡ Yamato Entity (${yamatoEntity.bitSignature})")
        println("  Confidence: ${alignment.confidence}")
        
        // Create more alignments
        val sumoPhysical = SumoBitgraph.createConcept("Physical")
        val yamatoPhysical = YamatoBitgraph.createCategory("PhysicalEndurant")
        
        val alignments = listOf(
            alignment,
            BitgraphAlignmentFactory.create(sumoPhysical, yamatoPhysical, AlignmentRelation.SUBCLASS)
        )
        
        val composed = BitgraphAlignmentFactory.compose(alignments)
        println("\nComposed alignment consistency: ${composed.isConsistent}")
    }
    
    private fun demonstratePerformance() {
        println("4. Performance Benchmarks")
        println("-".repeat(30))
        
        val bitOps = BitgraphOperations()
        
        // Create large concept hierarchy
        val concepts = (1..1000).map { i ->
            bitOps.createNode("Concept$i", i.toLong())
        }
        
        // Benchmark subsumption checking
        val start1 = System.nanoTime()
        var subsumptionCount = 0
        for (i in concepts.indices) {
            for (j in i + 1 until concepts.size) {
                if (bitOps.isSubsumedBy(concepts[j], concepts[i])) {
                    subsumptionCount++
                }
            }
        }
        val subsumptionTime = (System.nanoTime() - start1) / 1_000_000
        
        println("Subsumption checks on 1000 concepts:")
        println("  Total checks: ${concepts.size * (concepts.size - 1) / 2}")
        println("  Found subsumptions: $subsumptionCount")
        println("  Time: ${subsumptionTime}ms")
        
        // Benchmark batch operations
        val mask = 0b11111L
        val start2 = System.nanoTime()
        val batchResults = bitOps.batchSubsumptionCheck(concepts, mask)
        val batchTime = (System.nanoTime() - start2) / 1_000_000
        
        println("\nBatch subsumption check:")
        println("  Concepts matching mask: ${batchResults.count { it }}")
        println("  Time: ${batchTime}ms")
        
        // Benchmark clustering
        val start3 = System.nanoTime()
        val clusters = bitOps.clusterByBitPattern(concepts.take(100), 5)
        val clusterTime = (System.nanoTime() - start3) / 1_000_000
        
        println("\nClustering 100 concepts into 5 groups:")
        clusters.forEachIndexed { i, cluster ->
            println("  Cluster $i: ${cluster.size} concepts")
        }
        println("  Time: ${clusterTime}ms")
        
        // Memory efficiency
        val traditionalSize = concepts.size * 64 // Assuming 64 bytes per concept object
        val bitgraphSize = concepts.size * 8   // 8 bytes per long
        
        println("\nMemory efficiency:")
        println("  Traditional representation: ${traditionalSize / 1024}KB")
        println("  Bitgraph representation: ${bitgraphSize / 1024}KB")
        println("  Compression ratio: ${traditionalSize.toDouble() / bitgraphSize}x")
    }
    
    /**
     * Demonstrate practical reasoning scenario
     */
    fun demonstrateReasoning() {
        println("\n5. Practical Reasoning Example")
        println("-".repeat(30))
        
        // Create financial ontology concepts
        val financialInstrument = SumoBitgraph.createConcept("FinancialInstrument")
        val equity = SumoBitgraph.createConcept("Equity")
        val debt = SumoBitgraph.createConcept("Debt")
        val derivative = SumoBitgraph.createConcept("Derivative")
        
        // Set up hierarchy
        SumoBitgraph.createSubsumption(financialInstrument, equity)
        SumoBitgraph.createSubsumption(financialInstrument, debt)
        SumoBitgraph.createSubsumption(financialInstrument, derivative)
        
        // Create hybrid instrument (convertible bond)
        val convertibleBond = SumoBitgraph.createConcept("ConvertibleBond")
        
        // Multiple inheritance using bit operations
        val bitOps = BitgraphOperations()
        val debtBits = debt.bitPattern
        val equityBits = equity.bitPattern
        
        // Convertible bond has characteristics of both debt and equity
        val hybridBits = debtBits or equityBits
        
        println("Financial instrument analysis:")
        println("  Debt bits:     ${debtBits.toString(2).padStart(8, '0')}")
        println("  Equity bits:   ${equityBits.toString(2).padStart(8, '0')}")
        println("  Hybrid bits:   ${hybridBits.toString(2).padStart(8, '0')}")
        
        // Check relationships
        val debtNode = bitOps.createNode("Debt", debtBits)
        val equityNode = bitOps.createNode("Equity", equityBits)
        val hybridNode = bitOps.createNode("ConvertibleBond", hybridBits)
        
        println("\nConvertible bond properties:")
        println("  Has debt features: ${(hybridBits and debtBits) == debtBits}")
        println("  Has equity features: ${(hybridBits and equityBits) == equityBits}")
        println("  Similarity to debt: ${bitOps.similarity(hybridNode, debtNode)}")
        println("  Similarity to equity: ${bitOps.similarity(hybridNode, equityNode)}")
    }
}

