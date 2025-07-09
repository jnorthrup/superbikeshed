package buildtools

import java.io.File
import kotlin.text.Regex

/**
 * Project Armor: Stacktrace fixer that agglomerates suppressions and opt-ins
 * to quiet distractions and fix type annotations in stacktraces.
 */
object ProjectArmorStacktraceFixer {
    
    data class SourceFileAnnotations(
        val suppressions: Set<String> = emptySet(),
        val optIns: Set<String> = emptySet(),
        val packageName: String? = null
    )
    
    data class StackTraceFrame(
        val className: String,
        val methodName: String,
        val fileName: String?,
        val lineNumber: Int,
        val rawLine: String
    )
    
    // Common suppressions to apply globally
    private val commonSuppressions = setOf(
        "UNCHECKED_CAST",
        "DEPRECATION",
        "EXPERIMENTAL_API_USAGE",
        "EXPERIMENTAL_UNSIGNED_LITERALS",
        "OPT_IN_USAGE"
    )
    
    // Common opt-ins for experimental APIs
    private val commonOptIns = setOf(
        "kotlin.ExperimentalStdlibApi",
        "kotlin.ExperimentalUnsignedTypes",
        "kotlinx.coroutines.ExperimentalCoroutinesApi",
        "kotlin.time.ExperimentalTime",
        "kotlin.contracts.ExperimentalContracts"
    )
    
    // Patterns to detect and fix in stacktraces
    private val stackTracePattern = Regex("""at\s+([a-zA-Z0-9.$]+)\.([a-zA-Z0-9$<>]+)\(([^:)]+):(\d+)\)""")
    private val lambdaPattern = Regex("""(\$lambda-\d+|\$\d+)""")
    private val infixLambdaPattern = Regex("""infix fun.*\{.*\}""")
    
    fun scanSourceFile(file: File): SourceFileAnnotations {
        if (!file.exists() || !file.name.endsWith(".kt")) {
            return SourceFileAnnotations()
        }
        
        val content = file.readText()
        val suppressions = mutableSetOf<String>()
        val optIns = mutableSetOf<String>()
        var packageName: String? = null
        
        // Extract package
        Regex("""package\s+([a-zA-Z0-9.]+)""").find(content)?.let {
            packageName = it.groupValues[1]
        }
        
        // Extract @Suppress annotations
        Regex("""@Suppress\s*\(\s*([^)]+)\s*\)""").findAll(content).forEach { match ->
            val suppressList = match.groupValues[1]
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
            suppressions.addAll(suppressList)
        }
        
        // Extract @OptIn annotations
        Regex("""@OptIn\s*\(\s*([^)]+)\s*\)""").findAll(content).forEach { match ->
            val optInList = match.groupValues[1]
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .map { it.removePrefix("::class") }
            optIns.addAll(optInList)
        }
        
        // Extract file-level annotations
        Regex("""@file:\s*Suppress\s*\(\s*([^)]+)\s*\)""").findAll(content).forEach { match ->
            val suppressList = match.groupValues[1]
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
            suppressions.addAll(suppressList)
        }
        
        Regex("""@file:\s*OptIn\s*\(\s*([^)]+)\s*\)""").findAll(content).forEach { match ->
            val optInList = match.groupValues[1]
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .map { it.removePrefix("::class") }
            optIns.addAll(optInList)
        }
        
        return SourceFileAnnotations(suppressions, optIns, packageName)
    }
    
