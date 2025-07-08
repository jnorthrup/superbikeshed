package k2script.launcher

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.concurrent.thread

/**
 * Surrogate Maven Project Launcher for K2Script
 * 
 * Creates a temporary Maven project that:
 * 1. Handles all dependencies via Maven
 * 2. Configures compiler plugins (serialization, etc)
 * 3. Compiles and runs the script
 * 4. Caches results for fast re-runs
 */
class SurrogateMavenLauncher {
    
    private val k2scriptHome = Paths.get(System.getProperty("user.home"), ".k2script")
    private val surrogatesDir = k2scriptHome.resolve("surrogates")
    private val mavenRepo = k2scriptHome.resolve("m2-cache")
    
    init {
        Files.createDirectories(surrogatesDir)
        Files.createDirectories(mavenRepo)
    }
    
    suspend fun runScript(
        scriptFile: File, 
        args: Array<String> = emptyArray()
    ): Int = withContext(Dispatchers.IO) {
        val scriptHash = scriptFile.readText().hashCode().toString(16)
        val surrogateName = "${scriptFile.nameWithoutExtension}-$scriptHash"
        val surrogateDir = surrogatesDir.resolve(surrogateName)
        
        // Check if we need to rebuild
        val needsRebuild = !Files.exists(surrogateDir.resolve("target/classes"))
        
        if (needsRebuild) {
            createSurrogateProject(scriptFile, surrogateDir.toFile())
        }
        
        // Run via Maven exec plugin
        val mvnPath = System.getenv("MAVEN_HOME")?.let { "$it/bin/mvn" } 
            ?: "/Users/jim/.sdkman/candidates/maven/current/bin/mvn"
        val process = ProcessBuilder(
            mvnPath, "-q",
            "-f", surrogateDir.toString(),
            "-Dmaven.repo.local=${mavenRepo.toAbsolutePath()}",
            "exec:java",
            "-Dexec.mainClass=${scriptFile.nameWithoutExtension}Kt",
            "-Dexec.args=${args.joinToString(" ")}"
        ).apply {
            inheritIO()
            environment()["MAVEN_OPTS"] = "-Dfile.encoding=UTF-8"
        }.start()
        
        process.waitFor()
    }
    
    private fun createSurrogateProject(scriptFile: File, projectDir: File) {
        projectDir.mkdirs()
        
        // Parse script annotations
        val annotations = parseScriptAnnotations(scriptFile)
        
        // Generate pom.xml
        val pomContent = generatePomXml(
            scriptFile.nameWithoutExtension,
            annotations
        )
        File(projectDir, "pom.xml").writeText(pomContent)
        
        // Prepare script
        val srcDir = File(projectDir, "src/main/kotlin")
        srcDir.mkdirs()
        
        val preparedScript = prepareScriptContent(scriptFile, annotations)
        File(srcDir, "${scriptFile.nameWithoutExtension}.kt").writeText(preparedScript)
        
        // Compile with Maven
        val mvnPath = System.getenv("MAVEN_HOME")?.let { "$it/bin/mvn" } 
            ?: "/Users/jim/.sdkman/candidates/maven/current/bin/mvn"
        val compileProcess = ProcessBuilder(
            mvnPath, "-q",
            "-f", projectDir.absolutePath,
            "-Dmaven.repo.local=${mavenRepo.toAbsolutePath()}",
            "compile"
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
    
    private fun generatePomXml(
        artifactId: String,
        annotations: ScriptAnnotations
    ): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                 http://maven.apache.org/xsd/maven-4.0.0.xsd">
            <modelVersion>4.0.0</modelVersion>
            
            <groupId>k2script.surrogate</groupId>
            <artifactId>$artifactId</artifactId>
            <version>1.0-SNAPSHOT</version>
            
            <properties>
                <kotlin.version>2.0.0</kotlin.version>
                <serialization.version>1.6.0</serialization.version>
                <maven.compiler.source>17</maven.compiler.source>
                <maven.compiler.target>17</maven.compiler.target>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            </properties>
            
            <repositories>
                <repository>
                    <id>mavenCentral</id>
                    <url>https://repo1.maven.org/maven2/</url>
                </repository>
                ${annotations.repositories.joinToString("\n") { repo ->
                    """
                    <repository>
                        <id>${repo.hashCode()}</id>
                        <url>$repo</url>
                    </repository>
                    """
                }}
            </repositories>
            
            <dependencies>
                <dependency>
                    <groupId>org.jetbrains.kotlin</groupId>
                    <artifactId>kotlin-stdlib</artifactId>
                    <version>${'$'}{kotlin.version}</version>
                </dependency>
                ${annotations.dependencies.joinToString("\n") { dep ->
                    val parts = dep.split(":")
                    if (parts.size >= 3) """
                    <dependency>
                        <groupId>${parts[0]}</groupId>
                        <artifactId>${parts[1]}</artifactId>
                        <version>${parts[2]}</version>
                    </dependency>
                    """ else ""
                }}
            </dependencies>
            
            <build>
                <sourceDirectory>src/main/kotlin</sourceDirectory>
                <plugins>
                    <plugin>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-plugin</artifactId>
                        <version>${'$'}{kotlin.version}</version>
                        <executions>
                            <execution>
                                <id>compile</id>
                                <phase>compile</phase>
                                <goals>
                                    <goal>compile</goal>
                                </goals>
                            </execution>
                        </executions>
                        <configuration>
                            <jvmTarget>17</jvmTarget>
                            ${if (annotations.compilerPlugins.contains("serialization")) """
                            <compilerPlugins>
                                <plugin>kotlinx-serialization</plugin>
                            </compilerPlugins>
                            """ else ""}
                        </configuration>
                        ${if (annotations.compilerPlugins.contains("serialization")) """
                        <dependencies>
                            <dependency>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-maven-serialization</artifactId>
                                <version>${'$'}{kotlin.version}</version>
                            </dependency>
                        </dependencies>
                        """ else ""}
                    </plugin>
                    <plugin>
                        <groupId>org.codehaus.mojo</groupId>
                        <artifactId>exec-maven-plugin</artifactId>
                        <version>3.1.0</version>
                    </plugin>
                </plugins>
            </build>
        </project>
    """.trimIndent()
    
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
        
        // Wrap in main function if needed
        val hasMain = cleanedLines.any { it.contains("fun main") }
        
        return if (hasMain) {
            cleanedLines.joinToString("\n")
        } else {
            """
            ${cleanedLines.joinToString("\n")}
            
            fun main(args: Array<String>) {
                ${if (content.contains("runBlocking")) "" else "runBlocking {"}
                    // Script content will execute here
                ${if (content.contains("runBlocking")) "" else "}"}
            }
            """.trimIndent()
        }
    }
    
    data class ScriptAnnotations(
        val dependencies: List<String>,
        val repositories: List<String>,
        val compilerPlugins: Set<String>
    )
}

// Direct k2script runner using surrogate approach
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
            val exitCode = SurrogateMavenLauncher().runScript(scriptFile, scriptArgs)
            System.exit(exitCode)
        } catch (e: Exception) {
            System.err.println("Script execution failed: ${e.message}")
            e.printStackTrace()
            System.exit(1)
        }
    }
}