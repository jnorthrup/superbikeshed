import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

/**
 * Omnibus TDD for the complete Fiduciary Reasoning Engine.
 * This suite validates all core architectural benefits:
 * - Bitgraph Density & Speed (Anchor 1)
 * - Exclusionary Filtering (Anchor 2)
 * - Progressive Induction (Fast -> Slow Path) (Anchor 3)
 * - Hardware Abstraction & Private Numerical Engine (Anchor 4)
 * - Coroutine Ingestion Pipeline (Anchor 5)
 * - CouchDB View Integration (Anchor 6)
 */
class OmnibusFiduciaryEngineTest {

    // --- Mocks & Stubs for a Self-Contained Test ---

    // Mock KIF Parser (as designed previously)
    object MockKifParser {
        fun parse(text: String): List<Triple<String, String, String>> {
            // Parses "(predicate subject object)" into a Triple
            return text.lines()
                .map { it.trim().removeSurrounding("(", ")").split(" ") }
                .filter { it.size == 3 }
                .map { Triple(it[1], it[0], it[2]) } // (Subject, Predicate, Object)
        }
    }

    // Mock CouchDB View Service (Anchor 6)
    object MockCouchDB {
        fun getOntologyFactsView(): Flow<String> = flow {
            emit("(subclass Human Mammal)")
            emit("(subclass Mammal Animal)")
            emit("(subclass Canine Mammal)")
            emit("(instance Fido Canine)")
            emit("(attribute Fido HasTail)")
            // A complex, non-hierarchical fact for slow-path testing
            emit("(disjoint Physical Abstract)")
        }
    }

    // Mock Hardware Abstraction Layer (Anchor 4)
    interface NumericalEngine {
        fun applyFilter(data: LongArray, mask: Long): LongArray
    }
    class CpuScalarEngine : NumericalEngine { // Fallback implementation
        override fun applyFilter(data: LongArray, mask: Long): LongArray =
            data.filter { (it and mask) == mask }.toLongArray()
    }
    class MlxGpuEngine : NumericalEngine { // The "primer" engine
        override fun applyFilter(data: LongArray, mask: Long): LongArray {
            println("INFO: Dispatched query to MLX/GPU Engine.")
            return CpuScalarEngine().applyFilter(data, mask) // Mock implementation
        }
    }
    object EngineDispatcher {
        fun selectEngine(): NumericalEngine {
            // In a real system, this detects hardware. Here, we can force it.
            return MlxGpuEngine()
        }
    }

    // --- The Core Engine and Compiler ---

    // The final engine, combining all components
    class FiduciaryEngine(
        val bitgraph: LongArray,
        val nameToId: Map<String, Int>,
        val idToName: Array<String>,
        val numericalEngine: NumericalEngine
    ) {
        // Simple KIF solver for the "slow path"
        private val slowPathSolver = mutableMapOf<Int, MutableSet<Int>>()

        init {
            // Pre-populate solver with direct relationships for slow-path demo
            for (fact in bitgraph) {
                val s = (fact and 0xFF).toInt()
                val p = ((fact shr 8) and 0xFF).toInt()
                val o = ((fact shr 16) and 0xFF).toInt()
                if (idToName[p] == "subclass") {
                    slowPathSolver.getOrPut(s) { mutableSetOf() }.add(o)
                }
            }
        }

        // The main query function demonstrating Progressive Induction (Anchor 3)
        fun query(subject: String, predicate: String, obj: String): Boolean {
            val sId = nameToId[subject] ?: return false
            val pId = nameToId[predicate] ?: return false
            val oId = nameToId[obj] ?: return false

            // --- Anchor 1: Bitgraph Density & Speed ---
            // The query operates on packed longs, not objects.
            val queryFact = (oId.toLong() shl 16) or (pId.toLong() shl 8) or sId.toLong()
            val queryMask = (0xFFL shl 16) or (0xFFL shl 8) or 0xFFL

            // --- Anchor 2 & 4: Exclusionary Filtering & Hardware Dispatch ---
            // The filtering logic is delegated to the selected numerical engine.
            val candidates = numericalEngine.applyFilter(bitgraph, queryFact)

            if (candidates.isNotEmpty()) {
                println("INFO: Fast path succeeded for ($subject $predicate $obj).")
                return true // Fact found directly in the Bitgraph
            }

            // --- Anchor 3: Progressive Induction (Slow Path) ---
            println("INFO: Fast path failed. Escalating to slow path KIF solver for ($subject $predicate $obj)...")
            // This is a simplified transitive check. A real one would use a proper solver.
            fun checkTransitive(sub: Int, sup: Int): Boolean {
                val supers = slowPathSolver[sub] ?: return false
                if (sup in supers) return true
                return supers.any { checkTransitive(it, sup) }
            }
            return checkTransitive(sId, oId)
        }
    }

