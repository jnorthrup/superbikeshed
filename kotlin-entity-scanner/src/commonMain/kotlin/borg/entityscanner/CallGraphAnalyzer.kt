@file:Suppress("NOTHING_TO_INLINE")

package borg.entityscanner

import borg.trikeshed.lib.*
import borg.trikeshed.graph.*

/**
 * Call Graph Analyzer - N-Depth Caller/Callee Relationship Parser
 * 
 * Generates comprehensive graphs for visualization showing:
 * - Direct callers and callees
 * - N-depth transitive relationships
 * - Bidirectional call chains
 * - Cycle detection
 * - Critical path analysis
 */

// ==== CALL GRAPH TYPES ====

@JvmInline
value class FunctionId(val id: String)

@JvmInline
value class CallDepth(val depth: UByte)

@JvmInline
value class CallWeight(val weight: UInt) // Call frequency/importance

@JvmInline
value class CycleId(val id: UInt)

// Core call relationship
typealias CallRelation = Join<FunctionId, FunctionId> // caller j callee
typealias WeightedCall = Join<CallRelation, CallWeight>
typealias DepthCall = Join<WeightedCall, CallDepth>

// Call graph structures
typealias CallGraph = Indexed<DepthCall>
typealias CallerMap = Indexed<Join<FunctionId, Indexed<FunctionId>>> // function j its callers
typealias CalleeMap = Indexed<Join<FunctionId, Indexed<FunctionId>>> // function j its callees

// Cycle detection
typealias CallCycle = Indexed<FunctionId> // sequence of functions in cycle
typealias CycleCollection = Indexed<Join<CycleId, CallCycle>>

// Path analysis
typealias CallPath = Indexed<FunctionId> // ordered sequence of calls
typealias PathCollection = Indexed<Join<Join<FunctionId, FunctionId>, CallPath>> // (start j end) j path

/**
 * Call Graph Analyzer - Main analysis engine
 */
object CallGraphAnalyzer {
    
    /**
     * Parse source code and build comprehensive call graph
     */
    fun analyzeCallGraph(source: KotlinSourceCode, maxDepth: UByte = 10u): CallGraphAnalysisResult {
        val functions = extractFunctions(source)
        val directCalls = extractDirectCalls(source, functions)
        val callerMap = buildCallerMap(directCalls)
        val calleeMap = buildCalleeMap(directCalls)
        val nDepthGraph = buildNDepthGraph(directCalls, maxDepth)
        val cycles = detectCycles(nDepthGraph)
        val criticalPaths = findCriticalPaths(nDepthGraph)
        
        return CallGraphAnalysisResult(
            functions = functions,
            directCalls = directCalls,
            callerMap = callerMap,
            calleeMap = calleeMap,
            nDepthGraph = nDepthGraph,
            cycles = cycles,
            criticalPaths = criticalPaths,
            maxDepth = maxDepth
        )
    }
    
    /**
     * Extract all function definitions from source
     */
    fun extractFunctions(source: KotlinSourceCode): Indexed<FunctionDefinition> {
        val lines = source.lines()
        val functions = mutableListOf<FunctionDefinition>()
        
        lines.forEachIndexed { lineIndex, line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("fun ") -> {
                    val functionName = extractFunctionName(trimmed)
                    if (functionName != null) {
                        val visibility = extractVisibility(trimmed)
                        val parameters = extractParameters(trimmed)
                        val returnType = extractReturnType(trimmed)
                        
                        functions.add(FunctionDefinition(
                            id = FunctionId(functionName),
                            name = functionName,
                            visibility = visibility,
                            parameters = parameters,
                            returnType = returnType,
                            lineNumber = lineIndex + 1,
                            isInline = trimmed.contains("inline"),
                            isSuspend = trimmed.contains("suspend"),
                            isOperator = trimmed.contains("operator"),
                            isInfix = trimmed.contains("infix")
                        ))
                    }
                }
                trimmed.startsWith("suspend fun ") -> {
                    val functionName = extractFunctionName(trimmed.removePrefix("suspend "))
                    if (functionName != null) {
                        functions.add(FunctionDefinition(
                            id = FunctionId(functionName),
                            name = functionName,
                            visibility = extractVisibility(trimmed),
                            parameters = extractParameters(trimmed),
                            returnType = extractReturnType(trimmed),
                            lineNumber = lineIndex + 1,
                            isInline = false,
                            isSuspend = true,
                            isOperator = false,
                            isInfix = false
                        ))
                    }
                }
            }
        }
        
