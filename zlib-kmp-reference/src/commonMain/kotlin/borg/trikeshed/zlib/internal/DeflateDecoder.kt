package borg.trikeshed.zlib.internal

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Series as IndexedAlias
import borg.trikeshed.lib.j
import borg.trikeshed.lib.size
import borg.trikeshed.lib.get
import borg.trikeshed.lib.toArray
import borg.trikeshed.lib.toSeries
import borg.trikeshed.crypto.hash.Adler32Hasher
import java.io.ByteArrayOutputStream // For collecting final output

// DEFLATE window size is 32KB
const val DEFLATE_WINDOW_SIZE = 32 * 1024

/**
 * The main DEFLATE decoder. This class handles parsing DEFLATE blocks
 * and orchestrating the Huffman and LZ77 decoding processes.
 *
 * @param bitStream The BitStream to read compressed data from.
 */
class DeflateDecoder(private val bitStream: BitStream) {

    // Pre-defined fixed Huffman tables for DEFLATE
    // These are constant and can be built once.
    private val fixedLitLenHuffmanTable: HuffmanLookupTable
    private val fixedDistHuffmanTable: HuffmanLookupTable

    init {
        // Build fixed literal/length Huffman table
        val fixedLitLenCodeLengths = IndexedAlias(288) { i ->
            when (i) {
                in 0..143 -> 8
                in 144..255 -> 9
                in 256..279 -> 7
                in 280..287 -> 8
                else -> 0 // Should not happen for 0-287
            }
        }
        fixedLitLenHuffmanTable = buildHuffmanTable(fixedLitLenCodeLengths, HUFFMAN_LOOKUP_BITS_LIT_LEN)

        // Build fixed distance Huffman table
        val fixedDistCodeLengths = IndexedAlias(32) { 5 }
        fixedDistHuffmanTable = buildHuffmanTable(fixedDistCodeLengths, HUFFMAN_LOOKUP_BITS_DIST)
    }

    // LZ77 sliding window (output buffer)
    private val slidingWindow = ByteArray(DEFLATE_WINDOW_SIZE) // 32KB circular buffer
    private var windowWritePos: Int = 0 // Current write position in the circular buffer
    private var decompressedBytesCount: Long = 0 // Total decompressed bytes

    // For collecting the final decompressed output
    private val outputStream = ByteArrayOutputStream()

    /**
     * Decodes a complete DEFLATE stream.
     * @return A Pair of (The decompressed data as an Indexed<Byte>, Adler32 checksum of the decompressed data).
     */
    fun decode(): Pair<Indexed<Byte>, UInt> {
        while (true) {
            val bfinal = bitStream.readBits(1) // BFINAL: 1 if last block, 0 otherwise
            val btype = bitStream.readBits(2)  // BTYPE: 00=no compression, 01=fixed Huffman, 10=dynamic Huffman, 11=reserved

            when (btype) {
                0b00 -> {
                    // No compression
                    decodeNoCompressionBlock()
                }
                0b01 -> {
                    // Fixed Huffman codes
                    decodeHuffmanBlock(fixedLitLenHuffmanTable, fixedDistHuffmanTable)
                }
                0b10 -> {
                    // Dynamic Huffman codes
                    val (litLenTable, distTable) = decodeDynamicHuffmanTables()
                    decodeHuffmanBlock(litLenTable, distTable)
                }
                0b11 -> {
                    // Reserved block type
                    throw ZlibException("Reserved DEFLATE block type (0b11) encountered.")
                }
            }

            if (bfinal == 1) {
                break // Last block, stop decoding
            }
        }

        val decompressedData = outputStream.toByteArray().toSeries()
        val adler32 = Adler32Hasher.checksum(decompressedData)
        return decompressedData to adler32
    }

    /**
     * Decodes a block with no compression.
     */
    private fun decodeNoCompressionBlock() {
        // Align to byte boundary
        bitStream.skipBits(bitStream.getBitOffset() % 8)

        // Read LEN and NLEN
        val len = bitStream.readBits(16)
        val nlen = bitStream.readBits(16)

        if (len.inv() and 0xFFFF != nlen) {
            throw ZlibException("LEN and NLEN mismatch in uncompressed block.")
        }

        // Read data bytes and write to sliding window and output stream
        for (i in 0 until len) {
            val byte = bitStream.readByte().toByte()
            writeToOutput(byte)
        }
    }

    /**
     * Decodes a block compressed with Huffman codes (fixed or dynamic).
     * @param litLenTable The Huffman table for literal/length codes.
     * @param distTable The Huffman table for distance codes.
     */
    private fun decodeHuffmanBlock(
        litLenTable: HuffmanLookupTable,
        distTable: HuffmanLookupTable
    ) {
        while (true) {
            val symbol = decodeHuffmanSymbol(bitStream, litLenTable)

            when (symbol) {
                in 0..255 -> {
                    // Literal byte
                    val byte = DeflateToken.Literal(symbol).byte.toByte()
                    writeToOutput(byte)
                }
                256 -> {
                    // End of block
                    return // End of Huffman block
                }
                in 257..285 -> {
                    // Length code
                    val length = decodeLength(symbol, bitStream)
                    val distSymbol = decodeHuffmanSymbol(bitStream, distTable)
                    val distance = decodeDistance(distSymbol, bitStream)

                    // Copy 'length' bytes from 'distance' bytes back in the output buffer
                    copyFromSlidingWindow(length, distance)
                }
                else -> {
                    throw ZlibException("Invalid literal/length symbol: $symbol")
                }
            }
        }
    }

