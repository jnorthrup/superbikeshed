package buildtools

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.writeText

/**
 * Strips version declarations from child project build.gradle.kts files.
 * Versions should only be declared in the root project.
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
            }
        }
    }
}