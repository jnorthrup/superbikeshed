package evolution

import kotlinx.cinterop.*
import platform.openssl.*
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class AesService actual constructor() : CoroutineContext.Element {
    actual override val key: CoroutineContext.Key<*> = AesServiceKey

    actual suspend fun gcmEncrypt(keyBytes: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        memScoped {
            val keyPtr = keyBytes.pin()
            val ivPtr = iv.pin()
            val aadPtr = aad.pin()
            val plaintextPtr = plaintext.pin()

            val ciphertextBuf = allocArray<UByteVar>(plaintext.size + EVP_GCM_TLS_TAG_LEN)
            val ciphertextLen = alloc<IntVar>()
            val tagBuf = allocArray<UByteVar>(EVP_GCM_TLS_TAG_LEN) // Standard GCM tag length is 16 bytes

            val ctx = EVP_CIPHER_CTX_new() ?: throw RuntimeException("EVP_CIPHER_CTX_new failed")
            try {
                if (EVP_EncryptInit_ex(ctx, EVP_aes_128_gcm(), null, null, null) != 1) throw RuntimeException("EVP_EncryptInit_ex failed (GCM)")
                if (EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, iv.size, null) != 1) throw RuntimeException("EVP_CTRL_GCM_SET_IVLEN failed")
                if (EVP_EncryptInit_ex(ctx, null, null, keyPtr.addressOf(0).reinterpret(), ivPtr.addressOf(0).reinterpret()) != 1) throw RuntimeException("EVP_EncryptInit_ex key/iv failed (GCM)")
                if (aad.isNotEmpty()) {
                    if (EVP_EncryptUpdate(ctx, null, ciphertextLen.ptr, aadPtr.addressOf(0).reinterpret(), aad.size) != 1) throw RuntimeException("EVP_EncryptUpdate AAD failed (GCM)")
                }
                if (EVP_EncryptUpdate(ctx, ciphertextBuf, ciphertextLen.ptr, plaintextPtr.addressOf(0).reinterpret(), plaintext.size) != 1) throw RuntimeException("EVP_EncryptUpdate plaintext failed (GCM)")
                val headLen = ciphertextLen.value
                if (EVP_EncryptFinal_ex(ctx, ciphertextBuf?.plus(headLen), ciphertextLen.ptr) != 1) throw RuntimeException("EVP_EncryptFinal_ex failed (GCM)")
                val totalCiphertextLen = headLen + ciphertextLen.value
                if (EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_GET_TAG, EVP_GCM_TLS_TAG_LEN, tagBuf) != 1) throw RuntimeException("EVP_CTRL_GCM_GET_TAG failed")

                return@withContext ciphertextBuf.readBytes(totalCiphertextLen) + tagBuf.readBytes(EVP_GCM_TLS_TAG_LEN)
            } finally {
                EVP_CIPHER_CTX_free(ctx)
                keyPtr.unpin()
                ivPtr.unpin()
                aadPtr.unpin()
                plaintextPtr.unpin()
            }
        }
    }

    actual suspend fun gcmDecrypt(keyBytes: ByteArray, iv: ByteArray, ciphertextAndTag: ByteArray, aad: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        memScoped {
            if (ciphertextAndTag.size < EVP_GCM_TLS_TAG_LEN) throw IllegalArgumentException("Ciphertext too short, does not include tag.")
            val ciphertextLenVal = ciphertextAndTag.size - EVP_GCM_TLS_TAG_LEN
            val ciphertextData = ciphertextAndTag.copyOfRange(0, ciphertextLenVal)
            val tagData = ciphertextAndTag.copyOfRange(ciphertextLenVal, ciphertextAndTag.size)

            val keyPtr = keyBytes.pin()
            val ivPtr = iv.pin()
            val aadPtr = aad.pin()
            val ciphertextPtr = ciphertextData.pin()
            val tagPtr = tagData.pin()

            val plaintextBuf = allocArray<UByteVar>(ciphertextData.size)
            val plaintextLen = alloc<IntVar>()

            val ctx = EVP_CIPHER_CTX_new() ?: throw RuntimeException("EVP_CIPHER_CTX_new failed for GCM decrypt")
            try {
                if (EVP_DecryptInit_ex(ctx, EVP_aes_128_gcm(), null, null, null) != 1) throw RuntimeException("EVP_DecryptInit_ex failed (GCM)")
                if (EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, iv.size, null) != 1) throw RuntimeException("EVP_CTRL_GCM_SET_IVLEN failed (GCM decrypt)")
                if (EVP_DecryptInit_ex(ctx, null, null, keyPtr.addressOf(0).reinterpret(), ivPtr.addressOf(0).reinterpret()) != 1) throw RuntimeException("EVP_DecryptInit_ex key/iv failed (GCM decrypt)")
                if (aad.isNotEmpty()) {
                    if (EVP_DecryptUpdate(ctx, null, plaintextLen.ptr, aadPtr.addressOf(0).reinterpret(), aad.size) != 1) throw RuntimeException("EVP_DecryptUpdate AAD failed (GCM decrypt)")
                }
                if (EVP_DecryptUpdate(ctx, plaintextBuf, plaintextLen.ptr, ciphertextPtr.addressOf(0).reinterpret(), ciphertextData.size) != 1) {
                    // This can fail if data is not authentic (but not necessarily tag mismatch yet)
                    // ERR_print_errors_fp(platform.posix.stdout) // For debugging
                    throw ActualDecryptionFailedException("EVP_DecryptUpdate plaintext failed (GCM decrypt), potentially corrupted data before tag check.")
                }
                val headLen = plaintextLen.value
                if (EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_TAG, tagData.size, tagPtr.addressOf(0).reinterpret()) != 1) throw RuntimeException("EVP_CTRL_GCM_SET_TAG failed")

                val finalResult = EVP_DecryptFinal_ex(ctx, plaintextBuf?.plus(headLen), plaintextLen.ptr)
                if (finalResult != 1) {
                    // This is the typical point for authentication failure (tag mismatch)
                    throw ActualDecryptionFailedException("AES-GCM decryption failed: Tag mismatch or other error during finalization.")
                }
                return@withContext plaintextBuf.readBytes(headLen + plaintextLen.value)
            } finally {
                EVP_CIPHER_CTX_free(ctx)
                keyPtr.unpin()
                ivPtr.unpin()
                aadPtr.unpin()
                ciphertextPtr.unpin()
                tagPtr.unpin()
            }
        }
    }

    actual suspend fun ecbEncrypt(keyBytes: ByteArray, plaintext: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        memScoped {
            val keyPtr = keyBytes.pin()
            val plaintextPtr = plaintext.pin()
            // ECB output size can be larger due to padding (e.g. PKCS#7)
            // Output buffer should be plaintext.size + block_size. AES block size is 16.
            val outBufSize = plaintext.size + AES_BLOCK_SIZE
            val outBuf = allocArray<UByteVar>(outBufSize)
            val outLen = alloc<IntVar>()
            val finalLen = alloc<IntVar>()

            val ctx = EVP_CIPHER_CTX_new() ?: throw RuntimeException("EVP_CIPHER_CTX_new failed for ECB encrypt")
            try {
                // EVP_aes_128_ecb() handles PKCS#7 padding by default
                if (EVP_EncryptInit_ex(ctx, EVP_aes_128_ecb(), null, keyPtr.addressOf(0).reinterpret(), null /* IV not used in ECB */) != 1) throw RuntimeException("EVP_EncryptInit_ex failed (ECB)")
                // Enable padding - it's usually on by default for ECB modes in OpenSSL if block size is standard.
                // EVP_CIPHER_CTX_set_padding(ctx, 1); // 1 for on, 0 for off. Default is on.

                if (EVP_EncryptUpdate(ctx, outBuf, outLen.ptr, plaintextPtr.addressOf(0).reinterpret(), plaintext.size) != 1) throw RuntimeException("EVP_EncryptUpdate failed (ECB)")
                if (EVP_EncryptFinal_ex(ctx, outBuf?.plus(outLen.value), finalLen.ptr) != 1) throw RuntimeException("EVP_EncryptFinal_ex failed (ECB)")

                return@withContext outBuf.readBytes(outLen.value + finalLen.value)
            } finally {
                EVP_CIPHER_CTX_free(ctx)
                keyPtr.unpin()
                plaintextPtr.unpin()
            }
        }
    }
}
