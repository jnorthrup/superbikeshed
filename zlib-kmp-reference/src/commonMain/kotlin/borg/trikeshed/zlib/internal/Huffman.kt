package borg.trikeshed.zlib.internal

import borg.trikeshed.lib.Indexed
Alias // Alias Series to Indexed
import borg.trikeshed.lib.size
import borg.trikeshed.lib.get
import borg.trikeshed.lib.j
import borg.trikeshed.lib.play

/**
 * Represents a Huffman code, which can be a full code, a partial code (prefix of a longer code),
 * or an invalid code. Uses value classes for efficient, allocation-free representation.
 */
sealed interface HuffmanCode {
    /**
     * Represents a complete Huffman code that has been successfully decoded.
     * @param value The decoded symbol.
     * @param length The bit length of this code.
     */
    @JvmInline
    value class FullCode(private val packed: Int) : HuffmanCode {
        constructor(value: Int, length: Int) : this((value shl 8) or (length and 0xFF)) // Pack value and length
        val value: Int get() = packed shr 8
        val length: Int get() = packed and 0xFF
    }

    /**
     * Represents a partial Huffman code. This is a prefix that requires more bits
     * to be read from the stream to form a complete code.
     * @param length The bit length of this partial code (the number of bits already read).
     */
    @JvmInline
    value class PartialCode(val length: Int) : HuffmanCode

    /**
     * Represents an invalid Huffman code or an unassigned bit pattern.
     * This indicates an error state during decoding.
     */
    object InvalidCode : HuffmanCode
}

/**
 * A Huffman lookup table for decoding, built using canonical Huffman coding principles.
 * This table allows for efficient decoding of variable-length Huffman codes.
 *
 * @param maxCodeLength The maximum bit length of any code in this Huffman set.
 * @param minCodeLength The minimum bit length of any code in this Huffman set.
 * @param firstCode An Indexed (Series) where `firstCode[length]` is the smallest canonical code
 *                  for that `length`. Codes are assigned numerically within each length.
 * @param firstSymbol An Indexed (Series) where `firstSymbol[length]` is the index into the `values`
 *                    array for the first symbol of that `length`.
 * @param values An Indexed (Series) of symbols, sorted by their canonical codes.
 *               `values[firstSymbol[length] + (code - firstCode[length])]` gives the symbol.
 * @param lookupTable An Indexed (Series) of HuffmanCode, used for initial fast lookup.
 *                    The size is 2^maxLookupBits. Entries can be FullCode, PartialCode, or InvalidCode.
 */
class HuffmanLookupTable(
    val maxCodeLength: Int,
    val minCodeLength: Int,
    val firstCode: Indexed<Int>,
    val firstSymbol: Indexed<Int>,
    val values: Indexed<Int>,
    val lookupTable: Indexed<HuffmanCode>
) {
    init {
        require(maxCodeLength >= minCodeLength) { "maxCodeLength must be >= minCodeLength" }
    }
}

/**
 * Builds a canonical Huffman lookup table from an array of code lengths.
 * This algorithm is based on the method used in zlib for DEFLATE.
 *
 * @param codeLengths An Indexed<Int> where the index is the symbol and the value is its bit length.
 *                    A length of 0 indicates the symbol is not present in the Huffman code set.
 * @param maxLookupBits The maximum number of bits to use for the initial lookup table. Codes longer
 *                      than this will result in PartialCode entries, requiring further bit reads.
 * @return A HuffmanLookupTable instance.
 * @throws ZlibException if the code lengths are invalid (e.g., result in an incomplete or over-subscribed code tree).
 */
fun buildHuffmanTable(codeLengths: Indexed<Int>, maxLookupBits: Int): HuffmanLookupTable {
    val maxCodeLength = codeLengths.play.maxOrNull() ?: 0
    val minCodeLength = codeLengths.play.filter { it > 0 }.minOrNull() ?: 0

    if (maxCodeLength == 0) {
        throw ZlibException("Cannot build Huffman table from all zero code lengths.")
    }

    // blCount[length] = number of codes with that length
    val blCount = IntArray(maxCodeLength + 1) { 0 }
    for (length in codeLengths.play) {
        if (length > 0) {
            blCount[length]++
        }
    }

    // nextCode[length] = first canonical code for that length
    val nextCode = IntArray(maxCodeLength + 1) { 0 }
    var code = 0
    for (len in 1..maxCodeLength) {
        code = (code + blCount[len - 1]) shl 1
        nextCode[len] = code
    }

    // values: symbols sorted by their canonical codes
    val valuesArray = IntArray(codeLengths.size) { 0 } // Max possible symbols
    val firstSymbolArray = IntArray(maxCodeLength + 1) { 0 }

    // Populate values and firstSymbol
    var currentValIdx = 0
    for (len in 1..maxCodeLength) {
        firstSymbolArray[len] = currentValIdx
        for (symbol in 0 until codeLengths.size) {
            if (codeLengths[symbol] == len) {
                valuesArray[currentValIdx++] = symbol
            }
        }
    }

    // Build the fast lookup table
    val tableSize = 1 shl maxLookupBits
    val lookupTableArray = arrayOfNulls<HuffmanCode>(tableSize) // Use nullable array for initial population

    for (symbol in 0 until codeLengths.size) {
        val length = codeLengths[symbol]
        if (length == 0) continue

        val currentCode = nextCode[length]++

        // Reverse bits for lookup (DEFLATE codes are LSB first)
        val reversedCode = reverseBits(currentCode, length)

        if (length <= maxLookupBits) {
            // Full code fits in lookup table
            val huffmanCode = HuffmanCode.FullCode(symbol, length)
            val startIndex = reversedCode shl (maxLookupBits - length)
            val fillCount = 1 shl (maxLookupBits - length)
            for (i in 0 until fillCount) {
                lookupTableArray[startIndex + i] = huffmanCode
            }
        } else {
            // Code is longer than maxLookupBits, mark as partial
            val partialCode = HuffmanCode.PartialCode(length)
            val startIndex = reversedCode shr (length - maxLookupBits)
            // Only one entry for this prefix, as it's a partial code
            lookupTableArray[startIndex] = partialCode
        }
    }

    // Fill any remaining nulls with InvalidCode
    for (i in 0 until tableSize) {
        if (lookupTableArray[i] == null) {
            lookupTableArray[i] = HuffmanCode.InvalidCode
        }
    }

    // Convert arrays to Indexed (Series) for CoreTypes compatibility
    val indexedFirstCode = IndexedAlias(maxCodeLength + 1) { nextCode[it] }
    val indexedFirstSymbol = IndexedAlias(maxCodeLength + 1) { firstSymbolArray[it] }
    val indexedValues = IndexedAlias(currentValIdx) { valuesArray[it] }
    val indexedLookupTable = IndexedAlias(tableSize) { lookupTableArray[it]!! }

    return HuffmanLookupTable(
        maxCodeLength = maxCodeLength,
        minCodeLength = minCodeLength,
        firstCode = indexedFirstCode,
        firstSymbol = indexedFirstSymbol,
        values = indexedValues,
        lookupTable = indexedLookupTable
    )
}

