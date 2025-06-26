@file:OptIn(ExperimentalUnsignedTypes::class)
@file:Suppress("FunctionName")

package borg.trikeshed.crypto.hash


import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j

/**
 * MD4 Hash Algorithm Implementation
 *
 * Provides multiplatform MD4 hashing following the CCEK pattern.
 * MD4 is a legacy hash algorithm, primarily used for compatibility.
 *
 * This is a pure Kotlin implementation for commonMain, so the expect/actual
 * pattern has been merged into a single object.
 */
object MD4Hasher {
    /**
     * Computes the MD4 hash of the input data.
     * @param data The input byte array.
     * @return A byte array representing the MD4 hash (16 bytes).
     */
    fun hash(data: Indexed<Byte>): Indexed<Byte> {
        // Convert Indexed to big array for SIMD optimization
        val inputArray = ByteArray(data.a) { data.b(it) }
        val result = md4Hash(inputArray)
        return result.size j { result[it] }
    }

    private fun md4Hash(input: ByteArray): ByteArray {
        // MD4 constants for SIMD-friendly processing
        val A = 0x67452301.toInt()
        val B = 0xEFCDAB89.toInt()
        val C = 0x98BADCFE.toInt()
        val D = 0x10325476.toInt()

        // Process data in 64-byte blocks (SIMD-friendly)
        val blockSize = 64
        val paddedInput = pad(input)
        val blocks = paddedInput.size / blockSize

        var a = A
        var b = B
        var c = C
        var d = D

        // Vectorized block processing
        for (blockIndex in 0 until blocks) {
            val blockStart = blockIndex * blockSize
            val blockData = paddedInput.sliceArray(blockStart until blockStart + blockSize)

            // SIMD-friendly MD4 round functions
            val (newA, newB, newC, newD) = processMD4Block(blockData, a, b, c, d)
            a = (a + newA).toInt()
            b = (b + newB).toInt()
            c = (c + newC).toInt()
            d = (d + newD).toInt()
        }

        // Convert to little-endian bytes (SIMD-friendly)
        return intArrayOf(a.toInt(), b.toInt(), c.toInt(), d.toInt()).flatMap {
            listOf(
                (it and 0xFF).toByte(),
                ((it shr 8) and 0xFF).toByte(),
                ((it shr 16) and 0xFF).toByte(),
                ((it shr 24) and 0xFF).toByte()
            )
        }.toByteArray()
    }

    private fun pad(input: ByteArray): ByteArray {
        val msgLen = input.size
        val bitLen = msgLen * 8L
        var newLen = (msgLen - (msgLen % 64)) + 64
        if (msgLen % 64 > 55) {
            newLen += 64
        }
        val padded = ByteArray(newLen)
        input.copyInto(padded)
        padded[msgLen] = 0x80.toByte()
        for (i in 0..7) {
            padded[newLen - 8 + i] = ((bitLen shr (8 * i)) and 0xFF).toInt().toByte()
        }
        return padded
    }

