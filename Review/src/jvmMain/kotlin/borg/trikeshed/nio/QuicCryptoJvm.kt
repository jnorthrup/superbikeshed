package borg.trikeshed.nio

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac
import evolution.QuicPacket
import evolution.QuicInitialKeys
import evolution.QuicConnection

object QuicCryptoJvm {
    private const val HKDF_ALGORITHM = "HkdfHmacSha256"
    private const val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"
    private const val AES_ECB_ALGORITHM = "AES/ECB/NoPadding"
    private const val GCM_TAG_LENGTH = 128 // bits

    fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        return mac.doFinal(ikm)
    }

    fun hkdfExpand(prk: ByteArray, info: ByteArray, len: Int): ByteArray {
        val iterations = (len + 31) / 32
        val result = ByteArray(len)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))

        var t = ByteArray(0)
        for (i in 0 until iterations) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(byteArrayOf((i + 1).toByte()))
            t = mac.doFinal()
            System.arraycopy(t, 0, result, i * 32, minOf(t.size, len - i * 32))
        }
        return result
    }

    fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    fun aesEcbEncrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_ECB_ALGORITHM)
        val keySpec = SecretKeySpec(key, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        return cipher.doFinal(plaintext)
    }
}

internal fun protectPacket(
    packet: QuicPacket,
    keys: QuicInitialKeys,
    connection: QuicConnection
): ByteArray {
    // Placeholder for actual packet protection implementation
    // Would include:
    // 1. Encrypting payload with AEAD
    // 2. Applying header protection
    // 3. Reconstructing full packet
    return byteArrayOf() // TODO: Implement full packet protection
}