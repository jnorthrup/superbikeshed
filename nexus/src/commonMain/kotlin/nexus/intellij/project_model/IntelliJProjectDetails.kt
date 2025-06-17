package nexus.intellij.project_model

import kotlinx.serialization.Serializable

/**
 * Represents the type of build system used in a project or module.
 */
@Serializable
enum class BuildSystemType {
    GRADLE,
    MAVEN,
    INTELLIJ_NATIVE, // For projects built purely with IntelliJ's own build system
    UNKNOWN
}

/**
 * Holds information about the build system.
 *
 * @property type The type of the build system (e.g., GRADLE, MAVEN).
 * @property buildFilePath The absolute path to the primary build file (e.g., build.gradle, pom.xml). Null if not applicable or not found.
 */
@Serializable
data class BuildSystemInfo(
    val type: BuildSystemType,
    val buildFilePath: String? = null
)

/**
 * Represents the type of a dependency.
 */
@Serializable
enum class DependencyType {
    MODULE, // Dependency on another module within the same project
    LIBRARY // Dependency on an external library (e.g., from Maven Central, local .jar)
}

/**
 * Contains details about a specific dependency for a module.
 *
 * @property name The name of the dependency (e.g., "org.jetbrains.kotlin:kotlin-stdlib", "my-other-module").
 * @property version The version of the dependency. Null if not applicable (e.g., for module dependencies or unversioned libraries).
 * @property scope The scope of the dependency (e.g., "COMPILE", "TEST", "RUNTIME", "PROVIDED"). Null if not specified.
 * @property type The type of the dependency (MODULE or LIBRARY).
 * @property libraryPath For library dependencies, the path to the JAR/library files if available. Null otherwise.
 */
@Serializable
data class DependencyInfo(
    val name: String,
    val version: String? = null,
    val scope: String? = null,
    val type: DependencyType,
    val libraryPath: String? = null // Path to JARs or library root
)

/**
 * Describes a module within an IntelliJ project.
 *
 * @property moduleName The name of the module.
 * @property imlPath The absolute path to the module's .iml file.
 * @property sourceDirs List of absolute paths to source directories.
 * @property resourceDirs List of absolute paths to resource directories.
 * @property testSourceDirs List of absolute paths to test source directories.
 * @property testResourceDirs List of absolute paths to test resource directories.
 * @property moduleSdkName The name of the SDK used by this module (e.g., "Kotlin SDK", "1.8"). Null if using project SDK or not specified.
 * @property moduleJdkVersion The JDK version used by this module. Null if using project JDK or not specified.
 * @property dependencies List of dependencies for this module.
 * @property buildSystemInfo Information about the build system specific to this module, if different from project level.
 *                         For example, a Gradle project might have submodules also managed by Gradle.
 */
@Serializable
data class ModuleDetails(
    val moduleName: String,
    val imlPath: String,
    val sourceDirs: List<String> = emptyList(),
    val resourceDirs: List<String> = emptyList(),
    val testSourceDirs: List<String> = emptyList(),
    val testResourceDirs: List<String> = emptyList(),
    val moduleSdkName: String? = null,
    val moduleJdkVersion: String? = null,
    val dependencies: List<DependencyInfo> = emptyList(),
    val buildSystemInfo: BuildSystemInfo? = null // Module-specific build info
)

/**
 * Top-level data class representing the extracted details of an IntelliJ project.
 *
 * @property projectName The name of the project.
 * @property projectRootPath The absolute root path of the project directory.
 * @property projectSdkName The name of the SDK used by the project (e.g., "11", "corretto-1.8"). Null if not specified.
 * @property projectJdkVersion The JDK version used by the project (e.g., "1.8.0_292", "11.0.12"). Null if not specified.
 * @property modules List of modules within this project.
 * @property buildSystemInfo Information about the primary build system used by the project.
 */
@Serializable
data class IntelliJProjectDetails(
    val projectName: String,
    val projectRootPath: String,
    val projectSdkName: String? = null,
    val projectJdkVersion: String? = null,
    val modules: List<ModuleDetails> = emptyList(),
    val buildSystemInfo: BuildSystemInfo? = null
)