    /**
     * Decodes the dynamic Huffman tables from the stream.
     * This is a complex process involving a third Huffman table (for code lengths).
     * @return A Pair of (literal/length Huffman table, distance Huffman table).
     */
    private fun decodeDynamicHuffmanTables(): Pair<HuffmanLookupTable, HuffmanLookupTable> {
        val hlit = bitStream.readBits(5) + 257 // Number of literal/length codes (257-286)
        val hdist = bitStream.readBits(5) + 1   // Number of distance codes (1-32)
        val hclen = bitStream.readBits(4) + 4   // Number of code length codes (4-19)

        // Read the code lengths for the code length alphabet
        val codeLengthCodeLengths = IntArray(19) { 0 }
        val codeLengthOrder = IndexedAlias(19) { i ->
            when (i) {
                0 -> 16; 1 -> 17; 2 -> 18; 3 -> 0; 4 -> 8; 5 -> 7; 6 -> 9; 7 -> 6; 8 -> 10; 9 -> 5;
                10 -> 11; 11 -> 4; 12 -> 12; 13 -> 3; 14 -> 13; 15 -> 2; 16 -> 14; 17 -> 1; 18 -> 15;
                else -> throw IllegalStateException("Invalid code length order index")
            }
        }

        for (i in 0 until hclen) {
            codeLengthCodeLengths[codeLengthOrder[i]] = bitStream.readBits(3)
        }

        // Build the code length Huffman table
        val codeLengthHuffmanTable = buildHuffmanTable(IndexedAlias(codeLengthCodeLengths.size) { codeLengthCodeLengths[it] }, HUFFMAN_LOOKUP_BITS_LIT_LEN)

        // Decode the literal/length and distance code lengths
        val litLenCodeLengths = IntArray(hlit) { 0 }
        val distCodeLengths = IntArray(hdist) { 0 }

        var i = 0
        while (i < hlit + hdist) {
            val code = decodeHuffmanSymbol(bitStream, codeLengthHuffmanTable)

            when (code) {
                in 0..15 -> {
                    // Literal code length
                    if (i < hlit) litLenCodeLengths[i] = code
                    else distCodeLengths[i - hlit] = code
                    i++
                }
                16 -> {
                    // Repeat previous code length (3 to 6 times)
                    val repeatCount = bitStream.readBits(2) + 3
                    val prevLength = if (i == 0) 0 else (if (i < hlit) litLenCodeLengths[i - 1] else distCodeLengths[i - hlit - 1])
                    for (j in 0 until repeatCount) {
                        if (i < hlit) litLenCodeLengths[i] = prevLength
                        else distCodeLengths[i - hlit] = prevLength
                        i++
                    }
                }
                17 -> {
                    // Repeat 0 for 3 to 10 times
                    val repeatCount = bitStream.readBits(3) + 3
                    for (j in 0 until repeatCount) {
                        if (i < hlit) litLenCodeLengths[i] = 0
                        else distCodeLengths[i - hlit] = 0
                        i++
                    }
                }
                18 -> {
                    // Repeat 0 for 11 to 138 times
                    val repeatCount = bitStream.readBits(7) + 11
                    for (j in 0 until repeatCount) {
                        if (i < hlit) litLenCodeLengths[i] = 0
                        else distCodeLengths[i - hlit] = 0
                        i++
                    }
                }
                else -> {
                    throw ZlibException("Invalid code length symbol: $code")
                }
            }
        }

        val litLenTable = buildHuffmanTable(IndexedAlias(litLenCodeLengths.size) { litLenCodeLengths[it] }, HUFFMAN_LOOKUP_BITS_LIT_LEN)
        val distTable = buildHuffmanTable(IndexedAlias(distCodeLengths.size) { distCodeLengths[it] }, HUFFMAN_LOOKUP_BITS_DIST)

        return litLenTable to distTable
    }

    /**
     * Decodes a length value based on the symbol and extra bits.
     * @param symbol The length symbol (257-285).
     * @param bitStream The BitStream to read extra bits from.
     * @return The decoded length.
     */
    private fun decodeLength(symbol: Int, bitStream: BitStream): Int {
        val (baseLength, extraBits) = LENGTH_CODES_TABLE[symbol - 257] // Adjust symbol to be 0-indexed for table
        return baseLength + if (extraBits > 0) bitStream.readBits(extraBits) else 0
    }

    /**
     * Decodes a distance value based on the symbol and extra bits.
     * @param symbol The distance symbol (0-31).
     * @param bitStream The BitStream to read extra bits from.
     * @return The decoded distance.
     */
    private fun decodeDistance(symbol: Int, bitStream: BitStream): Int {
        val (baseDistance, extraBits) = DISTANCE_CODES_TABLE[symbol] // Symbol is 0-indexed for table
        return baseDistance + if (extraBits > 0) bitStream.readBits(extraBits) else 0
    }

    /**
     * Writes a single byte to the sliding window and the final output stream.
     */
    private fun writeToOutput(byte: Byte) {
        slidingWindow[windowWritePos] = byte
        windowWritePos = (windowWritePos + 1) % DEFLATE_WINDOW_SIZE
        outputStream.write(byte.toInt())
        decompressedBytesCount++
    }

    /**
     * Copies bytes from the sliding window to the current position.
     * @param length The number of bytes to copy.
     * @param distance The distance back in the buffer to start copying from.
     */
    private fun copyFromSlidingWindow(length: Int, distance: Int) {
        if (distance > decompressedBytesCount) {
            throw ZlibException("Distance is greater than decompressed bytes count, invalid back-reference.")
        }

        var currentLength = length
        var currentDistance = distance

        // Handle overlapping copies efficiently
        while (currentLength > 0) {
            val readPos = (windowWritePos - currentDistance + DEFLATE_WINDOW_SIZE) % DEFLATE_WINDOW_SIZE
            val byteToCopy = slidingWindow[readPos]

            writeToOutput(byteToCopy)
            currentLength--
        }
    }
}
