package borg.trikeshed.util

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AeadTest {
    @Test
    fun testChaCha20Poly1305_RFC8439() {
        // RFC 8439 Section 2.8.2 test vector
        val key = hex("1c9240a5eb55d38af333888604f6b5f007a889d7c4ef8c7f8e4e415e07e7e12a")
        val nonce = hex("000000000102030405060708")
        val plaintext = hex("496e7465726e65742d4472616674732061726520647261667420646f63756d656e74732076616c696420666f722061206d6178696d756d206f6620736978206d6f6e74687320616e64206d617920626520757064617465642c207265706c616365642c206f72206f62736f6c65746564206279206f7468657220646f63756d656e747320617420616e792074696d652e")
        val aad = hex("f33388860000000000004e91")
        val expected = hex("64a0861575861af460f062c79be643bd5e805cfd345cf389f108670ac76c8cb24c6cfc18755d43eea09ee94e382d26b0bdb7b73c321b0100d4f03b7f355894cf332f830e710b97ce98c8a84abd0b9481 14ad176e008d33bd60f982b1ff37c8559797a06ef4f0ef61c186324e2b3506383606907b6a7c02b0f9f6157b53c867e4b9166c767b804d46a59b5216cde7a4e993a8ee6c7a6a532e".replace(" ", ""))
        val aead = ChaCha20Poly1305(key)
        val actual = aead.encrypt(nonce, plaintext, aad)
        assertContentEquals(expected, actual, "ChaCha20Poly1305 encrypt matches RFC 8439 test vector")
        val decrypted = aead.decrypt(nonce, actual, aad)
        assertNotNull(decrypted, "ChaCha20Poly1305 decrypt should succeed")
        assertContentEquals(plaintext, decrypted, "ChaCha20Poly1305 decrypt matches original plaintext")
    }

    @Test
    fun testAesGcm_NIST() {
        // NIST SP 800-38D F.2.1 test vector (AES-128-GCM)
        val key = hex("00000000000000000000000000000000")
        val nonce = hex("000000000000000000000000")
        val plaintext = hex("00000000000000000000000000000000")
        val aad = ByteArray(0)
        val expected = hex("0388dace60b6a392f328c2b971b2fe78 ab6e47d42cec13bdf53a67b21257bddf".replace(" ", ""))
        val aead = AesGcm(key)
        val actual = aead.encrypt(nonce, plaintext, aad)
        assertContentEquals(expected, actual, "AesGcm encrypt matches NIST test vector")
        val decrypted = aead.decrypt(nonce, actual, aad)
        assertNotNull(decrypted, "AesGcm decrypt should succeed")
        assertContentEquals(plaintext, decrypted, "AesGcm decrypt matches original plaintext")
    }

    private fun hex(s: String): ByteArray {
        val clean = s.replace(" ", "").replace("\n", "")
        return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
} 