    fun applyArmorToFile(file: File): Boolean {
        if (!file.exists() || !file.name.endsWith(".kt")) {
            return false
        }
        
        val annotations = scanSourceFile(file)
        val content = file.readText()
        var modified = content
        
        // Determine what suppressions and opt-ins are needed
        val neededSuppressions = commonSuppressions - annotations.suppressions
        val neededOptIns = commonOptIns - annotations.optIns
        
        if (neededSuppressions.isEmpty() && neededOptIns.isEmpty()) {
            return false
        }
        
        // Build file-level annotations
        val fileAnnotations = mutableListOf<String>()
        
        if (neededSuppressions.isNotEmpty()) {
            val suppressAnnotation = "@file:Suppress(${neededSuppressions.joinToString(", ") { "\"$it\"" }})"
            fileAnnotations.add(suppressAnnotation)
        }
        
        if (neededOptIns.isNotEmpty()) {
            val optInAnnotation = "@file:OptIn(${neededOptIns.joinToString(", ") { "$it::class" }})"
            fileAnnotations.add(optInAnnotation)
        }
        
        // Insert after package declaration or at the beginning
        val packageMatch = Regex("""package\s+[a-zA-Z0-9.]+\s*\n""").find(modified)
        if (packageMatch != null) {
            val insertPos = packageMatch.range.last + 1
            val annotationBlock = "\n${fileAnnotations.joinToString("\n")}\n"
            modified = modified.substring(0, insertPos) + annotationBlock + modified.substring(insertPos)
        } else {
            // No package declaration, insert at beginning
            modified = "${fileAnnotations.joinToString("\n")}\n\n$modified"
        }
        
        // Fix infix lambda type annotations
        modified = fixInfixLambdaAnnotations(modified)
        
        file.writeText(modified)
        return true
    }
    
    private fun fixInfixLambdaAnnotations(content: String): String {
        var modified = content
        
        // Pattern for infix functions with lambda parameters missing type annotations
        val infixPattern = Regex(
            """(infix\s+fun\s+[^(]+\([^)]*\)\s*:\s*)(\([^)]*\)\s*->\s*[^{]+)(\s*\{)""",
            RegexOption.MULTILINE
        )
        
        // Add explicit type annotations to lambda parameters
        infixPattern.findAll(modified).forEach { match ->
            val lambdaType = match.groupValues[2]
            if (!lambdaType.contains(":")) { // No type annotation
                // Try to infer types from context or use Any
                val fixedLambda = lambdaType.replace(
                    Regex("""\(([a-zA-Z0-9_,\s]+)\)"""),
                    { lambdaMatch ->
                        val params = lambdaMatch.groupValues[1].split(",").map { it.trim() }
                        val typedParams = params.map { param ->
                            if (param.contains(":")) param else "$param: Any"
                        }
                        "(${typedParams.joinToString(", ")})"
                    }
                )
                modified = modified.replace(match.value, match.groupValues[1] + fixedLambda + match.groupValues[3])
            }
        }
        
        return modified
    }
    
    fun processStackTrace(stackTrace: String, sourceRoot: File): String {
        val lines = stackTrace.lines()
        val processedLines = mutableListOf<String>()
        val annotationCache = mutableMapOf<String, SourceFileAnnotations>()
        val zoomOutSegments = mutableListOf<ZoomOutSegment>()
        val stacktraceFiles = mutableSetOf<File>()
        
        // First pass: collect all files involved in stacktrace
        lines.forEach { line ->
            val match = stackTracePattern.find(line)
            if (match != null) {
                val frame = StackTraceFrame(
                    className = match.groupValues[1],
                    methodName = match.groupValues[2],
                    fileName = match.groupValues[3],
                    lineNumber = match.groupValues[4].toInt(),
                    rawLine = line
                )
                
                val sourceFile = findSourceFile(sourceRoot, frame.className)
                if (sourceFile != null) {
                    stacktraceFiles.add(sourceFile)
                }
            }
        }
        
        // Get dirty files and find intersection with stacktrace files
        val dirtyFiles = getDirtyKotlinFiles(sourceRoot).toSet()
        val filesToArmor = stacktraceFiles.intersect(dirtyFiles)
        
        // Apply armor only to files that are BOTH dirty AND in stacktrace
        filesToArmor.forEach { file ->
            applyArmorToFile(file)
            println("Applied armor to dirty stacktrace file: ${file.path}")
        }
        
        // Second pass: process the stacktrace
        lines.forEach { line ->
            val match = stackTracePattern.find(line)
            if (match != null) {
                val frame = StackTraceFrame(
                    className = match.groupValues[1],
                    methodName = match.groupValues[2],
                    fileName = match.groupValues[3],
                    lineNumber = match.groupValues[4].toInt(),
                    rawLine = line
                )
                
                // Try to find the source file
                val sourceFile = findSourceFile(sourceRoot, frame.className)
                if (sourceFile != null) {
                    if (!annotationCache.containsKey(sourceFile.path)) {
                        annotationCache[sourceFile.path] = scanSourceFile(sourceFile)
                    }
                    
                    // Get zoom-out context
                    val zoomOut = getZoomOutContext(sourceFile, frame.lineNumber, 20)
                    zoomOutSegments.add(ZoomOutSegment(frame, zoomOut))
                }
                
                // Process the frame
                val processedFrame = processFrame(frame, annotationCache[sourceFile?.path])
                processedLines.add(processedFrame)
            } else {
                // Keep non-stacktrace lines as-is, unless they're noise
                if (!isNoiseMessage(line)) {
                    processedLines.add(line)
                }
            }
        }
        
        // Build output with zoom-out segments
        val output = StringBuilder()
        output.appendLine("=== PROCESSED STACKTRACE ===")
        output.appendLine(processedLines.joinToString("\n"))
        output.appendLine()
        
        // Add zoom-out segments
        if (zoomOutSegments.isNotEmpty()) {
            output.appendLine("=== ZOOM-OUT CONTEXT ===")
            zoomOutSegments.forEach { segment ->
                output.appendLine()
                output.appendLine("====== ${segment.frame.className}.${segment.frame.methodName} (${segment.frame.fileName}:${segment.frame.lineNumber}) ======")
                output.appendLine(segment.context)
            }
        }
        
        return output.toString()
    }
    
    data class ZoomOutSegment(
        val frame: StackTraceFrame,
        val context: String
    )
    
    private fun getZoomOutContext(file: File, lineNumber: Int, contextLines: Int): String {
        if (!file.exists()) return "// File not found: ${file.path}"
        
        val lines = file.readLines()
        val startLine = (lineNumber - contextLines).coerceAtLeast(1)
        val endLine = (lineNumber + contextLines).coerceAtMost(lines.size)
        
        val contextBuilder = StringBuilder()
        
        // Check if we need to add armor annotations
        val hasFileAnnotations = lines.any { it.contains("@file:") }
        val needsArmor = !hasFileAnnotations && commonSuppressions.isNotEmpty()
        
        if (needsArmor) {
            contextBuilder.appendLine("// [ARMOR APPLIED]")
            contextBuilder.appendLine("@file:Suppress(${commonSuppressions.joinToString(", ") { "\"$it\"" }})")
            contextBuilder.appendLine("@file:OptIn(${commonOptIns.joinToString(", ") { "$it::class" }})")
            contextBuilder.appendLine()
        }
        
        // Add line numbers and highlight the error line
        for (i in startLine..endLine) {
            val line = if (i <= lines.size) lines[i - 1] else ""
            val lineNumber = i.toString().padStart(4)
            val marker = if (i == lineNumber) " >>> " else "     "
            contextBuilder.appendLine("$lineNumber$marker$line")
        }
        
        // Coalesce multiple segments if they overlap
        return coalesceContext(contextBuilder.toString(), lineNumber)
    }
    
    private fun coalesceContext(context: String, centerLine: Int): String {
        // This would be enhanced to merge overlapping contexts
        // For now, return as-is
        return context
    }
    
    private fun processFrame(
        frame: StackTraceFrame, 
        annotations: SourceFileAnnotations?
    ): String {
        var processedLine = frame.rawLine
        
        // Clean up lambda names
        if (lambdaPattern.containsMatchIn(frame.methodName)) {
            val cleanMethod = frame.methodName.replace(lambdaPattern, "<lambda>")
            processedLine = processedLine.replace(frame.methodName, cleanMethod)
        }
        
        // Add context about suppressions if available
        if (annotations != null && annotations.suppressions.isNotEmpty()) {
            processedLine += " [Suppressed: ${annotations.suppressions.joinToString(", ")}]"
        }
        
        return processedLine
    }
    
    private fun findSourceFile(sourceRoot: File, className: String): File? {
        val path = className.replace('.', '/').substringBefore('$') + ".kt"
        val file = File(sourceRoot, "src/commonMain/kotlin/$path")
        if (file.exists()) return file
        
        // Try other source sets
        val sourceSets = listOf("jvmMain", "nativeMain", "jsMain", "commonTest", "jvmTest")
        for (sourceSet in sourceSets) {
            val altFile = File(sourceRoot, "src/$sourceSet/kotlin/$path")
            if (altFile.exists()) return altFile
        }
        
        return null
    }
    
    private fun isNoiseMessage(line: String): Boolean {
        val noisePatterns = listOf(
            "WARNING:",
            "w: ",
            "Note:",
            "Deprecated:",
            "is deprecated",
            "will be removed",
            "experimental API",
            "opt-in requirement",
            "This declaration is experimental"
        )
        
        return noisePatterns.any { line.contains(it, ignoreCase = true) }
    }
    
    fun applyArmorToProject(rootDir: File, onlyDirty: Boolean = true) {
        val kotlinFiles = if (onlyDirty) {
            // Get dirty files from git
            getDirtyKotlinFiles(rootDir)
        } else {
            rootDir.walk()
                .filter { it.isFile && it.name.endsWith(".kt") }
                .filter { !it.path.contains("build/") }
                .toList()
        }
        
        if (kotlinFiles.isEmpty()) {
            println("No ${if (onlyDirty) "dirty " else ""}Kotlin files to armor")
            return
        }
        
        println("Applying Project Armor to ${kotlinFiles.size} ${if (onlyDirty) "dirty " else ""}Kotlin files...")
        
        var modifiedCount = 0
        kotlinFiles.forEach { file ->
            if (applyArmorToFile(file)) {
                modifiedCount++
                println("Armored: ${file.path}")
            }
        }
        
        println("Modified $modifiedCount files with armor annotations")
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
}