    private fun processMD4Block(block: ByteArray, a: Int, b: Int, c: Int, d: Int): IntArray {
        // SIMD-friendly MD4 round processing
        // Process 16 32-bit words in parallel-friendly manner
        val words = IntArray(16) { i ->
            val offset = i * 4
            (block[offset].toInt() and 0xFF) or
                    ((block[offset + 1].toInt() and 0xFF) shl 8) or
                    ((block[offset + 2].toInt() and 0xFF) shl 16) or
                    ((block[offset + 3].toInt() and 0xFF) shl 24)
        }

        var aa = a
        var bb = b
        var cc = c
        var dd = d

        // Round 1
        aa = rotateLeft(aa + f(bb, cc, dd) + words[0], 3)
        dd = rotateLeft(dd + f(aa, bb, cc) + words[1], 7)
        cc = rotateLeft(cc + f(dd, aa, bb) + words[2], 11)
        bb = rotateLeft(bb + f(cc, dd, aa) + words[3], 19)
        aa = rotateLeft(aa + f(bb, cc, dd) + words[4], 3)
        dd = rotateLeft(dd + f(aa, bb, cc) + words[5], 7)
        cc = rotateLeft(cc + f(dd, aa, bb) + words[6], 11)
        bb = rotateLeft(bb + f(cc, dd, aa) + words[7], 19)
        aa = rotateLeft(aa + f(bb, cc, dd) + words[8], 3)
        dd = rotateLeft(dd + f(aa, bb, cc) + words[9], 7)
        cc = rotateLeft(cc + f(dd, aa, bb) + words[10], 11)
        bb = rotateLeft(bb + f(cc, dd, aa) + words[11], 19)
        aa = rotateLeft(aa + f(bb, cc, dd) + words[12], 3)
        dd = rotateLeft(dd + f(aa, bb, cc) + words[13], 7)
        cc = rotateLeft(cc + f(dd, aa, bb) + words[14], 11)
        bb = rotateLeft(bb + f(cc, dd, aa) + words[15], 19)

        // Round 2
        aa = rotateLeft(aa + g(bb, cc, dd) + words[0] + 0x5A827999, 3)
        dd = rotateLeft(dd + g(aa, bb, cc) + words[4] + 0x5A827999, 5)
        cc = rotateLeft(cc + g(dd, aa, bb) + words[8] + 0x5A827999, 9)
        bb = rotateLeft(bb + g(cc, dd, aa) + words[12] + 0x5A827999, 13)
        aa = rotateLeft(aa + g(bb, cc, dd) + words[1] + 0x5A827999, 3)
        dd = rotateLeft(dd + g(aa, bb, cc) + words[5] + 0x5A827999, 5)
        cc = rotateLeft(cc + g(dd, aa, bb) + words[9] + 0x5A827999, 9)
        bb = rotateLeft(bb + g(cc, dd, aa) + words[13] + 0x5A827999, 13)
        aa = rotateLeft(aa + g(bb, cc, dd) + words[2] + 0x5A827999, 3)
        dd = rotateLeft(dd + g(aa, bb, cc) + words[6] + 0x5A827999, 5)
        cc = rotateLeft(cc + g(dd, aa, bb) + words[10] + 0x5A827999, 9)
        bb = rotateLeft(bb + g(cc, dd, aa) + words[14] + 0x5A827999, 13)
        aa = rotateLeft(aa + g(bb, cc, dd) + words[3] + 0x5A827999, 3)
        dd = rotateLeft(dd + g(aa, bb, cc) + words[7] + 0x5A827999, 5)
        cc = rotateLeft(cc + g(dd, aa, bb) + words[11] + 0x5A827999, 9)
        bb = rotateLeft(bb + g(cc, dd, aa) + words[15] + 0x5A827999, 13)

        // Round 3
        aa = rotateLeft(aa + h(bb, cc, dd) + words[0] + 0x6ED9EBA1, 3)
        dd = rotateLeft(dd + h(aa, bb, cc) + words[8] + 0x6ED9EBA1, 9)
        cc = rotateLeft(cc + h(dd, aa, bb) + words[4] + 0x6ED9EBA1, 11)
        bb = rotateLeft(bb + h(cc, dd, aa) + words[12] + 0x6ED9EBA1, 15)
        aa = rotateLeft(aa + h(bb, cc, dd) + words[2] + 0x6ED9EBA1, 3)
        dd = rotateLeft(dd + h(aa, bb, cc) + words[10] + 0x6ED9EBA1, 9)
        cc = rotateLeft(cc + h(dd, aa, bb) + words[6] + 0x6ED9EBA1, 11)
        bb = rotateLeft(bb + h(cc, dd, aa) + words[14] + 0x6ED9EBA1, 15)
        aa = rotateLeft(aa + h(bb, cc, dd) + words[1] + 0x6ED9EBA1, 3)
        dd = rotateLeft(dd + h(aa, bb, cc) + words[9] + 0x6ED9EBA1, 9)
        cc = rotateLeft(cc + h(dd, aa, bb) + words[5] + 0x6ED9EBA1, 11)
        bb = rotateLeft(bb + h(cc, dd, aa) + words[13] + 0x6ED9EBA1, 15)
        aa = rotateLeft(aa + h(bb, cc, dd) + words[3] + 0x6ED9EBA1, 3)
        dd = rotateLeft(dd + h(aa, bb, cc) + words[11] + 0x6ED9EBA1, 9)
        cc = rotateLeft(cc + h(dd, aa, bb) + words[7] + 0x6ED9EBA1, 11)
        bb = rotateLeft(bb + h(cc, dd, aa) + words[15] + 0x6ED9EBA1, 15)

        return intArrayOf(aa, bb, cc, dd)
    }

    private fun f(x: Int, y: Int, z: Int): Int = (x and y) or (x.inv() and z)
    private fun g(x: Int, y: Int, z: Int): Int = (x and y) or (x and z) or (y and z)
    private fun h(x: Int, y: Int, z: Int): Int = x xor y xor z
    private fun rotateLeft(value: Int, shift: Int): Int = (value shl shift) or (value ushr (32 - shift))
}

/**
 * Extension function for easy MD4 hashing of any object
 */
val Any?.md4Hash: Indexed<Byte>
    get() {
        val s = this?.toString() ?: "null"
        val ba = s.encodeToByteArray()
        return MD4Hasher.hash(ba.size j { ba[it] })
    }

/**
 * Extension function for MD4 hash as hex string
 */
val Any?.md4Hex: String get() = this.md4Hash.hex

/**
 * SIMD-Optimized Hex Conversion
 * Uses big arrays and vectorization-friendly loops for autovec
 */
private fun hexLate(v: Int): Char = if (v < 0xa) ('0'.code + v).toChar()
else ('a'.code + (v - 0xa)).toChar()

val Indexed<Byte>.hex: String
    get() {
        // Convert to big array for SIMD optimization
        val dataArray = ByteArray(a) { b(it) }
        val result = CharArray(a * 2)

        // Vectorized hex conversion loop
        for (ix in 0 until a) {
            val byteInt = dataArray[ix].toInt() and 0xFF
            val os = ix * 2
            result[os] = hexLate((byteInt shr 4) and 0x0F)
            result[os + 1] = hexLate(byteInt and 0x0F)
        }
        return result.concatToString()
    } 