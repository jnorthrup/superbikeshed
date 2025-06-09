package borg.trikeshed.net.http3.qpack

import borg.trikeshed.net.http.HttpHeaders // Typealias for Map<String, List<String>>
import borg.trikeshed.net.http.QuicCurlException
import borg.trikeshed.net.quic.util.BufferReader
import kotlin.text.CharacterCodingException


/**
 * QPACK Decoder (Minimal Implementation).
 * - Supports static table references (exact match and name-only match).
 * - Supports literal encoding for names and values (Huffman bit always 0).
 * - Does NOT support dynamic table or Huffman coding.
 * - Uses simplified QPACK instruction prefix handling (first byte check).
 */
class QpackDecoder {

    fun decode(encodedData: ByteArray, streamIdForContext: Long): HttpHeaders {
        // streamIdForContext is ignored in this minimal, dynamic-table-less decoder.
        val reader = BufferReader(encodedData)
        val decodedHeaders = mutableMapOf<String, MutableList<String>>()

        while (reader.hasRemaining()) {
            val firstByte = reader.peekByte().toInt() and 0xFF // Read without consuming for type check
            val instructionType = determineInstructionType(firstByte)

            try {
                when (instructionType) {
                    QpackInstructionSimpleType.INDEXED_STATIC -> {
                        reader.readByte() // Consume the first byte
                        val index: Int
                        // QPACK spec: 1 T Index. T=1 (static) -> 11xxxxxx (index in 6 bits)
                        // If index >= 62, then 1111111 (0xFF), then varint(index - 62).
                        // Our prefix was 11xxxxxx
                        if ((firstByte and 0xFF) == 0xFF) { // Matches the 0xFF prefix for larger static indices
                            index = (reader.readVarint() + (QPACK_PREFIX_INDEX_6BIT_MAX + 1)).toInt()
                        } else {
                            index = firstByte and QPACK_PREFIX_INDEX_6BIT_MAX // Get 6-bit index
                        }

                        if (index < 0 || index >= QPACK_STATIC_TABLE.size) {
                            throw QuicCurlException("Invalid static table index: $index")
                        }
                        val headerField = QPACK_STATIC_TABLE[index]
                        decodedHeaders.getOrPut(headerField.name) { mutableListOf() }.add(headerField.value ?: "")
                    }

                    QpackInstructionSimpleType.LITERAL_STATIC_NAME_REF -> {
                        reader.readByte() // Consume the first byte
                        // Format: 01 N T Index. Simplified: 0101xxxx (N=0, T=1, index in 4 bits)
                        // Or 01011111 (0x5F) + varint(index - 15)
                        // Huffman bit for value is assumed 0.
                        val index: Int
                        if ((firstByte and 0x0F) == 0x0F) { // Check if the lower 4 bits are all 1s (01011111)
                             index = (reader.readVarint() + (QPACK_PREFIX_INDEX_4BIT_MAX + 1)).toInt()
                        } else {
                            index = firstByte and QPACK_PREFIX_INDEX_4BIT_MAX // Get 4-bit index
                        }

                        if (index < 0 || index >= QPACK_STATIC_TABLE.size) {
                            throw QuicCurlException("Invalid static table index for name reference: $index")
                        }
                        val name = QPACK_STATIC_TABLE[index].name
                        val value = reader.readQStringLiteral(huffmanBitIsSet = false) // H=0
                        decodedHeaders.getOrPut(name) { mutableListOf() }.add(value)
                    }

                    QpackInstructionSimpleType.LITERAL_LITERAL_NAME -> {
                        reader.readByte() // Consume the first byte (001Hxxxx, H assumed 0)
                        // Assumed H=0 for name from prefix byte (e.g. 0x20 means H=0)
                        val name = reader.readQStringLiteral(huffmanBitIsSet = false) // H=0 for name
                        val value = reader.readQStringLiteral(huffmanBitIsSet = false) // H=0 for value
                        decodedHeaders.getOrPut(name) { mutableListOf() }.add(value)
                    }

                    QpackInstructionSimpleType.UNKNOWN -> {
                        throw QuicCurlException("Unknown or unsupported QPACK instruction byte: ${firstByte.toString(16)}")
                    }
                }
            } catch (e: IndexOutOfBoundsException) {
                throw QuicCurlException("Malformed QPACK data: Buffer underflow during field parsing. Instruction byte: ${firstByte.toString(16)}", cause = e)
            } catch (e: IllegalArgumentException) {
                 throw QuicCurlException("Malformed QPACK data: Error reading varint or string. Instruction byte: ${firstByte.toString(16)}", cause = e)
            } catch (e: CharacterCodingException) {
                throw QuicCurlException("Malformed QPACK data: UTF-8 decoding error. Instruction byte: ${firstByte.toString(16)}", cause = e)
            }
        }
        return decodedHeaders.mapValues { it.value.toList() }
    }
}
