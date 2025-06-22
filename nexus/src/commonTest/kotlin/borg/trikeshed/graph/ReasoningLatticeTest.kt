package borg.trikeshed.graph

import borg.trikeshed.lib.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reasoning Lattice Test - Complete evidence chain to pattern recognition pipeline
 * 
 * Demonstrates the full "bolstering clustering of logic or reasoning" where:
 * 1. Token scanners collect positional evidence from keyword anchors
 * 2. Forward/backward chaining builds evidence statistics  
 * 3. Evidence clusters into dense bitfield patterns
 * 4. Patterns strengthen through statistical accumulation
 * 5. LLM can traverse the reasoning lattice instantly
 * 
 * This creates a "semantic map" that preserves the complete reasoning chain
 * from raw tokens to architectural understanding.
 */
class ReasoningLatticeTest {
    
    /**
     * Complete reasoning lattice node - combines evidence chain with dense clustering
     */
    data class ReasoningLatticeNode(
        val evidenceChain: CompleteEvidenceChain,
        val denseCluster: DenseEvidenceCluster,
        val confidenceScore: Float,
        val reasoningPath: List<InferenceStep>
    )
    
    /**
     * Inference step in the reasoning chain
     */
    data class InferenceStep(
        val stepType: InferenceType,
        val inputEvidence: String,
        val outputPattern: String,
        val confidence: Float
    )
    
    enum class InferenceType {
        TOKEN_SCAN,           // Raw token collection
        POSITIONAL_ANALYSIS,  // Forward/backward chaining
        PATTERN_CLUSTERING,   // Evidence bit clustering  
        SEMANTIC_DEDUCTION,   // Final pattern recognition
        CONFIDENCE_BOLSTERING // Statistical strengthening
    }
    
    /**
     * Reasoning lattice builder - creates complete evidence → pattern pipeline
     */
    class ReasoningLatticeBuilder {
        private val nodes = mutableListOf<ReasoningLatticeNode>()
        private val patternCounts = mutableMapOf<String, Int>()
        
        fun processKotlinCode(sourceCode: String): ReasoningLattice {
            val tokens = tokenize(sourceCode)
            val anchorPositions = findKeywordAnchors(tokens)
            
            anchorPositions.forEach { (anchor, position) ->
                val reasoningPath = buildReasoningPath(tokens, anchor, position)
                val latticeNode = createLatticeNode(reasoningPath, anchor)
                nodes.add(latticeNode)
                
                // Track pattern frequency for bolstering
                val pattern = latticeNode.denseCluster.anchorType.toString()
                patternCounts[pattern] = patternCounts.getOrDefault(pattern, 0) + 1
            }
            
            // Apply confidence bolstering based on pattern frequency
            bolsterConfidenceFromClustering()
            
            return ReasoningLattice(nodes.toIndexed())
        }
        
        private fun buildReasoningPath(
            tokens: List<Token>, 
            anchor: String, 
            position: Int
        ): List<InferenceStep> {
            val steps = mutableListOf<InferenceStep>()
            
            // Step 1: Token scanning
            steps.add(InferenceStep(
                stepType = InferenceType.TOKEN_SCAN,
                inputEvidence = "tokens[${position-2}..${position+5}]",
                outputPattern = "anchor=$anchor at position=$position",
                confidence = 1.0f
            ))
            
            // Step 2: Positional analysis
            val backwardEvidence = analyzeBackward(tokens, position)
            val forwardEvidence = analyzeForward(tokens, position)
            steps.add(InferenceStep(
                stepType = InferenceType.POSITIONAL_ANALYSIS,
                inputEvidence = "backward=$backwardEvidence, forward=$forwardEvidence",
                outputPattern = "positional_signature",
                confidence = 0.85f
            ))
            
            // Step 3: Pattern clustering
            val clusterSignature = createClusterSignature(backwardEvidence, forwardEvidence, anchor)
            steps.add(InferenceStep(
                stepType = InferenceType.PATTERN_CLUSTERING,
                inputEvidence = "positional_signature",
                outputPattern = clusterSignature,
                confidence = 0.90f
            ))
            
            // Step 4: Semantic deduction
            val semanticPattern = deduceSemanticPattern(clusterSignature)
            steps.add(InferenceStep(
                stepType = InferenceType.SEMANTIC_DEDUCTION,
                inputEvidence = clusterSignature,
                outputPattern = semanticPattern,
                confidence = 0.80f
            ))
            
            return steps
        }
        
        private fun createLatticeNode(
            reasoningPath: List<InferenceStep>,
            anchor: String
        ): ReasoningLatticeNode {
            // Create evidence chain
            val chainBuilder = EvidenceChainBuilder()
            val evidenceChain = chainBuilder.buildChain(
                initialSource = EvidenceSources.SOURCE_CODE,
                initialStrength = EvidenceStrengthLevels.STRONG,
                inferenceSteps = reasoningPath.map { it.stepType.name j it.confidence }.toIndexed()
            )
            
            // Create dense cluster
            val denseCluster = createDenseClusterFromPath(reasoningPath, anchor)
            
            // Calculate confidence from reasoning path
            val pathConfidence = reasoningPath.map { it.confidence }.average().toFloat()
            
            return ReasoningLatticeNode(
                evidenceChain = evidenceChain,
                denseCluster = denseCluster,
                confidenceScore = pathConfidence,
                reasoningPath = reasoningPath
            )
        }
        
