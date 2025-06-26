@file:OptIn(ExperimentalUnsignedTypes::class)
package borg.trikeshed.crypto.hash


import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import borg.trikeshed.dht.kademlia.id.NUID.Companion.toHex

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
        val inputArray = ByteArray(data.a) { i -> data[i] }
        val result = sha256Hash(inputArray)
        return result.size j { i: Int -> result[i] }
    }
    
    private fun sha256Hash(input: ByteArray): ByteArray {
        // SHA256 constants for SIMD-friendly processing
        val h = IntArray(8) { i ->
            when (i) {
                0 -> 0x6a09e667.toInt()
                1 -> 0xbb67ae85.toInt()
                2 -> 0x3c6ef372.toInt()
                3 -> 0xa54ff53a.toInt()
                4 -> 0x510e527f.toInt()
                5 -> 0x9b05688c.toInt()
                6 -> 0x1f83d9ab.toInt()
                7 -> 0x5be0cd19.toInt()
                else -> 0
            }
        }
        
        val k = IntArray(64) { i: Int ->
            when (i) {
                0 -> 0x428a2f98.toInt(); 1 -> 0x71374491.toInt(); 2 -> 0xb5c0fbcf.toInt(); 3 -> 0xe9b5dba5.toInt()
                4 -> 0x3956c25b.toInt(); 5 -> 0x59f111f1.toInt(); 6 -> 0x923f82a4.toInt(); 7 -> 0xab1c5ed5.toInt()
                8 -> 0xd807aa98.toInt(); 9 -> 0x12835b01.toInt(); 10 -> 0x243185be.toInt(); 11 -> 0x550c7dc3.toInt()
                12 -> 0x72be5d74.toInt(); 13 -> 0x80deb1fe.toInt(); 14 -> 0x9bdc06a7.toInt(); 15 -> 0xc19bf174.toInt()
                16 -> 0xe49b69c1.toInt(); 17 -> 0xefbe4786.toInt(); 18 -> 0x0fc19dc6.toInt(); 19 -> 0x240ca1cc.toInt()
                20 -> 0x2de92c6f.toInt(); 21 -> 0x4a7484aa.toInt(); 22 -> 0x5cb0a9dc.toInt(); 23 -> 0x76f988da.toInt()
                24 -> 0x983e5152.toInt(); 25 -> 0xa831c66d.toInt(); 26 -> 0xb00327c8.toInt(); 27 -> 0xbf597fc7.toInt()
                28 -> 0xc6e00bf3.toInt(); 29 -> 0xd5a79147.toInt(); 30 -> 0x06ca6351.toInt(); 31 -> 0x14292967.toInt()
                32 -> 0x27b70a85.toInt(); 33 -> 0x2e1b2138.toInt(); 34 -> 0x4d2c6dfc.toInt(); 35 -> 0x53380d13.toInt()
                36 -> 0x650a7354.toInt(); 37 -> 0x766a0abb.toInt(); 38 -> 0x81c2c92e.toInt(); 39 -> 0x92722c85.toInt()
                40 -> 0xa2bfe8a1.toInt(); 41 -> 0xa81a664b.toInt(); 42 -> 0xc24b8b70.toInt(); 43 -> 0xc76c51a3.toInt()
                44 -> 0xd192e819.toInt(); 45 -> 0xd6990624.toInt(); 46 -> 0xf40e3585.toInt(); 47 -> 0x106aa070.toInt()
                48 -> 0x19a4c116.toInt(); 49 -> 0x1e376c08.toInt(); 50 -> 0x2748774c.toInt(); 51 -> 0x34b0bcb5.toInt()
                52 -> 0x391c0cb3.toInt(); 53 -> 0x4ed8aa4a.toInt(); 54 -> 0x5b9cca4f.toInt(); 55 -> 0x682e6ff3.toInt()
                56 -> 0x748f82ee.toInt(); 57 -> 0x78a5636f.toInt(); 58 -> 0x84c87814.toInt(); 59 -> 0x8cc70208.toInt()
                60 -> 0x90befffa.toInt(); 61 -> 0xa4506ceb.toInt(); 62 -> 0xbef9a3f7.toInt(); 63 -> 0xc67178f2.toInt()
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
        val w = IntArray(64)
        
        // Initialize first 16 words from block data
        for (i in 0 until 16) {
            val offset = i * 4
            w[i] = (block[offset].toInt() and 0xFF shl 24) or
                   (block[offset + 1].toInt() and 0xFF shl 16) or
                   (block[offset + 2].toInt() and 0xFF shl 8) or
                   (block[offset + 3].toInt() and 0xFF)
        }
        
        // Extend the first 16 words into the remaining 48 words
        for (i in 16 until 64) {
            val s0 = rotateRight(w[i - 15], 7) xor rotateRight(w[i - 15], 18) xor (w[i - 15] ushr 3)
            val s1 = rotateRight(w[i - 2], 17) xor rotateRight(w[i - 2], 19) xor (w[i - 2] ushr 10)
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
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
val Any?.sha256Hex: String get() = this.sha256Hash.toHex() 