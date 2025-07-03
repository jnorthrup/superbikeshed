package moneyfan.trikeshed

import moneyfan.trikeshed.scope.AttentionScope // Added import for AttentionScope
import kotlin.NoSuchElementException // For extendByClamping on empty series

/**
 * Defines the behavior for accessing elements outside the original bounds of a [Indexed]
 * when it is extended using the [Indexed.extend] function or its convenience wrappers.
 */
enum class ExtensionMode {
    /**
     * Clamps the requested index to the valid range of the original series (`0` to `originalSeries.a - 1`).
     * - Accessing an index less than `0` returns the element at index `0`.
     * - Accessing an index greater than or equal to `originalSeries.a` returns the element at `originalSeries.a - 1`.
     * - **Warning:** If the original series is empty, attempts to access the extended series
     *   will result in a [NoSuchElementException] because there are no elements to clamp to.
     *   The [Indexed.extendByClamping] helper provides specific behavior for this case.
     */
    CLAMP_TO_EDGE,

    /**
     * Returns a specified default value for any out-of-bounds access.
     * The `defaultValue` must be provided in the [SeriesExtension] configuration.
     */
    DEFAULT_VALUE,

    /**
     * Uses a provided generative function to produce values for out-of-bounds access.
     * The function receives the requested (out-of-bounds) index and a reference to the original series,
     * allowing for dynamic content generation (e.g., procedural patterns, calculated values).
     * The `generativeFunction` must be provided in the [SeriesExtension] configuration.
     */
    GENERATIVE
}

/**
 * Configuration class that defines how a [Indexed] should behave when extended
 * beyond its original finite bounds. Used with the [Indexed.extend] function.
 *
 * @param T The type of elements in the Indexed.
 * @property mode The [ExtensionMode] specifying the primary strategy for handling out-of-bounds access.
 * @property defaultValue The value to return for out-of-bounds access when `mode` is [ExtensionMode.DEFAULT_VALUE].
 *                      This property **must** be non-null if `mode` is `DEFAULT_VALUE`.
 * @property generativeFunction A lambda function `(index: Int, originalSeries: Indexed<T>) -> T`
 *                              used to generate values for out-of-bounds access when `mode` is [ExtensionMode.GENERATIVE].
 *                              The function takes the requested out-of-bounds `index` and the `originalSeries` itself.
 *                              This property **must** be non-null if `mode` is `GENERATIVE`.
 * @throws IllegalArgumentException if `defaultValue` is null when `mode` is `DEFAULT_VALUE`,
 *                                  or if `generativeFunction` is null when `mode` is `GENERATIVE`.
 */
data class SeriesExtension<T>(
    val mode: ExtensionMode,
    val defaultValue: T? = null,
    val generativeFunction: ((index: Int, originalSeries: Indexed<T>) -> T)? = null
) {
    init {
        when (mode) {
            ExtensionMode.DEFAULT_VALUE -> require(defaultValue != null) {
                "defaultValue must be provided when ExtensionMode is DEFAULT_VALUE."
            }
            ExtensionMode.GENERATIVE -> require(generativeFunction != null) {
                "generativeFunction must be provided when ExtensionMode is GENERATIVE."
            }
            ExtensionMode.CLAMP_TO_EDGE -> {
                // No specific value requirement for CLAMP_TO_EDGE itself,
                // as it relies on the original series' content.
            }
        }
    }
}

/**
 * Extends a [Indexed] to behave as if it were infinitely long, handling out-of-bounds access
 * according to the rules defined in the provided [extension] configuration.
 *
 * The returned [Indexed] will report its size (`a`) as [Int.MAX_VALUE].
 * Accessing elements within the original series' bounds (`0` to `originalSeries.a - 1`)
 * will return the original elements. Accessing elements outside these bounds will trigger
 * the behavior defined by `extension.mode`.
 *
 * @param T The type of elements in the Indexed.
 * @param extension The [SeriesExtension] configuration object that specifies how to handle
 *                  access beyond the original series' finite bounds.
 * @return A new [Indexed] that appears to be infinitely long.
 * @see SeriesExtension
 * @see ExtensionMode
 * @see extendByClamping
 * @see extendWithDefault
 * @see extendWithGenerator
 */