        return functions.toIndexed()
    }
    
    /**
     * Extract direct function calls from source
     */
    fun extractDirectCalls(source: KotlinSourceCode, functions: Indexed<FunctionDefinition>): CallGraph {
        val calls = mutableListOf<DepthCall>()
        val lines = source.lines()
        val functionNames = functions.play.map { it.name }.toSet()
        
        var currentFunction: String? = null
        
        lines.forEach { line ->
            val trimmed = line.trim()
            
            // Track current function context
            if (trimmed.startsWith("fun ") || trimmed.startsWith("suspend fun ")) {
                currentFunction = extractFunctionName(trimmed.removePrefix("suspend "))
            }
            
            // Find function calls in line
            if (currentFunction != null) {
                functionNames.forEach { functionName ->
                    if (trimmed.contains("$functionName(") && functionName != currentFunction) {
                        val callCount = countOccurrences(trimmed, "$functionName(")
                        val relation = FunctionId(currentFunction!!) j FunctionId(functionName)
                        val weighted = relation j CallWeight(callCount.toUInt())
                        val depthCall = weighted j CallDepth(1u) // Direct call = depth 1
                        calls.add(depthCall)
                    }
                }
            }
        }
        
        return calls.toIndexed()
    }
    
    /**
     * Build caller mapping (who calls this function)
     */
    fun buildCallerMap(directCalls: CallGraph): CallerMap {
        val callerMap = mutableMapOf<String, MutableList<String>>()
        
        directCalls.play.forEach { depthCall ->
            val weighted = depthCall.a
            val relation = weighted.a
            val caller = relation.a.id
            val callee = relation.b.id
            
            callerMap.getOrPut(callee) { mutableListOf() }.add(caller)
        }
        
        return callerMap.map { (function, callers) ->
            FunctionId(function) j callers.distinct().toSeries { FunctionId(it) }
        }.toIndexed()
    }
    
    /**
     * Build callee mapping (what this function calls)
     */
    fun buildCalleeMap(directCalls: CallGraph): CalleeMap {
        val calleeMap = mutableMapOf<String, MutableList<String>>()
        
        directCalls.play.forEach { depthCall ->
            val weighted = depthCall.a
            val relation = weighted.a
            val caller = relation.a.id
            val callee = relation.b.id
            
            calleeMap.getOrPut(caller) { mutableListOf() }.add(callee)
        }
        
        return calleeMap.map { (function, callees) ->
            FunctionId(function) j callees.distinct().toSeries { FunctionId(it) }
        }.toIndexed()
    }
    
    /**
     * Build N-depth transitive call graph
     */
    fun buildNDepthGraph(directCalls: CallGraph, maxDepth: UByte): CallGraph {
        val allCalls = mutableListOf<DepthCall>()
        val directCallMap = mutableMapOf<String, MutableList<String>>()
        
        // Build direct call map
        directCalls.play.forEach { depthCall ->
            val relation = depthCall.a.a
            val caller = relation.a.id
            val callee = relation.b.id
            directCallMap.getOrPut(caller) { mutableListOf() }.add(callee)
            allCalls.add(depthCall) // Include direct calls
        }
        
        // Build transitive calls up to maxDepth
        for (depth in 2u..maxDepth) {
            val newCalls = mutableListOf<DepthCall>()
            
            allCalls.filter { it.b.depth == (depth - 1u).toUByte() }.forEach { depthCall ->
                val intermediateCaller = depthCall.a.a.a.id
                val intermediateCallee = depthCall.a.a.b.id
                
                // Find what intermediateCallee calls
                directCallMap[intermediateCallee]?.forEach { finalCallee ->
                    if (finalCallee != intermediateCaller) { // Avoid immediate cycles
                        val relation = FunctionId(intermediateCaller) j FunctionId(finalCallee)
                        val weighted = relation j CallWeight(1u) // Transitive calls have weight 1
                        val transitive = weighted j CallDepth(depth.toUByte())
                        newCalls.add(transitive)
                    }
                }
            }
            
            allCalls.addAll(newCalls)
        }
        
        return allCalls.distinct().toIndexed()
    }
    
    /**
     * Detect cycles in call graph
     */
    fun detectCycles(callGraph: CallGraph): CycleCollection {
        val cycles = mutableListOf<Join<CycleId, CallCycle>>()
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val graph = buildAdjacencyMap(callGraph)
        
        fun dfs(node: String, path: MutableList<String>): Boolean {
            if (recursionStack.contains(node)) {
                // Found cycle - extract it
                val cycleStart = path.indexOf(node)
                if (cycleStart >= 0) {
                    val cycle = path.subList(cycleStart, path.size).toSeries { FunctionId(it) }
                    val cycleId = CycleId(cycle.play.joinToString("->").hashCode().toUInt())
                    cycles.add(cycleId j cycle)
                }
                return true
            }
            
            if (visited.contains(node)) return false
            
            visited.add(node)
            recursionStack.add(node)
            path.add(node)
            
            graph[node]?.forEach { neighbor ->
                if (dfs(neighbor, path)) return true
            }
            
            recursionStack.remove(node)
            path.removeAt(path.size - 1)
            return false
        }
        
        graph.keys.forEach { node ->
            if (!visited.contains(node)) {
                dfs(node, mutableListOf())
            }
        }
        
        return cycles.toIndexed()
    }
    
    /**
     * Find critical paths (longest paths between functions)
     */
    fun findCriticalPaths(callGraph: CallGraph): PathCollection {
        val paths = mutableListOf<Join<Join<FunctionId, FunctionId>, CallPath>>()
        val graph = buildAdjacencyMap(callGraph)
        
        graph.keys.forEach { start ->
            graph.keys.forEach { end ->
                if (start != end) {
                    val path = findLongestPath(graph, start, end)
                    if (path.isNotEmpty()) {
                        val pathSeries = path.toSeries { FunctionId(it) }
                        val endpoints = FunctionId(start) j FunctionId(end)
                        paths.add(endpoints j pathSeries)
                    }
                }
            }
        }
        
        return paths.toIndexed()
    }
    
    // === HELPER FUNCTIONS ===
    
    private fun extractFunctionName(line: String): String? {
        val regex = """fun\s+(\w+)\s*\(""".toRegex()
        return regex.find(line)?.groupValues?.get(1)
    }
    
    private fun extractVisibility(line: String): String = when {
        line.contains("private") -> "private"
        line.contains("protected") -> "protected"
        line.contains("internal") -> "internal"
        else -> "public"
    }
    
    private fun extractParameters(line: String): String {
        val start = line.indexOf('(')
        val end = line.indexOf(')')
        return if (start >= 0 && end > start) {
            line.substring(start + 1, end)
        } else ""
    }
    
    private fun extractReturnType(line: String): String {
        val colonIndex = line.lastIndexOf(": ")
        val braceIndex = line.indexOf(" {")
        val equalIndex = line.indexOf(" =")
        
        return when {
            colonIndex >= 0 && braceIndex > colonIndex -> 
                line.substring(colonIndex + 2, braceIndex).trim()
            colonIndex >= 0 && equalIndex > colonIndex -> 
                line.substring(colonIndex + 2, equalIndex).trim()
            colonIndex >= 0 -> 
                line.substring(colonIndex + 2).trim()
            else -> "Unit"
        }
    }
    
    private fun countOccurrences(text: String, pattern: String): Int {
        return text.split(pattern).size - 1
    }
    
    private fun buildAdjacencyMap(callGraph: CallGraph): Map<String, List<String>> {
        val map = mutableMapOf<String, MutableList<String>>()
        
        callGraph.play.forEach { depthCall ->
            val relation = depthCall.a.a
            val caller = relation.a.id
            val callee = relation.b.id
            map.getOrPut(caller) { mutableListOf() }.add(callee)
        }
        
        return map.mapValues { it.value.distinct() }
    }
    
    private fun findLongestPath(
        graph: Map<String, List<String>>, 
        start: String, 
        end: String
    ): List<String> {
        val visited = mutableSetOf<String>()
        var longestPath = emptyList<String>()
        
        fun dfs(node: String, path: List<String>) {
            if (node == end) {
                if (path.size > longestPath.size) {
                    longestPath = path
                }
                return
            }
            
            if (visited.contains(node)) return
            visited.add(node)
            
            graph[node]?.forEach { neighbor ->
                dfs(neighbor, path + neighbor)
            }
            
            visited.remove(node)
        }
        
        dfs(start, listOf(start))
        return longestPath
    }
}

