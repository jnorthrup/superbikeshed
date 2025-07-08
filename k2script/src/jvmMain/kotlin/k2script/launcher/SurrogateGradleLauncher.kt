package k2script.launcher

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.concurrent.thread

/**
 * Surrogate Gradle Project Launcher for K2Script
 * 
 * Creates a temporary Gradle project that:
 * 1. Handles all dependencies via Gradle
 * 2. Configures compiler plugins (serialization, etc)
 * 3. Compiles and runs the script
 * 4. Caches results for fast re-runs
 */
class SurrogateGradleLauncher {
    
    private val k2scriptHome = Paths.get(System.getProperty("user.home"), ".k2script")
    private val surrogatesDir = k2scriptHome.resolve("gradle-surrogates")
    
    init {
        Files.createDirectories(surrogatesDir)
    }
    
    suspend fun runScript(
        scriptFile: File, 
        args: Array<String> = emptyArray()
    ): Int = withContext(Dispatchers.IO) {
        val scriptHash = scriptFile.readText().hashCode().toString(16)
        val surrogateName = "${scriptFile.nameWithoutExtension}-$scriptHash"
        val surrogateDir = surrogatesDir.resolve(surrogateName)
        
        // Check if we need to rebuild
        val needsRebuild = !Files.exists(surrogateDir.resolve("build/classes/kotlin/main"))
        
        if (needsRebuild) {
            createSurrogateProject(scriptFile, surrogateDir.toFile())
        }
        
        // Run via Gradle
        val mainClass = "${scriptFile.nameWithoutExtension}Kt"
        val process = ProcessBuilder(
            "./gradlew", "-p", surrogateDir.toString(),
            "run",
            "--console=plain",
            "--no-daemon",
            "-PmainClass=$mainClass",
            "-Pargs=${args.joinToString(" ")}"
        ).apply {
            inheritIO()
            environment()["JAVA_OPTS"] = "-Dfile.encoding=UTF-8"
        }.start()
        
        process.waitFor()
    }
    
    private fun createSurrogateProject(scriptFile: File, projectDir: File) {
        projectDir.mkdirs()
        
        // Parse script annotations
        val annotations = parseScriptAnnotations(scriptFile)
        
        // Generate build.gradle.kts
        val buildGradleContent = generateBuildGradle(
            scriptFile.nameWithoutExtension,
            annotations
        )
        File(projectDir, "build.gradle.kts").writeText(buildGradleContent)
        
        // Generate settings.gradle.kts
        File(projectDir, "settings.gradle.kts").writeText("""
            rootProject.name = "${scriptFile.nameWithoutExtension}"
        """.trimIndent())
        
        // Copy gradle wrapper from parent project
        val gradleWrapperSrc = File("../gradle/wrapper")
        val gradleWrapperDst = File(projectDir, "gradle/wrapper")
        if (gradleWrapperSrc.exists()) {
            gradleWrapperDst.mkdirs()
            File(gradleWrapperSrc, "gradle-wrapper.jar").copyTo(
                File(gradleWrapperDst, "gradle-wrapper.jar"), overwrite = true
            )
            File(gradleWrapperSrc, "gradle-wrapper.properties").copyTo(
                File(gradleWrapperDst, "gradle-wrapper.properties"), overwrite = true
            )
        }
        
        // Copy gradlew scripts
        File("../gradlew").copyTo(File(projectDir, "gradlew"), overwrite = true)
        File(projectDir, "gradlew").setExecutable(true)
        if (File("../gradlew.bat").exists()) {
            File("../gradlew.bat").copyTo(File(projectDir, "gradlew.bat"), overwrite = true)
        }
        
        // Prepare script
        val srcDir = File(projectDir, "src/main/kotlin")
        srcDir.mkdirs()
        
        val preparedScript = prepareScriptContent(scriptFile, annotations)
        File(srcDir, "${scriptFile.nameWithoutExtension}.kt").writeText(preparedScript)
        
        // Compile with Gradle
        val compileProcess = ProcessBuilder(
            "./gradlew", "-p", projectDir.absolutePath,
            "compileKotlin",
            "--console=plain",
            "--no-daemon"
        ).apply {
            inheritIO()
        }.start()
        
        if (compileProcess.waitFor() != 0) {
            throw RuntimeException("Compilation failed")
        }
    }
    
