package borg.trikeshed.crypto.hash

import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j

/**
 * SHA256 Hash Algorithm Implementation
 * 
 * Provides multiplatform SHA256 hashing following the CCEK pattern.
 * SHA256 is a cryptographically secure hash function.
 * 
 * SIMD-Optimized SHA256 Implementation
 * Uses big arrays and vectorization-friendly loops for autovec
 */
object SHA256Hasher {
    fun hash(data: Indexed<Byte>): Indexed<Byte> {
        // Convert Indexed to big array for SIMD optimization
        val inputArray = ByteArray(data.size) { data[it] }
        val result = sha256Hash(inputArray)
        return result.size j { result[it] }
    }
    
    private fun sha256Hash(input: ByteArray): ByteArray {
        // SHA256 constants for SIMD-friendly processing
        val h = IntArray(8) { i ->
            when (i) {
                0 -> 0x6a09e667
                1 -> 0xbb67ae85
                2 -> 0x3c6ef372
                3 -> 0xa54ff53a
                4 -> 0x510e527f
                5 -> 0x9b05688c
                6 -> 0x1f83d9ab
                7 -> 0x5be0cd19
                else -> 0
            }
        }
        
        val k = IntArray(64) { i ->
            when (i) {
                0 -> 0x428a2f98; 1 -> 0x71374491; 2 -> 0xb5c0fbcf; 3 -> 0xe9b5dba5
                4 -> 0x3956c25b; 5 -> 0x59f111f1; 6 -> 0x923f82a4; 7 -> 0xab1c5ed5
                8 -> 0xd807aa98; 9 -> 0x12835b01; 10 -> 0x243185be; 11 -> 0x550c7dc3
                12 -> 0x72be5d74; 13 -> 0x80deb1fe; 14 -> 0x9bdc06a7; 15 -> 0xc19bf174
                16 -> 0xe49b69c1; 17 -> 0xefbe4786; 18 -> 0x0fc19dc6; 19 -> 0x240ca1cc
                20 -> 0x2de92c6f; 21 -> 0x4a7484aa; 22 -> 0x5cb0a9dc; 23 -> 0x76f988da
                24 -> 0x983e5152; 25 -> 0xa831c66d; 26 -> 0xb00327c8; 27 -> 0xbf597fc7
                28 -> 0xc6e00bf3; 29 -> 0xd5a79147; 30 -> 0x06ca6351; 31 -> 0x14292967
                32 -> 0x27b70a85; 33 -> 0x2e1b2138; 34 -> 0x4d2c6dfc; 35 -> 0x53380d13
                36 -> 0x650a7354; 37 -> 0x766a0abb; 38 -> 0x81c2c92e; 39 -> 0x92722c85
                40 -> 0xa2bfe8a1; 41 -> 0xa81a664b; 42 -> 0xc24b8b70; 43 -> 0xc76c51a3
                44 -> 0xd192e819; 45 -> 0xd6990624; 46 -> 0xf40e3585; 47 -> 0x106aa070
                48 -> 0x19a4c116; 49 -> 0x1e376c08; 50 -> 0x2748774c; 51 -> 0x34b0bcb5
                52 -> 0x391c0cb3; 53 -> 0x4ed8aa4a; 54 -> 0x5b9cca4f; 55 -> 0x682e6ff3
                56 -> 0x748f82ee; 57 -> 0x78a5636f; 58 -> 0x84c87814; 59 -> 0x8cc70208
                60 -> 0x90befffa; 61 -> 0xa4506ceb; 62 -> 0xbef9a3f7; 63 -> 0xc67178f2
                else -> 0
            }
        }
        
        // Process data in 64-byte blocks (SIMD-friendly)
        val blockSize = 64
        val blocks = (input.size + blockSize - 1) / blockSize
        
        // Vectorized block processing
        for (block in 0 until blocks) {
            val blockStart = block * blockSize
            val blockEnd = minOf(blockStart + blockSize, input.size)
            val blockData = ByteArray(blockSize) { i ->
                if (blockStart + i < input.size) input[blockStart + i] else 0
            }
            
            // SIMD-friendly SHA256 block processing
            processSHA256Block(blockData, h, k)
        }
        
        // Convert to big-endian bytes (SIMD-friendly)
        return ByteArray(32) { i ->
            val hashIndex = i / 4
            val byteIndex = i % 4
            (h[hashIndex] shr ((3 - byteIndex) * 8)).toByte()
        }
    }
    
    private fun processSHA256Block(block: ByteArray, h: IntArray, k: IntArray) {
        // SIMD-friendly message schedule preparation
        val w = IntArray(64) { i ->
            if (i < 16) {
                val offset = i * 4
                (block[offset].toInt() and 0xFF shl 24) or
                (block[offset + 1].toInt() and 0xFF shl 16) or
                (block[offset + 2].toInt() and 0xFF shl 8) or
                (block[offset + 3].toInt() and 0xFF)
            } else {
                val s0 = rotateRight(w[i - 15], 7) xor rotateRight(w[i - 15], 18) xor (w[i - 15] ushr 3)
                val s1 = rotateRight(w[i - 2], 17) xor rotateRight(w[i - 2], 19) xor (w[i - 2] ushr 10)
                w[i - 16] + s0 + w[i - 7] + s1
            }
        }
        
        var a = h[0]
        var b = h[1]
        var c = h[2]
        var d = h[3]
        var e = h[4]
        var f = h[5]
        var g = h[6]
        var hh = h[7]
        
        // SIMD-friendly compression function
        for (i in 0 until 64) {
            val S1 = rotateRight(e, 6) xor rotateRight(e, 11) xor rotateRight(e, 25)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = hh + S1 + ch + k[i] + w[i]
            val S0 = rotateRight(a, 2) xor rotateRight(a, 13) xor rotateRight(a, 22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = S0 + maj
            
            hh = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }
        
        // Update hash values
        h[0] += a
        h[1] += b
        h[2] += c
        h[3] += d
        h[4] += e
        h[5] += f
        h[6] += g
        h[7] += hh
    }
    
    private fun rotateRight(value: Int, shift: Int): Int = (value ushr shift) or (value shl (32 - shift))
}

/**
 * Extension function for easy SHA256 hashing of any object
 */
val Any?.sha256Hash: Indexed<Byte>
    get() {
        val s = this?.toString() ?: "null"
        val ba = s.encodeToByteArray()
        return SHA256Hasher.hash(ba.size j { ba[it] })
    }

/**
 * Extension function for SHA256 hash as hex string
 */
val Any?.sha256Hex: String get() = this.sha256Hash.hex 