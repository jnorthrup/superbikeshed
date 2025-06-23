package borg.trikeshed.util

// Pure Kotlin AES-GCM AEAD implementation for commonMain
// Reference: NIST SP 800-38D, FIPS-197, public domain code
// Not constant-time, not hardened for production, but correct for research and multiplatform

class AesGcm(private val key: ByteArray) {
    init {
        require(key.size == 16 || key.size == 24 || key.size == 32) { "Key must be 16, 24, or 32 bytes" }
    }

    fun encrypt(nonce: ByteArray, plaintext: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        require(nonce.size == 12) { "Nonce must be 12 bytes (96 bits)" }
        val gcm = GcmBlockCipher(AesEngine(key))
        return gcm.encrypt(nonce, plaintext, aad)
    }

    fun decrypt(nonce: ByteArray, ciphertextWithTag: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray? {
        require(nonce.size == 12) { "Nonce must be 12 bytes (96 bits)" }
        val gcm = GcmBlockCipher(AesEngine(key))
        return gcm.decrypt(nonce, ciphertextWithTag, aad)
    }
}

// --- AES block cipher (ECB, 128/192/256) ---
private class AesEngine(private val key: ByteArray) {
    private val roundKeys: IntArray
    private val rounds: Int

    init {
        val nk = key.size / 4
        rounds = when (nk) { 4 -> 10; 6 -> 12; 8 -> 14; else -> throw IllegalArgumentException("Invalid AES key size") }
        roundKeys = keyExpansion(key)
    }

    fun encryptBlock(input: ByteArray, inOff: Int, output: ByteArray, outOff: Int) {
        val state = IntArray(16)
        for (i in 0 until 16) state[i] = input[inOff + i].toInt() and 0xff
        addRoundKey(state, 0)
        for (round in 1 until rounds) {
            subBytes(state)
            shiftRows(state)
            mixColumns(state)
            addRoundKey(state, round)
        }
        subBytes(state)
        shiftRows(state)
        addRoundKey(state, rounds)
        for (i in 0 until 16) output[outOff + i] = state[i].toByte()
    }

    // --- AES internals ---
    private fun keyExpansion(key: ByteArray): IntArray {
        val nk = key.size / 4
        val nb = 4
        val nr = rounds
        val w = IntArray(nb * (nr + 1))
        for (i in 0 until nk) {
            w[i] = (key[4 * i].toInt() and 0xff shl 24) or
                   (key[4 * i + 1].toInt() and 0xff shl 16) or
                   (key[4 * i + 2].toInt() and 0xff shl 8) or
                   (key[4 * i + 3].toInt() and 0xff)
        }
        for (i in nk until nb * (nr + 1)) {
            var temp = w[i - 1]
            if (i % nk == 0) {
                temp = subWord(rotWord(temp)) xor RCON[i / nk]
            } else if (nk > 6 && i % nk == 4) {
                temp = subWord(temp)
            }
            w[i] = w[i - nk] xor temp
        }
        return w
    }

    private fun addRoundKey(state: IntArray, round: Int) {
        for (i in 0 until 4) {
            val rk = roundKeys[round * 4 + i]
            state[4 * i] = state[4 * i] xor (rk ushr 24 and 0xff)
            state[4 * i + 1] = state[4 * i + 1] xor (rk ushr 16 and 0xff)
            state[4 * i + 2] = state[4 * i + 2] xor (rk ushr 8 and 0xff)
            state[4 * i + 3] = state[4 * i + 3] xor (rk and 0xff)
        }
    }

    private fun subBytes(state: IntArray) {
        for (i in 0 until 16) state[i] = SBOX[state[i]]
    }

    private fun shiftRows(state: IntArray) {
        val t = IntArray(16)
        for (i in 0 until 16) t[i] = state[i]
        for (i in 0 until 4) {
            for (j in 0 until 4) {
                state[4 * i + j] = t[4 * i + (j + i) % 4]
            }
        }
    }

    private fun mixColumns(state: IntArray) {
        for (i in 0 until 4) {
            val s0 = state[4 * i]
            val s1 = state[4 * i + 1]
            val s2 = state[4 * i + 2]
            val s3 = state[4 * i + 3]
            state[4 * i] = mul2(s0) xor mul3(s1) xor s2 xor s3
            state[4 * i + 1] = s0 xor mul2(s1) xor mul3(s2) xor s3
            state[4 * i + 2] = s0 xor s1 xor mul2(s2) xor mul3(s3)
            state[4 * i + 3] = mul3(s0) xor s1 xor s2 xor mul2(s3)
        }
    }

    private fun subWord(w: Int): Int =
        (SBOX[w ushr 24 and 0xff] shl 24) or
        (SBOX[w ushr 16 and 0xff] shl 16) or
        (SBOX[w ushr 8 and 0xff] shl 8) or
        (SBOX[w and 0xff])

    private fun rotWord(w: Int): Int = (w shl 8) or (w ushr 24)

