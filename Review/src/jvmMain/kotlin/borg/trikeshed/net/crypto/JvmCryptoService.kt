package borg.trikeshed.net.crypto

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.AEADBadTagException

class JvmCryptoService : CryptoService {
    private val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
    private val AES_ECB_NO_PADDING = "AES/ECB/NoPadding"
    private val AES_ALGORITHM = "AES"
    private val GCM_TAG_LENGTH_BITS = 128 // Standard for QUIC (16 bytes)
    private val HP_SAMPLE_LENGTH = 16
    private val HP_MASK_LENGTH = 5

    override fun aeadEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, associatedData: ByteArray): CryptoResult {
        return try {
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            val keySpec = SecretKeySpec(key, AES_ALGORITHM)
            // Ensure nonce is the correct size for GCMParameterSpec (typically 12 bytes for QUIC AES-GCM)
            if (nonce.size != 12) {
                // While GCMParameterSpec might allow other sizes, QUIC usage is typically 12 bytes.
                // Forcing this here for stricter adherence, or document assumptions.
                // return CryptoResult.Error("Nonce must be 12 bytes for AES-GCM in QUIC context, got ${nonce.size}")
            }
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
            cipher.updateAAD(associatedData)
            val ciphertextWithTag = cipher.doFinal(plaintext)
            CryptoResult.Success(ciphertextWithTag) // No need to copy, doFinal returns a new array
        } catch (e: Exception) {
            CryptoResult.Error("JVM AEAD Encrypt failed: ${e.message}", e)
        }
    }

    override fun aeadDecrypt(key: ByteArray, nonce: ByteArray, ciphertextWithTag: ByteArray, associatedData: ByteArray): CryptoResult {
        return try {
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            val keySpec = SecretKeySpec(key, AES_ALGORITHM)
            if (nonce.size != 12) {
                // return CryptoResult.Error("Nonce must be 12 bytes for AES-GCM in QUIC context, got ${nonce.size}")
            }
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            cipher.updateAAD(associatedData)
            val plaintext = cipher.doFinal(ciphertextWithTag)
            CryptoResult.Success(plaintext) // No need to copy
        } catch (e: AEADBadTagException) {
            CryptoResult.Error("JVM AEAD Decrypt failed: Tag mismatch", e)
        } catch (e: Exception) {
            CryptoResult.Error("JVM AEAD Decrypt failed: ${e.message}", e)
        }
    }

    override fun generateHeaderProtectionMask(hpKey: ByteArray, sample: ByteArray): CryptoResult {
        if (sample.size != HP_SAMPLE_LENGTH) {
            return CryptoResult.Error("Sample for HP mask must be $HP_SAMPLE_LENGTH bytes for AES, got ${sample.size}")
        }
        if (hpKey.size != 16 && hpKey.size != 24 && hpKey.size != 32) { // AES-128, 192, 256
             return CryptoResult.Error("HP Key for AES must be 16, 24, or 32 bytes, got ${hpKey.size}")
        }

        return try {
            val cipher = Cipher.getInstance(AES_ECB_NO_PADDING)
            val keySpec = SecretKeySpec(hpKey, AES_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec) // ECB does not use an IV
            // Encrypt the sample. The result is the keystream.
            val keystream = cipher.doFinal(sample)
            // The mask is the first 5 bytes of this keystream.
            CryptoResult.Success(keystream.copyOfRange(0, HP_MASK_LENGTH))
        } catch (e: Exception) {
            CryptoResult.Error("JVM HP Mask generation failed: ${e.message}", e)
        }
    }
}
