@file:OptIn(kotlin.ExperimentalStdlibApi::class)
package k2script.launcher

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.tools.JavaCompiler
import javax.tools.ToolProvider
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.*
import kotlin.script.experimental.jvm.dependenciesFromClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath

/**
 * Maven-based Kotlin Script Launcher
 * 
 * Handles kscript-style annotations:
 * - @file:DependsOn("group:artifact:version")
 * - @file:Repository("url")
 * - @file:Include("path")
 * - @file:CompilerOpts("-jvm-target 17")
 * - @file:KotlinOpts("-Xopt-in=kotlin.RequiresOptIn")
 */
class MavenScriptLauncher {
    
    private val mavenLocalRepo = Paths.get(System.getProperty("user.home"), ".m2", "repository")
    private val k2scriptCache = Paths.get(System.getProperty("user.home"), ".k2script", "cache")
    
    init {
        Files.createDirectories(k2scriptCache)
    }
    
    // Parse script annotations using TrikeShed patterns
    fun parseAnnotations(scriptFile: File): ScriptAnnotations {
        val lines = scriptFile.readLines()
        val dependencies = mutableListOf<String>()
        val repositories = mutableListOf<String>()
        val includes = mutableListOf<String>()
        val compilerOpts = mutableListOf<String>()
        val kotlinOpts = mutableListOf<String>()
        
        lines.forEach { line ->
            when {
                line.contains("@file:DependsOn") -> {
                    extractQuotedValue(line)?.let { dependencies.add(it) }
                }
                line.contains("@file:Repository") -> {
                    extractQuotedValue(line)?.let { repositories.add(it) }
                }
                line.contains("@file:Include") -> {
                    extractQuotedValue(line)?.let { includes.add(it) }
                }
                line.contains("@file:CompilerOpts") -> {
                    extractQuotedValue(line)?.let { compilerOpts.add(it) }
                }
                line.contains("@file:KotlinOpts") -> {
                    extractQuotedValue(line)?.let { kotlinOpts.add(it) }
                }
            }
        }
        
        return ScriptAnnotations(
            dependencies = dependencies.toIndexed(),
            repositories = repositories.toIndexed(),
            includes = includes.toIndexed(),
            compilerOpts = compilerOpts.toIndexed(),
            kotlinOpts = kotlinOpts.toIndexed()
        )
    }
    
    private fun extractQuotedValue(line: String): String? {
        val regex = """"([^"]*)"""".toRegex()
        return regex.find(line)?.groupValues?.get(1)
    }
    
    // Generate Maven POM for dependencies
    fun generatePom(annotations: ScriptAnnotations, scriptName: String): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                
                <groupId>k2script.generated</groupId>
                <artifactId>$scriptName</artifactId>
                <version>1.0-SNAPSHOT</version>
                
                <properties>
                    <kotlin.version>1.9.22</kotlin.version>
                    <maven.compiler.source>17</maven.compiler.source>
                    <maven.compiler.target>17</maven.compiler.target>
                </properties>
                
