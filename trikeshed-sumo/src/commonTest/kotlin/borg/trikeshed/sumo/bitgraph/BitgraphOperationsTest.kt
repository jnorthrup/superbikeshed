package borg.trikeshed.sumo.bitgraph

import kotlin.test.*
import borg.trikeshed.lib.*

/**
 * TDD Test for Bitgraph Operations
 * 
 * Testing efficient bit-level operations for ontology reasoning
 */
class BitgraphOperationsTest {
    
    @Test
    fun `test bitgraph subsumption checking with bit masks`() {
        // Create test hierarchy
        val bitOps = BitgraphOperations()
        
        val entity = bitOps.createNode("Entity", 0b1)
        val physical = bitOps.createNode("Physical", 0b11)  // Inherits entity bit
        val object_ = bitOps.createNode("Object", 0b111)    // Inherits physical bits
        val artifact = bitOps.createNode("Artifact", 0b1111) // Inherits object bits
        
        // Test subsumption using bit operations
        assertTrue(bitOps.isSubsumedBy(artifact, object_))
        assertTrue(bitOps.isSubsumedBy(object_, physical))
        assertTrue(bitOps.isSubsumedBy(physical, entity))
        
        // Test transitive subsumption
        assertTrue(bitOps.isSubsumedBy(artifact, entity))
        
        // Test non-subsumption
        val abstract = bitOps.createNode("Abstract", 0b10)
        assertFalse(bitOps.isSubsumedBy(physical, abstract))
    }
    
    @Test
    fun `test efficient concept intersection using AND operation`() {
        val bitOps = BitgraphOperations()
        
        // Create concepts with specific bit patterns
        val human = bitOps.createNode("Human", 0b110100)     // Physical + Animate + Rational
        val animal = bitOps.createNode("Animal", 0b110000)    // Physical + Animate
        val rational = bitOps.createNode("Rational", 0b100100) // Abstract + Rational
        
        // Test intersection
        val humanAnimalIntersection = bitOps.intersect(human, animal)
        assertEquals(0b110000L, humanAnimalIntersection) // Common bits: Physical + Animate
        
        val humanRationalIntersection = bitOps.intersect(human, rational)
        assertEquals(0b100100L, humanRationalIntersection) // Common bit: Rational
    }
    
    @Test
    fun `test concept union using OR operation`() {
        val bitOps = BitgraphOperations()
        
        val student = bitOps.createNode("Student", 0b1010)
        val employee = bitOps.createNode("Employee", 0b1100)
        
        // Union represents "Student OR Employee"
        val studentOrEmployee = bitOps.union(student, employee)
        assertEquals(0b1110L, studentOrEmployee)
        
        // Test membership
        assertTrue(bitOps.isMemberOf(student, studentOrEmployee))
        assertTrue(bitOps.isMemberOf(employee, studentOrEmployee))
    }
    
    @Test
    fun `test fast sibling detection using XOR`() {
        val bitOps = BitgraphOperations()
        
        // Siblings share parent bits but differ in their specific bits
        val cat = bitOps.createNode("Cat", 0b11010)     // Animal + Feline
        val dog = bitOps.createNode("Dog", 0b11100)     // Animal + Canine
        val tiger = bitOps.createNode("Tiger", 0b11011) // Animal + Feline + Wild
        
        // Test sibling detection
        assertTrue(bitOps.areSiblings(cat, dog))    // Both animals, different species
        assertFalse(bitOps.areSiblings(cat, tiger)) // Tiger is subclass of cat
    }
    
    @Test
    fun `test role propagation using bit shifting`() {
        val bitOps = BitgraphOperations()
        
        // Roles use specific bit ranges
        val agentRole = 0b1L shl 32      // Agent role bit
        val patientRole = 0b1L shl 33    // Patient role bit
        val instrumentRole = 0b1L shl 34 // Instrument role bit
        
        val person = bitOps.createNode("Person", 0b111)
        
        // Assign roles
        val personAsAgent = bitOps.assignRole(person, agentRole)
        val personAsPatient = bitOps.assignRole(person, patientRole)
        
        // Test role checking
        assertTrue(bitOps.hasRole(personAsAgent, agentRole))
        assertTrue(bitOps.hasRole(personAsPatient, patientRole))
        assertFalse(bitOps.hasRole(personAsAgent, instrumentRole))
    }
    
