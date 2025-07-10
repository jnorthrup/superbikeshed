@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package k2script.api

import borg.trikeshed.lib.*
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Maven dependency resolution utilities for k2script
 * Provides functions to fetch dependencies and build classpaths using Maven coordinates
 */
object MavenDependencyResolver {
    
    /**
     * Resolves dependencies using Maven coordinates and returns the classpath
     * @param coordinates List of Maven coordinates (groupId:artifactId:version)
     * @return List of resolved jar file paths
     */
    fun resolveDependencies(coordinates: Indexed<String>): Indexed<Path> {
        val mavenHome = System.getProperty("user.home") + "/.m2"
        val repositoryPath = Paths.get(mavenHome, "repository")
        
        val resolved = mutableListOf<Path>()
        for (i in 0 until coordinates.size) {
            val coord = coordinates[i]
            val path = resolveCoordinate(coord, repositoryPath)
            if (path != null) resolved.add(path)
        }
        return resolved.toIdx()
    }
    
    /**
     * Resolves a single Maven coordinate to a jar file path
     * @param coordinate Maven coordinate (groupId:artifactId:version)
     * @param repositoryPath Path to Maven repository
     * @return Path to the jar file, or null if not found
     */
    internal fun resolveCoordinate(coordinate: String, repositoryPath: Path): Path? {
        val parts = coordinate.split(":")
        if (parts.size != 3) {
            throw IllegalArgumentException("Invalid Maven coordinate: $coordinate. Expected format: groupId:artifactId:version")
        }
        
        val (groupId, artifactId, version) = parts
        val groupPath = groupId.replace(".", "/")
        val jarName = "$artifactId-$version.jar"
        
        val jarPath = repositoryPath.resolve(groupPath).resolve(artifactId).resolve(version).resolve(jarName)
        
        return if (jarPath.toFile().exists()) jarPath else null
    }
    
    /**
     * Builds a classpath string from resolved dependencies
     * @param dependencies List of jar file paths
     * @return Classpath string (platform-specific separator)
     */
    fun buildClasspath(dependencies: Indexed<Path>): String {
        val paths = mutableListOf<String>()
        for (i in 0 until dependencies.size) {
            paths.add(dependencies[i].toString())
        }
        return paths.joinToString(File.pathSeparator)
    }
    
    /**
     * Fetches dependencies using Coursier (if available)
     * @param coordinates List of Maven coordinates
     * @return List of resolved jar file paths
     */
    fun fetchWithCoursier(coordinates: Indexed<String>): Indexed<Path> {
        // TODO: Implement Coursier integration
        // This would use the coursier CLI to fetch dependencies
        return emptyList<Path>().toIdx()
    }
    
    /**
     * Fetches dependencies using Maven CLI (if available)
     * @param coordinates List of Maven coordinates
     * @return List of resolved jar file paths
     */
    fun fetchWithMaven(coordinates: Indexed<String>): Indexed<Path> {
        // TODO: Implement Maven CLI integration
        // This would use mvn dependency:get to fetch dependencies
        return emptyList<Path>().toIdx()
    }
    
    /**
     * Creates a sandboxed classpath with only the specified dependencies
     * @param coordinates List of Maven coordinates
     * @return Sandboxed classpath string
     */
    fun createSandboxedClasspath(coordinates: Indexed<String>): String {
        val dependencies = resolveDependencies(coordinates)
        return buildClasspath(dependencies)
    }
    
    /**
     * Validates that all dependencies are available in the local Maven repository
     * @param coordinates List of Maven coordinates
     * @return List of missing dependencies
     */
    fun validateDependencies(coordinates: Indexed<String>): Indexed<String> {
        val mavenHome = System.getProperty("user.home") + "/.m2"
        val repositoryPath = Paths.get(mavenHome, "repository")
        
        val missing = mutableListOf<String>()
        for (i in 0 until coordinates.size) {
            val coordinate = coordinates[i]
            val parts = coordinate.split(":")
            if (parts.size != 3) {
                missing.add(coordinate)
                continue
            }
            
            val (groupId, artifactId, version) = parts
            val groupPath = groupId.replace(".", "/")
            val jarName = "$artifactId-$version.jar"
            
            val jarPath = repositoryPath.resolve(groupPath).resolve(artifactId).resolve(version).resolve(jarName)
            if (!jarPath.toFile().exists()) {
                missing.add(coordinate)
            }
        }
        return missing.toIdx()
    }
} 