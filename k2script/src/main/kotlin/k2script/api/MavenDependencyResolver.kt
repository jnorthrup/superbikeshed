package k2script.api

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
    fun resolveDependencies(coordinates: List<String>): List<Path> {
        val mavenHome = System.getProperty("user.home") + "/.m2"
        val repositoryPath = Paths.get(mavenHome, "repository")
        
        return coordinates.mapNotNull { coordinate ->
            resolveCoordinate(coordinate, repositoryPath)
        }
    }
    
    /**
     * Resolves a single Maven coordinate to a jar file path
     * @param coordinate Maven coordinate (groupId:artifactId:version)
     * @param repositoryPath Path to Maven repository
     * @return Path to the jar file, or null if not found
     */
    private fun resolveCoordinate(coordinate: String, repositoryPath: Path): Path? {
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
    fun buildClasspath(dependencies: List<Path>): String {
        return dependencies.joinToString(File.pathSeparator)
    }
    
    /**
     * Fetches dependencies using Coursier (if available)
     * @param coordinates List of Maven coordinates
     * @return List of resolved jar file paths
     */
    fun fetchWithCoursier(coordinates: List<String>): List<Path> {
        // TODO: Implement Coursier integration
        // This would use the coursier CLI to fetch dependencies
        return emptyList()
    }
    
    /**
     * Fetches dependencies using Maven CLI (if available)
     * @param coordinates List of Maven coordinates
     * @return List of resolved jar file paths
     */
    fun fetchWithMaven(coordinates: List<String>): List<Path> {
        // TODO: Implement Maven CLI integration
        // This would use mvn dependency:get to fetch dependencies
        return emptyList()
    }
    
    /**
     * Creates a sandboxed classpath with only the specified dependencies
     * @param coordinates List of Maven coordinates
     * @return Sandboxed classpath string
     */
    fun createSandboxedClasspath(coordinates: List<String>): String {
        val dependencies = resolveDependencies(coordinates)
        return buildClasspath(dependencies)
    }
    
    /**
     * Validates that all dependencies are available in the local Maven repository
     * @param coordinates List of Maven coordinates
     * @return List of missing dependencies
     */
    fun validateDependencies(coordinates: List<String>): List<String> {
        val mavenHome = System.getProperty("user.home") + "/.m2"
        val repositoryPath = Paths.get(mavenHome, "repository")
        
        return coordinates.filter { coordinate ->
            val parts = coordinate.split(":")
            if (parts.size != 3) return@filter true
            
            val (groupId, artifactId, version) = parts
            val groupPath = groupId.replace(".", "/")
            val jarName = "$artifactId-$version.jar"
            
            val jarPath = repositoryPath.resolve(groupPath).resolve(artifactId).resolve(version).resolve(jarName)
            !jarPath.toFile().exists()
        }
    }
} 