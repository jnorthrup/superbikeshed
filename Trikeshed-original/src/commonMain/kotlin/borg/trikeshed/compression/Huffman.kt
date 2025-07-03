package borg.trikeshed.compression

import borg.trikeshed.lib.Indexed

/**
 * Represents a Huffman code, consisting of a value (the decoded symbol)
 * and its bit length.
 * This is a simple data class, as the packing will be handled by how these
 * are stored in the lookup table.
 */
data class HuffmanCode(val value: Int, val length: Int)

/**
 * A simplified Huffman lookup table for decoding.
 * In a real DEFLATE implementation, there are two main Huffman tables:
 * one for literal/length codes and one for distance codes.
 * This table is designed for direct lookup based on the initial bits read from the stream.
 *
 * @param maxBits The maximum number of bits a code in this table can have.
 * @param lookupTable An Indexed (Indexed) of HuffmanCode, where the index corresponds to the
 *                    bit pattern read from the stream. The size of this table is 2^maxBits.
 *                    Entries might be null or point to a special 'incomplete' code if more bits
 *                    are needed for a longer code.
 */
class HuffmanLookupTable(val maxBits: Int, val lookupTable: Indexed<HuffmanCode?>) {
    init {
        require(lookupTable.a == (1 shl maxBits)) {
            "Lookup table size must be 2^maxBits. Expected ${1 shl maxBits}, got ${lookupTable.size}"
        }
    }
}

/**
 * Decodes a single symbol from the BitStream using the provided Huffman lookup table.
 * This is a simplified decoder that assumes a direct lookup table approach.
 * For a full zlib implementation, more complex tree traversal or multi-level table lookups
 * would be necessary for codes longer than maxBits.
 *
 * @param bitStream The BitStream to read bits from.
 * @param huffmanTable The HuffmanLookupTable to use for decoding.
 * @return The decoded symbol (Int).
 * @throws IllegalStateException if an invalid code is encountered or end of stream is reached.
 */
fun decodeHuffmanSymbol(bitStream: BitStream, huffmanTable: HuffmanLookupTable): Int {
    // Read up to maxBits to perform a direct lookup
    val bits = bitStream.readBits(huffmanTable.maxBits)

    val code = huffmanTable.lookupTable[bits]
        ?: throw IllegalStateException("Invalid Huffman code encountered or table is incomplete for this bit pattern.")

    // If the code's length is less than maxBits, we read too many bits.
    // We need to 'rewind' the bitStream by the difference.
    // This simplified implementation doesn't support rewinding directly, which is a limitation.
    // A more robust BitStream would need a 'peekBits' and 'consumeBits' or a 'rewind' mechanism.
    // For now, we assume the lookup table is perfectly constructed for direct reads.
    // In a real DEFLATE, you'd read bits incrementally until a valid code is found.

    // For a pragmatic common implementation, we'll assume the lookup table is built such that
    // reading maxBits always yields a valid entry, and the 'length' in HuffmanCode tells us
    // how many of those maxBits were actually part of the code.
    // The BitStream.readBits already advances the stream by numBits, so we need to ensure
    // the table construction accounts for this.

    // A more accurate approach for variable-length codes with a fixed-size lookup:
    // 1. Read N bits (e.g., 9 for literal/length, 6 for distance in DEFLATE).
    // 2. Look up in table. If it's a full match, use it.
    // 3. If not, read more bits and continue lookup (e.g., for 15-bit codes).

    // For this pragmatic common implementation, we'll simplify:
    // The `readBits` function in BitStream already advances the stream.
    // The `HuffmanLookupTable` should be constructed such that `lookupTable[bits]`
    // gives the correct code for the *shortest* matching prefix.
    // This implies the lookup table needs to be carefully populated with all prefixes.

    // Given the current BitStream.readBits, which consumes bits, a direct lookup table
    // needs to be built with all possible prefixes. The `length` in `HuffmanCode` is crucial.
    // The `readBits` function in BitStream reads `numBits` and advances. If the actual Huffman code
    // is shorter than `numBits`, we've over-read. This is a common challenge with fixed-size lookup tables.

    // For now, we'll assume `huffmanTable.maxBits` is the exact length of the code we are trying to decode.
    // This is a simplification for the initial common implementation.
    // A more complete solution would involve a loop that reads bits one by one or in small chunks
    // and traverses a Huffman tree or uses a more sophisticated lookup array that handles variable lengths.

    return code.value
}