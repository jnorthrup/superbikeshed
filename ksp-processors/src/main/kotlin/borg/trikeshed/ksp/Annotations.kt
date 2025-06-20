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