        private fun bolsterConfidenceFromClustering() {
            // Strengthen confidence for frequently occurring patterns
            nodes.forEachIndexed { index, node ->
                val pattern = node.denseCluster.anchorType.toString()
                val frequency = patternCounts[pattern] ?: 1
                
                // Bolstering factor: more frequent patterns get higher confidence
                val bolsteringFactor = 1.0f + (frequency - 1) * 0.1f
                val bolsteredConfidence = (node.confidenceScore * bolsteringFactor).coerceAtMost(1.0f)
                
                nodes[index] = node.copy(
                    confidenceScore = bolsteredConfidence,
                    reasoningPath = node.reasoningPath + InferenceStep(
                        stepType = InferenceType.CONFIDENCE_BOLSTERING,
                        inputEvidence = "pattern_frequency=$frequency",
                        outputPattern = "bolstered_confidence=$bolsteredConfidence",
                        confidence = bolsteredConfidence
                    )
                )
            }
        }
        
        // Helper methods (simplified for test)
        private fun tokenize(code: String) = code.split(Regex("\\s+")).mapIndexed { i, token ->
            Token(
                type = when {
                    token in listOf("class", "fun", "val", "var", "interface", "object") -> TokenType.KEYWORD
                    token in listOf("private", "public", "protected", "internal") -> TokenType.VISIBILITY
                    token in listOf("suspend", "inline", "data", "sealed") -> TokenType.MODIFIER
                    token.startsWith("@") -> TokenType.ANNOTATION
                    token == ":" -> TokenType.COLON
                    token == "=" -> TokenType.EQUALS
                    token == "(" -> TokenType.OPEN_PAREN
                    else -> TokenType.IDENTIFIER
                },
                value = token
            )
        }
        
        private fun findKeywordAnchors(tokens: List<Token>) = tokens
            .mapIndexedNotNull { index, token ->
                if (token.type == TokenType.KEYWORD) token.value to index else null
            }
        
        private fun analyzeBackward(tokens: List<Token>, pos: Int) = 
            tokens.subList(maxOf(0, pos - 3), pos).joinToString(",") { it.value }
        
        private fun analyzeForward(tokens: List<Token>, pos: Int) =
            tokens.subList(pos + 1, minOf(tokens.size, pos + 4)).joinToString(",") { it.value }
        
        private fun createClusterSignature(backward: String, forward: String, anchor: String) =
            "cluster:$anchor[$backward→$forward]"
        
        private fun deduceSemanticPattern(signature: String) = when {
            "class" in signature && ":" in signature -> "inheritance_class"
            "fun" in signature && "suspend" in signature -> "suspend_function"  
            "val" in signature && ":" in signature -> "typed_property"
            else -> "unknown_pattern"
        }
        
        private fun createDenseClusterFromPath(path: List<InferenceStep>, anchor: String): DenseEvidenceCluster {
            val confidence = (path.map { it.confidence }.average() * 255).toInt().toUByte()
            val anchorType = when (anchor) {
                "class" -> AnchorTypes.CLASS
                "fun" -> AnchorTypes.FUNCTION
                "val", "var" -> AnchorTypes.PROPERTY
                else -> 0U
            }
            
            return DenseEvidenceCluster.create(
                backwardEvidence = 0x01U, // simplified
                forwardEvidence = 0x02U,  // simplified
                patternConfidence = confidence,
                anchorType = anchorType
            )
        }
    }
    
    /**
     * Complete reasoning lattice - the semantic map for LLM consumption
     */
    data class ReasoningLattice(
        val nodes: Indexed<ReasoningLatticeNode>
    ) {
        /**
         * Traverse lattice to find reasoning paths for a pattern
         */
        fun findReasoningPaths(pattern: String): List<ReasoningLatticeNode> {
            return nodes.filter { node ->
                node.reasoningPath.any { step ->
                    pattern in step.outputPattern
                }
            }
        }
        
        /**
         * Get high-confidence patterns for LLM rapid access
         */
        fun getHighConfidencePatterns(threshold: Float = 0.8f): List<ReasoningLatticeNode> {
            return nodes.filter { it.confidenceScore >= threshold }
        }
        
        /**
         * Calculate lattice connectivity - how patterns relate
         */
        fun calculateConnectivity(): Map<String, List<String>> {
            val connections = mutableMapOf<String, MutableList<String>>()
            
            nodes.forEach { node ->
                val patterns = node.reasoningPath.map { it.outputPattern }
                patterns.zipWithNext().forEach { (from, to) ->
                    connections.getOrPut(from) { mutableListOf() }.add(to)
                }
            }
            
            return connections.mapValues { it.value.toList() }
        }
    }
    
