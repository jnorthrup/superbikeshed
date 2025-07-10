@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package k2script.api

import borg.trikeshed.lib.*
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.NodeList

/**
 * Maven POM-based dependency resolver that builds the full dependency graph
 * including transitive dependencies, just like Maven does
 */
object PomDependencyResolver {
    
    data class Dependency(
        val groupId: String,
        val artifactId: String,
        val version: String,
        val scope: String = "compile",
        val optional: Boolean = false,
        val exclusions: Indexed<Exclusion> = emptyList<Exclusion>().toIdx()
    ) {
        fun toCoordinate() = "$groupId:$artifactId:$version"
        
        fun toPath(repoPath: Path): Path {
            val groupPath = groupId.replace(".", "/")
            return repoPath.resolve(groupPath)
                .resolve(artifactId)
                .resolve(version)
        }
    }
    
    data class Exclusion(
        val groupId: String,
        val artifactId: String
    )
    
    /**
     * Resolves the full dependency graph including transitive dependencies
     * Returns a Join of direct dependencies to their transitive closure
     */
    fun resolveEffectiveGraph(coordinates: Indexed<String>): Join<Dependency, Indexed<Dependency>> {
        val mavenHome = System.getProperty("user.home") + "/.m2"
        val repositoryPath = Paths.get(mavenHome, "repository")
        
        val visited = mutableSetOf<String>()
        val graph = mutableMapOf<Dependency, Indexed<Dependency>>()
        
        // Parse each coordinate and resolve its dependencies
        for (i in 0 until coordinates.size) {
            val coord = coordinates[i]
            val dep = parseCoordinate(coord)
            if (dep != null) {
                val transitives = resolveTransitiveDependencies(dep, repositoryPath, visited)
                graph[dep] = transitives
            }
        }
        
        // Convert to TrikeShed Join
        return if (graph.isEmpty()) {
            val emptyDep = Dependency("", "", "")
            emptyDep j emptyList<Dependency>().toIdx()
        } else {
            val entry = graph.entries.first()
            entry.key j entry.value
        }
    }
    
    /**
     * Resolves all dependencies (direct + transitive) into a flat list of JARs
     */
    fun resolveClasspath(coordinates: Indexed<String>): Indexed<Path> {
        val mavenHome = System.getProperty("user.home") + "/.m2"
        val repositoryPath = Paths.get(mavenHome, "repository")
        
        val visited = mutableSetOf<String>()
        val allJars = mutableSetOf<Path>()
        
        for (i in 0 until coordinates.size) {
            val coord = coordinates[i]
            val dep = parseCoordinate(coord)
            if (dep != null) {
                collectJars(dep, repositoryPath, visited, allJars)
            }
        }
        
        return allJars.toList().toIdx()
    }
    
    internal fun resolveTransitiveDependencies(
        dependency: Dependency,
        repoPath: Path,
        visited: MutableSet<String>
    ): Indexed<Dependency> {
        val coord = dependency.toCoordinate()
        if (coord in visited) {
            return emptyList<Dependency>().toIdx()
        }
        visited.add(coord)
        
        val pomPath = dependency.toPath(repoPath).resolve("${dependency.artifactId}-${dependency.version}.pom")
        if (!pomPath.toFile().exists()) {
            return emptyList<Dependency>().toIdx()
        }
        
        val dependencies = parsePom(pomPath.toFile())
        val transitive = mutableListOf<Dependency>()
        
        for (i in 0 until dependencies.size) {
            val dep = dependencies[i]
            if (!isExcluded(dep, dependency.exclusions) && dep.scope == "compile") {
                transitive.add(dep)
                // Recursively resolve transitive dependencies
                val subDeps = resolveTransitiveDependencies(dep, repoPath, visited)
                for (j in 0 until subDeps.size) {
                    transitive.add(subDeps[j])
                }
            }
        }
        
        return transitive.toIdx()
    }
    
