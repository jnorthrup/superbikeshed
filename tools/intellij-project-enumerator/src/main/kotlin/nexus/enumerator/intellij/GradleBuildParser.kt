package nexus.enumerator.intellij

import nexus.enumerator.intellij.project_model.DependencyInfo
import nexus.enumerator.intellij.project_model.DependencyType
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern

data class GradleProjectInfo(
    val group: String?,
    val version: String?,
    val dependencies: List<DependencyInfo>
)

/**
 * Basic Gradle build file parser using regular expressions.
 *
 * NOTE: This approach is inherently fragile and has significant limitations.
 * Gradle build scripts are code (Groovy or Kotlin DSL) and can have complex logic,
 * variable substitutions, conditional blocks, plugins applying dependencies, etc.
 * Regex cannot reliably parse all valid Gradle build scripts.
 * A proper solution would involve using the Gradle Tooling API, but that is
 * a much heavier dependency and is out of scope for a "basic" standalone parser.
 * This parser will only catch simple, common declarations.
 */
class GradleBuildParser {

    // Regex for group and version (simplified)
    // e.g., group = "com.example" or group 'com.example'
    internal val groupRegex = Pattern.compile("""^\s*group\s*[=:]?\s*['"]([^'"]+)['"]""", Pattern.MULTILINE)
    internal val versionRegex = Pattern.compile("""^\s*version\s*[=:]?\s*['"]([^'"]+)['"]""", Pattern.MULTILINE)

    // Regex for dependencies (simplified, matches common forms)
    // Example: implementation "group:name:version"
    // Example: testImplementation 'group:name:version'
    // Example: api project(':other-module') - (module dependencies are harder with regex, focusing on GAV)
    internal val depRegexString = Pattern.compile(
        """^\s*(api|implementation|compileOnly|runtimeOnly|testImplementation)\s+['"]([^:'"]+):([^:'"]+):([^:'"]+)['"]""",
        Pattern.MULTILINE
    )
    // Example: implementation(group: 'group', name: 'name', version: 'version')
    internal val depRegexMap = Pattern.compile(
        """^\s*(api|implementation|compileOnly|runtimeOnly|testImplementation)\s*\(\s*group:\s*['"]([^'"]+)['"],\s*name:\s*['"]([^'"]+)['"],\s*version:\s*['"]([^'"]+)['"]\s*\)""",
        Pattern.MULTILINE
    )
    // Example: implementation(platform("group:name:version")) - BOMs / platforms
    internal val depRegexPlatform = Pattern.compile(
        """^\s*(api|implementation|compileOnly|runtimeOnly|testImplementation)\s*\(platform\s*\(['"]([^:'"]+):([^:'"]+):([^:'"]+)['"]\s*\)\s*\)""",
        Pattern.MULTILINE
    )


    fun parse(buildFile: Path): GradleProjectInfo {
        val content = try {
            Files.readString(buildFile)
        } catch (e: Exception) {
            System.err.println("Error reading Gradle build file $buildFile: ${e.message}")
            return GradleProjectInfo(null, null, emptyList())
        }

        val group = extractValue(content, groupRegex)
        val version = extractValue(content, versionRegex)
        val dependencies = mutableListOf<DependencyInfo>()

        // Match string dependencies "group:name:version"
        var matcher = depRegexString.matcher(content)
        while (matcher.find()) {
            val config = matcher.group(1) // e.g., implementation
            val depGroup = matcher.group(2)
            val depName = matcher.group(3)
            val depVersion = matcher.group(4)
            dependencies.add(
                DependencyInfo(
                    name = "$depGroup:$depName",
                    version = depVersion,
                    scope = configToScope(config),
                    type = DependencyType.LIBRARY
                )
            )
        }

        // Match map dependencies group: 'group', name: 'name', version: 'version'
        matcher = depRegexMap.matcher(content)
        while (matcher.find()) {
            val config = matcher.group(1)
            val depGroup = matcher.group(2)
            val depName = matcher.group(3)
            val depVersion = matcher.group(4)
            dependencies.add(
                DependencyInfo(
                    name = "$depGroup:$depName",
                    version = depVersion,
                    scope = configToScope(config),
                    type = DependencyType.LIBRARY
                )
            )
        }

        // Match platform dependencies platform("group:name:version")
        matcher = depRegexPlatform.matcher(content)
        while (matcher.find()) {
            // val config = matcher.group(1) // Configuration like 'implementation'
            val bomGroup = matcher.group(2)
            val bomName = matcher.group(3)
            val bomVersion = matcher.group(4)
            // This is a BOM (Bill of Materials) or platform, not a direct dependency in the same way.
            // For simplicity, we can record it as a special type of library or note it.
            // Or, more accurately, BOMs influence versions of other dependencies but aren't direct deps themselves.
            // For now, let's add it as a library with a "platform" scope or similar note.
             dependencies.add(
                DependencyInfo(
                    name = "$bomGroup:$bomName", // This is the BOM artifact itself
                    version = bomVersion,
                    scope = "platform", // Custom scope to denote it's a BOM/platform
                    type = DependencyType.LIBRARY
                )
            )
        }


        return GradleProjectInfo(group, version, dependencies.distinct())
    }

    internal fun extractValue(content: String, pattern: Pattern): String? {
        val matcher = pattern.matcher(content)
        return if (matcher.find()) matcher.group(1) else null
    }

    internal fun configToScope(config: String): String {
        // Simplified mapping, Gradle configurations can be custom.
        return when (config) {
            "api", "implementation" -> "COMPILE" // COMPILE is a common Maven scope, Gradle's api/implementation are similar for library consumers
            "compileOnly" -> "PROVIDED" // Similar to Maven's provided
            "runtimeOnly" -> "RUNTIME"
            "testImplementation" -> "TEST"
            else -> config.uppercase() // Best effort
        }
    }
}
