#!/usr/bin/env kotlin

// Direct test of SurrogateMavenLauncher concept without full k2script build
// This demonstrates the surrogate Maven project approach

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

data class ScriptAnnotations(
    val dependencies: List<String>,
    val repositories: List<String>,
    val compilerPlugins: Set<String>
)

fun parseScriptAnnotations(scriptFile: File): ScriptAnnotations {
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

fun generatePomXml(
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

fun prepareScriptContent(
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
    
    // Already has main function and runBlocking
    return cleanedLines.joinToString("\n")
}

fun main(args: Array<String>) {
    println("=== Testing Surrogate Maven Launcher Concept ===")
    
    val scriptFile = File("../nexus/nvidia-tasker.main.kts")
    if (!scriptFile.exists()) {
        println("Script not found: ${scriptFile.absolutePath}")
        return
    }
    
    val k2scriptHome = Paths.get(System.getProperty("user.home"), ".k2script")
    val surrogatesDir = k2scriptHome.resolve("surrogates")
    val mavenRepo = k2scriptHome.resolve("m2-cache")
    
    Files.createDirectories(surrogatesDir)
    Files.createDirectories(mavenRepo)
    
    val scriptHash = scriptFile.readText().hashCode().toString(16)
    val surrogateName = "${scriptFile.nameWithoutExtension}-$scriptHash"
    val surrogateDir = surrogatesDir.resolve(surrogateName).toFile()
    
    println("\n1. Parsing script annotations...")
    val annotations = parseScriptAnnotations(scriptFile)
    println("   Dependencies: ${annotations.dependencies}")
    println("   Compiler plugins: ${annotations.compilerPlugins}")
    
    println("\n2. Creating surrogate Maven project at: ${surrogateDir}")
    surrogateDir.mkdirs()
    
    // Generate pom.xml
    val pomContent = generatePomXml(scriptFile.nameWithoutExtension, annotations)
    File(surrogateDir, "pom.xml").writeText(pomContent)
    println("   Generated pom.xml")
    
    // Prepare script
    val srcDir = File(surrogateDir, "src/main/kotlin")
    srcDir.mkdirs()
    
    val preparedScript = prepareScriptContent(scriptFile, annotations)
    File(srcDir, "${scriptFile.nameWithoutExtension}.kt").writeText(preparedScript)
    println("   Prepared script as ${scriptFile.nameWithoutExtension}.kt")
    
    // Compile with Maven
    println("\n3. Compiling with Maven...")
    val compileProcess = ProcessBuilder(
        "/Users/jim/.sdkman/candidates/maven/current/bin/mvn", "-q",
        "-f", surrogateDir.absolutePath,
        "-Dmaven.repo.local=${mavenRepo.toAbsolutePath()}",
        "compile"
    ).apply {
        inheritIO()
    }.start()
    
    if (compileProcess.waitFor() != 0) {
        println("Compilation failed!")
        return
    }
    
    println("\n4. Running script...")
    val runProcess = ProcessBuilder(
        "/Users/jim/.sdkman/candidates/maven/current/bin/mvn", "-q",
        "-f", surrogateDir.absolutePath,
        "-Dmaven.repo.local=${mavenRepo.toAbsolutePath()}",
        "exec:java",
        "-Dexec.mainClass=${scriptFile.nameWithoutExtension}Kt",
        "-Dexec.args=test query: what is 2+2?"
    ).apply {
        inheritIO()
        environment()["MAVEN_OPTS"] = "-Dfile.encoding=UTF-8"
    }.start()
    
    val exitCode = runProcess.waitFor()
    println("\nScript exited with code: $exitCode")
}