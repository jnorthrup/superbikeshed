package borg.trikeshed.sumo.bitgraph

import kotlin.test.*
import borg.trikeshed.lib.*

/**
 * TDD Test for SUMO as Bitgraph
 * 
 * Testing SUMO ontology representation as bit-level graph structures
 */
class SumoBitgraphTest {
    
    @Test
    fun `test create SUMO concept as bitgraph node`() {
        // Test creating a SUMO concept as a bitgraph node
        val entityConcept = SumoBitgraph.createConcept("Entity")
        
        assertNotNull(entityConcept)
        assertEquals("Entity", entityConcept.name)
        assertTrue(entityConcept.bitPattern > 0)
        assertEquals(0, entityConcept.parents.size) // Entity is root
    }
    
    @Test
    fun `test SUMO subsumption as bitgraph edges`() {
        // Test subsumption relationships as bitgraph edges
        val entity = SumoBitgraph.createConcept("Entity")
        val physical = SumoBitgraph.createConcept("Physical")
        val abstract = SumoBitgraph.createConcept("Abstract")
        
        // Create subsumption edges
        val physicalEdge = SumoBitgraph.createSubsumption(entity, physical)
        val abstractEdge = SumoBitgraph.createSubsumption(entity, abstract)
        
        assertEquals(entity.bitPattern, physicalEdge.fromBits)
        assertEquals(physical.bitPattern, physicalEdge.toBits)
        assertTrue(physicalEdge.edgeBits != 0L)
        
        // Verify parent-child relationships
        assertEquals(1, physical.parents.size)
        assertTrue(physical.parents.contains(entity.bitPattern))
        assertEquals(2, entity.children.size)
    }
    
    @Test
    fun `test SUMO axiom as bitgraph constraint`() {
        // Test SUMO axioms as bitgraph constraints
        val human = SumoBitgraph.createConcept("Human")
        val animal = SumoBitgraph.createConcept("Animal")
        
        // Create axiom: (=> (instance ?X Human) (instance ?X Animal))
        val axiom = SumoBitgraph.createAxiom(
            type = AxiomType.IMPLICATION,
            antecedent = BitPattern.instance(human.bitPattern),
            consequent = BitPattern.instance(animal.bitPattern)
        )
        
        assertNotNull(axiom)
        assertEquals(AxiomType.IMPLICATION, axiom.type)
        assertTrue(axiom.validate(human.bitPattern))
    }
    
    @Test
    fun `test SUMO relation as bitgraph hyperedge`() {
        // Test n-ary relations as hyperedges
        val located = SumoBitgraph.createRelation(
            name = "located",
            arity = 2,
            domainTypes = listOf("Physical", "Object"),
            rangeType = "Object"
        )
        
        assertEquals("located", located.name)
        assertEquals(2, located.arity)
        assertTrue(located.signature != 0L)
        
        // Test relation instance
        val person = SumoBitgraph.createConcept("Person")
        val city = SumoBitgraph.createConcept("City")
        
        val locationFact = SumoBitgraph.createRelationInstance(
            relation = located,
            arguments = listOf(person.bitPattern, city.bitPattern)
        )
        
        assertEquals(located.signature, locationFact.relationSignature)
        assertEquals(2, locationFact.argumentBits.size)
    }
    
    @Test
    fun `test SUMO inference using bitgraph operations`() {
        // Test inference using bit operations
        val entity = SumoBitgraph.createConcept("Entity")
        val physical = SumoBitgraph.createConcept("Physical")
        val object = SumoBitgraph.createConcept("Object")
        
        SumoBitgraph.createSubsumption(entity, physical)
        SumoBitgraph.createSubsumption(physical, object)
        
        // Test transitive closure using bit operations
        val closure = SumoBitgraph.computeTransitiveClosure(object)
        
        assertTrue(closure.contains(physical.bitPattern))
        assertTrue(closure.contains(entity.bitPattern))
        assertEquals(3, closure.size) // object, physical, entity
    }
    
