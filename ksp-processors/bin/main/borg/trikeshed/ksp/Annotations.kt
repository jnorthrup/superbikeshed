package borg.trikeshed.ksp

import kotlin.annotation.AnnotationTarget.*
import kotlin.reflect.KClass

/**
 * Marks a class for DSL generation by TrikeShedDslProcessor.
 *
 * The processor will generate:
 * - A builder class with fluent methods for each property
 * - Validation logic for constrained properties (e.g., port ranges)
 * - Nested builders for complex property types
 * - Collection helper methods for list/set properties
 * - A top-level DSL function for instantiation
 *
 * Example:
 * ```kotlin
 * @GenerateDsl
 * data class ServerConfig(
 *     val host: String,
 *     val port: Int,
 *     val endpoints: List<Endpoint>
 * )
 *
 * // Generated DSL usage:
 * val config = serverConfig {
 *     host("localhost")
 *     port(8080)
 *     endpoint { path = "/api"; method = "GET" }
 *     endpoint { path = "/health"; method = "GET" }
 * }
 * ```
 */
@Target(CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GenerateDsl

/**
 * Creates specialized map, filter, and fold operations that work directly
 * with the packed representations when possible.
 */
@Target(CLASS)
@Retention(AnnotationRetention.SOURCE)
typealias GenerateSeriesExtensions = GenerateSeriesExtensionsV2

/**
 * Generates utility functions for enum classes including:
 * - Efficient lookup by ordinal using when expressions
 * - Bit flag operations if enum values are powers of 2
 * - String parsing with fuzzy matching
 */
@Target(CLASS)
@Retention(AnnotationRetention.SOURCE)
typealias GenerateEnumUtilities = GenerateEnumUtilitiesV2

/**
 * Generates a builder pattern for data classes with:
 * - Immutable builder with copy-on-write semantics
 * - Validation hooks at build time
 * - Default value support
 */
@Target(CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GenerateDataClassBuilder

/**
 * Marks a property as requiring validation in generated code.
 * The validation logic is inferred from the property name and type,
 * or can be explicitly specified.
 */
@Target(PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Validated(
    val min: Long = Long.MIN_VALUE,
    val max: Long = Long.MAX_VALUE,
    val pattern: String = "",
    val message: String = "",
)

/**
 * Indicates that a class participates in the TrikeShed metaclass system.
 * The processor will generate specialized Join implementations and
 * packing strategies for this type.
 */
@Target(CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Metaclass(
    val packingStrategy: PackingStrategyHint = PackingStrategyHint.AUTO,
)

/**
 * Hints for the code generator about preferred packing strategies
 */
enum class PackingStrategyHint {
    /** Let the system choose based on type analysis */
    AUTO,

    /** Prefer diagonal packing for small numeric pairs */
    DIAGONAL,

    /** Use prefix byte for type discrimination */
    PREFIXED,

    /** Pack as offset from a base value */
    RANGE_OFFSET,

    /** Store increments between values */
    RELATIVE_INCREMENT,

    /** Use palette indexing for repeated values */
    PALETTE,

    /** Group into multiple clusters */
    MULTI_CLUSTER,

    /** Don't attempt packing */
    NONE,
}

// === ENUM FLOTILLA ANNOTATIONS ===

/**
 * Marks a class as a handler in the agglomerated view system.
 * Handlers process requests and define capabilities.
 */
@Target(CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Handler(
    val capabilities: Array<String> = [],
    val description: String = ""
)

/**
 * Marks a class as a service in the agglomerated view system.
 * Services provide functionality with dependencies and versioning.
 */
@Target(CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Service(
    val dependencies: Array<String> = [],
    val version: String = "1.0.0"
)

/**
 * Marks a class as a plugin in the agglomerated view system.
 * Plugins extend functionality and can be enabled/disabled.
 */
@Target(CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Plugin(
    val name: String = "",
    val version: String = "1.0.0",
    val enabled: Boolean = true
)

/**
 * Marks a class as a workflow in the agglomerated view system.
 * Workflows define sequences of operations with triggers and priority.
 */
@Target(CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Workflow(
    val triggers: Array<String> = [],
    val priority: Int = 0
)

/**
 * Marks a class as a capability in the agglomerated view system.
 * Capabilities define features that can be required or optional.
 */
@Target(CLASS)  
@Retention(AnnotationRetention.SOURCE)
annotation class Capability(
    val category: String = "GENERAL",
    val required: Boolean = false
)

/**
 * Marker annotation to generate optimized j overloads for primitive type combinations
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GenerateJoinPackers(
    val leftTypes: Array<KClass<*>> = [
        Int::class, Long::class, Boolean::class, Byte::class, Short::class, Float::class, Double::class
    ],
    val rightTypes: Array<KClass<*>> = [
        Int::class, Long::class, Boolean::class, Byte::class, Short::class, Float::class, Double::class
    ],
    val packageName: String = "borg.trikeshed.generated"
)

/**
 * Marker annotation to generate unified MetaSeries hierarchy
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GenerateMetaSeries(
    val elementTypes: Array<KClass<*>> = [
        Int::class, Long::class, Double::class, Float::class, String::class, Boolean::class
    ],
    val packageName: String = "borg.trikeshed.generated"
)

/**
 * Marker annotation to generate specialized packing strategies
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GeneratePackingStrategies(
    val strategies: Array<String> = ["direct", "delta", "zigzag", "rle"],
    val packageName: String = "borg.trikeshed.generated"
)

/**
 * Marker annotation to generate serialization/deserialization adapters
 * Eliminates manual mapping between wire and in-memory formats
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GenerateWireAdapters(
    val wireFormat: String = "protobuf",
    val includeValidation: Boolean = true,
    val includePerformanceMonitoring: Boolean = true,
    val packageName: String = "borg.trikeshed.generated"
)

/**
 * Marker annotation to generate Series<T> extensions and utilities
 * Eliminates repetitive Series transformation boilerplate
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GenerateSeriesExtensions(
    val elementTypes: Array<KClass<*>> = [
        Int::class, Long::class, Double::class, String::class, Boolean::class
    ],
    val operations: Array<String> = ["map", "filter", "reduce", "slice", "join"],
    val packageName: String = "borg.trikeshed.generated"
)

/**
 * Marker annotation to generate data class builders and copy utilities
 * Eliminates manual copy() boilerplate and provides fluent builders
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GenerateDataClassBuilders(
    val includeValidation: Boolean = true,
    val includeJsonSerialization: Boolean = true,
    val includeWireSerialization: Boolean = true,
    val packageName: String = "borg.trikeshed.generated"
)

/**
 * Marker annotation to generate enum utilities and extensions
 * Eliminates repetitive enum pattern matching and utility functions
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GenerateEnumUtilities(
    val includeFromString: Boolean = true,
    val includeToString: Boolean = true,
    val includeValidation: Boolean = true,
    val includeSerialization: Boolean = true,
    val packageName: String = "borg.trikeshed.generated"
)

/**
 * Marker annotation to generate attention monitoring delegates
 * Eliminates manual performance monitoring boilerplate
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GenerateAttentionDelegates(
    val includeMetrics: Boolean = true,
    val includeThresholds: Boolean = true,
    val includeReporting: Boolean = true,
    val packageName: String = "borg.trikeshed.generated"
)

/**
 * Marker annotation to generate platform-specific implementations
 * Eliminates manual platform detection and implementation boilerplate
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GeneratePlatformImplementations(
    val platforms: Array<String> = ["jvm", "native", "js", "wasm"],
    val includeFallbacks: Boolean = true,
    val packageName: String = "borg.trikeshed.generated"
)

/**
 * Marker annotation to generate test utilities and fixtures
 * Eliminates repetitive test setup and assertion boilerplate
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GenerateTestUtilities(
    val includeFixtures: Boolean = true,
    val includeAssertions: Boolean = true,
    val includeMocking: Boolean = true,
    val packageName: String = "borg.trikeshed.generated"
)

// Type aliases for duplicate annotation classes to resolve redeclaration errors
typealias GenerateSeriesExtensionsV2 = GenerateSeriesExtensions
typealias GenerateEnumUtilitiesV2 = GenerateEnumUtilities
