import kotlin.math.*
package borg.trikeshed.ts.kotlin.kotlin
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.lib.*

/**
 * TypeScript to Kotlin Migration Utilities
 * Helper functions for converting TypeScript patterns to Kotlin
 */

/**
 * Convert TypeScript array to Indexed
 */
fun <T> arrayToIndexed(array: Array<T>): Indexed<T> {
    return array.size j { array[it] }
}

/**
 * Convert TypeScript array to Indexed (for Int)
 */
fun intArrayToIndexed(array: IntArray): Indexed<Int> {
    return array.size j { array[it] }
}

/**
 * Convert TypeScript 2D array to Indexed<Indexed<T>>
 */
fun <T> array2DToIndexed(array: Array<Array<T>>): Indexed<Indexed<T>> {
    return array.size j { rowIndex ->
        array[rowIndex].size j { colIndex ->
            array[rowIndex][colIndex]
        }
    }
}

/**
 * Convert TypeScript 2D array to Indexed<Indexed<Int>>
 */
fun intArray2DToIndexed(array: Array<IntArray>): Indexed<Indexed<Int>> {
    return array.size j { rowIndex ->
        array[rowIndex].size j { colIndex ->
            array[rowIndex][colIndex]
        }
    }
}

/**
 * Convert Indexed to List
 */
fun <T> indexedToList(indexed: Indexed<T>): List<T> {
    return List(indexed.size) { indexed[it] }
}

/**
 * Convert Indexed<Indexed<T>> to List<List<T>>
 */
fun <T> indexed2DToList(indexed: Indexed<Indexed<T>>): List<List<T>> {
    return List(indexed.size) { rowIndex ->
        List(indexed[rowIndex].size) { colIndex ->
            indexed[rowIndex][colIndex]
        }
    }
}

/**
 * TypeScript-style object creation helper
 */
inline fun <reified T> createObject(vararg pairs: Pair<String, Any>): Map<String, Any> {
    return pairs.toMap()
}

/**
 * TypeScript-style destructuring helper
 */
fun <T> destructure(pair: Join<T, T>): Pair<T, T> {
    return pair.a to pair.b
}

/**
 * TypeScript-style spread operator simulation
 */
fun <T> spread(vararg items: T): List<T> {
    return items.toList()
}

/**
 * TypeScript-style optional chaining simulation
 */
fun <T> safeCall(obj: T?, block: (T) -> Unit) {
    obj?.let(block)
}

/**
 * TypeScript-style nullish coalescing
 */
fun <T> nullishCoalesce(value: T?, defaultValue: T): T {
    return value ?: defaultValue
} 