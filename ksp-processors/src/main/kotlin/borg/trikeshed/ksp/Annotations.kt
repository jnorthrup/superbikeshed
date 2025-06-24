package borg.trikeshed.ksp

import kotlin.annotation.AnnotationTarget.*

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
 * Generates efficient Series extension functions for a data class.
 * Creates specialized map, filter, and fold operations that work directly
 * with the packed representations when possible.
 */
@Target(CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GenerateSeriesExtensions

/**
 * Generates utility functions for enum classes including:
 * - Efficient lookup by ordinal using when expressions
 * - Bit flag operations if enum values are powers of 2
 * - String parsing with fuzzy matching
 */
@Target(CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GenerateEnumUtilities

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
    val message: String = ""
)

/**
 * Indicates that a class participates in the TrikeShed metaclass system.
 * The processor will generate specialized Join implementations and
 * packing strategies for this type.
 */
@Target(CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Metaclass(
    val packingStrategy: PackingStrategyHint = PackingStrategyHint.AUTO
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
    NONE
}