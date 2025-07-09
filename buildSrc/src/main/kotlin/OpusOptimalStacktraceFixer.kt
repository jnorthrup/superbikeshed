package buildtools

import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Opus-Optimal Stacktrace Fixer
 * 
 * Designed to present stacktraces in a format that maximizes LLM comprehension
 * by providing rich context, semantic grouping, and pattern recognition cues.
 */
object OpusOptimalStacktraceFixer {
    
    data class EnrichedStackFrame(
        val frame: StackTraceFrame,
        val sourceContext: SourceContext,
        val semanticContext: SemanticContext,
        val buildContext: BuildContext,
        val relatedFrames: List<StackTraceFrame>
    )
    
    data class SourceContext(
        val fileContent: String,
        val imports: List<String>,
        val classHierarchy: List<String>,
        val functionSignature: String,
        val surroundingFunctions: List<String>,
        val annotations: List<String>,
        val genericTypes: List<String>
    )
    
    data class SemanticContext(
        val domainConcepts: Set<String>,      // e.g., "fiduciary", "attention", "CRDT"
        val patterns: Set<String>,            // e.g., "visitor pattern", "coroutine builder"
        val dependencies: Set<String>,        // What this code depends on
        val dependents: Set<String>,          // What depends on this code
        val relatedTests: List<String>,       // Test files that exercise this code
        val documentation: String?            // KDoc/comments
    )
    
    data class BuildContext(
        val module: String,
        val sourceSet: String,
        val dependencies: List<String>,
        val compilerArgs: List<String>,
        val gradleTask: String,
        val platformTarget: String
    )
    
    fun processStackTraceForLLM(
        stackTrace: String, 
        sourceRoot: File,
        gradleLog: String? = null
    ): String {
        val frames = parseStackTrace(stackTrace)
        
        // Apply armor only to files that are BOTH dirty AND in stacktrace
        val stacktraceFiles = frames.mapNotNull { frame ->
            findSourceFile(sourceRoot, frame.className)
        }.toSet()
        
        val dirtyFiles = getDirtyKotlinFiles(sourceRoot).toSet()
        val filesToArmor = stacktraceFiles.intersect(dirtyFiles)
        
        filesToArmor.forEach { file ->
            ProjectArmorStacktraceFixer.applyArmorToFile(file)
            println("Applied armor to dirty stacktrace file: ${file.path}")
        }
        
        val enrichedFrames = frames.map { frame ->
            enrichFrame(frame, sourceRoot, gradleLog)
        }
        
        return formatForOptimalComprehension(enrichedFrames, stackTrace, gradleLog)
    }
    
    private fun getDirtyKotlinFiles(rootDir: File): List<File> {
        val processBuilder = ProcessBuilder("git", "status", "--porcelain", "*.kt")
        processBuilder.directory(rootDir)
        
        val process = processBuilder.start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        
        return output.lines()
            .filter { it.isNotBlank() }
            .filter { line ->
                // Only include modified files (M), not new files (A) or other states
                // This focuses on files dirty from bugfix work, not new feature additions
                line.startsWith(" M") || line.startsWith("M ")
            }
            .mapNotNull { line ->
                // Git status format: XY filename
                val filename = line.substring(3).trim()
                val file = File(rootDir, filename)
                if (file.exists() && file.name.endsWith(".kt")) file else null
            }
    }
    
    private fun enrichFrame(
        frame: StackTraceFrame,
        sourceRoot: File,
        gradleLog: String?
    ): EnrichedStackFrame {
        val sourceFile = findSourceFile(sourceRoot, frame.className)
        
        val sourceContext = if (sourceFile != null) {
            extractSourceContext(sourceFile, frame)
        } else {
            SourceContext("", emptyList(), emptyList(), "", emptyList(), emptyList(), emptyList())
        }
        
        val semanticContext = extractSemanticContext(sourceFile, frame, sourceRoot)
        val buildContext = extractBuildContext(frame, gradleLog)
        val relatedFrames = findRelatedFrames(frame, parseStackTrace(stackTrace))
        
        return EnrichedStackFrame(frame, sourceContext, semanticContext, buildContext, relatedFrames)
    }
    
