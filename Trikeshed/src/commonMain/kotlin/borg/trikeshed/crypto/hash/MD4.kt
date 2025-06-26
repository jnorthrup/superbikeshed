package borg.trikeshed.crypto.hash

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j

/**
 * MD4 Hash Algorithm Implementation
 * 
 * Provides multiplatform MD4 hashing following the CCEK pattern.
 * MD4 is a legacy hash algorithm, primarily used for compatibility.
 */
expect object MD4Hasher {
    /**
     * Computes the MD4 hash of the input data.
     * @param data The input byte array.
     * @return A byte array representing the MD4 hash (16 bytes).
     */
    fun hash(data: Indexed<Byte>): Indexed<Byte>
}

/**
 * SIMD-Optimized MD4 Implementation
 * Uses big arrays and vectorization-friendly loops for autovec
 */
actual object MD4Hasher {
    actual fun hash(data: Indexed<Byte>): Indexed<Byte> {
        // Convert Indexed to big array for SIMD optimization
        val inputArray = ByteArray(data.size) { data[it] }
        val result = md4Hash(inputArray)
        return result.size j { result[it] }
    }
    
    private fun md4Hash(input: ByteArray): ByteArray {
        // MD4 constants for SIMD-friendly processing
        val A = 0x67452301
        val B = 0xEFCDAB89
        val C = 0x98BADCFE
        val D = 0x10325476
        
        // Process data in 64-byte blocks (SIMD-friendly)
        val blockSize = 64
        val blocks = (input.size + blockSize - 1) / blockSize
        
        var a = A
        var b = B
        var c = C
        var d = D
        
        // Vectorized block processing
        for (block in 0 until blocks) {
            val blockStart = block * blockSize
            val blockEnd = minOf(blockStart + blockSize, input.size)
            val blockData = ByteArray(blockSize) { i ->
                if (blockStart + i < input.size) input[blockStart + i] else 0
            }
            
            // SIMD-friendly MD4 round functions
            val (newA, newB, newC, newD) = processMD4Block(blockData, a, b, c, d)
            a = newA
            b = newB
            c = newC
            d = newD
        }
        
        // Convert to little-endian bytes (SIMD-friendly)
        return ByteArray(16) { i ->
            when (i / 4) {
                0 -> (a shr (i % 4 * 8)).toByte()
                1 -> (b shr (i % 4 * 8)).toByte()
                2 -> (c shr (i % 4 * 8)).toByte()
                3 -> (d shr (i % 4 * 8)).toByte()
                else -> 0
            }
        }
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
        
        // Round 1: SIMD-friendly operations
        for (i in 0..3) {
            aa = rotateLeft(aa + f(bb, cc, dd) + words[i * 4], 3)
            dd = rotateLeft(dd + f(aa, bb, cc) + words[i * 4 + 1], 7)
            cc = rotateLeft(cc + f(dd, aa, bb) + words[i * 4 + 2], 11)
            bb = rotateLeft(bb + f(cc, dd, aa) + words[i * 4 + 3], 19)
        }
        
        // Round 2: SIMD-friendly operations
        for (i in 0..3) {
            aa = rotateLeft(aa + g(bb, cc, dd) + words[i] + 0x5A827999, 3)
            dd = rotateLeft(dd + g(aa, bb, cc) + words[i + 4] + 0x5A827999, 5)
            cc = rotateLeft(cc + g(dd, aa, bb) + words[i + 8] + 0x5A827999, 9)
            bb = rotateLeft(bb + g(cc, dd, aa) + words[i + 12] + 0x5A827999, 13)
        }
        
        // Round 3: SIMD-friendly operations
        for (i in 0..3) {
            aa = rotateLeft(aa + h(bb, cc, dd) + words[i * 4] + 0x6ED9EBA1, 3)
            dd = rotateLeft(dd + h(aa, bb, cc) + words[i * 4 + 8] + 0x6ED9EBA1, 9)
            cc = rotateLeft(cc + h(dd, aa, bb) + words[i * 4 + 4] + 0x6ED9EBA1, 11)
            bb = rotateLeft(bb + h(cc, dd, aa) + words[i * 4 + 12] + 0x6ED9EBA1, 15)
        }
        
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
fun hexLate(v: Int): Char = if (v < 0xa) ('0'.code + v).toChar() 
else ('a'.code + (v - 0xa)).toChar()

val Indexed<Byte>.hex: String
    get() {
        // Convert to big array for SIMD optimization
        val dataArray = ByteArray(size) { this[it] }
        val result = CharArray(size * 2)
        
        // Vectorized hex conversion loop
        for (ix in 0 until size) {
            val byteInt = dataArray[ix].toInt() and 0xFF
            val os = ix * 2
            result[os] = hexLate((byteInt shr 4) and 0x0F)
            result[os + 1] = hexLate(byteInt and 0x0F)
        }
        return String(result)
    } 