@file:OptIn(kotlin.ExperimentalStdlibApi::class)
package buildtools

import java.io.File

/**
 * Project Armor: Clean implementation for stacktrace fixing
 */
object ProjectArmorStacktraceFixerClean {
    
    private val commonSuppressions = setOf(
        "UNCHECKED_CAST",
        "DEPRECATION", 
        "EXPERIMENTAL_API_USAGE",
        "EXPERIMENTAL_UNSIGNED_LITERALS",
        "OPT_IN_USAGE"
    )
    
    private val commonOptIns = setOf(
        "kotlin.ExperimentalStdlibApi",
        "kotlin.ExperimentalUnsignedTypes",
        "kotlinx.coroutines.ExperimentalCoroutinesApi",
        "kotlin.time.ExperimentalTime",
        "kotlin.contracts.ExperimentalContracts"
    )
    
    fun applyArmorToFile(file: File): Boolean {
        if (!file.exists() || !file.name.endsWith(".kt")) {
            return false
        }
        
        val content = file.readText()
        
        // Check if already has file-level suppressions
        if (content.contains("@file:Suppress") || content.contains("@file:OptIn")) {
            return false // Already armored
        }
        
        // Build armor annotations
        val suppressAnnotation = "@file:Suppress(${commonSuppressions.joinToString(", ") { "\"$it\"" }})"
        val optInAnnotation = "@file:OptIn(${commonOptIns.joinToString(", ") { "$it::class" }})"
        
        // Insert after package declaration
        val packageMatch = Regex("""package\s+[a-zA-Z0-9.]+\s*\n""").find(content)
        val modified = if (packageMatch != null) {
            val insertPos = packageMatch.range.last + 1
            val annotationBlock = "\n$suppressAnnotation\n$optInAnnotation\n"
            content.substring(0, insertPos) + annotationBlock + content.substring(insertPos)
        } else {
            "$suppressAnnotation\n$optInAnnotation\n\n$content"
        }
        
        file.writeText(modified)
        return true
    }
    
    fun processStackTrace(stackTrace: String, sourceRoot: File): String {
        val stackTracePattern = Regex("""at\s+([a-zA-Z0-9.$]+)\.([a-zA-Z0-9$<>]+)\(([^:)]+):(\d+)\)""")
        val stacktraceFiles = mutableSetOf<File>()
        
        // Find all files involved in stacktrace
        stackTrace.lines().forEach { line ->
            stackTracePattern.find(line)?.let { match ->
                val className = match.groupValues[1]
                findSourceFile(sourceRoot, className)?.let { file ->
                    stacktraceFiles.add(file)
                }
            }
        }
        
        // Get dirty files and apply armor to intersection
        val dirtyFiles = getDirtyKotlinFiles(sourceRoot).toSet()
        val filesToArmor = stacktraceFiles.intersect(dirtyFiles)
        
        filesToArmor.forEach { file ->
            if (applyArmorToFile(file)) {
                println("Applied armor to dirty stacktrace file: ${file.path}")
            }
        }
        
        return "=== PROCESSED STACKTRACE ===\n\n$stackTrace\n\n=== ARMOR APPLIED TO ${filesToArmor.size} FILES ==="
    }
    
    private fun getDirtyKotlinFiles(rootDir: File): List<File> {
        val processBuilder = ProcessBuilder("git", "status", "--porcelain", "*.kt")
        processBuilder.directory(rootDir)
        
        val process = processBuilder.start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        
        return output.lines()
            .filter { it.isNotBlank() }
            .filter { line -> line.startsWith(" M") || line.startsWith("M ") }
            .mapNotNull { line ->
                val filename = line.substring(3).trim()
                val file = File(rootDir, filename)
                if (file.exists() && file.name.endsWith(".kt")) file else null
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
    
    fun applyArmorToProject(rootDir: File, onlyDirty: Boolean = true) {
        val kotlinFiles = if (onlyDirty) {
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
        
        var modifiedCount = 0
        kotlinFiles.forEach { file ->
            if (applyArmorToFile(file)) {
                modifiedCount++
                println("Armored: ${file.path}")
            }
        }
        
        println("Modified $modifiedCount files with armor annotations")
    }
}