fun <T> Indexed<T>.extend(extension: SeriesExtension<T>): Indexed<T> {
    val originalSeries = this

    return object : Indexed<T> {
        override val a: Int = Int.MAX_VALUE // Represents a virtually "infinite" series

        override val b: (index: Int) -> T = { index ->
            when {
                // In-bounds access: delegate to the original series
                index >= 0 && index < originalSeries.a -> originalSeries.b(index)

                // Out-of-bounds access: apply extension logic
                else -> {
                    when (extension.mode) {
                        ExtensionMode.CLAMP_TO_EDGE -> {
                            if (originalSeries.isEmpty()) {
                                // Clamping is ill-defined for an empty series as there are no edges.
                                throw NoSuchElementException("Cannot clamp access to an empty series. Consider extendByClamping() for specific empty series behavior or provide a non-empty series.")
                            }
                            // Coerce the index to be within the valid range of the original series.
                            val clampedIndex = index.coerceIn(0, originalSeries.a - 1)
                            originalSeries.b(clampedIndex)
                        }
                        ExtensionMode.DEFAULT_VALUE -> {
                            extension.defaultValue!! // Validated non-null by SeriesExtension's init block
                        }
                        ExtensionMode.GENERATIVE -> {
                            extension.generativeFunction!!(index, originalSeries) // Validated non-null
                        }
                    }
                }
            }
        }
    }
}

/**
 * Extends a [Indexed] to be virtually infinite by clamping out-of-bounds access
 * to the nearest edge element of the original series.
 * - If `index < 0`, it returns the element at index `0`.
 * - If `index >= originalSeries.a`, it returns the element at index `originalSeries.a - 1`.
 *
 * **Special Behavior for Empty Indexed:**
 * If the original series is empty, the returned "infinite" series will
 * throw a [NoSuchElementException] upon any access attempt, as there are no elements to clamp to.
 *
 * @param T The type of elements in the Indexed.
 * @return A new, virtually infinite [Indexed] that clamps access to its edges or throws if empty.
 */
fun <T> Indexed<T>.extendByClamping(): Indexed<T> {
    if (this.isEmpty()) {
        // Return an infinite series that always throws because clamping to an empty series is not possible.
        return object : Indexed<T> {
            override val a: Int = Int.MAX_VALUE
            override val b: (index: Int) -> T = { throw NoSuchElementException("Cannot access elements from an extended empty series using CLAMP_TO_EDGE mode.") }
        }
    }
    return this.extend(SeriesExtension(mode = ExtensionMode.CLAMP_TO_EDGE))
}

/**
 * Extends a [Indexed] to be virtually infinite, returning a specified `defaultValue`
 * for any out-of-bounds access (i.e., for `index < 0` or `index >= originalSeries.a`).
 *
 * @param T The type of elements in the Indexed.
 * @param defaultValue The value to return for indices outside the original series' bounds.
 * @return A new, virtually infinite [Indexed] that returns `defaultValue` for out-of-bounds access.
 */
fun <T> Indexed<T>.extendWithDefault(defaultValue: T): Indexed<T> {
    return this.extend(SeriesExtension(mode = ExtensionMode.DEFAULT_VALUE, defaultValue = defaultValue))
}

/**
 * Extends a [Indexed] to be virtually infinite, using a `generativeFunction`
 * to produce values for any out-of-bounds access (i.e., for `index < 0` or `index >= originalSeries.a`).
 *
 * The `generativeFunction` receives the requested out-of-bounds index and
 * a reference to the original series, allowing for dynamic computation of extended values.
 *
 * @param T The type of elements in the Indexed.
 * @param generativeFunction A lambda `(index: Int, originalSeries: Indexed<T>) -> T` that computes
 *                           the value for an out-of-bounds `index`.
 * @return A new, virtually infinite [Indexed] that uses `generativeFunction` for out-of-bounds access.
 */
fun <T> Indexed<T>.extendWithGenerator(generativeFunction: (index: Int, originalSeries: Indexed<T>) -> T): Indexed<T> {
    return this.extend(SeriesExtension(mode = ExtensionMode.GENERATIVE, generativeFunction = generativeFunction))
}

// --- fillna, dropna, ffill implementations for sparse Indexed<T?> ---