    internal fun collectJars(
        dependency: Dependency,
        repoPath: Path,
        visited: MutableSet<String>,
        jars: MutableSet<Path>
    ) {
        val coord = dependency.toCoordinate()
        if (coord in visited) return
        visited.add(coord)
        
        // Add the JAR for this dependency
        val jarPath = dependency.toPath(repoPath)
            .resolve("${dependency.artifactId}-${dependency.version}.jar")
        
        if (jarPath.toFile().exists()) {
            jars.add(jarPath)
        }
        
        // Parse POM and collect transitive dependencies
        val pomPath = dependency.toPath(repoPath)
            .resolve("${dependency.artifactId}-${dependency.version}.pom")
        
        if (pomPath.toFile().exists()) {
            val dependencies = parsePom(pomPath.toFile())
            for (i in 0 until dependencies.size) {
                val dep = dependencies[i]
                if (!isExcluded(dep, dependency.exclusions) && dep.scope == "compile") {
                    collectJars(dep, repoPath, visited, jars)
                }
            }
        }
    }
    
    internal fun parsePom(pomFile: File): Indexed<Dependency> {
        val dependencies = mutableListOf<Dependency>()
        
        try {
            val dbFactory = DocumentBuilderFactory.newInstance()
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(pomFile)
            doc.documentElement.normalize()
            
            // Get parent version if needed
            val parentVersion = doc.getElementsByTagName("parent").item(0)?.let { parent ->
                (parent as Element).getElementsByTagName("version").item(0)?.textContent
            }
            
            // Get project version
            val projectVersion = doc.documentElement.getElementsByTagName("version").item(0)?.textContent
                ?: parentVersion
            
            // Parse dependencies
            val depNodes = doc.getElementsByTagName("dependency")
            for (i in 0 until depNodes.length) {
                val dep = depNodes.item(i) as Element
                
                val groupId = dep.getElementsByTagName("groupId").item(0)?.textContent ?: continue
                val artifactId = dep.getElementsByTagName("artifactId").item(0)?.textContent ?: continue
                var version = dep.getElementsByTagName("version").item(0)?.textContent
                
                // Handle property placeholders like ${project.version}
                if (version?.startsWith("\${") == true) {
                    version = when (version) {
                        "\${project.version}" -> projectVersion
                        "\${parent.version}" -> parentVersion
                        else -> resolveProperty(version, doc)
                    }
                }
                
                if (version == null) continue
                
                val scope = dep.getElementsByTagName("scope").item(0)?.textContent ?: "compile"
                val optional = dep.getElementsByTagName("optional").item(0)?.textContent == "true"
                
                // Parse exclusions
                val exclusions = parseExclusions(dep)
                
                dependencies.add(Dependency(groupId, artifactId, version, scope, optional, exclusions))
            }
        } catch (e: Exception) {
            // Log error but continue
            System.err.println("Error parsing POM: ${pomFile.absolutePath}: ${e.message}")
        }
        
        return dependencies.toIdx()
    }
    
    internal fun parseExclusions(depElement: Element): Indexed<Exclusion> {
        val exclusions = mutableListOf<Exclusion>()
        val exclusionNodes = depElement.getElementsByTagName("exclusion")
        
        for (i in 0 until exclusionNodes.length) {
            val exc = exclusionNodes.item(i) as Element
            val groupId = exc.getElementsByTagName("groupId").item(0)?.textContent ?: "*"
            val artifactId = exc.getElementsByTagName("artifactId").item(0)?.textContent ?: "*"
            exclusions.add(Exclusion(groupId, artifactId))
        }
        
        return exclusions.toIdx()
    }
    
    internal fun resolveProperty(property: String, doc: org.w3c.dom.Document): String? {
        if (!property.startsWith("\${") || !property.endsWith("}")) {
            return property
        }
        
        val propName = property.substring(2, property.length - 1)
        
        // Look in properties section
        val props = doc.getElementsByTagName("properties").item(0) as? Element
        if (props != null) {
            val propValue = props.getElementsByTagName(propName).item(0)?.textContent
            if (propValue != null) return propValue
        }
        
        return null
    }
    
    internal fun isExcluded(dependency: Dependency, exclusions: Indexed<Exclusion>): Boolean {
        for (i in 0 until exclusions.size) {
            val exc = exclusions[i]
            val groupMatch = exc.groupId == "*" || exc.groupId == dependency.groupId
            val artifactMatch = exc.artifactId == "*" || exc.artifactId == dependency.artifactId
            if (groupMatch && artifactMatch) return true
        }
        return false
    }
    
    internal fun parseCoordinate(coordinate: String): Dependency? {
        val parts = coordinate.split(":")
        return when (parts.size) {
            3 -> Dependency(parts[0], parts[1], parts[2])
            4 -> Dependency(parts[0], parts[1], parts[3], scope = parts[2])
            else -> null
        }
    }
}