// ==== DATA STRUCTURES ====

/**
 * Function definition with metadata
 */
data class FunctionDefinition(
    val id: FunctionId,
    val name: String,
    val visibility: String,
    val parameters: String,
    val returnType: String,
    val lineNumber: Int,
    val isInline: Boolean,
    val isSuspend: Boolean,
    val isOperator: Boolean,
    val isInfix: Boolean
)

/**
 * Complete call graph analysis result
 */
data class CallGraphAnalysisResult(
    val functions: Indexed<FunctionDefinition>,
    val directCalls: CallGraph,
    val callerMap: CallerMap,
    val calleeMap: CalleeMap,
    val nDepthGraph: CallGraph,
    val cycles: CycleCollection,
    val criticalPaths: PathCollection,
    val maxDepth: UByte
)

// ==== EXTENSION FUNCTIONS ====

/**
 * Get all callers of a function up to N depth
 */
fun CallGraphAnalysisResult.getCallersOfDepth(functionId: FunctionId, depth: UByte): Indexed<FunctionId> {
    return nDepthGraph.play
        .filter { it.a.a.b == functionId && it.b <= depth }
        .map { it.a.a.a }
        .distinct()
        .toIndexed()
}

/**
 * Get all callees of a function up to N depth
 */
fun CallGraphAnalysisResult.getCalleesOfDepth(functionId: FunctionId, depth: UByte): Indexed<FunctionId> {
    return nDepthGraph.play
        .filter { it.a.a.a == functionId && it.b <= depth }
        .map { it.a.a.b }
        .distinct()
        .toIndexed()
}