    private fun extractSourceContext(file: File, frame: StackTraceFrame): SourceContext {
        val lines = file.readLines()
        val lineNum = frame.lineNumber - 1
        
        // Extract imports
        val imports = lines.filter { it.trim().startsWith("import ") }
        
        // Find class hierarchy
        val classHierarchy = findClassHierarchy(lines, frame.className)
        
        // Extract function signature with full context
        val functionSignature = findFunctionSignature(lines, lineNum)
        
        // Get surrounding functions for context
        val surroundingFunctions = findSurroundingFunctions(lines, lineNum)
        
        // Extract annotations
        val annotations = findAnnotations(lines, lineNum)
        
        // Extract generic type parameters
        val genericTypes = findGenericTypes(lines, lineNum)
        
        // Get expanded context (50 lines each way for LLM comprehension)
        val contextStart = max(0, lineNum - 50)
        val contextEnd = min(lines.size - 1, lineNum + 50)
        val numberedContext = (contextStart..contextEnd).map { i ->
            val marker = when (i) {
                lineNum - 1 -> " ❌❌❌ ERROR HERE ❌❌❌ "
                lineNum -> " ^^^ NEXT LINE ^^^ "
                else -> "     "
            }
            "${(i + 1).toString().padStart(4)}$marker${lines[i]}"
        }.joinToString("\n")
        
        return SourceContext(
            fileContent = numberedContext,
            imports = imports,
            classHierarchy = classHierarchy,
            functionSignature = functionSignature,
            surroundingFunctions = surroundingFunctions,
            annotations = annotations,
            genericTypes = genericTypes
        )
    }
    
    private fun extractSemanticContext(
        file: File?,
        frame: StackTraceFrame,
        sourceRoot: File
    ): SemanticContext {
        if (file == null) return SemanticContext(emptySet(), emptySet(), emptySet(), emptySet(), emptyList(), null)
        
        val content = file.readText()
        
        // Domain concepts detection
        val domainConcepts = detectDomainConcepts(content, frame.className)
        
        // Pattern detection
        val patterns = detectPatterns(content)
        
        // Dependency analysis
        val dependencies = findDependencies(file, sourceRoot)
        val dependents = findDependents(file, sourceRoot)
        
        // Find related tests
        val relatedTests = findRelatedTests(file, sourceRoot)
        
        // Extract documentation
        val documentation = extractDocumentation(file, frame.lineNumber)
        
        return SemanticContext(domainConcepts, patterns, dependencies, dependents, relatedTests, documentation)
    }
    
    private fun detectDomainConcepts(content: String, className: String): Set<String> {
        val concepts = mutableSetOf<String>()
        
        // TrikeShed specific concepts
        val domainPatterns = mapOf(
            "fiduciary" to "Fiduciary System",
            "attention" to "Attention Mechanism",
            "blackboard" to "Blackboard Architecture",
            "crdt" to "CRDT",
            "wave" to "Apache Wave Protocol",
            "memvid" to "Memory Video System",
            "kademlia" to "Kademlia DHT",
            "xcaml" to "XCAML Security",
            "jetsam" to "Jetsam Gossip",
            "patrick.*devine" to "Patrick Devine Corpus",
            "indexed" to "Indexed Collection",
            "join" to "Join Type",
            "twin" to "Twin Type",
            "cursor" to "Cursor Pattern",
            "reactor" to "Reactor Pattern",
            "channel" to "Channel API"
        )
        
        domainPatterns.forEach { (pattern, concept) ->
            if (content.contains(Regex(pattern, RegexOption.IGNORE_CASE))) {
                concepts.add(concept)
            }
        }
        
        return concepts
    }
    
