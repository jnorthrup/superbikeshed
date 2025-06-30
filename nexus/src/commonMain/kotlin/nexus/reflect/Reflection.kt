package nexus.reflect

import borg.trikeshed.lib.*
import nexus.capabilities.*
import kotlinx.coroutines.flow.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

/**
 * Self-describing metadata and reflection system for Nexus tools
 * 
 * Design principles:
 * - Rich metadata at runtime
 * - Dynamic discovery and introspection
 * - Type-safe reflection
 * - Versioned interfaces
 */

// Tool metadata hierarchy
data class ToolMetadata(
    val id: String,
    val version: SemanticVersion,
    val capability: Capability,
    val interface: ToolInterface,
    val implementation: ToolImplementation,
    val documentation: Documentation,
    val examples: Indexed<Example>,
    val telemetry: TelemetryConfig? = null,
    val experimental: Boolean = false
) {
    fun isCompatible(requirement: VersionRequirement): Boolean =
        requirement.satisfiedBy(version)
}

data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
    val build: String? = null
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        return compareValuesBy(
            this, other,
            { it.major }, { it.minor }, { it.patch }
        )
    }
    
    override fun toString(): String = buildString {
        append("$major.$minor.$patch")
        preRelease?.let { append("-$it") }
        build?.let { append("+$it") }
    }
    
    companion object {
        fun parse(version: String): SemanticVersion {
            val pattern = Regex("""(\d+)\.(\d+)\.(\d+)(?:-([^+]+))?(?:\+(.+))?""")
            val match = pattern.matchEntire(version)
                ?: throw IllegalArgumentException("Invalid version format: $version")
            
            return SemanticVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toInt(),
                preRelease = match.groupValues[4].takeIf { it.isNotEmpty() },
                build = match.groupValues[5].takeIf { it.isNotEmpty() }
            )
        }
    }
}

sealed interface VersionRequirement {
    fun satisfiedBy(version: SemanticVersion): Boolean
    
    data class Exact(val version: SemanticVersion) : VersionRequirement {
        override fun satisfiedBy(version: SemanticVersion) = version == this.version
    }
    
    data class Range(
        val min: SemanticVersion? = null,
        val max: SemanticVersion? = null,
        val includeMin: Boolean = true,
        val includeMax: Boolean = false
    ) : VersionRequirement {
        override fun satisfiedBy(version: SemanticVersion): Boolean {
            if (min != null) {
                val comparison = version.compareTo(min)
                if (comparison < 0 || (comparison == 0 && !includeMin)) return false
            }
            if (max != null) {
                val comparison = version.compareTo(max)
                if (comparison > 0 || (comparison == 0 && !includeMax)) return false
            }
            return true
        }
    }
    
    data class Compatible(val base: SemanticVersion) : VersionRequirement {
        override fun satisfiedBy(version: SemanticVersion): Boolean {
            // Compatible means same major version, >= minor.patch
            return version.major == base.major && version >= base
        }
    }
}

// Tool interface description
data class ToolInterface(
    val inputs: Indexed<ParameterSpec>,
    val outputs: OutputSpec,
    val errors: Indexed<ErrorSpec>,
    val sideEffects: Indexed<SideEffect>,
    val constraints: Indexed<Constraint>
)

data class ParameterSpec(
    val name: String,
    val type: TypeSpec,
    val description: String,
    val required: Boolean = true,
    val default: Any? = null,
    val validation: ValidationRule? = null,
    val examples: Indexed<Any?> = emptyArray<Any?>().toSeries()
)

sealed interface TypeSpec {
    fun isAssignableFrom(value: Any?): Boolean
    fun describe(): String
    
    data class Primitive(val type: KClass<*>) : TypeSpec {
        override fun isAssignableFrom(value: Any?) = 
            value != null && type.isInstance(value)
        override fun describe() = type.simpleName ?: "unknown"
    }
    
    data class Collection(
        val elementType: TypeSpec,
        val collectionType: CollectionType
    ) : TypeSpec {
        override fun isAssignableFrom(value: Any?) = when (collectionType) {
            CollectionType.LIST -> value is List<*>
            CollectionType.SET -> value is Set<*>
            CollectionType.MAP -> value is Map<*, *>
            CollectionType.INDEXED -> value is Array<*>
        }
        override fun describe() = "${collectionType.name.lowercase()}<${elementType.describe()}>"
    }
    
    data class Union(val types: Indexed<TypeSpec>) : TypeSpec {
        override fun isAssignableFrom(value: Any?) = 
            types.any { it.isAssignableFrom(value) }
        override fun describe() = types.joinToString(" | ") { it.describe() }
    }
    