    @Test
    fun `test SUMO partition as bitgraph disjoint sets`() {
        // Test disjoint partitions
        val physical = SumoBitgraph.createConcept("Physical")
        val abstract = SumoBitgraph.createConcept("Abstract")
        
        val partition = SumoBitgraph.createPartition(
            parent = "Entity",
            children = listOf(physical, abstract)
        )
        
        assertTrue(partition.isExhaustive)
        assertTrue(partition.isDisjoint)
        
        // Test disjointness using bit operations
        val intersection = physical.bitPattern and abstract.bitPattern
        assertEquals(0L, intersection)
    }
    
    @Test
    fun `test SUMO function as bitgraph computation`() {
        // Test SUMO functions as computational graphs
        val motherFn = SumoBitgraph.createFunction(
            name = "MotherFn",
            domainType = "Human",
            rangeType = "Human"
        )
        
        assertEquals("MotherFn", motherFn.name)
        assertTrue(motherFn.computationBits != 0L)
        
        // Test function application
        val john = SumoBitgraph.createConcept("John")
        val mary = SumoBitgraph.createConcept("Mary")
        
        val application = SumoBitgraph.applyFunction(
            function = motherFn,
            argument = john.bitPattern,
            result = mary.bitPattern
        )
        
        assertEquals(motherFn.computationBits, application.functionBits)
        assertEquals(john.bitPattern, application.inputBits)
        assertEquals(mary.bitPattern, application.outputBits)
    }
    
    @Test
    fun `test SUMO merge using bitgraph union`() {
        // Test merging SUMO modules using bit operations
        val midLevelOntology = SumoBitgraph.createModule("MILO")
        val domainOntology = SumoBitgraph.createModule("Finance")
        
        // Add concepts to modules
        midLevelOntology.addConcept("GeographicArea")
        midLevelOntology.addConcept("GeopoliticalArea")
        
        domainOntology.addConcept("FinancialTransaction")
        domainOntology.addConcept("CurrencyExchange")
        
        // Merge modules
        val merged = SumoBitgraph.mergeModules(midLevelOntology, domainOntology)
        
        assertEquals(4, merged.conceptCount)
        assertTrue(merged.containsConcept("GeographicArea"))
        assertTrue(merged.containsConcept("FinancialTransaction"))
        
        // Check bit patterns don't collide
        val allPatterns = merged.getAllBitPatterns()
        assertEquals(allPatterns.size, allPatterns.toSet().size)
    }
}

/**
 * TDD Test for Yamato as Bitgraph
 */
class YamatoBitgraphTest {
    
    @Test
    fun `test create Yamato upper ontology as bitgraph`() {
        // Test Yamato's distinctive upper categories
        val endurant = YamatoBitgraph.createCategory("Endurant")
        val perdurant = YamatoBitgraph.createCategory("Perdurant")
        val abstract = YamatoBitgraph.createCategory("Abstract")
        
        assertNotNull(endurant)
        assertNotNull(perdurant)
        assertNotNull(abstract)
        
        // Verify bit encoding preserves distinctness
        assertNotEquals(endurant.bitSignature, perdurant.bitSignature)
        assertNotEquals(perdurant.bitSignature, abstract.bitSignature)
        assertNotEquals(endurant.bitSignature, abstract.bitSignature)
    }
    
    @Test
    fun `test Yamato role concepts as bitgraph roles`() {
        // Test Yamato's role hierarchy
        val role = YamatoBitgraph.createRole("Role")
        val qua = YamatoBitgraph.createRole("Qua")
        val phase = YamatoBitgraph.createRole("Phase")
        
        // Create role subsumption
        YamatoBitgraph.addRoleSubsumption(role, qua)
        YamatoBitgraph.addRoleSubsumption(role, phase)
        
        // Test bit operations for role checking
        assertTrue(YamatoBitgraph.isSubrole(qua, role))
        assertTrue(YamatoBitgraph.isSubrole(phase, role))
        assertFalse(YamatoBitgraph.isSubrole(qua, phase))
    }
    
