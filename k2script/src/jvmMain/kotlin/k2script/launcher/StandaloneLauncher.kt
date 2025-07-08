@file:JvmName("K2Script")
package k2script.launcher

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Standalone K2Script launcher with surrogate Maven project approach
 * This version has no TrikeShed dependencies
 */
class StandaloneSurrogateMavenLauncher {
    
    private val k2scriptHome = Paths.get(System.getProperty("user.home"), ".k2script")
    private val surrogatesDir = k2scriptHome.resolve("surrogates")
    private val mavenRepo = k2scriptHome.resolve("m2-cache")
    
    init {
        Files.createDirectories(surrogatesDir)
        Files.createDirectories(mavenRepo)
    }
    
    fun runScript(
        scriptFile: File, 
        args: Array<String> = emptyArray()
    ): Int {
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
        
        return process.waitFor()
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
                <kotlin.version>1.9.22</kotlin.version>
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
        
        val cleanedContent = cleanedLines.joinToString("\n")
        
        // Check if script has a suspend main function with top-level runBlocking
        if (cleanedContent.contains("suspend fun main") && cleanedContent.contains("runBlocking")) {
            // Find and replace the top-level runBlocking pattern
            val runBlockingPattern = Regex("""runBlocking\s*\{\s*main\s*\(\s*args\s*\)\s*\}""", RegexOption.MULTILINE)
            if (runBlockingPattern.containsMatchIn(cleanedContent)) {
                // Transform suspend main to regular main with runBlocking inside
                var transformed = cleanedContent.replace(runBlockingPattern, "// Removed top-level runBlocking")
                
                // Find the suspend main function and wrap its body with runBlocking
                val suspendMainPattern = Regex("""suspend\s+fun\s+main\s*\(\s*args:\s*Array<String>\s*\)\s*\{""")
                val match = suspendMainPattern.find(transformed)
                if (match != null) {
                    // Find the matching closing brace
                    var braceCount = 1
                    var pos = match.range.last + 1
                    val chars = transformed.toCharArray()
                    
                    while (pos < chars.size && braceCount > 0) {
                        when (chars[pos]) {
                            '{' -> braceCount++
                            '}' -> braceCount--
                        }
                        pos++
                    }
                    
                    if (braceCount == 0) {
                        // Insert runBlocking at the start of the function body
                        val bodyStart = match.range.last + 1
                        val bodyEnd = pos - 1
                        
                        transformed = transformed.substring(0, bodyStart) +
                            "\n    runBlocking {\n        " +
                            transformed.substring(bodyStart, bodyEnd).replace("\n", "\n        ") +
                            "\n    }\n" +
                            transformed.substring(bodyEnd)
                    }
                    
                    // Replace suspend fun with regular fun
                    transformed = transformed.replace("suspend fun main", "fun main")
                }
                
                return transformed.trim()
            }
        }
        
        return cleanedContent
    }
    
    data class ScriptAnnotations(
        val dependencies: List<String>,
        val repositories: List<String>,
        val compilerPlugins: Set<String>
    )
}

// Direct k2script runner entry point
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("K2Script - Kotlin Script Runner")
        println("Usage: k2script <script.kts> [args...]")
        println()
        println("Features:")
        println("  - Automatic dependency resolution via @file:DependsOn")
        println("  - Compiler plugin auto-detection (e.g., serialization)")
        println("  - Script caching for fast re-runs")
        println("  - Maven-based execution for reliability")
        return
    }
    
    val scriptFile = File(args[0])
    if (!scriptFile.exists()) {
        System.err.println("Error: Script not found: ${args[0]}")
        System.exit(1)
    }
    
    val scriptArgs = args.drop(1).toTypedArray()
    
    try {
        val exitCode = StandaloneSurrogateMavenLauncher().runScript(scriptFile, scriptArgs)
        System.exit(exitCode)
    } catch (e: Exception) {
        System.err.println("Script execution failed: ${e.message}")
        e.printStackTrace()
        System.exit(1)
    }
}