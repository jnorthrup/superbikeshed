package borg.trikeshed.parse.json

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import borg.trikeshed.lib.play
import borg.trikeshed.lib.bridge.toSeries

/**
 * Creates a lazy, tensor-native view of a pre-computed JSON bitmap using TrikeShed's Series<T>.
 *
 * @param input The raw UByteArray of JSON data.
 * @return A Series<UByte> where each element is a 4-bit pixel from the bitmap.
 *         The accessor function performs the necessary bit-shifting to read from the
 *         underlying ULongArray on demand.
 */
@OptIn(ExperimentalUnsignedTypes::class)
fun createBitmapAsSeries(input: UByteArray): Indexed<UByte> {
    // 1. Eagerly create the bitmap using the hyper-optimized `actual` implementation.
    val bitmapArray = JsonBitmapSimd.createBitmap(input)
    val inputSize = input.size

    // 2. Return a lazy Series view over the materialized array.
    return inputSize j { i ->
        val ulongIndex = i / 16
        val bitPosition = (i % 16) * 4
        
        // The accessor's logic is to simply read the pre-computed pixel.
        ((bitmapArray[ulongIndex] shr bitPosition) and 0b1111uL).toUByte()
    }
}

/**
 * Simplified JSON parsing without regex dependencies
 */
fun parseJsonToTensor(jsonString: String): Indexed<String> {
    // Simple JSON value extraction - basic implementation
    val values = mutableListOf<String>()
    var inString = false
    var current = StringBuilder()
    
    for (char in jsonString) {
        when (char) {
            '"' -> {
                inString = !inString
                if (!inString && current.isNotEmpty()) {
                    values.add(current.toString())
                    current.clear()
                }
            }
            ',', ']', '}' -> {
                if (!inString && current.isNotEmpty()) {
                    val value = current.toString().trim()
                    if (value.isNotEmpty() && value != ":" && value != "{" && value != "[") {
                        values.add(value)
                    }
                    current.clear()
                }
            }
            ':', '{', '[' -> {
                if (!inString && current.isNotEmpty()) {
                    val value = current.toString().trim()
                    if (value.isNotEmpty()) {
                        values.add(value)
                    }
                    current.clear()
                }
            }
            else -> {
                if (inString || !char.isWhitespace()) {
                    current.append(char)
                }
            }
        }
    }
    
    // Add final value if exists
    if (current.isNotEmpty()) {
        val value = current.toString().trim()
        if (value.isNotEmpty()) {
            values.add(value)
        }
    }
    
    return values.toIdx()
}

/**
 * Basic shape detection for JSON arrays
 */
fun detectArrayShape(jsonString: String): Indexed<Int> {
    var depth = 0
    var maxDepth = 0
    val dimensions = mutableListOf<Int>()
    var currentCount = 0
    
    for (char in jsonString) {
        when (char) {
            '[' -> {
                depth++
                maxDepth = maxOf(maxDepth, depth)
                if (depth == 1) currentCount = 0
            }
            ']' -> {
                if (depth == 1 && currentCount > 0) {
                    dimensions.add(currentCount)
                }
                depth--
            }
            ',' -> {
                if (depth == 1) currentCount++
            }
        }
    }
    
    return if (dimensions.isEmpty()) {
        1 j { 0 }
    } else {
        dimensions.size j { i -> dimensions[i] }
    }
}

// JsonBitmapSimd is defined as expect object in JsonBitmapSimd.kt