    private fun detectPatterns(content: String): Set<String> {
        val patterns = mutableSetOf<String>()
        
        // Common patterns
        if (content.contains("accept(") && content.contains("Visitor")) patterns.add("Visitor Pattern")
        if (content.contains("suspend fun")) patterns.add("Coroutine")
        if (content.contains("Flow<")) patterns.add("Reactive Streams")
        if (content.contains("Channel<")) patterns.add("Channel Communication")
        if (content.contains("sealed class")) patterns.add("Sealed Hierarchy")
        if (content.contains("inline fun")) patterns.add("Inline Function")
        if (content.contains("reified")) patterns.add("Reified Generics")
        if (content.contains("by lazy")) patterns.add("Lazy Initialization")
        
        return patterns
    }
    
    private fun formatForOptimalComprehension(
        enrichedFrames: List<EnrichedStackFrame>,
        originalStackTrace: String,
        gradleLog: String?
    ): String {
        val output = StringBuilder()
        
        // Executive Summary for LLM
        output.appendLine("🔍 STACKTRACE ANALYSIS - OPUS-OPTIMIZED FORMAT")
        output.appendLine("=" * 80)
        output.appendLine()
        
        // Quick diagnosis section
        output.appendLine("## 🎯 QUICK DIAGNOSIS")
        output.appendLine("Primary Error: ${enrichedFrames.firstOrNull()?.frame?.className}.${enrichedFrames.firstOrNull()?.frame?.methodName}")
        output.appendLine("Error Location: ${enrichedFrames.firstOrNull()?.frame?.fileName}:${enrichedFrames.firstOrNull()?.frame?.lineNumber}")
        
        // Domain context
        val allConcepts = enrichedFrames.flatMap { it.semanticContext.domainConcepts }.toSet()
        if (allConcepts.isNotEmpty()) {
            output.appendLine("Domain Concepts: ${allConcepts.joinToString(", ")}")
        }
        
        // Pattern context
        val allPatterns = enrichedFrames.flatMap { it.semanticContext.patterns }.toSet()
        if (allPatterns.isNotEmpty()) {
            output.appendLine("Patterns Involved: ${allPatterns.joinToString(", ")}")
        }
        
        output.appendLine()
        output.appendLine("## 📊 GRADLE BUILD CONTEXT")
        gradleLog?.let { log ->
            val relevantSection = extractRelevantGradleSection(log, enrichedFrames.first())
            output.appendLine("```")
            output.appendLine(relevantSection)
            output.appendLine("```")
        }
        
        output.appendLine()
        output.appendLine("## 🔗 CALL CHAIN VISUALIZATION")
        output.appendLine(visualizeCallChain(enrichedFrames))
        
        output.appendLine()
        output.appendLine("## 📝 DETAILED FRAME ANALYSIS")
        
        enrichedFrames.forEachIndexed { index, frame ->
            output.appendLine()
            output.appendLine("### Frame ${index + 1}: ${frame.frame.className}.${frame.frame.methodName}")
            output.appendLine("File: ${frame.frame.fileName}:${frame.frame.lineNumber}")
            
            // Semantic context
            if (frame.semanticContext.documentation != null) {
                output.appendLine("Documentation: ${frame.semanticContext.documentation}")
            }
            
            // Build context
            output.appendLine("Module: ${frame.buildContext.module}")
            output.appendLine("Platform: ${frame.buildContext.platformTarget}")
            
            // Source context with rich annotations
            output.appendLine()
            output.appendLine("#### SOURCE CONTEXT")
            output.appendLine("```kotlin")
            
            // Add imports for context
            if (frame.sourceContext.imports.isNotEmpty()) {
                output.appendLine("// Key imports:")
                frame.sourceContext.imports.take(5).forEach { 
                    output.appendLine(it)
                }
                output.appendLine("// ... ${frame.sourceContext.imports.size} total imports")
                output.appendLine()
            }
            
            // Add class hierarchy
            if (frame.sourceContext.classHierarchy.isNotEmpty()) {
                output.appendLine("// Class hierarchy: ${frame.sourceContext.classHierarchy.joinToString(" > ")}")
                output.appendLine()
            }
            
            // Add the actual source
            output.appendLine(frame.sourceContext.fileContent)
            output.appendLine("```")
            
            // Dependencies for understanding
            if (frame.semanticContext.dependencies.isNotEmpty()) {
                output.appendLine()
                output.appendLine("#### DEPENDENCIES")
                frame.semanticContext.dependencies.forEach {
                    output.appendLine("- $it")
                }
            }
            
            // Related tests for context
            if (frame.semanticContext.relatedTests.isNotEmpty()) {
                output.appendLine()
                output.appendLine("#### RELATED TESTS")
                frame.semanticContext.relatedTests.forEach {
                    output.appendLine("- $it")
                }
            }
        }
        
        // Original stacktrace for reference
        output.appendLine()
        output.appendLine("## 📋 ORIGINAL STACKTRACE")
        output.appendLine("```")
        output.appendLine(originalStackTrace)
        output.appendLine("```")
        
        // Suggested fixes based on patterns
        output.appendLine()
        output.appendLine("## 💡 PATTERN-BASED SUGGESTIONS")
        generatePatternBasedSuggestions(enrichedFrames).forEach {
            output.appendLine("- $it")
        }
        
        return output.toString()
    }
    