    @Test
    fun `test Yamato dependence relations as bitgraph dependencies`() {
        // Test existential dependence
        val person = YamatoBitgraph.createCategory("Person")
        val brain = YamatoBitgraph.createCategory("Brain")
        
        val dependence = YamatoBitgraph.createDependence(
            dependent = person,
            foundation = brain,
            type = DependenceType.EXISTENTIAL
        )
        
        assertEquals(DependenceType.EXISTENTIAL, dependence.type)
        assertEquals(person.bitSignature, dependence.dependentBits)
        assertEquals(brain.bitSignature, dependence.foundationBits)
        
        // Test dependence propagation
        assertTrue(YamatoBitgraph.checkDependence(person, brain))
    }
    
    @Test
    fun `test Yamato process representation as temporal bitgraph`() {
        // Test process/perdurant representation
        val walking = YamatoBitgraph.createProcess("Walking")
        val running = YamatoBitgraph.createProcess("Running")
        
        // Add temporal ordering
        val sequence = YamatoBitgraph.createTemporalSequence(
            listOf(walking, running)
        )
        
        assertEquals(2, sequence.stages.size)
        assertTrue(sequence.isOrdered)
        
        // Test temporal bit operations
        val overlap = YamatoBitgraph.computeTemporalOverlap(walking, running)
        assertNotNull(overlap)
    }
    
    @Test
    fun `test Yamato quality spaces as bitgraph dimensions`() {
        // Test quality dimensions
        val color = YamatoBitgraph.createQualityDimension("Color")
        val weight = YamatoBitgraph.createQualityDimension("Weight")
        
        // Create quality space
        val qualitySpace = YamatoBitgraph.createQualitySpace(
            dimensions = listOf(color, weight)
        )
        
        assertEquals(2, qualitySpace.dimensionCount)
        assertTrue(qualitySpace.bitRepresentation != 0L)
        
        // Test quality assignment
        val apple = YamatoBitgraph.createCategory("Apple")
        val redHeavy = YamatoBitgraph.assignQualities(
            entity = apple,
            qualities = mapOf(
                color to 0xFF0000L, // red
                weight to 1000L      // 1kg
            )
        )
        
        assertEquals(2, redHeavy.qualityBits.size)
    }
    
    @Test
    fun `test Yamato-SUMO alignment as bitgraph mapping`() {
        // Test cross-ontology alignment
        val sumoEntity = SumoBitgraph.createConcept("Entity")
        val yamatoEndurant = YamatoBitgraph.createCategory("Endurant")
        
        val alignment = BitgraphAlignmentFactory.create(
            source = sumoEntity,
            target = yamatoEndurant,
            relation = AlignmentRelation.EQUIVALENT
        )
        
        assertEquals(AlignmentRelation.EQUIVALENT, alignment.relation)
        assertTrue(alignment.confidence >= 0.8)
        
        // Test alignment composition
        val sumoPhysical = SumoBitgraph.createConcept("Physical")
        val yamatoPhysical = YamatoBitgraph.createCategory("PhysicalEndurant")
        
        val composed = BitgraphAlignmentFactory.compose(
            listOf(alignment,
                BitgraphAlignmentFactory.create(sumoPhysical, yamatoPhysical, AlignmentRelation.SUBCLASS))
        )
        
        assertTrue(composed.isConsistent)
    }
    
    @Test
    fun `test bitgraph query optimization`() {
        // Test efficient querying using bit operations
        val graph = YamatoBitgraph.buildTestOntology()
        
        // Query: Find all physical endurants
        val physicalMask = YamatoBitgraph.getBitMask("PhysicalEndurant")
        val results = graph.queryByBitMask(physicalMask)
        
        assertTrue(results.isNotEmpty())
        results.forEach { result ->
            assertTrue((result.bitSignature and physicalMask) != 0L)
        }
    }
    
    @Test
    fun `test bitgraph serialization and compression`() {
        // Test efficient storage
        val graph = YamatoBitgraph.buildTestOntology()
        
        val serialized = graph.serialize()
        assertTrue(serialized.size < 1000) // Compact representation
        
        val deserialized = YamatoBitgraph.deserialize(serialized)
        assertEquals(graph.nodeCount, deserialized.nodeCount)
        assertEquals(graph.edgeCount, deserialized.edgeCount)
    }
}