    @Test
    fun `test parallel reasoning with SIMD-like operations`() {
        val bitOps = BitgraphOperations()
        
        // Batch subsumption checking
        val concepts = listOf(
            bitOps.createNode("Dog", 0b11100),
            bitOps.createNode("Cat", 0b11010),
            bitOps.createNode("Bird", 0b10110),
            bitOps.createNode("Fish", 0b10011)
        )
        
        val animalMask = 0b10000L // Animal bit
        
        // Parallel check which concepts are animals
        val results = bitOps.batchSubsumptionCheck(concepts, animalMask)
        
        assertEquals(4, results.count { it }) // All are animals
    }
    
    @Test
    fun `test concept similarity using Hamming distance`() {
        val bitOps = BitgraphOperations()
        
        val car = bitOps.createNode("Car", 0b110110)
        val truck = bitOps.createNode("Truck", 0b110111)
        val bicycle = bitOps.createNode("Bicycle", 0b100110)
        val airplane = bitOps.createNode("Airplane", 0b101110)
        
        // Calculate similarities
        val carTruckSim = bitOps.similarity(car, truck)
        val carBicycleSim = bitOps.similarity(car, bicycle)
        val carAirplaneSim = bitOps.similarity(car, airplane)
        
        // Truck is most similar to car
        assertTrue(carTruckSim > carBicycleSim)
        assertTrue(carTruckSim > carAirplaneSim)
    }
    
    @Test
    fun `test efficient path finding using bit operations`() {
        val bitOps = BitgraphOperations()
        
        // Create hierarchy with path encoding
        val root = bitOps.createNode("Thing", 0b1)
        val a = bitOps.createNode("A", 0b11)
        val b = bitOps.createNode("B", 0b101)
        val c = bitOps.createNode("C", 0b111)
        val d = bitOps.createNode("D", 0b1111)
        
        // Find path from D to root
        val path = bitOps.findPath(d, root)
        
        assertEquals(4, path.size) // D -> C -> A -> Thing
        assertTrue(path.contains(c.bits))
        assertTrue(path.contains(a.bits))
    }
    
    @Test
    fun `test concept negation using bit complement`() {
        val bitOps = BitgraphOperations()
        
        val living = bitOps.createNode("Living", 0b1010)
        val mask = 0b1111L // Domain mask
        
        // Negation within domain
        val nonLiving = bitOps.negate(living, mask)
        assertEquals(0b0101L, nonLiving)
        
        // Test disjointness
        val intersection = bitOps.intersect(living, BitgraphNode("NonLiving", nonLiving))
        assertEquals(0L, intersection)
    }
    
    @Test
    fun `test incremental reasoning with bit updates`() {
        val bitOps = BitgraphOperations()
        
        // Start with basic concept
        var concept = bitOps.createNode("Basic", 0b1)
        
        // Incrementally add features
        concept = bitOps.addFeature(concept, Feature.PHYSICAL)
        assertTrue((concept.bits and Feature.PHYSICAL.bit) != 0L)
        
        concept = bitOps.addFeature(concept, Feature.ANIMATE)
        assertTrue((concept.bits and Feature.ANIMATE.bit) != 0L)
        
        concept = bitOps.addFeature(concept, Feature.RATIONAL)
        assertTrue((concept.bits and Feature.RATIONAL.bit) != 0L)
        
        // Verify all features present
        assertEquals(0b1111L, concept.bits)
    }
    
    @Test
    fun `test concept clustering using bit patterns`() {
        val bitOps = BitgraphOperations()
        
        val concepts = listOf(
            bitOps.createNode("Dog", 0b11100),     // Cluster 1: Land animals
            bitOps.createNode("Cat", 0b11101),
            bitOps.createNode("Horse", 0b11110),
            
            bitOps.createNode("Shark", 0b10011),   // Cluster 2: Sea animals
            bitOps.createNode("Whale", 0b10010),
            bitOps.createNode("Dolphin", 0b10010),
            
            bitOps.createNode("Eagle", 0b10101),   // Cluster 3: Flying animals
            bitOps.createNode("Sparrow", 0b10100),
            bitOps.createNode("Hawk", 0b10101)
        )
        
        val clusters = bitOps.clusterByBitPattern(concepts, 3)
        
        assertEquals(3, clusters.size)
        assertEquals(3, clusters[0].size) // Land animals
        assertEquals(3, clusters[1].size) // Sea animals
        assertEquals(3, clusters[2].size) // Flying animals
    }
}