    companion object {
        private val SBOX = intArrayOf(
            0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
            0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0, 0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
            0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc, 0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
            0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a, 0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75,
            0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0, 0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84,
            0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b, 0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
            0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85, 0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8,
            0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5, 0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2,
            0xcd, 0x0c, 0x13, 0xec, 0x5f, 0x97, 0x44, 0x17, 0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
            0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88, 0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb,
            0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c, 0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
            0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9, 0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
            0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6, 0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a,
            0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e, 0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e,
            0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94, 0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
            0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68, 0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16
        )
        private val RCON = intArrayOf(
            0x00000000, 0x01000000, 0x02000000, 0x04000000, 0x08000000, 0x10000000, 0x20000000, 0x40000000, 0x80000000, 0x1b000000, 0x36000000
        )
        private fun mul2(x: Int): Int = if (x < 0x80) x shl 1 else (x shl 1) xor 0x11b and 0xff
        private fun mul3(x: Int): Int = mul2(x) xor x
    }
}

// --- GCM mode ---
private class GcmBlockCipher(private val blockCipher: AesEngine) {
    fun encrypt(nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val tagLen = 16
        val j0 = ByteArray(16)
        nonce.copyInto(j0, 0, 0, 12)
        j0[15] = 1
        val ctr = j0.copyOf()
        val ciphertext = ByteArray(plaintext.size)
        var counter = 1
        for (i in plaintext.indices step 16) {
            inc32(ctr)
            val keystream = ByteArray(16)
            blockCipher.encryptBlock(ctr, 0, keystream, 0)
            val blockLen = minOf(16, plaintext.size - i)
            for (j in 0 until blockLen) ciphertext[i + j] = (plaintext[i + j].toInt() xor keystream[j].toInt()).toByte()
        }
        val s = ghash(blockCipher, aad, ciphertext)
        val tag = ByteArray(tagLen)
        val eJ0 = ByteArray(16)
        blockCipher.encryptBlock(j0, 0, eJ0, 0)
        for (i in 0 until tagLen) tag[i] = (s[i].toInt() xor eJ0[i].toInt()).toByte()
        return ciphertext + tag
    }
    fun decrypt(nonce: ByteArray, ciphertextWithTag: ByteArray, aad: ByteArray): ByteArray? {
        val tagLen = 16
        if (ciphertextWithTag.size < tagLen) return null
        val ciphertext = ciphertextWithTag.copyOfRange(0, ciphertextWithTag.size - tagLen)
        val tag = ciphertextWithTag.copyOfRange(ciphertextWithTag.size - tagLen, ciphertextWithTag.size)
        val j0 = ByteArray(16)
        nonce.copyInto(j0, 0, 0, 12)
        j0[15] = 1
        val ctr = j0.copyOf()
        val plaintext = ByteArray(ciphertext.size)
        var counter = 1
        for (i in ciphertext.indices step 16) {
            inc32(ctr)
            val keystream = ByteArray(16)
            blockCipher.encryptBlock(ctr, 0, keystream, 0)
            val blockLen = minOf(16, ciphertext.size - i)
            for (j in 0 until blockLen) plaintext[i + j] = (ciphertext[i + j].toInt() xor keystream[j].toInt()).toByte()
        }
        val s = ghash(blockCipher, aad, ciphertext)
        val eJ0 = ByteArray(16)
        blockCipher.encryptBlock(j0, 0, eJ0, 0)
        val expectedTag = ByteArray(tagLen)
        for (i in 0 until tagLen) expectedTag[i] = (s[i].toInt() xor eJ0[i].toInt()).toByte()
        if (!tag.contentEquals(expectedTag)) return null
        return plaintext
    }
    private fun inc32(block: ByteArray) {
        for (i in 15 downTo 12) {
            if (++block[i] != 0.toByte()) break
        }
    }
    private fun ghash(blockCipher: AesEngine, aad: ByteArray, ciphertext: ByteArray): ByteArray {
        val h = ByteArray(16)
        blockCipher.encryptBlock(ByteArray(16), 0, h, 0)
        val y = ByteArray(16)
        fun processBlock(data: ByteArray, offset: Int) {
            for (i in 0 until 16) y[i] = (y[i].toInt() xor data[offset + i].toInt()).toByte()
            multiply(y, h)
        }
        val aadPadded = if (aad.size % 16 == 0) aad else aad + ByteArray(16 - (aad.size % 16))
        val ctPadded = if (ciphertext.size % 16 == 0) ciphertext else ciphertext + ByteArray(16 - (ciphertext.size % 16))
        for (i in aadPadded.indices step 16) processBlock(aadPadded, i)
        for (i in ctPadded.indices step 16) processBlock(ctPadded, i)
        val lens = ByteArray(16)
        val aadBits = aad.size.toLong() * 8
        val ctBits = ciphertext.size.toLong() * 8
        for (i in 0..7) lens[7 - i] = (aadBits ushr (i * 8)).toByte()
        for (i in 0..7) lens[15 - i] = (ctBits ushr (i * 8)).toByte()
        processBlock(lens, 0)
        return y
    }
    private fun multiply(x: ByteArray, y: ByteArray) {
        // Galois field multiplication in GF(2^128)
        var z = LongArray(2)
        var v = LongArray(2)
        z[0] = 0L; z[1] = 0L
        v[0] = toLong(y, 0); v[1] = toLong(y, 8)
        for (i in 0 until 16) {
            for (j in 7 downTo 0) {
                if ((x[i].toInt() ushr j and 1) != 0) {
                    z[0] = z[0] xor v[0]
                    z[1] = z[1] xor v[1]
                }
                val lsb = v[1] and 1L
                v[1] = v[1] ushr 1 or (v[0] and 1L shl 63)
                v[0] = v[0] ushr 1
                if (lsb != 0L) v[0] = v[0] xor 0xe100000000000000L
            }
        }
        for (i in 0..7) x[i] = (z[0] ushr (56 - 8 * i)).toByte()
        for (i in 0..7) x[8 + i] = (z[1] ushr (56 - 8 * i)).toByte()
    }
    private fun toLong(b: ByteArray, off: Int): Long {
        var n = 0L
        for (i in 0..7) n = (n shl 8) or (b[off + i].toLong() and 0xff)
        return n
    }
} 