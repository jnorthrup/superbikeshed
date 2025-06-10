@JvmInline
value class PackedBits(val value: Long) {
    // Inline decoding for Series lambda capture
    inline fun getSizeAndValue(elementBitSize: Int): Pair<Int, Long> {
        val size = (value shr elementBitSize).toInt()
        val firstValue = value and ((1L shl elementBitSize) - 1)
        return size to firstValue
    }

    // Inline decoding for Join components
    inline fun getJoinComponents(leftBitSize: Int, rightBitSize: Int): Pair<Long, Long> {
        val left = if (leftBitSize > 0) (value shr rightBitSize) and ((1L shl leftBitSize) - 1) else 0L
        val right = if (rightBitSize > 0) value and ((1L shl rightBitSize) - 1) else 0L
        return left to right
    }

    // Check continuation bit
    inline fun hasContinuation(): Boolean = (value shr 63) and 1L == 1L
}

typealias Join<A, B> = PackedBits
typealias Series<T> = Join<Int, (Int) -> T>

// Inline spilling function
inline fun spill(bits: PackedBits, bitSize: Int, targetRegisterSize: Int): PackedBits {
    if (bitSize <= targetRegisterSize - 2) return bits
    val chunkSize = minOf(targetRegisterSize - 3, bitSize) // Reserve 1 bit for continuation
    val chunkValue = bits.value and ((1L shl chunkSize) - 1)
    val continuationBit = if (bitSize > chunkSize) 1L else 0L
    return PackedBits((continuationBit shl (chunkSize + 2)) or ((chunkSize.toLong() - 1) shl chunkSize) or chunkValue)
}

// Join factories
inline fun join(a: Boolean, b: Boolean, targetRegisterSize: Int = 64): Join<Boolean, Boolean> {
    val packed = ((if (a) 1 else 0).toLong() shl 1) or (if (b) 1 else 0).toLong()
    val totalBits = 1 + 2 // 1-bit indicator + 2 bits
    return if (totalBits <= targetRegisterSize - 2) {
        PackedBits(1L shl 2 or packed) // 1-bit indicator
    } else {
        spill(PackedBits(packed), 2, targetRegisterSize)
    }
}

inline fun join(a: Byte, b: Byte, targetRegisterSize: Int = 64): Join<Byte, Byte> {
    val packed = ((a.toInt() shl 8) or (b.toInt() and 0xFF)).toLong()
    val totalBits = 2 + 16 // 2-bit indicator + 16 bits
    return if (totalBits <= targetRegisterSize - 2) {
        PackedBits(1L shl 16 or packed) // 2-bit indicator (01)
    } else {
        spill(PackedBits(packed), 16, targetRegisterSize)
    }
}

inline fun join(a: Short, b: Short, targetRegisterSize: Int = 64): Join<Short, Short> {
    val packed = ((a.toInt() shl 16) or (b.toInt() and 0xFFFF)).toLong()
    val totalBits = 2 + 32 // 2-bit indicator + 32 bits
    return if (totalBits <= targetRegisterSize - 2) {
        PackedBits(1L shl 32 or packed) // 2-bit indicator (01)
    } else {
        spill(PackedBits(packed), 32, targetRegisterSize)
    }
}

inline fun join(a: Int, b: Int, targetRegisterSize: Int = 64): Join<Int, Int> {
    val packed = (a.toLong() shl 32) or (b.toLong() and 0xFFFFFFFFL)
    val totalBits = 1 + 64 // 1-bit indicator + 64 bits
    return if (totalBits <= targetRegisterSize - 2) {
        PackedBits(1L shl 63 or packed) // 1-bit indicator, no continuation
    } else {
        spill(PackedBits(packed), 64, targetRegisterSize)
    }
}

inline fun join(value: Long, bitSize: Int, targetRegisterSize: Int = 64): Join<Long, Int> {
    require(bitSize in 0..16) { "Bit size must be 0–16" }
    val totalBits = 4 + bitSize // 4-bit indicator + payload
    return if (totalBits <= targetRegisterSize - 2) {
        val packed = ((bitSize.toLong() - 1) shl bitSize) or (value and ((1L shl bitSize) - 1))
        PackedBits(packed)
    } else {
        spill(PackedBits(value), bitSize, targetRegisterSize)
    }
}

inline fun <reified A, reified B> join(a: A, b: B, targetRegisterSize: Int = 64): Join<A, B> {
    val leftBits = if (a == null) 0 else 32
    val rightBits = if (b == null) 0 else 32
    val packed = ((a?.hashCode()?.toLong() ?: 0L) shl 32) or (b?.hashCode()?.toLong() ?: 0L and 0xFFFFFFFFL)
    val totalBits = 2 + leftBits + 2 + rightBits
    return if (totalBits <= targetRegisterSize - 2) {
        val indicator = (if (leftBits > 0) 1L else 0L) shl (rightBits + 2) or (if (rightBits > 0) 1L else 0L) shl rightBits
        PackedBits(indicator shl rightBits or packed)
    } else {
        spill(PackedBits(packed), leftBits + rightBits, targetRegisterSize)
    }
}

