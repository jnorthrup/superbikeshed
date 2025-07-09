package buildtools

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.writeText

/**
 * Strips version declarations from child project build.gradle.kts files.
 * Versions should only be declared in the root project.
 * Also enforces platform target consistency with trikeshed-lib.
 */
object GradleVersionStripper {
    
    private val versionPatterns = listOf(
        // kotlin("plugin.serialization") version "2.2.0"
        Regex("""kotlin\s*\(\s*"[^"]+"\s*\)\s+version\s+"[^"]+""""),
        // id("some.plugin") version "1.2.3"
        Regex("""id\s*\(\s*"[^"]+"\s*\)\s+version\s+"[^"]+""""),
        // implementation("group:artifact:version")
        Regex("""(implementation|api|testImplementation|compileOnly|runtimeOnly)\s*\(\s*"[^:]+:[^:]+:[^"]+"\s*\)"""),
        // Version variables
        Regex("""val\s+\w*[Vv]ersion\s*=\s*"[^"]+""""),
        Regex("""const\s+val\s+\w*[Vv]ersion\s*=\s*"[^"]+"""")
    )
    
    // Reference target configuration from trikeshed-lib
    private val referenceTargets = """
        jvm()
        
        // Native target based on host OS
        val hostOs = System.getProperty("os.name")
        val hostArch = System.getProperty("os.arch")
        when {
            hostOs == "Mac OS X" && hostArch == "aarch64" -> macosArm64()
            hostOs == "Mac OS X" -> macosX64()
            hostOs == "Linux" -> linuxX64()
        }
    """.trimIndent()
    
    fun stripVersionsFromFile(file: File): Boolean {
        if (!file.exists() || !file.name.endsWith(".gradle.kts")) {
            return false
        }
        
        val content = file.readText()
        var modified = content
        var hasChanges = false
        
        versionPatterns.forEach { pattern ->
            val matches = pattern.findAll(modified)
            matches.forEach { match ->
                when {
                    // Plugin versions - remove version clause
                    match.value.contains("kotlin") || match.value.contains("id") -> {
                        val stripped = match.value.replace(Regex("""\s+version\s+"[^"]+"""), "")
                        modified = modified.replace(match.value, stripped)
                        hasChanges = true
                    }
                    // Dependency versions - extract group:artifact only
                    match.value.contains("implementation") || 
                    match.value.contains("api") || 
                    match.value.contains("testImplementation") -> {
                        val depPattern = Regex("""("[^:]+:[^:]+):[^"]+"""")
                        depPattern.find(match.value)?.let { depMatch ->
                            val groupArtifact = depMatch.groupValues[1]
                            val functionCall = match.value.substringBefore("(")
                            val stripped = """$functionCall($groupArtifact")"""
                            modified = modified.replace(match.value, stripped)
                            hasChanges = true
                        }
                    }
                    // Version variables - comment out
                    match.value.contains("ersion") -> {
                        modified = modified.replace(match.value, "// $match.value // VERSION SHOULD BE IN ROOT")
                        hasChanges = true
                    }
                }
            }
        }
        
        if (hasChanges) {
            Files.writeString(file.toPath(), modified)
            println("Stripped versions from: ${file.path}")
        }
        
        return hasChanges
    }
    
    fun processProject(rootDir: File, dryRun: Boolean = false) {
        val buildFiles = rootDir.walk()
            .filter { it.isFile && it.name == "build.gradle.kts" }
            .filter { it.parentFile != rootDir } // Skip root build.gradle.kts
            .toList()
        
        println("Found ${buildFiles.size} child build.gradle.kts files")
        
        buildFiles.forEach { file ->
            if (dryRun) {
                println("Would process: ${file.path}")
            } else {
                stripVersionsFromFile(file)
                enforceTargetConsistency(file)
            }
        }
    }
    
    /**
     * Enforces target consistency by replacing deviating targets with trikeshed-lib reference.
     * This "stomps" any custom target configurations.
     */
    fun enforceTargetConsistency(file: File) {
        if (!file.exists() || !file.name.endsWith(".gradle.kts")) {
            return
        }
        
        val content = file.readText()
        val kotlinBlockPattern = Regex("""kotlin\s*\{([^{}]*(?:\{[^{}]*\}[^{}]*)*)\}""", RegexOption.DOT_MATCHES_ALL)
        
        val match = kotlinBlockPattern.find(content)
        if (match != null) {
            val kotlinBlock = match.groupValues[1]
            
            // Check if this deviates from reference targets
            val hasCustomTargets = kotlinBlock.contains(Regex("""(js|wasm|mingw|ios|watchos|tvos|linux.*(?<!X64)|android)"""))
            val hasStandardTargets = kotlinBlock.contains("jvm()") && 
                                    kotlinBlock.contains("System.getProperty")
            
            if (hasCustomTargets || !hasStandardTargets) {
                println("Stomping deviating targets in: ${file.path}")
                
                // Extract source sets if any
                val sourceSetsPattern = Regex("""sourceSets\s*\{([^{}]*(?:\{[^{}]*\}[^{}]*)*)\}""", RegexOption.DOT_MATCHES_ALL)
                val sourceSetsMatch = sourceSetsPattern.find(kotlinBlock)
                val sourceSets = sourceSetsMatch?.value ?: ""
                
                // Build new kotlin block with reference targets
                val newKotlinBlock = """
    $referenceTargets
    
    $sourceSets
""".trimIndent()
                
                val newContent = content.replace(match.value, "kotlin {\n$newKotlinBlock\n}")
                file.writeText(newContent)
            }
        }
    }
}