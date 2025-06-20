package borg.trikeshed.ksp

import kotlin.reflect.KClass

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