    private fun visualizeCallChain(frames: List<EnrichedStackFrame>): String {
        val chain = StringBuilder()
        frames.forEachIndexed { index, frame ->
            val indent = "  " * index
            val concepts = if (frame.semanticContext.domainConcepts.isNotEmpty()) {
                " [${frame.semanticContext.domainConcepts.first()}]"
            } else ""
            chain.appendLine("$indent└─> ${frame.frame.className}.${frame.frame.methodName}$concepts")
        }
        return chain.toString()
    }
    
    private fun generatePatternBasedSuggestions(frames: List<EnrichedStackFrame>): List<String> {
        val suggestions = mutableListOf<String>()
        
        // Analyze patterns for common issues
        frames.forEach { frame ->
            if ("Coroutine" in frame.semanticContext.patterns) {
                suggestions.add("Check coroutine context and cancellation handling")
            }
            if ("Channel Communication" in frame.semanticContext.patterns) {
                suggestions.add("Verify channel is not closed and has proper exception handling")
            }
            if (frame.frame.className.contains("Native")) {
                suggestions.add("Ensure native interop declarations match platform expectations")
            }
        }
        
        return suggestions.distinct()
    }
    
    // Helper methods
    private fun parseStackTrace(stackTrace: String): List<StackTraceFrame> {
        val pattern = Regex("""at\s+([a-zA-Z0-9.$]+)\.([a-zA-Z0-9$<>]+)\(([^:)]+):(\d+)\)""")
        return stackTrace.lines().mapNotNull { line ->
            pattern.find(line)?.let { match ->
                StackTraceFrame(
                    className = match.groupValues[1],
                    methodName = match.groupValues[2],
                    fileName = match.groupValues[3],
                    lineNumber = match.groupValues[4].toInt(),
                    rawLine = line
                )
            }
        }
    }
    
    private fun findSourceFile(sourceRoot: File, className: String): File? {
        val path = className.replace('.', '/').substringBefore('$') + ".kt"
        val sourceSets = listOf("commonMain", "jvmMain", "nativeMain", "jsMain", "commonTest", "jvmTest")
        
        for (sourceSet in sourceSets) {
            val file = File(sourceRoot, "src/$sourceSet/kotlin/$path")
            if (file.exists()) return file
        }
        
        // Check module subdirectories
        sourceRoot.listFiles()?.filter { it.isDirectory }?.forEach { module ->
            for (sourceSet in sourceSets) {
                val file = File(module, "src/$sourceSet/kotlin/$path")
                if (file.exists()) return file
            }
        }
        
        return null
    }
    