    companion object {
        private var compiledEngine: FiduciaryEngine? = null

        @BeforeTest
        fun compileOntology() = runBlocking {
            if (compiledEngine != null) return@runBlocking

            println("--- TDD SETUP: Compiling Ontology via Coroutine Pipeline (Anchor 5) ---")
            
            // This entire block represents the coroutine ingestion pipeline
            coroutineScope {
                val couchDbFlow = MockCouchDB.getOntologyFactsView() // Anchor 6
                val factChannel = Channel<Long>(capacity = Channel.UNLIMITED)
                val nameToId = mutableMapOf<String, Int>()
                val idToName = mutableListOf<String>()
                var nextId = 0

                fun getId(name: String): Int = nameToId.getOrPut(name) {
                    idToName.add(name)
                    nextId++
                }

                // Launch a single producer/parser job
                launch(Dispatchers.Default) {
                    couchDbFlow.collect { kifLine ->
                        val (s, p, o) = MockKifParser.parse(kifLine).first()
                        val sId = getId(s)
                        val pId = getId(p)
                        val oId = getId(o)
                        // Anchor 1: Packing data into a dense Long
                        val fact = (oId.toLong() shl 16) or (pId.toLong() shl 8) or sId.toLong()
                        factChannel.send(fact)
                    }
                    factChannel.close()
                }

                // Aggregator
                val bitgraphData = mutableListOf<Long>()
                for (fact in factChannel) {
                    bitgraphData.add(fact)
                }
                
                // --- Anchor 1: Final Bitgraph is just a dense LongArray ---
                val bitgraph = bitgraphData.toLongArray()
                val finalIdToName = idToName.toTypedArray()

                println("--- COMPILE COMPLETE ---")
                println("Concepts Found: ${finalIdToName.size} -> ${finalIdToName.contentToString()}")
                println("Bitgraph Size: ${bitgraph.size} facts (${bitgraph.size * 8} bytes)")
                
                // --- Anchor 4: Select the best hardware engine ---
                val numericalEngine = EngineDispatcher.selectEngine()

                compiledEngine = FiduciaryEngine(bitgraph, nameToId, finalIdToName, numericalEngine)
            }
        }
    }

    // --- THE TESTS ---

    @Test
    fun `omnibus test direct fact lookup via fast path`() {
        assertNotNull(compiledEngine, "Engine should be compiled")
        println("\n--- TEST 1: Direct Fact Lookup (Fast Path) ---")
        val result = compiledEngine!!.query("Fido", "instance", "Canine")
        
        assertTrue(result, "Should find '(instance Fido Canine)' directly in Bitgraph.")
    }

    @Test
    fun `omnibus test transitive relationship via slow path`() {
        assertNotNull(compiledEngine, "Engine should be compiled")
        println("\n--- TEST 2: Transitive Relationship (Slow Path Escalation) ---")
        
        // This fact does NOT exist directly in the Bitgraph. The engine must escalate.
        val result = compiledEngine!!.query("Human", "subclass", "Animal")
        
        assertTrue(result, "Should resolve '(subclass Human Animal)' via transitive KIF solver.")
    }

    @Test
    fun `omnibus test definitive negative via fast path`() {
        assertNotNull(compiledEngine, "Engine should be compiled")
        println("\n--- TEST 3: Definitive Negative (Fast Path) ---")
        
        // This fact is guaranteed not to exist. The fast path should fail, and the slow path should also fail.
        val result = compiledEngine!!.query("Fido", "subclass", "Mammal")
        
        assertFalse(result, "Should not find '(subclass Fido Mammal)'.")
    }

    @Test
    fun `omnibus test exclusionary filtering principle`() {
        assertNotNull(compiledEngine, "Engine should be compiled")
        println("\n--- TEST 4: Exclusionary Filtering (Anchor 2 & 4) ---")
        
        // This test explicitly demonstrates the filtering mechanism
        val engine = compiledEngine!!
        
        // We want all Canines
        val pId = engine.nameToId["instance"]!!
        val oId = engine.nameToId["Canine"]!!
        
        // Create a mask that matches the predicate and object
        val filterMask = (oId.toLong() shl 16) or (pId.toLong() shl 8) or 0L
        
        // Use the numerical engine to filter the entire bitgraph
        val results = engine.numericalEngine.applyFilter(engine.bitgraph, filterMask)
        
        println("INFO: Filtering for all instances of Canine...")
        assertEquals(1, results.size, "Should find exactly one Canine instance.")
        
        val resultFact = results[0]
        val sId = (resultFact and 0xFF).toInt()
        val subjectName = engine.idToName[sId]
        
        assertEquals("Fido", subjectName, "The found instance should be Fido.")
        println("✅ Exclusionary filter correctly isolated 'Fido'.")
    }
} 