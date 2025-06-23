package borg.trikeshed.util

// Pure Kotlin ChaCha20-Poly1305 AEAD implementation for commonMain
// Reference: RFC 8439, Bernstein public domain code
// Not constant-time, not hardened for production, but correct for research and multiplatform

private const val ROUNDS = 20

class ChaCha20Poly1305(private val key: ByteArray) {
    init {
        require(key.size == 32) { "Key must be 32 bytes" }
    }

    fun encrypt(nonce: ByteArray, plaintext: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        require(nonce.size == 12) { "Nonce must be 12 bytes" }
        val ciphertext = chacha20Xor(key, nonce, 1, plaintext)
        val tag = poly1305Auth(key, nonce, aad, ciphertext)
        return ciphertext + tag
    }

    fun decrypt(nonce: ByteArray, ciphertextWithTag: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray? {
        require(nonce.size == 12) { "Nonce must be 12 bytes" }
        if (ciphertextWithTag.size < 16) return null
        val ciphertext = ciphertextWithTag.copyOfRange(0, ciphertextWithTag.size - 16)
        val tag = ciphertextWithTag.copyOfRange(ciphertextWithTag.size - 16, ciphertextWithTag.size)
        val expectedTag = poly1305Auth(key, nonce, aad, ciphertext)
        if (!tag.contentEquals(expectedTag)) return null
        return chacha20Xor(key, nonce, 1, ciphertext)
    }
}

// --- ChaCha20 core ---
private fun chacha20Xor(key: ByteArray, nonce: ByteArray, counter: Int, input: ByteArray): ByteArray {
    val output = ByteArray(input.size)
    val block = ByteArray(64)
    var blockCount = counter
    var offset = 0
    while (offset < input.size) {
        chacha20Block(key, nonce, blockCount, block)
        val blockLen = minOf(64, input.size - offset)
        for (i in 0 until blockLen) {
            output[offset + i] = (input[offset + i].toInt() xor block[i].toInt()).toByte()
        }
        offset += blockLen
        blockCount++
    }
    return output
}

private fun chacha20Block(key: ByteArray, nonce: ByteArray, counter: Int, outBlock: ByteArray) {
    val state = IntArray(16)
    val constants = intArrayOf(
        0x61707865, 0x3320646e, 0x79622d32, 0x6b206574
    )
    for (i in 0..3) state[i] = constants[i]
    for (i in 0..7) state[4 + i] = littleEndianToInt(key, i * 4)
    state[12] = counter
    state[13] = littleEndianToInt(nonce, 0)
    state[14] = littleEndianToInt(nonce, 4)
    state[15] = littleEndianToInt(nonce, 8)
    val working = state.copyOf()
    for (i in 0 until ROUNDS step 2) {
        // Odd round
        quarterRound(working, 0, 4, 8, 12)
        quarterRound(working, 1, 5, 9, 13)
        quarterRound(working, 2, 6, 10, 14)
        quarterRound(working, 3, 7, 11, 15)
        // Even round
        quarterRound(working, 0, 5, 10, 15)
        quarterRound(working, 1, 6, 11, 12)
        quarterRound(working, 2, 7, 8, 13)
        quarterRound(working, 3, 4, 9, 14)
    }
    for (i in 0..15) working[i] += state[i]
    for (i in 0..15) intToLittleEndian(working[i], outBlock, i * 4)
}

private fun quarterRound(x: IntArray, a: Int, b: Int, c: Int, d: Int) {
    x[a] += x[b]; x[d] = (x[d] xor x[a]).rotateLeft(16)
    x[c] += x[d]; x[b] = (x[b] xor x[c]).rotateLeft(12)
    x[a] += x[b]; x[d] = (x[d] xor x[a]).rotateLeft(8)
    x[c] += x[d]; x[b] = (x[b] xor x[c]).rotateLeft(7)
}

private fun littleEndianToInt(b: ByteArray, off: Int): Int =
    (b[off].toInt() and 0xff) or
    ((b[off + 1].toInt() and 0xff) shl 8) or
    ((b[off + 2].toInt() and 0xff) shl 16) or
    ((b[off + 3].toInt() and 0xff) shl 24)

private fun intToLittleEndian(n: Int, b: ByteArray, off: Int) {
    b[off] = (n and 0xff).toByte()
    b[off + 1] = ((n ushr 8) and 0xff).toByte()
    b[off + 2] = ((n ushr 16) and 0xff).toByte()
    b[off + 3] = ((n ushr 24) and 0xff).toByte()
}

private fun Int.rotateLeft(bits: Int): Int = (this shl bits) or (this ushr (32 - bits))

// --- Poly1305 core ---
private fun poly1305Auth(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray {
    // Poly1305 key is chacha20(key, nonce, 0, 32 zero bytes)
    val polyKey = chacha20Xor(key, nonce, 0, ByteArray(32))
    val mac = Poly1305(polyKey)
    mac.update(aad)
    mac.update(pad16(aad.size))
    mac.update(ciphertext)
    mac.update(pad16(ciphertext.size))
    val aadLen = aad.size.toLong()
    val ctLen = ciphertext.size.toLong()
    val lens = ByteArray(16)
    for (i in 0..7) lens[i] = (aadLen ushr (8 * i)).toByte()
    for (i in 0..7) lens[8 + i] = (ctLen ushr (8 * i)).toByte()
    mac.update(lens)
    return mac.finish()
}

private fun pad16(len: Int): ByteArray = if (len % 16 == 0) ByteArray(0) else ByteArray(16 - (len % 16))

private class Poly1305(key: ByteArray) {
    private val r = LongArray(5)
    private val h = LongArray(5)
    private val pad = LongArray(4)
    private var buffer = ByteArray(16)
    private var bufferUsed = 0
    private var finished = false
    private var totalLen = 0

    init {
        require(key.size == 32)
        val t = ByteArray(16)
        for (i in 0..15) t[i] = key[i]
        r[0] = (t[0].toInt() and 0xff or ((t[1].toInt() and 0xff) shl 8) or ((t[2].toInt() and 0xff) shl 16) or ((t[3].toInt() and 0x0f) shl 24)).toLong() and 0xffffffffL
        r[1] = ((t[3].toInt() and 0xf0) ushr 4 or ((t[4].toInt() and 0xff) shl 4) or ((t[5].toInt() and 0xff) shl 12) or ((t[6].toInt() and 0x3f) shl 20)).toLong() and 0xffffffffL
        r[2] = ((t[6].toInt() and 0xc0) ushr 6 or ((t[7].toInt() and 0xff) shl 2) or ((t[8].toInt() and 0xff) shl 10) or ((t[9].toInt() and 0x0f) shl 18)).toLong() and 0xffffffffL
        r[3] = ((t[9].toInt() and 0xf0) ushr 4 or ((t[10].toInt() and 0xff) shl 4) or ((t[11].toInt() and 0xff) shl 12) or ((t[12].toInt() and 0x3f) shl 20)).toLong() and 0xffffffffL
        r[4] = ((t[12].toInt() and 0xc0) ushr 6 or ((t[13].toInt() and 0xff) shl 2) or ((t[14].toInt() and 0xff) shl 10) or ((t[15].toInt() and 0x0f) shl 18)).toLong() and 0xffffffffL
        for (i in 0..3) pad[i] = (key[16 + 4 * i].toInt() and 0xff or ((key[17 + 4 * i].toInt() and 0xff) shl 8) or ((key[18 + 4 * i].toInt() and 0xff) shl 16) or ((key[19 + 4 * i].toInt() and 0xff) shl 24)).toLong() and 0xffffffffL
    }

    fun update(data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val toCopy = minOf(16 - bufferUsed, data.size - offset)
            for (i in 0 until toCopy) buffer[bufferUsed + i] = data[offset + i]
            bufferUsed += toCopy
            offset += toCopy
            if (bufferUsed == 16) {
                processBlock(buffer, 0)
                bufferUsed = 0
            }
        }
        totalLen += data.size
    }

    fun finish(): ByteArray {
        if (bufferUsed > 0) {
            val last = ByteArray(16)
            for (i in 0 until bufferUsed) last[i] = buffer[i]
            last[bufferUsed] = 1
            for (i in bufferUsed + 1 until 16) last[i] = 0
            processBlock(last, 0)
        }
        val mac = ByteArray(16)
        for (i in 0..3) {
            val t = (h[i] + pad[i]) and 0xffffffffL
            mac[4 * i] = (t and 0xff).toByte()
            mac[4 * i + 1] = ((t ushr 8) and 0xff).toByte()
            mac[4 * i + 2] = ((t ushr 16) and 0xff).toByte()
            mac[4 * i + 3] = ((t ushr 24) and 0xff).toByte()
        }
        finished = true
        return mac
    }

    private fun processBlock(block: ByteArray, offset: Int) {
        // Poly1305 block math, per RFC 8439
        val t = LongArray(4)
        for (i in 0..3) {
            t[i] = (block[offset + 4 * i].toInt() and 0xff).toLong() or
                   ((block[offset + 4 * i + 1].toInt() and 0xff).toLong() shl 8) or
                   ((block[offset + 4 * i + 2].toInt() and 0xff).toLong() shl 16) or
                   ((block[offset + 4 * i + 3].toInt() and 0xff).toLong() shl 24)
        }
        var c = 1L
        for (i in 0..3) {
            h[i] += t[i] and 0xffffffffL
            h[i] += c
            c = h[i] ushr 32
            h[i] = h[i] and 0xffffffffL
        }
        h[4] += c
        // Multiply (h * r) mod 2^130-5
        val hr = LongArray(5)
        for (i in 0..4) {
            hr[i] = 0L
            for (j in 0..i) hr[i] += h[j] * r[i - j]
            for (j in i + 1..4) hr[i] += h[j] * r[5 + i - j]
        }
        // Partial reduction
        var c2 = 0L
        for (i in 0..4) {
            hr[i] += c2
            c2 = hr[i] ushr 32
            h[i] = hr[i] and 0xffffffffL
        }
        // Final reduction mod 2^130-5
        val g = LongArray(5)
        var c3 = h[0] + 5
        var mask = (c3 ushr 32) - 1
        g[0] = h[0] + 5 and 0xffffffffL
        for (i in 1..4) {
            g[i] = h[i] + (if (i == 1) 0 else 5) + (g[i - 1] ushr 32)
            g[i] = g[i] and 0xffffffffL
        }
        mask = (g[4] ushr 2) - 1
        for (i in 0..4) h[i] = (h[i] and mask.inv()) or (g[i] and mask)
    }
} 