package borg.trikeshed.zlib.internal

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Series as IndexedAlias // Alias Series to Indexed

/**
 * Represents a Huffman code, consisting of a value (the decoded symbol)
 * and its bit length.
 * This is a simple data class, as the packing will be handled by how these
 * are stored in the lookup table.
 */
data class HuffmanCode(val value: Int, val length: Int)

/**
 * A Huffman lookup table for decoding, designed for efficient lookup of variable-length codes.
 * This table is built to allow looking up a code by reading a fixed number of bits (maxLookupBits)
 * from the stream. If the actual code is shorter than maxLookupBits, the table entry will contain
 * the code and its true length. If the code is longer, the entry will indicate that more bits
 * need to be read (e.g., by having a special value or a length greater than maxLookupBits).
 *
 * @param maxLookupBits The maximum number of bits to read from the stream for an initial lookup.
 * @param lookupTable An Indexed (Series) of HuffmanCode?, where the index corresponds to the
 *                    bit pattern read from the stream. The size of this table is 2^maxLookupBits.
 *                    Entries can be null if a bit pattern does not correspond to a valid code
 *                    within the `maxLookupBits` prefix, or if it's a prefix of a longer code.
 */
class HuffmanLookupTable(val maxLookupBits: Int, val lookupTable: Indexed<HuffmanCode?>) {
    init {
        require(lookupTable.size == (1 shl maxLookupBits)) {
            "Lookup table size must be 2^maxLookupBits. Expected ${1 shl maxLookupBits}, got ${lookupTable.size}"
        }
    }
}

/**
 * Decodes a single symbol from the BitStream using the provided Huffman lookup table.
 * This function implements a common strategy for decoding variable-length Huffman codes
 * using a fixed-size lookup table and then potentially reading more bits for longer codes.
 *
 * @param bitStream The BitStream to read bits from.
 * @param huffmanTable The HuffmanLookupTable to use for decoding.
 * @return The decoded symbol (Int).
 * @throws ZlibException if an invalid code is encountered or end of stream is not handled gracefully.
 */
fun decodeHuffmanSymbol(bitStream: BitStream, huffmanTable: HuffmanLookupTable): Int {
    // Peek the maximum number of bits for the initial lookup.
    // This allows us to check for codes of various lengths that fit within maxLookupBits.
    val peekedBits = bitStream.peekBits(huffmanTable.maxLookupBits)

    // Attempt to find a code in the lookup table.
    val code = huffmanTable.lookupTable[peekedBits]

    if (code == null) {
        // This indicates an invalid bit pattern or a code that is longer than maxLookupBits
        // and requires a more complex lookup (e.g., tree traversal or secondary tables).
        // For a full zlib implementation, this would need to be handled by iterating
        // through code lengths or traversing a Huffman tree.
        throw ZlibException("Invalid Huffman code or code too long for direct lookup table.")
    }

    // Consume the bits that correspond to the actual length of the decoded code.
    // This is crucial for variable-length codes.
    bitStream.skipBits(code.length)

    return code.value
}