    data class Struct(
        val fields: Map<String, TypeSpec>,
        val sealed: Boolean = true
    ) : TypeSpec {
        override fun isAssignableFrom(value: Any?) = value is Map<*, *>
        override fun describe() = fields.entries.joinToString(", ", "{", "}") { 
            "${it.key}: ${it.value.describe()}" 
        }
    }
    
    data class Function(
        val parameters: Indexed<TypeSpec>,
        val returnType: TypeSpec
    ) : TypeSpec {
        override fun isAssignableFrom(value: Any?) = value is kotlin.Function<*>
        override fun describe() = "(${parameters.joinToString(", ") { it.describe() }}) -> ${returnType.describe()}"
    }
}

enum class CollectionType {
    LIST, SET, MAP, INDEXED
}

data class OutputSpec(
    val type: TypeSpec,
    val streaming: Boolean = false,
    val async: Boolean = false,
    val cacheable: Boolean = false,
    val ttl: Long? = null // Time to live for cached results
)

data class ErrorSpec(
    val code: String,
    val message: String,
    val recoverable: Boolean = true,
    val retryable: Boolean = false,
    val httpStatus: Int? = null
)

data class SideEffect(
    val type: SideEffectType,
    val description: String,
    val reversible: Boolean = false,
    val compensation: String? = null // How to undo the side effect
)

enum class SideEffectType {
    FILE_SYSTEM, NETWORK, DATABASE, PROCESS, ENVIRONMENT, LOG
}

sealed interface Constraint {
    fun check(context: ExecutionContext): Boolean
    
    data class ResourceConstraint(
        val resource: ResourceType,
        val limit: Long,
        val unit: String
    ) : Constraint {
        override fun check(context: ExecutionContext) = true // Check against actual usage
    }
    
    data class TimeConstraint(
        val maxDuration: Long,
        val unit: TimeUnit
    ) : Constraint {
        override fun check(context: ExecutionContext) = true
    }
    
    data class RateLimitConstraint(
        val maxCalls: Int,
        val window: Long,
        val unit: TimeUnit
    ) : Constraint {
        override fun check(context: ExecutionContext) = true
    }
}

enum class ResourceType {
    MEMORY, CPU, DISK, NETWORK, FILE_HANDLES
}

enum class TimeUnit {
    MILLISECONDS, SECONDS, MINUTES, HOURS
}

// Implementation details
data class ToolImplementation(
    val language: String,
    val runtime: RuntimeInfo,
    val dependencies: Indexed<Dependency>,
    val source: SourceInfo? = null,
    val binary: BinaryInfo? = null
)

data class RuntimeInfo(
    val name: String,
    val version: SemanticVersion,
    val platform: Platform,
    val features: Set<String> = emptySet()
)

data class Platform(
    val os: String,
    val arch: String,
    val variant: String? = null
) {
    companion object {
        val CURRENT = Platform(
            os = System.getProperty("os.name"),
            arch = System.getProperty("os.arch"),
            variant = System.getProperty("os.version")
        )
    }
}

data class Dependency(
    val name: String,
    val version: VersionRequirement,
    val type: DependencyType,
    val optional: Boolean = false
)

enum class DependencyType {
    LIBRARY, SERVICE, TOOL, RESOURCE
}

data class SourceInfo(
    val repository: String,
    val commit: String? = null,
    val path: String? = null,
    val license: String? = null
)

data class BinaryInfo(
    val format: String,
    val size: Long,
    val checksum: String,
    val signature: String? = null
)

// Documentation
data class Documentation(
    val summary: String,
    val description: String,
    val parameters: Map<String, ParameterDoc>,
    val returns: String? = null,
    val throws: Indexed<ThrowsDoc> = emptyArray<ThrowsDoc>().toSeries(),
    val see: Indexed<String> = emptyArray<String>().toSeries(),
    val since: SemanticVersion? = null,
    val deprecated: DeprecationInfo? = null
)

data class ParameterDoc(
    val description: String,
    val constraints: String? = null,
    val examples: Indexed<String> = emptyArray<String>().toSeries()
)

data class ThrowsDoc(
    val exception: String,
    val condition: String,
    val handling: String? = null
)

data class DeprecationInfo(
    val since: SemanticVersion,
    val reason: String,
    val replacement: String? = null,
    val removal: SemanticVersion? = null
)

// Examples
data class Example(
    val name: String,
    val description: String,
    val input: Map<String, Any?>,
    val expectedOutput: Any?,
    val explanation: String? = null,
    val tags: Set<String> = emptySet()
)

// Telemetry configuration
data class TelemetryConfig(
    val enabled: Boolean = true,
    val metrics: Set<MetricType> = MetricType.values().toSet(),
    val sampling: SamplingStrategy = SamplingStrategy.Adaptive(),
    val export: ExportConfig? = null
)

