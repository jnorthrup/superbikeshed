@file:OptIn(kotlin.ExperimentalStdlibApi::class)
package k2script.launcher

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.*

/**
 * Gradle-based Kotlin Script Launcher
 * 
 * Uses Gradle's dependency management instead of Maven
 * Supports same annotations as kscript
 */
class GradleScriptLauncher {
    
    private val k2scriptCache = Paths.get(System.getProperty("user.home"), ".k2script", "gradle-cache")
    
    init {
        Files.createDirectories(k2scriptCache)
    }
    
    // Generate build.gradle.kts for script
    fun generateBuildScript(annotations: ScriptAnnotations, scriptName: String): String {
        return """
            import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
            
            plugins {
                kotlin("jvm") version "1.9.22"
                application
            }
            
            repositories {
                mavenCentral()
                ${annotations.repositories.toList().joinToString("\n") { repo ->
                    """maven { url = uri("$repo") }"""
                }}
            }
            
            dependencies {
                implementation(kotlin("stdlib"))
                implementation(kotlin("script-runtime"))
                ${annotations.dependencies.toList().joinToString("\n") { dep ->
                    """implementation("$dep")"""
                }}
            }
            
            tasks.withType<KotlinCompile> {
                kotlinOptions {
                    jvmTarget = "17"
                    freeCompilerArgs = listOf(
                        "-Xopt-in=kotlin.RequiresOptIn",
                        "-Xopt-in=kotlin.ExperimentalStdlibApi"
                    ) + listOf(${annotations.kotlinOpts.toList().joinToString(", ") { "\"$it\"" }})
                }
            }
            
            application {
                mainClass.set("${scriptName}Kt")
            }
            
            tasks.register<JavaExec>("runScript") {
                classpath = sourceSets.main.get().runtimeClasspath
                mainClass.set("${scriptName}Kt")
                args = project.findProperty("scriptArgs")?.toString()?.split(" ") ?: emptyList()
            }
        """.trimIndent()
    }
    
    // Generate settings.gradle.kts
    fun generateSettingsScript(scriptName: String): String {
        return """
            rootProject.name = "$scriptName"
        """.trimIndent()
    }
    
    // Prepare script for compilation by wrapping in main function
    fun prepareScript(scriptFile: File, annotations: ScriptAnnotations): String {
        val scriptContent = scriptFile.readText()
        
        // Remove annotation lines
        val cleanedContent = scriptContent.lines()
            .filter { line ->
                !line.contains("@file:DependsOn") &&
                !line.contains("@file:Repository") &&
                !line.contains("@file:Include") &&
                !line.contains("@file:CompilerOpts") &&
                !line.contains("@file:KotlinOpts") &&
                !line.trim().startsWith("#!/")
            }
            .joinToString("\n")
        
        // Handle includes
        val includeContent = annotations.includes.toList().joinToString("\n") { includePath ->
            val includeFile = File(scriptFile.parentFile, includePath)
            if (includeFile.exists()) {
                "// Included from $includePath\n${includeFile.readText()}\n"
            } else ""
        }
        
        return """
            $includeContent
            
            fun main(args: Array<String>) {
                $cleanedContent
            }
        """.trimIndent()
    }
    
    suspend fun runScript(
        scriptFile: File,
        args: Array<String> = emptyArray()
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val annotations = MavenScriptLauncher().parseAnnotations(scriptFile)
            val scriptName = scriptFile.nameWithoutExtension
            
            // Create project directory
            val projectDir = k2scriptCache.resolve(scriptName)
            Files.createDirectories(projectDir)
            
            // Generate build files
            val buildFile = projectDir.resolve("build.gradle.kts").toFile()
            buildFile.writeText(generateBuildScript(annotations, scriptName))
            
            val settingsFile = projectDir.resolve("settings.gradle.kts").toFile()
            settingsFile.writeText(generateSettingsScript(scriptName))
            
            // Create source directory
            val srcDir = projectDir.resolve("src/main/kotlin")
            Files.createDirectories(srcDir)
            
            // Prepare and write script
            val preparedScript = prepareScript(scriptFile, annotations)
            val scriptOutputFile = srcDir.resolve("$scriptName.kt").toFile()
            scriptOutputFile.writeText(preparedScript)
            
            // Run gradle
            val scriptArgsProperty = if (args.isNotEmpty()) {
                "-PscriptArgs=${args.joinToString(" ")}"
            } else ""
            
            val process = ProcessBuilder(
                "./gradlew",
                "-p", projectDir.toString(),
                "runScript",
                "--console=plain",
                "--no-daemon",
                scriptArgsProperty
            ).apply {
                environment()["JAVA_HOME"] = System.getProperty("java.home")
                inheritIO()
            }.start()
            
            val exitCode = process.waitFor()
            Result.success(exitCode)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}