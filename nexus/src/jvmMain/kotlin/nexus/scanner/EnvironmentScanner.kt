package nexus.scanner

import java.io.File
import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

/**
 * Environment Scanner for Nexus
 * 
 * Discovers project structure, capabilities, and tools
 */
class EnvironmentScanner(
    private val rootDir: File = File(".")
) {
    
    data class EnvironmentInfo(
        val projectType: ProjectType,
        val buildTools: Set<BuildTool>,
        val languages: Set<Language>,
        val frameworks: Set<String>,
        val structure: ProjectStructure
    )
    
    enum class ProjectType {
        KOTLIN_MULTIPLATFORM,
        KOTLIN_JVM,
        JAVA,
        MIXED,
        UNKNOWN
    }
    
    enum class BuildTool {
        GRADLE,
        MAVEN,
        SBT,
        BAZEL
    }
    
    enum class Language {
        KOTLIN,
        JAVA,
        SCALA,
        GROOVY,
        PYTHON,
        JAVASCRIPT,
        TYPESCRIPT
    }
    
    data class ProjectStructure(
        val sourceRoots: List<File>,
        val testRoots: List<File>,
        val resourceRoots: List<File>,
        val buildFiles: List<File>,
        val totalFiles: Int,
        val totalSize: Long
    )
    
    suspend fun scan(): EnvironmentInfo = coroutineScope {
        val scanTime = measureTimeMillis {
            println("Scanning project structure...")
        }
        
        val buildTools = detectBuildTools()
        val languages = detectLanguages()
        val projectType = detectProjectType(buildTools, languages)
        val structure = analyzeStructure()
        val frameworks = detectFrameworks(structure)
        
        println("Scan completed in ${scanTime}ms")
        
        EnvironmentInfo(
            projectType = projectType,
            buildTools = buildTools,
            languages = languages,
            frameworks = frameworks,
            structure = structure
        )
    }
    
    private fun detectBuildTools(): Set<BuildTool> {
        val tools = mutableSetOf<BuildTool>()
        
        if (File(rootDir, "build.gradle.kts").exists() || File(rootDir, "build.gradle").exists()) {
            tools.add(BuildTool.GRADLE)
        }
        if (File(rootDir, "pom.xml").exists()) {
            tools.add(BuildTool.MAVEN)
        }
        if (File(rootDir, "build.sbt").exists()) {
            tools.add(BuildTool.SBT)
        }
        if (File(rootDir, "BUILD").exists() || File(rootDir, "WORKSPACE").exists()) {
            tools.add(BuildTool.BAZEL)
        }
        
        return tools
    }
    
    private fun detectLanguages(): Set<Language> {
        val languages = mutableSetOf<Language>()
        
        rootDir.walkTopDown()
            .filter { it.isFile }
            .take(1000) // Limit scan for performance
            .forEach { file ->
                when (file.extension.lowercase()) {
                    "kt", "kts" -> languages.add(Language.KOTLIN)
                    "java" -> languages.add(Language.JAVA)
                    "scala" -> languages.add(Language.SCALA)
                    "groovy" -> languages.add(Language.GROOVY)
                    "py" -> languages.add(Language.PYTHON)
                    "js" -> languages.add(Language.JAVASCRIPT)
                    "ts" -> languages.add(Language.TYPESCRIPT)
                }
            }
        
        return languages
    }
    
    private fun detectProjectType(buildTools: Set<BuildTool>, languages: Set<Language>): ProjectType {
        return when {
            BuildTool.GRADLE in buildTools && 
                File(rootDir, "gradle.properties").let { it.exists() && it.readText().contains("kotlin.mpp.enableGranularSourceSetsMetadata") }
                -> ProjectType.KOTLIN_MULTIPLATFORM
            Language.KOTLIN in languages && Language.JAVA !in languages 
                -> ProjectType.KOTLIN_JVM
            Language.JAVA in languages && Language.KOTLIN !in languages 
                -> ProjectType.JAVA
            Language.KOTLIN in languages && Language.JAVA in languages 
                -> ProjectType.MIXED
            else -> ProjectType.UNKNOWN
        }
    }
    
    private fun analyzeStructure(): ProjectStructure {
        val sourceRoots = mutableListOf<File>()
        val testRoots = mutableListOf<File>()
        val resourceRoots = mutableListOf<File>()
        val buildFiles = mutableListOf<File>()
        
        var totalFiles = 0
        var totalSize = 0L
        
        // Common source directories
        val sourceDirs = listOf("src/main", "src/commonMain", "src/jvmMain", "src")
        val testDirs = listOf("src/test", "src/commonTest", "src/jvmTest", "test")
        val resourceDirs = listOf("src/main/resources", "resources")
        
        sourceDirs.forEach { dir ->
            val sourceDir = File(rootDir, dir)
            if (sourceDir.exists() && sourceDir.isDirectory) {
                sourceRoots.add(sourceDir)
            }
        }
        
        testDirs.forEach { dir ->
            val testDir = File(rootDir, dir)
            if (testDir.exists() && testDir.isDirectory) {
                testRoots.add(testDir)
            }
        }
        
        resourceDirs.forEach { dir ->
            val resourceDir = File(rootDir, dir)
            if (resourceDir.exists() && resourceDir.isDirectory) {
                resourceRoots.add(resourceDir)
            }
        }
        
        // Find build files
        rootDir.walkTopDown()
            .maxDepth(3)
            .filter { it.isFile }
            .forEach { file ->
                when (file.name) {
                    "build.gradle.kts", "build.gradle", "pom.xml", "build.sbt" -> buildFiles.add(file)
                }
                totalFiles++
                totalSize += file.length()
            }
        
        return ProjectStructure(
            sourceRoots = sourceRoots,
            testRoots = testRoots,
            resourceRoots = resourceRoots,
            buildFiles = buildFiles,
            totalFiles = totalFiles,
            totalSize = totalSize
        )
    }
    
    private fun detectFrameworks(structure: ProjectStructure): Set<String> {
        val frameworks = mutableSetOf<String>()
        
        // Check build files for framework dependencies
        structure.buildFiles.forEach { buildFile ->
            val content = buildFile.readText()
            
            // Kotlin frameworks
            if (content.contains("ktor")) frameworks.add("Ktor")
            if (content.contains("exposed")) frameworks.add("Exposed")
            if (content.contains("kotlinx-coroutines")) frameworks.add("Coroutines")
            if (content.contains("kotlinx-serialization")) frameworks.add("Serialization")
            
            // Java frameworks
            if (content.contains("spring")) frameworks.add("Spring")
            if (content.contains("micronaut")) frameworks.add("Micronaut")
            if (content.contains("quarkus")) frameworks.add("Quarkus")
            
            // Testing
            if (content.contains("junit")) frameworks.add("JUnit")
            if (content.contains("kotest")) frameworks.add("Kotest")
        }
        
        return frameworks
    }
    
    fun printReport(info: EnvironmentInfo) {
        println("\n=== Environment Scan Report ===")
        println("Project Type: ${info.projectType}")
        println("Build Tools: ${info.buildTools.joinToString(", ")}")
        println("Languages: ${info.languages.joinToString(", ")}")
        println("Frameworks: ${info.frameworks.joinToString(", ")}")
        println("\nProject Structure:")
        println("  Source Roots: ${info.structure.sourceRoots.size}")
        info.structure.sourceRoots.forEach { println("    - ${it.relativeTo(rootDir)}") }
        println("  Test Roots: ${info.structure.testRoots.size}")
        info.structure.testRoots.forEach { println("    - ${it.relativeTo(rootDir)}") }
        println("  Build Files: ${info.structure.buildFiles.size}")
        info.structure.buildFiles.forEach { println("    - ${it.name}") }
        println("  Total Files: ${info.structure.totalFiles}")
        println("  Total Size: ${info.structure.totalSize / 1024 / 1024} MB")
    }
}