/**
 * Fills `null` (missing) values in a `Indexed<T?>` with a specified non-null `defaultValue`.
 *
 * This operation produces a new [Indexed] of non-nullable type `T` and the same size as the original.
 *
 * @param T The non-nullable underlying type of the elements.
 * @param defaultValue The non-null value to use for replacing `null` elements in the original series.
 * @return A new [Indexed<T>] where `null`s from the original series are replaced by `defaultValue`.
 */
fun <T : Any> Indexed<T?>.fillna(defaultValue: T): Indexed<T> {
    return this.a j { index:Int -> this.b(index) ?: defaultValue }
}

/**
 * Fills `null` (missing) values in a `Indexed<T?>` using a `defaultProvider` function.
 * The provider function is called with the current `index` each time a `null` value is encountered,
 * allowing for context-dependent default value generation.
 *
 * This operation produces a new [Indexed] of non-nullable type `T` and the same size as the original.
 *
 * @param T The non-nullable underlying type of the elements.
 * @param defaultProvider A lambda function `(index: Int) -> T` that takes an `Int` (the index of the `null` value)
 *                        and returns a non-null replacement value of type `T`.
 * @return A new [Indexed<T>] where `null`s from the original series are replaced by values generated by `defaultProvider`.
 */
fun <T : Any> Indexed<T?>.fillna(defaultProvider: (index: Int) -> T): Indexed<T> {
    return this.a j { index:Int -> this.b(index) ?: defaultProvider(index) }
}

/**
 * Removes all `null` (missing) values from a `Indexed<T?>`, returning a new, dense `Indexed<T>`.
 *
 * The order of the remaining non-null elements is preserved.
 * The size of the returned series will be less than or equal to the original series' size.
 * If the original series contains only `null`s or is empty, an empty `Indexed<T>` is returned.
 *
 * @param T The non-nullable underlying type of the elements.
 * @return A new [Indexed<T>] containing only the non-null values from the original series.
 */
fun <T : Any> Indexed<T?>.dropna(): Indexed<T> {
    // Uses IterableSeries (`play`) to leverage Kotlin's standard library sequence operations.
    // `filterNotNull` correctly transforms Sequence<T?> to Sequence<T>.
    return this.`play`.asSequence().filterNotNull().toList().toSeries()
}

/**
 * Forward fills `null` (missing) values in a `Indexed<T?>`.
 *
 * Each `null` value is replaced by the last non-null value encountered at a preceding index in the series.
 * Leading `null` values (at the beginning of the series, before any non-null value is seen) remain `null`.
 * The resulting series retains the nullable type `T?` because of these potential leading nulls.
 *
 * @param T The underlying (potentially non-nullable) type of the elements. The constraint `T: Any` ensures
 *          clarity for `lastNonNullValue: T?`.
 * @return A new [Indexed<T?>]` of the same size as the original, with `null`s forward-filled.
 *         Returns an [emptySeries] if the original series is empty.
 */
fun <T : Any> Indexed<T?>.ffill(): Indexed<T?> {
    if (this.isEmpty()) {
        return emptySeries()
    }

    var lastNonNullValue: T? = null // Holds the last seen non-null value.
    return this.a j { index:Int ->
        val currentValue = this.b(index)
        if (currentValue != null) {
            lastNonNullValue = currentValue // Update if current is non-null
            currentValue // Return current non-null value
        } else {
            lastNonNullValue // Return last non-null seen (or null if still in leading nulls)
        }
    }
}

// --- AttentionScope related extension ---

/**
 * Applies an [AttentionScope] to this [Indexed], returning a new [Indexed]
 * that represents the focused subset or transformation defined by the scope.
 *
 * This is a convenience extension function that simply delegates to `scope.apply(this)`.
 * It provides a fluent API for applying scopes: `mySeries.focus(myScope)`.
 *
 * @param T The type of elements in the Indexed.
 * @param scope The [AttentionScope] instance defining the focus criteria (e.g., a range, a fraction).
 * @return A new [Indexed<T>] containing only the elements within the defined scope,
 *         or a transformed series as defined by the specific [AttentionScope] implementation.
 */
fun <T> Indexed<T>.focus(scope: AttentionScope<T>): Indexed<T> = scope.apply(this)
