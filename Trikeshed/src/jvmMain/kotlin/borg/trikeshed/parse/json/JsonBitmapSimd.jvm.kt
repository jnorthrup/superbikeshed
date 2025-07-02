package borg.trikeshed.parse.json

import kotlin.math.ceil

// Enum for structure types, with explicit 2-bit values
enum class JsonStructureType(val bits: Int) {
    None(0b00),
    ScopeOpen(0b01),
    ScopeClose(0b10),
    Delimiter(0b11)
}

object JsonBitmapSimd {
    fun createBitmap(input: UByteArray): ULongArray {
        val inputSize = input.size
        if (inputSize == 0) return ULongArray(0)

        val outputSize = ceil(inputSize / 32.0).toInt() // 32 entries per ULong (2 bits each)
        val output = ULongArray(outputSize)

        for (i in 0 until inputSize) {
            val byte = input[i]
            val structureType = when (byte.toInt().toChar()) {
                '{', '[' -> JsonStructureType.ScopeOpen
                '}', ']' -> JsonStructureType.ScopeClose
                ':', ',' -> JsonStructureType.Delimiter
                else -> JsonStructureType.None
            }
            val ulongIndex = i / 32
            val bitPosition = (i % 32) * 2
            output[ulongIndex] = output[ulongIndex] or (structureType.bits.toULong() shl bitPosition)
        }
        return output
    }
}