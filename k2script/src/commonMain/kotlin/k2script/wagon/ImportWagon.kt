package k2script.wagon

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

/**
 * Minimal ImportWagon for k2script @file:Import annotations
 * 
 * Handles:
 * - @file:Import("groupId:artifactId:version") annotations
 * - Maven coordinate resolution
 * - Classpath generation (like maven dependency-classpath)
 */

// === Import Annotation Parser ===

data class ImportAnnotation(
    val coordinate: String,
    val scope: String = "compile", // compile, runtime, test
    val classifier: String? = null,
    val type: String = "jar"
)

object ImportParser {
    
    fun parseImportAnnotation(annotation: String): ImportAnnotation {
        // Remove @file:Import wrapper
        val content = annotation
            .removePrefix("@file:Import(")
            .removeSuffix(")")
            .trim('"')
        
        val parts = content.split(":")
        if (parts.size < 3) {
            throw IllegalArgumentException("Invalid import: $content. Expected: groupId:artifactId:version")
        }
        
        return ImportAnnotation(
            coordinate = content,
            scope = "compile"
        )
    }
    
    fun parseImportWithScope(annotation: String): ImportAnnotation {
        // Format: @file:Import("groupId:artifactId:version:scope")
        val content = annotation
            .removePrefix("@file:Import(")
            .removeSuffix(")")
            .trim('"')
        
        val parts = content.split(":")
        if (parts.size < 3) {
            throw IllegalArgumentException("Invalid import: $content")
        }
        
        return ImportAnnotation(
            coordinate = "${parts[0]}:${parts[1]}:${parts[2]}",
            scope = if (parts.size > 3) parts[3] else "compile"
        )
    }
}

// === Minimal Import Wagon ===

class ImportWagon(
    private val repositories: List<Repository> = listOf(
        Repository("central", "https://repo1.maven.org/maven2"),
        Repository("gradle-plugins", "https://plugins.gradle.org/m2")
    ),
    private val cacheDir: String = "${System.getProperty("user.home")}/.k2script/imports"
) {
    
    suspend fun resolveImport(import: ImportAnnotation): String {
        val artifact = parseCoordinate(import.coordinate)
        val localPath = resolveToLocalPath(artifact)
        
        if (!isCached(localPath)) {
            downloadArtifact(artifact, localPath)
        }
        
        return localPath
    }
    
    suspend fun resolveImports(imports: List<ImportAnnotation>): List<String> = coroutineScope {
        imports.map { import ->
            async { resolveImport(import) }
        }.awaitAll()
    }
    
    suspend fun generateClasspath(imports: List<ImportAnnotation>): String {
        val paths = resolveImports(imports)
        return paths.joinToString(":")
    }
    
    private fun parseCoordinate(coordinate: String): Artifact {
        val parts = coordinate.split(":")
        return Artifact(
            groupId = parts[0],
            artifactId = parts[1],
            version = parts[2]
        )
    }
    
    private fun resolveToLocalPath(artifact: Artifact): String {
        val groupPath = artifact.groupId.replace('.', '/')
        return "$cacheDir/$groupPath/${artifact.artifactId}/${artifact.version}/${artifact.fileName}"
    }
    
    private fun isCached(localPath: String): Boolean {
        return IOMemento.exists(localPath)
    }
    
    private suspend fun downloadArtifact(artifact: Artifact, localPath: String) {
        for (repo in repositories) {
            try {
                val artifactUrl = "${repo.url}/${artifact.path}"
                val data = downloadFromUrl(artifactUrl)
                
                // Ensure cache directory exists
                val cacheFile = IOMemento.createFile(localPath)
                IOMemento.writeBytes(cacheFile, data)
                
                println("✅ Downloaded: ${artifact.fileName}")
                return
                
            } catch (e: Exception) {
                println("❌ Failed from ${repo.id}: ${e.message}")
                continue
            }
        }
        
        throw ArtifactNotFoundException("Artifact not found: ${artifact.groupId}:${artifact.artifactId}:${artifact.version}")
    }
    
    private suspend fun downloadFromUrl(url: String): ByteIndexed {
        // Simple HTTP download - would use trikeshed-net in real implementation
        val connection = HttpConnection.connect(url)
        val response = connection.get()
        
        return when (response.status) {
            200 -> response.body
            404 -> throw ArtifactNotFoundException("Not found: $url")
            else -> throw TransportException("HTTP ${response.status}: ${response.message}")
        }
    }
}

// === Script Import Processor ===

class ScriptImportProcessor(
    private val wagon: ImportWagon = ImportWagon()
) {
    
    suspend fun processScriptImports(scriptContent: String): ProcessedScript {
        val imports = extractImports(scriptContent)
        val classpath = if (imports.isNotEmpty()) {
            wagon.generateClasspath(imports)
        } else {
            ""
        }
        
        return ProcessedScript(
            originalContent = scriptContent,
            imports = imports,
            classpath = classpath,
            processedContent = removeImportAnnotations(scriptContent)
        )
    }
    
    private fun extractImports(scriptContent: String): List<ImportAnnotation> {
        val importPattern = """@file:Import\("([^"]+)"\)""".toRegex()
        val matches = importPattern.findAll(scriptContent)
        
        return matches.map { match ->
            ImportParser.parseImportAnnotation(match.value)
        }.toList()
    }
    
    private fun removeImportAnnotations(scriptContent: String): String {
        val importPattern = """@file:Import\("[^"]+"\)\s*\n?""".toRegex()
        return importPattern.replace(scriptContent, "")
    }
}

data class ProcessedScript(
    val originalContent: String,
    val imports: List<ImportAnnotation>,
    val classpath: String,
    val processedContent: String
)

// === Classpath Generator (like maven dependency-classpath) ===

object ClasspathGenerator {
    
    suspend fun generateClasspath(imports: List<String>): String {
        val wagon = ImportWagon()
        val importAnnotations = imports.map { ImportParser.parseImportAnnotation("@file:Import(\"$it\")") }
        return wagon.generateClasspath(importAnnotations)
    }
    
    suspend fun generateClasspathFromFile(importsFile: String): String {
        val content = IOMemento.readText(importsFile)
        val imports = content.lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.trim() }
        
        return generateClasspath(imports)
    }
}

// === Usage Examples ===

/*
Example kts file with imports:
@file:Import("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
@file:Import("org.jetbrains.kotlin:kotlin-reflect:1.9.0")

import kotlin.reflect.full.*

fun main() {
    println("Hello from k2script with imports!")
}
*/

/*
CLI usage (like maven dependency-classpath):
$ k2script classpath org.jetbrains.kotlin:kotlin-stdlib:1.9.0 org.jetbrains.kotlin:kotlin-reflect:1.9.0
/home/user/.k2script/imports/org/jetbrains/kotlin/kotlin-stdlib/1.9.0/kotlin-stdlib-1.9.0.jar:/home/user/.k2script/imports/org/jetbrains/kotlin/kotlin-reflect/1.9.0/kotlin-reflect-1.9.0.jar
*/ 