/**
 * Decodes a single symbol from the BitStream using the provided Huffman lookup table.
 * This function implements a double inline dispatch strategy for efficient decoding.
 *
 * @param bitStream The BitStream to read bits from.
 * @param huffmanTable The HuffmanLookupTable to use for decoding.
 * @return The decoded symbol (Int).
 * @throws ZlibException if an invalid code is encountered or end of stream is not handled gracefully.
 */
inline fun decodeHuffmanSymbol(bitStream: BitStream, huffmanTable: HuffmanLookupTable): Int {
    // First dispatch: initial lookup using maxLookupBits
    val initialBits = bitStream.peekBits(huffmanTable.lookupTable.size.countTrailingZeroBits()) // maxLookupBits
    val huffmanCode = huffmanTable.lookupTable[initialBits]

    return when (huffmanCode) {
        is HuffmanCode.FullCode -> {
            bitStream.skipBits(huffmanCode.length)
            huffmanCode.value
        }
        is HuffmanCode.PartialCode -> {
            // Second dispatch: handle codes longer than maxLookupBits
            // This path involves reading more bits incrementally
            bitStream.skipBits(huffmanTable.lookupTable.size.countTrailingZeroBits()) // Consume initial peeked bits
            decodeLongHuffmanCode(bitStream, huffmanTable, huffmanCode.length) // Pass the length of the partial code
        }
        HuffmanCode.InvalidCode -> {
            throw ZlibException("Invalid Huffman code encountered.")
        }
    }
}

/**
 * Helper function for decoding Huffman codes that are longer than the initial lookup bits.
 * This function is designed to be inlined into the decodeHuffmanSymbol function.
 */
@PublishedApi
internal inline fun decodeLongHuffmanCode(bitStream: BitStream, huffmanTable: HuffmanLookupTable, initialLength: Int): Int {
    var currentCode = bitStream.peekBits(huffmanTable.maxCodeLength) // Peek up to max possible code length
    var currentLength = initialLength

    // Iterate through possible lengths, starting from the initial partial length + 1
    for (len in initialLength + 1..huffmanTable.maxCodeLength) {
        // Mask currentCode to only consider bits up to 'len'
        val maskedCode = currentCode and ((1 shl len) - 1)

        // Check if the masked code matches a canonical code of this length
        if (len >= huffmanTable.minCodeLength && maskedCode >= huffmanTable.firstCode[len]) {
            val offset = maskedCode - huffmanTable.firstCode[len]
            val symbolIndex = huffmanTable.firstSymbol[len] + offset

            if (symbolIndex < huffmanTable.values.size) {
                bitStream.skipBits(len) // Consume the actual bits for this code
                return huffmanTable.values[symbolIndex]
            }
        }
    }

    throw ZlibException("Invalid Huffman code encountered or no valid code found after reading max bits.")
}

/**
 * Reverses the bits of an integer up to a specified length.
 * This is necessary because DEFLATE Huffman codes are stored with the least significant bit first,
 * but our `peekBits` reads them in standard order.
 * This function is primarily used during table construction to align with the canonical code representation.
 */
private fun reverseBits(value: Int, numBits: Int): Int {
    var reversed = 0
    for (i in 0 until numBits) {
        if ((value shr i) and 1 == 1) {
            reversed = reversed or (1 shl (numBits - 1 - i))
        }
    }
    return reversed
}

// Helper extension to get maxLookupBits from table size
private fun Int.countTrailingZeroBits(): Int {
    if (this == 0) return 32 // Or throw, depending on desired behavior for 0
    return this.countTrailingZeroBits()
}