    @Test
    fun testCompleteReasoningPipeline() {
        val kotlinCode = """
            @Entity
            data class User : Person {
                val id: Long = 0
                suspend fun save(): Result<Unit>
            }
        """.trimIndent()
        
        val builder = ReasoningLatticeBuilder()
        val lattice = builder.processKotlinCode(kotlinCode)
        
        // Should detect multiple reasoning chains
        assertTrue(lattice.nodes.size >= 3) // class, property, function
        
        // Check that reasoning paths are complete
        lattice.nodes.forEach { node ->
            assertTrue(node.reasoningPath.isNotEmpty())
            assertTrue(node.reasoningPath.any { it.stepType == InferenceType.TOKEN_SCAN })
            assertTrue(node.reasoningPath.any { it.stepType == InferenceType.SEMANTIC_DEDUCTION })
        }
    }
    
    @Test
    fun testConfidenceBolsteringFromClustering() {
        val kotlinCode = """
            val prop1: String = "a"
            val prop2: Int = 1  
            val prop3: Boolean = true
        """.trimIndent()
        
        val builder = ReasoningLatticeBuilder()
        val lattice = builder.processKotlinCode(kotlinCode)
        
        // Multiple similar patterns should bolster confidence
        val propertyNodes = lattice.findReasoningPaths("typed_property")
        assertTrue(propertyNodes.isNotEmpty())
        
        // Check for confidence bolstering step
        propertyNodes.forEach { node ->
            assertTrue(node.reasoningPath.any { it.stepType == InferenceType.CONFIDENCE_BOLSTERING })
        }
    }
    
    @Test
    fun testLLMSemanticMapTraversal() {
        val kotlinCode = """
            suspend fun process(data: String): Result<String> {
                val result: String = transform(data)
                return Result.success(result)
            }
        """.trimIndent()
        
        val builder = ReasoningLatticeBuilder()
        val lattice = builder.processKotlinCode(kotlinCode)
        
        // LLM can rapidly identify suspend function pattern
        val suspendFunctions = lattice.findReasoningPaths("suspend_function")
        assertTrue(suspendFunctions.isNotEmpty())
        
        // LLM can find high-confidence patterns instantly
        val highConfidence = lattice.getHighConfidencePatterns(0.7f)
        assertTrue(highConfidence.isNotEmpty())
        
        // LLM can understand pattern relationships
        val connectivity = lattice.calculateConnectivity()
        assertTrue(connectivity.isNotEmpty())
    }
    
    @Test
    fun testEvidenceChainPreservation() {
        val kotlinCode = "private val name: String = \"test\""
        
        val builder = ReasoningLatticeBuilder()
        val lattice = builder.processKotlinCode(kotlinCode)
        
        val node = lattice.nodes.first()
        
        // Evidence chain should preserve complete reasoning
        assertTrue(node.evidenceChain.reasoning.depth.depth > 0U)
        assertTrue(node.evidenceChain.reasoning.progression.primary.strength.strength > 0U)
        
        // Dense cluster should pack the evidence efficiently
        assertTrue(node.denseCluster.patternConfidence > 0U)
        assertEquals(AnchorTypes.PROPERTY, node.denseCluster.anchorType)
        
        // Reasoning path should show complete inference
        assertTrue(node.reasoningPath.size >= 4) // scan → position → cluster → semantic
    }
    
    @Test
    fun testReasoningLatticeAsLLMSemanticMap() {
        val kotlinCode = """
            @Service
            class UserService : BaseService {
                private val repo: UserRepository
                
                suspend fun findUser(id: Long): User? {
                    return repo.findById(id)
                }
                
                suspend fun saveUser(user: User): Result<Unit> {
                    return repo.save(user)
                }
            }
        """.trimIndent()
        
        val builder = ReasoningLatticeBuilder()
        val lattice = builder.processKotlinCode(kotlinCode)
        
        // LLM can instantly understand this is a service layer class
        val servicePatterns = lattice.findReasoningPaths("inheritance_class")
        assertTrue(servicePatterns.isNotEmpty())
        
        // LLM can identify async repository pattern
        val suspendMethods = lattice.findReasoningPaths("suspend_function")
        assertTrue(suspendMethods.size >= 2) // findUser, saveUser
        
        // LLM can traverse from high-level to implementation details
        val highLevel = lattice.getHighConfidencePatterns(0.8f)
        val connectivity = lattice.calculateConnectivity()
        
        // This creates a navigable semantic map where LLM can:
        // 1. Instantly identify architectural patterns
        // 2. Understand reasoning behind classifications  
        // 3. Navigate from patterns to implementation
        // 4. Access complete evidence chains for verification
        
        assertTrue(highLevel.isNotEmpty())
        assertTrue(connectivity.isNotEmpty())
        
        // The lattice becomes "doxygen for LLMs" - rapid semantic navigation
    }
    
    private fun <T> List<T>.toIndexed(): Indexed<T> {
        return this.fold(createEmptyIndexed<T>()) { acc, item -> acc.add(item) }
    }
}