                <repositories>
                    <repository>
                        <id>mavenCentral</id>
                        <url>https://repo1.maven.org/maven2/</url>
                    </repository>
                    ${annotations.repositories.toList().mapIndexed { i, repo -> 
                        """
                        <repository>
                            <id>custom-$i</id>
                            <url>$repo</url>
                        </repository>
                        """
                    }.joinToString("\n")}
                </repositories>
                
                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-stdlib</artifactId>
                        <version>${'$'}{kotlin.version}</version>
                    </dependency>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-script-runtime</artifactId>
                        <version>${'$'}{kotlin.version}</version>
                    </dependency>
                    ${annotations.dependencies.toList().map { dep ->
                        val parts = dep.split(":")
                        if (parts.size >= 3) {
                            """
                            <dependency>
                                <groupId>${parts[0]}</groupId>
                                <artifactId>${parts[1]}</artifactId>
                                <version>${parts[2]}</version>
                            </dependency>
                            """
                        } else ""
                    }.joinToString("\n")}
                </dependencies>
            </project>
        """.trimIndent()
    }
    
    // Run Maven to download dependencies
    suspend fun downloadDependencies(pomFile: File): Result<Indexed<File>> = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(
                "mvn",
                "-f", pomFile.absolutePath,
                "dependency:copy-dependencies",
                "-DoutputDirectory=${k2scriptCache.toAbsolutePath()}",
                "-Dmdep.stripVersion=false"
            ).start()
            
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                return@withContext Result.failure(Exception("Maven failed with exit code $exitCode"))
            }
            
            // Find all downloaded JARs
            val jars = Files.walk(k2scriptCache)
                .filter { it.toString().endsWith(".jar") }
                .map { it.toFile() }
                .toList()
            
            Result.success(jars.toIndexed())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Compile and run script with dependencies
    suspend fun runScript(
        scriptFile: File,
        args: Array<String> = emptyArray()
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // Parse annotations
            val annotations = parseAnnotations(scriptFile)
            
            // Generate POM
            val scriptName = scriptFile.nameWithoutExtension
            val pomContent = generatePom(annotations, scriptName)
            val pomFile = k2scriptCache.resolve("$scriptName-pom.xml").toFile()
            pomFile.writeText(pomContent)
            
            // Download dependencies
            val jarsResult = downloadDependencies(pomFile)
            if (jarsResult.isFailure) {
                return@withContext Result.failure(jarsResult.exceptionOrNull()!!)
            }
            
            val jars = jarsResult.getOrThrow()
            
            // Create classloader with all JARs
            val urls = (0 until jars.a).map { i -> 
                jars.b(i).toURI().toURL() 
            }.toTypedArray()
            
            val classLoader = URLClassLoader(urls, Thread.currentThread().contextClassLoader)
            
            // Configure script compilation
            val compilationConfiguration = createJvmScriptCompilationConfiguration {
                jvm {
                    dependenciesFromClassloader(classLoader = classLoader)
                }
                compilerOptions.append(annotations.compilerOpts.toList())
                ide {
                    acceptedLocations(ScriptAcceptedLocation.Everywhere)
                }
            }
            
            // Configure script evaluation
            val evaluationConfiguration = createJvmScriptEvaluationConfiguration {
                jvm {
                    updateClasspath(jars.toList())
                }
                constructorArgs(args)
            }
            
            // Run the script
            val scriptSource = scriptFile.toScriptSource()
            val result = BasicJvmScriptingHost().eval(
                scriptSource,
                compilationConfiguration,
                evaluationConfiguration
            )
            
            when (result) {
                is ResultValue.Value -> Result.success(0)
                is ResultValue.Error -> Result.failure(result.error)
                else -> Result.success(0)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// Data class for script annotations using TrikeShed patterns
data class ScriptAnnotations(
    val dependencies: Indexed<String>,
    val repositories: Indexed<String>,
    val includes: Indexed<String>,
    val compilerOpts: Indexed<String>,
    val kotlinOpts: Indexed<String>
)

// Extension to convert List to Indexed
fun <T> List<T>.toIndexed(): Indexed<T> = this.size j { i: Int -> this[i] }

// Extension to convert Indexed to List
fun <T> Indexed<T>.toList(): List<T> = (0 until this.a).map { i -> this.b(i) }

// Sandboxed script runner with security manager
class SandboxedScriptRunner {
    private val securityManager = ScriptSecurityManager()
    
    fun runInSandbox(scriptFile: File, args: Array<String> = emptyArray()): Result<Int> {
        val originalSecurityManager = System.getSecurityManager()
        return try {
            System.setSecurityManager(securityManager)
            runBlocking {
                MavenScriptLauncher().runScript(scriptFile, args)
            }
        } finally {
            System.setSecurityManager(originalSecurityManager)
        }
    }
}

// Custom security manager for script sandboxing
class ScriptSecurityManager : SecurityManager() {
    private val allowedPaths = setOf(
        System.getProperty("java.io.tmpdir"),
        System.getProperty("user.home") + "/.k2script",
        System.getProperty("user.dir")
    )
    
    override fun checkRead(file: String?) {
        file?.let {
            if (!allowedPaths.any { allowed -> file.startsWith(allowed) }) {
                throw SecurityException("Read access denied: $file")
            }
        }
    }
    
    override fun checkWrite(file: String?) {
        file?.let {
            if (!allowedPaths.any { allowed -> file.startsWith(allowed) }) {
                throw SecurityException("Write access denied: $file")
            }
        }
    }
    
    override fun checkExec(cmd: String?) {
        // Allow only specific commands
        val allowedCommands = setOf("mvn", "java", "kotlin", "kotlinc")
        cmd?.let {
            val command = it.split(" ").firstOrNull()?.split("/")?.lastOrNull()
            if (command !in allowedCommands) {
                throw SecurityException("Execution denied: $cmd")
            }
        }
    }
}

// Main entry point
suspend fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: k2script <script.kts> [script args...]")
        exitProcess(1)
    }
    
    val scriptFile = File(args[0])
    if (!scriptFile.exists()) {
        println("Script file not found: ${args[0]}")
        exitProcess(1)
    }
    
    val scriptArgs = args.drop(1).toTypedArray()
    
    println("Running script: ${scriptFile.name}")
    
    val launcher = MavenScriptLauncher()
    val result = launcher.runScript(scriptFile, scriptArgs)
    
    result.fold(
        onSuccess = { exitCode ->
            exitProcess(exitCode)
        },
        onFailure = { error ->
            System.err.println("Script execution failed: ${error.message}")
            error.printStackTrace()
            exitProcess(1)
        }
    )
}