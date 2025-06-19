package borg.trikeshed.parse.json

import borg.trikeshed.lib.*

/**
 * Creates a lazy, tensor-native view of a pre-computed JSON bitmap using TrikeShed's Series<T>.
 *
 * @param input The raw UByteArray of JSON data.
 * @return A Series<UByte> where each element is a 4-bit pixel from the bitmap.
 *         The accessor function performs the necessary bit-shifting to read from the
 *         underlying ULongArray on demand.
 */
@OptIn(ExperimentalUnsignedTypes::class)
fun createBitmapAsSeries(input: UByteArray): Series<UByte> {
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
 * Lightning-fast JSON parser using SIMD bitmap and Series<T> for TrikeShed integration.
 */
@OptIn(ExperimentalUnsignedTypes::class)
object LightningJson {
    
    /**
     * Parse JSON string to structural bitmap using lightning-fast SIMD processing.
     */
    fun parseToBitmap(jsonString: String): Series<UByte> {
        val jsonBytes = jsonString.encodeToByteArray().toUByteArray()
        return createBitmapAsSeries(jsonBytes)
    }
    
    /**
     * Find all structural indices (opening/closing braces, brackets, commas) in JSON.
     */
    fun findStructuralIndices(jsonString: String): Series<Int> {
        val bitmap = parseToBitmap(jsonString)
        val indices = mutableListOf<Int>()
        
        bitmap.play.forEachIndexed { index, pixel ->
            val jsState = pixel.toInt() and 0b11
            if (jsState != JsonBitmapProcessor.JsStateEvent.Unchanged.ordinal) {
                indices.add(index)
            }
        }
        
        return indices.toSeries()
    }
    
    /**
     * Extract JSON values using Series<T> operations - pure TrikeShed style.
     */
    fun extractValues(jsonString: String): Series<String> {
        val structuralIndices = findStructuralIndices(jsonString)
        val jsonChars = jsonString.toSeries()
        
        // Simple value extraction between structural characters
        val values = mutableListOf<String>()
        var i = 0
        
        structuralIndices.play.zipWithNext().forEach { (start, end) ->
            val segment = jsonChars[start + 1 until end]
            val value = segment.play.joinToString("").trim()
            if (value.isNotEmpty() && value != ":") {
                values.add(value)
            }
        }
        
        return values.toSeries()
    }
}

// Extension functions for convenience
fun String.toSeries(): Series<Char> = length j { index -> this[index] }
fun <T> List<T>.toSeries(): Series<T> = size j { index -> this[index] }