/**
 * Bitgraph operations implementation
 */
class BitgraphOperations {
    
    fun createNode(name: String, bits: Long): BitgraphNode {
        return BitgraphNode(name, bits)
    }
    
    fun isSubsumedBy(child: BitgraphNode, parent: BitgraphNode): Boolean {
        // Child has all parent's bits
        return (child.bits and parent.bits) == parent.bits
    }
    
    fun intersect(n1: BitgraphNode, n2: BitgraphNode): Long {
        return n1.bits and n2.bits
    }
    
    fun union(n1: BitgraphNode, n2: BitgraphNode): Long {
        return n1.bits or n2.bits
    }
    
    fun isMemberOf(node: BitgraphNode, unionBits: Long): Boolean {
        return (node.bits and unionBits) == node.bits
    }
    
    fun areSiblings(n1: BitgraphNode, n2: BitgraphNode): Boolean {
        // Share significant parent bits but differ in specifics
        val commonBits = n1.bits and n2.bits
        val xorBits = n1.bits xor n2.bits
        
        // Heuristic: siblings if they share >50% bits and have some differences
        val sharedCount = commonBits.countOneBits()
        val totalBits = (n1.bits or n2.bits).countOneBits()
        
        return sharedCount > totalBits / 2 && xorBits != 0L
    }
    
    fun assignRole(node: BitgraphNode, roleBit: Long): BitgraphNode {
        return node.copy(bits = node.bits or roleBit)
    }
    
    fun hasRole(node: BitgraphNode, roleBit: Long): Boolean {
        return (node.bits and roleBit) != 0L
    }
    
    fun batchSubsumptionCheck(nodes: List<BitgraphNode>, mask: Long): List<Boolean> {
        return nodes.map { node ->
            (node.bits and mask) != 0L
        }
    }
    
    fun similarity(n1: BitgraphNode, n2: BitgraphNode): Double {
        val xor = n1.bits xor n2.bits
        val distance = xor.countOneBits()
        val maxBits = maxOf(n1.bits.countOneBits(), n2.bits.countOneBits())
        
        return 1.0 - (distance.toDouble() / maxBits)
    }
    
    fun findPath(from: BitgraphNode, to: BitgraphNode): List<Long> {
        val path = mutableListOf<Long>()
        var current = from.bits
        
        // Simple bit reduction towards target
        while (current != to.bits && current != 0L) {
            path.add(current)
            // Remove least significant differing bit
            val diff = current xor to.bits
            val lsb = diff and -diff
            current = current and lsb.inv()
        }
        
        if (current == to.bits) path.add(to.bits)
        return path
    }
    
    fun negate(node: BitgraphNode, mask: Long): Long {
        return node.bits.inv() and mask
    }
    
    fun addFeature(node: BitgraphNode, feature: Feature): BitgraphNode {
        return node.copy(bits = node.bits or feature.bit)
    }
    
    fun clusterByBitPattern(nodes: List<BitgraphNode>, k: Int): List<List<BitgraphNode>> {
        // Simple k-means-like clustering based on bit patterns
        val clusters = MutableList(k) { mutableListOf<BitgraphNode>() }
        
        // Initialize cluster centers
        val centers = nodes.take(k).map { it.bits }.toMutableList()
        
        // Assign nodes to nearest cluster
        nodes.forEach { node ->
            val nearestCluster = centers.indices.minByOrNull { i ->
                (node.bits xor centers[i]).countOneBits()
            } ?: 0
            clusters[nearestCluster].add(node)
        }
        
        return clusters
    }
}

enum class Feature(val bit: Long) {
    PHYSICAL(0b10),
    ANIMATE(0b100),
    RATIONAL(0b1000)
}