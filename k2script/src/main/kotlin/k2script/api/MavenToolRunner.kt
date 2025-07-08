@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package k2script.api

import borg.trikeshed.lib.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Uses Maven command-line tools to resolve dependencies
 * "Fake it till we make it" approach - let Maven do the heavy lifting
 */
object MavenToolRunner {
    
    /**
     * Uses mvn dependency:get to fetch dependencies and their transitives
     * Returns the classpath of all resolved JARs
     */
    fun fetchDependencies(coordinates: Indexed<String>): Indexed<Path> {
        val mavenHome = System.getProperty("user.home") + "/.m2"
        val repositoryPath = Paths.get(mavenHome, "repository")
        
        // Check if Maven is available
        val mvnCmd = findMavenCommand()
        if (mvnCmd == null) {
            println("Maven not found, falling back to local repository")
            return resolveFromLocalRepo(coordinates, repositoryPath)
        }
        
        val resolvedPaths = mutableListOf<Path>()
        
        // Use Maven to fetch each dependency
        for (i in 0 until coordinates.size) {
            val coord = coordinates[i]
            println("Fetching: $coord")
            
            val parts = coord.split(":")
            if (parts.size >= 3) {
                val groupId = parts[0]
                val artifactId = parts[1]
                val version = parts[2]
                
                // Run mvn dependency:get
                val process = ProcessBuilder(
                    mvnCmd,
                    "dependency:get",
                    "-DgroupId=$groupId",
                    "-DartifactId=$artifactId",
                    "-Dversion=$version",
                    "-DremoteRepositories=https://repo.maven.apache.org/maven2",
                    "-Dtransitive=true"
                ).start()
                
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    println("✓ Downloaded: $coord")
                } else {
                    println("✗ Failed to download: $coord")
                }
            }
        }
        
        // Now use mvn dependency:build-classpath to get the full classpath
        return buildClasspathWithMaven(coordinates, repositoryPath)
    }
    
    /**
     * Uses mvn dependency:build-classpath to resolve the full classpath
     */
    fun buildClasspathWithMaven(coordinates: Indexed<String>, repoPath: Path): Indexed<Path> {
        // Create a temporary POM file
        val tempDir = Files.createTempDirectory("k2script-deps")
        val pomFile = File(tempDir.toFile(), "pom.xml")
        
        pomFile.writeText(generatePom(coordinates))
        
        val mvnCmd = findMavenCommand() ?: return resolveFromLocalRepo(coordinates, repoPath)
        
        // Run mvn dependency:build-classpath
        val process = ProcessBuilder(
            mvnCmd,
            "dependency:build-classpath",
            "-DincludeScope=runtime",
            "-f", pomFile.absolutePath
        ).redirectErrorStream(true).start()
        
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        
        if (exitCode == 0) {
            // Parse the classpath from Maven output
            val classpathLine = output.lines().find { it.contains(".m2/repository") }
            if (classpathLine != null) {
                val paths = classpathLine.split(File.pathSeparator)
                    .map { Paths.get(it) }
                    .filter { it.toFile().exists() }
                
                // Clean up
                pomFile.delete()
                tempDir.toFile().delete()
                
                return paths.toIdx()
            }
        }
        
        // Fallback to local resolution
        return resolveFromLocalRepo(coordinates, repoPath)
    }
    
    /**
     * Generates a minimal POM file with the given dependencies
     */
    internal fun generatePom(coordinates: Indexed<String>): String {
        val deps = StringBuilder()
        
        for (i in 0 until coordinates.size) {
            val coord = coordinates[i]
            val parts = coord.split(":")
            if (parts.size >= 3) {
                deps.append("""
        <dependency>
            <groupId>${parts[0]}</groupId>
            <artifactId>${parts[1]}</artifactId>
            <version>${parts[2]}</version>
        </dependency>
                """.trimIndent())
                deps.append("\n")
            }
        }
        
        return """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <groupId>k2script.temp</groupId>
    <artifactId>temp-deps</artifactId>
    <version>1.0.0</version>
    
    <dependencies>
$deps    </dependencies>
</project>
"""
    }
    
    /**
     * Finds the Maven command (mvn or mvn.cmd on Windows)
     */
    internal fun findMavenCommand(): String? {
        val commands = listOf("mvn", "mvn.cmd", "mvn.bat")
        
        for (cmd in commands) {
            try {
                val process = ProcessBuilder(cmd, "--version").start()
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    return cmd
                }
            } catch (e: Exception) {
                // Command not found, try next
            }
        }
        
        // Check MAVEN_HOME
        val mavenHome = System.getenv("MAVEN_HOME") ?: System.getenv("M2_HOME")
        if (mavenHome != null) {
            val mvnPath = File(mavenHome, "bin/mvn")
            if (mvnPath.exists()) {
                return mvnPath.absolutePath
            }
        }
        
        return null
    }
    
    /**
     * Fallback: resolve from local repository without downloading
     */
    internal fun resolveFromLocalRepo(coordinates: Indexed<String>, repoPath: Path): Indexed<Path> {
        val paths = mutableListOf<Path>()
        
        for (i in 0 until coordinates.size) {
            val coord = coordinates[i]
            val parts = coord.split(":")
            if (parts.size >= 3) {
                val groupPath = parts[0].replace(".", "/")
                val artifactId = parts[1]
                val version = parts[2]
                
                val jarPath = repoPath
                    .resolve(groupPath)
                    .resolve(artifactId)
                    .resolve(version)
                    .resolve("$artifactId-$version.jar")
                
                if (jarPath.toFile().exists()) {
                    paths.add(jarPath)
                }
            }
        }
        
        return paths.toIdx()
    }
}