    private fun parseScriptAnnotations(scriptFile: File): ScriptAnnotations {
        val lines = scriptFile.readLines()
        val dependencies = mutableListOf<String>()
        val repositories = mutableListOf<String>()
        val compilerPlugins = mutableSetOf<String>()
        
        lines.forEach { line ->
            when {
                line.contains("@file:DependsOn") -> {
                    val dep = line.substringAfter('"').substringBefore('"')
                    dependencies.add(dep)
                    
                    // Auto-detect needed plugins
                    when {
                        dep.contains("kotlinx-serialization") -> {
                            compilerPlugins.add("serialization")
                        }
                    }
                }
                line.contains("@file:Repository") -> {
                    repositories.add(line.substringAfter('"').substringBefore('"'))
                }
            }
        }
        
        return ScriptAnnotations(dependencies, repositories, compilerPlugins)
    }
    
    private fun generateBuildGradle(
        projectName: String,
        annotations: ScriptAnnotations
    ): String {
        val kotlinVersion = "1.9.22" // Match what script expects
        
        return """
        plugins {
            kotlin("jvm") version "$kotlinVersion"
            ${if (annotations.compilerPlugins.contains("serialization")) {
                """kotlin("plugin.serialization") version "$kotlinVersion""""
            } else ""}
            application
        }
        
        repositories {
            mavenCentral()
            ${annotations.repositories.joinToString("\n            ") { repo ->
                """maven { url = uri("$repo") }"""
            }}
        }
        
        dependencies {
            ${annotations.dependencies.joinToString("\n            ") { dep ->
                """implementation("$dep")"""
            }}
        }
        
        application {
            mainClass.set(project.findProperty("mainClass")?.toString() ?: "MainKt")
        }
        
        tasks.withType<JavaExec> {
            standardInput = System.`in`
            val argsString = project.findProperty("args")?.toString() ?: ""
            if (argsString.isNotEmpty()) {
                args = argsString.split(" ")
            }
        }
        
        kotlin {
            jvmToolchain(17)
        }
        """.trimIndent()
    }
    
    private fun prepareScriptContent(
        scriptFile: File,
        annotations: ScriptAnnotations
    ): String {
        val content = scriptFile.readText()
        
        // Remove annotations and shebang
        val cleanedLines = content.lines().filter { line ->
            !line.startsWith("#!/") &&
            !line.contains("@file:DependsOn") &&
            !line.contains("@file:Repository") &&
            !line.contains("@file:Include") &&
            !line.contains("@file:CompilerOpts") &&
            !line.contains("@file:KotlinOpts")
        }
        
        // Script already has main function and runBlocking
        return cleanedLines.joinToString("\n")
    }
    
    data class ScriptAnnotations(
        val dependencies: List<String>,
        val repositories: List<String>,
        val compilerPlugins: Set<String>
    )
}

// Direct k2script runner using Gradle surrogate approach
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: k2script <script.kts> [args...]")
        return
    }
    
    val scriptFile = File(args[0])
    if (!scriptFile.exists()) {
        println("Script not found: ${args[0]}")
        System.exit(1)
    }
    
    val scriptArgs = args.drop(1).toTypedArray()
    
    runBlocking {
        try {
            val exitCode = SurrogateGradleLauncher().runScript(scriptFile, scriptArgs)
            System.exit(exitCode)
        } catch (e: Exception) {
            System.err.println("Script execution failed: ${e.message}")
            e.printStackTrace()
            System.exit(1)
        }
    }
}