enum class MetricType {
    LATENCY, THROUGHPUT, ERROR_RATE, RESOURCE_USAGE, CUSTOM
}

sealed interface SamplingStrategy {
    data class Fixed(val rate: Double) : SamplingStrategy
    data class Adaptive(
        val minRate: Double = 0.01,
        val maxRate: Double = 1.0,
        val targetLatency: Long = 100
    ) : SamplingStrategy
    data object All : SamplingStrategy
}

data class ExportConfig(
    val endpoint: String,
    val format: ExportFormat,
    val interval: Long = 60_000,
    val batch: Int = 100
)

enum class ExportFormat {
    OTLP, PROMETHEUS, JAEGER, CUSTOM
}

// Validation rules
sealed interface ValidationRule {
    fun validate(value: Any?): ValidationResult
    
    data class Pattern(val regex: Regex) : ValidationRule {
        override fun validate(value: Any?) = when {
            value !is String -> ValidationResult.Error("Expected string")
            !regex.matches(value) -> ValidationResult.Error("Does not match pattern: $regex")
            else -> ValidationResult.Success
        }
    }
    
    data class Range<T : Comparable<T>>(
        val min: T? = null,
        val max: T? = null
    ) : ValidationRule {
        override fun validate(value: Any?): ValidationResult {
            @Suppress("UNCHECKED_CAST")
            val comparable = value as? T ?: return ValidationResult.Error("Value is not comparable")
            
            if (min != null && comparable < min) {
                return ValidationResult.Error("Value $comparable is less than minimum $min")
            }
            if (max != null && comparable > max) {
                return ValidationResult.Error("Value $comparable exceeds maximum $max")
            }
            return ValidationResult.Success
        }
    }
    
    data class OneOf<T>(val values: Set<T>) : ValidationRule {
        override fun validate(value: Any?) = when (value) {
            in values -> ValidationResult.Success
            else -> ValidationResult.Error("Value must be one of: ${values.joinToString(", ")}")
        }
    }
    
    data class Custom(
        val name: String,
        val validator: (Any?) -> ValidationResult
    ) : ValidationRule {
        override fun validate(value: Any?) = validator(value)
    }
}

// Execution context for constraints
data class ExecutionContext(
    val startTime: Long = System.currentTimeMillis(),
    val resources: MutableMap<ResourceType, Long> = mutableMapOf(),
    val callCount: MutableMap<String, Int> = mutableMapOf()
)

// Tool discovery and registry
class ToolDiscovery(private val registry: ToolRegistry = ToolRegistry()) {
    
    fun discover(scanPath: String? = null): Flow<ToolMetadata> = flow {
        // Scan for tools in classpath or specified path
        // This would use reflection or file scanning in a real implementation
    }
    
    fun register(metadata: ToolMetadata) {
        registry.register(metadata)
    }
    
    fun search(query: ToolQuery): Flow<ToolMetadata> = 
        registry.search(query).asFlow()
    
    fun getCompatible(
        id: String,
        requirement: VersionRequirement
    ): ToolMetadata? = registry.getCompatibleVersions(id, requirement).maxByOrNull { it.version }
}

class ToolRegistry {
    private val tools = mutableMapOf<String, MutableList<ToolMetadata>>()
    
    fun register(metadata: ToolMetadata) {
        tools.getOrPut(metadata.id) { mutableListOf() }.add(metadata)
    }
    
    fun get(id: String, version: SemanticVersion): ToolMetadata? =
        tools[id]?.find { it.version == version }
    
    fun getLatest(id: String): ToolMetadata? =
        tools[id]?.maxByOrNull { it.version }
    
    fun getCompatibleVersions(id: String, requirement: VersionRequirement): List<ToolMetadata> =
        tools[id]?.filter { requirement.satisfiedBy(it.version) } ?: emptyList()
    
    fun search(query: ToolQuery): List<ToolMetadata> =
        tools.values.flatten().filter { query.matches(it) }
}

data class ToolQuery(
    val namePattern: Regex? = null,
    val capability: Capability? = null,
    val tags: Set<String>? = null,
    val minVersion: SemanticVersion? = null,
    val experimental: Boolean? = null
) {
    fun matches(metadata: ToolMetadata): Boolean {
        if (namePattern != null && !namePattern.matches(metadata.id)) return false
        if (capability != null && !metadata.capability.implies(capability)) return false
        if (tags != null && !metadata.examples.any { example -> example.tags.any { it in tags } }) return false
        if (minVersion != null && metadata.version < minVersion) return false
        if (experimental != null && metadata.experimental != experimental) return false
        return true
    }
}