inline fun join(a: PackedBits, b: PackedBits, targetRegisterSize: Int = 64): Join<PackedBits, PackedBits> {
    val leftBits = 32 // Assume fixed size for PackedBits
    val rightBits = 32
    val packed = (a.value shl 32) or (b.value and 0xFFFFFFFFL)
    val totalBits = 2 + leftBits + 2 + rightBits
    return if (totalBits <= targetRegisterSize - 2) {
        val indicator = (1L shl (rightBits + 2)) or (1L shl rightBits)
        PackedBits(indicator shl rightBits or packed)
    } else {
        spill(PackedBits(packed), leftBits + rightBits, targetRegisterSize)
    }
}

// Series factory
inline fun <reified T> joinSeries(size: Int, crossinline accessor: (Int) -> T, bitSizePerElement: Int = 32, targetRegisterSize: Int = 64): Series<T> {
    require(bitSizePerElement in 0..32) { "Element bit size must be 0–32" }
    require(size >= 0) { "Size must be non-negative" }

    // Free storage: Pack size + first value
    if (size <= 1 && bitSizePerElement <= 31 && (1 + 32 + bitSizePerElement) <= targetRegisterSize) {
        val firstValue = if (size > 0) accessor(0).hashCode().toLong() and ((1L shl bitSizePerElement) - 1) else 0L
        val packed = (size.toLong() shl bitSizePerElement) or firstValue
        return PackedBits(1L shl (32 + bitSizePerElement) or packed) // 1-bit indicator
    }

    // Powers of 2 series lengths
    val totalBits = size * bitSizePerElement
    if (totalBits <= targetRegisterSize - 2) {
        var packed = 0L
        for (i in 0 until size) {
            val value = accessor(i).hashCode().toLong() and ((1L shl bitSizePerElement) - 1)
            packed = packed or (value shl (i * bitSizePerElement))
        }
        return PackedBits(1L shl totalBits or packed) // 1-bit indicator
    } else {
        // Spill: Encode first chunk
        val chunkSize = minOf(size, (targetRegisterSize - 3) / bitSizePerElement)
        var chunkValue = 0L
        for (i in 0 until chunkSize) {
            val value = accessor(i).hashCode().toLong() and ((1L shl bitSizePerElement) - 1)
            chunkValue = chunkValue or (value shl (i * bitSizePerElement))
        }
        val continuationBit = if (size > chunkSize) 1L else 0L
        return PackedBits((continuationBit shl (chunkSize * bitSizePerElement + 2)) or ((chunkSize.toLong() - 1) shl (chunkSize * bitSizePerElement)) or chunkValue)
    }
}

inline fun joinLarge(value: Long, bitSize: Int, targetRegisterSize: Int = 64): Join<Long, Int> {
    if (bitSize <= 16) return join(value, bitSize, targetRegisterSize)
    return spill(PackedBits(value), bitSize, targetRegisterSize)
}

// Inline transcoding
inline fun transcode(bits: PackedBits, targetRegisterSize: Int): PackedBits {
    return spill(bits, 64, targetRegisterSize)
}

// Example usage
fun main() {
    // Series: 2 Ints (64 bits)
    val seriesFree = joinSeries(1, { 42 }, bitSizePerElement = 31)
    println("Free Series: ${seriesFree.value.toString(2).padStart(64, '0')} (length: ${1 + seriesFree.getSizeAndValue(31).second.toInt()})") // 1+32+31=64

    // Series: 16 x 4-bit (64 bits)
    val series4bit = joinSeries(16, { i -> i % 16 }, bitSizePerElement = 4)
    println("4-bit Series (16): ${series4bit.value.toString(2).padStart(64, '0')} (length: ${1 + series4bit.getSizeAndValue(4).second.toInt()})") // 1+64=65

    // Series: 8 x 4-bit (32-bit kernel)
    val series32bit = transcode(joinSeries(8, { i -> i % 16 }, bitSizePerElement = 4), 32)
    println("4-bit Series (8, 32-bit): ${series32bit.value.toString(2).padStart(32, '0')}")

    // Int join (64-bit)
    val intJoin = join(42, 17)
    println("Int join: ${intJoin.value.toString(2).padStart(64, '0')}")

    // 6-bit value 63
    val value63 = join(63L, 6)
    println("63 (6-bit): ${value63.value.toString(2).padStart(64, '0')}")

    // Nested join: Int j (Byte j Byte)
    val bc = join(10.toByte(), 20.toByte())
    val abc = join(42, bc)
    println("a j (b j c): ${abc.value.toString(2).padStart(64, '0')}")

    // 63-bit value (spills)
    val large63 = joinLarge((1L shl 63) - 1, 63)
    println("63-bit value: ${large63.value.toString(2).padStart(64, '0')} (continuation: ${large63.hasContinuation()})")
}