/**
 * Get functions involved in cycles
 */
fun CallGraphAnalysisResult.getCyclicFunctions(): Indexed<FunctionId> {
    return cycles.play
        .flatMap { it.b.play }
        .distinct()
        .toIndexed()
}

/**
 * Get call depth between two functions
 */
fun CallGraphAnalysisResult.getCallDepth(from: FunctionId, to: FunctionId): CallDepth? {
    return nDepthGraph.play
        .firstOrNull { it.a.a.a == from && it.a.a.b == to }
        ?.b
}

// ==== VISUALIZATION HELPERS ====

/**
 * Export call graph to DOT format for visualization
 */
fun CallGraphAnalysisResult.toDotFormat(): String = buildString {
    appendLine("digraph CallGraph {")
    appendLine("  rankdir=TB;")
    appendLine("  node [shape=box];")
    
    // Add nodes (functions)
    functions.play.forEach { func ->
        val color = when {
            getCyclicFunctions().play.contains(func.id) -> "red"
            func.isSuspend -> "blue"
            func.isInline -> "green"
            else -> "black"
        }
        appendLine("  \"${func.name}\" [color=$color];")
    }
    
    // Add edges (calls)
    directCalls.play.forEach { call ->
        val relation = call.a.a
        val weight = call.a.b.weight
        appendLine("  \"${relation.a.id}\" -> \"${relation.b.id}\" [label=\"$weight\"];")
    }
    
    appendLine("}")
}

/**
 * Export to JSON for web visualization
 */
fun CallGraphAnalysisResult.toJsonFormat(): String = buildString {
    appendLine("{")
    appendLine("  \"nodes\": [")
    functions.play.forEachIndexed { index, func ->
        val comma = if (index < functions.size - 1) "," else ""
        appendLine("    {\"id\": \"${func.name}\", \"type\": \"${if (func.isSuspend) "suspend" else "regular"}\"}$comma")
    }
    appendLine("  ],")
    appendLine("  \"edges\": [")
    directCalls.play.forEachIndexed { index, call ->
        val relation = call.a.a
        val weight = call.a.b.weight
        val comma = if (index < directCalls.size - 1) "," else ""
        appendLine("    {\"from\": \"${relation.a.id}\", \"to\": \"${relation.b.id}\", \"weight\": $weight}$comma")
    }
    appendLine("  ]")
    appendLine("}")
}

// ==== CONVENIENCE EXTENSIONS ====

fun KotlinSourceCode.analyzeCallGraph(maxDepth: UByte = 10u): CallGraphAnalysisResult = 
    CallGraphAnalyzer.analyzeCallGraph(this, maxDepth)

fun KotlinSourceCode.extractCallGraph(): CallGraph = 
    CallGraphAnalyzer.analyzeCallGraph(this).nDepthGraph

fun KotlinSourceCode.findCycles(): CycleCollection = 
    CallGraphAnalyzer.analyzeCallGraph(this).cycles

fun KotlinSourceCode.getCriticalPaths(): PathCollection = 
    CallGraphAnalyzer.analyzeCallGraph(this).criticalPaths