    // Additional helper methods would go here...
    
    private fun findClassHierarchy(lines: List<String>, className: String): List<String> {
        // Simplified - would need full parsing for accuracy
        val hierarchy = mutableListOf<String>()
        val classLine = lines.find { it.contains("class $className") || it.contains("object $className") }
        classLine?.let {
            if (it.contains(":")) {
                val inheritance = it.substringAfter(":").substringBefore("{").trim()
                hierarchy.add(inheritance)
            }
        }
        return hierarchy
    }
    
    private fun findFunctionSignature(lines: List<String>, lineNum: Int): String {
        // Walk backwards to find function signature
        for (i in lineNum downTo 0) {
            val line = lines[i]
            if (line.contains("fun ")) {
                // Continue reading until we find the opening brace
                val signature = StringBuilder(line)
                var j = i + 1
                while (j < lines.size && !signature.contains("{")) {
                    signature.append(" ").append(lines[j].trim())
                    j++
                }
                return signature.toString().substringBefore("{").trim()
            }
        }
        return ""
    }
    
    private fun findSurroundingFunctions(lines: List<String>, lineNum: Int): List<String> {
        val functions = mutableListOf<String>()
        val range = max(0, lineNum - 30)..min(lines.size - 1, lineNum + 30)
        
        for (i in range) {
            val line = lines[i]
            if (line.trim().startsWith("fun ") || line.contains(" fun ")) {
                functions.add(line.trim())
            }
        }
        
        return functions
    }
    
    private fun findAnnotations(lines: List<String>, lineNum: Int): List<String> {
        val annotations = mutableListOf<String>()
        
        // Look backwards for annotations
        var i = lineNum - 1
        while (i >= 0 && (lines[i].trim().startsWith("@") || lines[i].trim().isEmpty())) {
            if (lines[i].trim().startsWith("@")) {
                annotations.add(lines[i].trim())
            }
            i--
        }
        
        return annotations.reversed()
    }
    
    private fun findGenericTypes(lines: List<String>, lineNum: Int): List<String> {
        val line = if (lineNum < lines.size) lines[lineNum] else return emptyList()
        val genericPattern = Regex("""<([^>]+)>""")
        return genericPattern.findAll(line).map { it.groupValues[1] }.toList()
    }
    
    private fun findDependencies(file: File, sourceRoot: File): Set<String> {
        // This would analyze imports and references
        return emptySet() // Simplified
    }
    
    private fun findDependents(file: File, sourceRoot: File): Set<String> {
        // This would search for files that import this one
        return emptySet() // Simplified
    }
    
    private fun findRelatedTests(file: File, sourceRoot: File): List<String> {
        val testFile = file.path.replace("/main/", "/test/").replace(".kt", "Test.kt")
        return if (File(testFile).exists()) listOf(testFile) else emptyList()
    }
    
    private fun extractDocumentation(file: File, lineNumber: Int): String? {
        val lines = file.readLines()
        val docBuilder = StringBuilder()
        
        // Look backwards for KDoc
        var i = lineNumber - 2
        while (i >= 0 && lines[i].trim().let { it.startsWith("*") || it.startsWith("/**") || it.startsWith("*/") }) {
            docBuilder.insert(0, lines[i] + "\n")
            i--
        }
        
        return if (docBuilder.isNotEmpty()) docBuilder.toString().trim() else null
    }
    
    private fun extractRelevantGradleSection(gradleLog: String, frame: EnrichedStackFrame): String {
        // Extract the most relevant section of gradle log
        val lines = gradleLog.lines()
        val modulePattern = ":${frame.buildContext.module}:"
        
        val relevantLines = lines.filter { it.contains(modulePattern) || it.contains("ERROR") || it.contains("FAILED") }
            .takeLast(20)
        
        return relevantLines.joinToString("\n")
    }
}

// Extension function for string multiplication
operator fun String.times(count: Int): String = repeat(count)