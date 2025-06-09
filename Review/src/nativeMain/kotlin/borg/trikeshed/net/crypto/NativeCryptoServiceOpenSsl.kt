package borg.trikeshed.net.crypto

import kotlinx.cinterop.*
import libopenssl.* // Assuming cinterop setup provides this or specific imports
import platform.posix.memcpy // For copying data if needed, thoughByteArray.copyInto should work

@OptIn(ExperimentalForeignApi::class, ExperimentalUnsignedTypes::class)
class NativeCryptoServiceOpenSsl : CryptoService {

    private val GCM_TAG_LENGTH = 16
    private val HP_SAMPLE_LENGTH = 16 // AES block size
    private val HP_MASK_OUTPUT_LENGTH = 5

    override fun aeadEncrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray
    ): CryptoResult = memScoped {
        // Check key and nonce lengths (OpenSSL GCM typically uses 12-byte nonce for AES-128-GCM)
        if (key.size != 16) return@memScoped CryptoResult.Error("Key must be 16 bytes for AES-128-GCM.")
        // if (nonce.size != 12) return@memScoped CryptoResult.Error("Nonce must be 12 bytes for AES-GCM.")

        val ctx = EVP_CIPHER_CTX_new() ?: return@memScoped CryptoResult.Error("Failed to create EVP_CIPHER_CTX")
        try {
            // Initialize encryption operation with AES-128-GCM
            if (EVP_EncryptInit_ex(ctx, EVP_aes_128_gcm(), null, null, null) != 1) {
                return@memScoped CryptoResult.Error("EVP_EncryptInit_ex failed: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
            }

            // Set IV (nonce) length
            if (EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, nonce.size, null) != 1) {
                return@memScoped CryptoResult.Error("EVP_CTRL_GCM_SET_IVLEN failed: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
            }

            // Set key and IV
            key.usePinned { pinnedKey ->
                nonce.usePinned { pinnedNonce ->
                    if (EVP_EncryptInit_ex(ctx, null, null, pinnedKey.addressOf(0).reinterpret(), pinnedNonce.addressOf(0).reinterpret()) != 1) {
                        return@memScoped CryptoResult.Error("EVP_EncryptInit_ex (key/iv) failed: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
                    }
                }
            }

            // Provide AAD
            if (associatedData.isNotEmpty()) {
                associatedData.usePinned { pinnedAAD ->
                    val outlen = alloc<IntVar>()
                    if (EVP_EncryptUpdate(ctx, null, outlen.ptr, pinnedAAD.addressOf(0).reinterpret(), associatedData.size) != 1) {
                        return@memScoped CryptoResult.Error("EVP_EncryptUpdate (AAD) failed: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
                    }
                }
            }

            // Encrypt plaintext
            // Output buffer needs space for ciphertext + potentially some block alignment (though GCM is a stream cipher mode)
            val ciphertextBuffer = UByteArray(plaintext.size + AES_BLOCK_SIZE) // AES_BLOCK_SIZE for GCM might be generous, but safe
            val outlen1 = alloc<IntVar>()
            plaintext.usePinned { pinnedPlaintext ->
                ciphertextBuffer.usePinned { pinnedCiphertext ->
                    if (EVP_EncryptUpdate(ctx, pinnedCiphertext.addressOf(0), outlen1.ptr, pinnedPlaintext.addressOf(0).reinterpret(), plaintext.size) != 1) {
                        return@memScoped CryptoResult.Error("EVP_EncryptUpdate (plaintext) failed: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
                    }
                }
            }

            // Finalize encryption (GCM often doesn't output more here, but required)
            val outlen2 = alloc<IntVar>()
            ciphertextBuffer.usePinned { pinnedCiphertext -> // Pin again for final part
                 if (EVP_EncryptFinal_ex(ctx, pinnedCiphertext.addressOf(outlen1.value), outlen2.ptr) != 1) {
                    return@memScoped CryptoResult.Error("EVP_EncryptFinal_ex failed: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
                }
            }
            val ciphertextLength = outlen1.value + outlen2.value

            // Get the GCM authentication tag
            val tag = allocArray<UByteVar>(GCM_TAG_LENGTH)
            if (EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_GET_TAG, GCM_TAG_LENGTH, tag) != 1) {
                return@memScoped CryptoResult.Error("EVP_CTRL_GCM_GET_TAG failed: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
            }

            val finalCiphertextWithTag = ciphertextBuffer.copyOfRange(0, ciphertextLength).asByteArray() + tag.readBytes(GCM_TAG_LENGTH)
            CryptoResult.Success(finalCiphertextWithTag)

        } finally {
            EVP_CIPHER_CTX_free(ctx)
        }
    }


    override fun aeadDecrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertextWithTag: ByteArray,
        associatedData: ByteArray
    ): CryptoResult = memScoped {
        if (key.size != 16) return@memScoped CryptoResult.Error("Key must be 16 bytes for AES-128-GCM.")
        // if (nonce.size != 12) return@memScoped CryptoResult.Error("Nonce must be 12 bytes for AES-GCM.")
        if (ciphertextWithTag.size < GCM_TAG_LENGTH) return@memScoped CryptoResult.Error("Ciphertext too short to contain tag.")

        val ciphertext = ciphertextWithTag.copyOfRange(0, ciphertextWithTag.size - GCM_TAG_LENGTH)
        val tagFromCiphertext = ciphertextWithTag.copyOfRange(ciphertextWithTag.size - GCM_TAG_LENGTH, ciphertextWithTag.size)

        val ctx = EVP_CIPHER_CTX_new() ?: return@memScoped CryptoResult.Error("Failed to create EVP_CIPHER_CTX for decrypt")
        try {
            if (EVP_DecryptInit_ex(ctx, EVP_aes_128_gcm(), null, null, null) != 1) {
                return@memScoped CryptoResult.Error("EVP_DecryptInit_ex failed: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
            }
            if (EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, nonce.size, null) != 1) {
                return@memScoped CryptoResult.Error("EVP_CTRL_GCM_SET_IVLEN failed: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
            }

            key.usePinned { pinnedKey ->
                nonce.usePinned { pinnedNonce ->
                    if (EVP_DecryptInit_ex(ctx, null, null, pinnedKey.addressOf(0).reinterpret(), pinnedNonce.addressOf(0).reinterpret()) != 1) {
                        return@memScoped CryptoResult.Error("EVP_DecryptInit_ex (key/iv) failed: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
                    }
                }
            }

            if (associatedData.isNotEmpty()) {
                associatedData.usePinned { pinnedAAD ->
                    val outlen = alloc<IntVar>()
                    if (EVP_DecryptUpdate(ctx, null, outlen.ptr, pinnedAAD.addressOf(0).reinterpret(), associatedData.size) != 1) {
                        return@memScoped CryptoResult.Error("EVP_DecryptUpdate (AAD) failed: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
                    }
                }
            }

            val plaintextBuffer = UByteArray(ciphertext.size) // Plaintext will be at most this size
            val outlen1 = alloc<IntVar>()
            ciphertext.usePinned { pinnedCiphertext ->
                plaintextBuffer.usePinned { pinnedPlaintext ->
                    if (EVP_DecryptUpdate(ctx, pinnedPlaintext.addressOf(0), outlen1.ptr, pinnedCiphertext.addressOf(0).reinterpret(), ciphertext.size) != 1) {
                        return@memScoped CryptoResult.Error("EVP_DecryptUpdate (ciphertext) failed: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
                    }
                }
            }

            tagFromCiphertext.asUByteArray().usePinned { pinnedTag -> // Convert to UByteArray for usePinned if needed by your K/N version
                if (EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_TAG, GCM_TAG_LENGTH, pinnedTag.addressOf(0).reinterpret()) != 1) {
                    return@memScoped CryptoResult.Error("EVP_CTRL_GCM_SET_TAG failed: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
                }
            }

            val outlen2 = alloc<IntVar>()
            val finalResult = plaintextBuffer.usePinned { pinnedPlaintext ->
                EVP_DecryptFinal_ex(ctx, pinnedPlaintext.addressOf(outlen1.value), outlen2.ptr)
            }

            if (finalResult != 1) { // Tag mismatch or other error
                return@memScoped CryptoResult.Error("AEAD Decrypt failed: Tag mismatch or other error. ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
            }

            val plaintextLength = outlen1.value + outlen2.value
            CryptoResult.Success(plaintextBuffer.copyOfRange(0, plaintextLength).asByteArray())

        } finally {
            EVP_CIPHER_CTX_free(ctx)
        }
    }

    override fun generateHeaderProtectionMask(hpKey: ByteArray, sample: ByteArray): CryptoResult = memScoped {
        if (sample.size != HP_SAMPLE_LENGTH) {
            return@memScoped CryptoResult.Error("Sample for HP mask must be $HP_SAMPLE_LENGTH bytes, got ${sample.size}")
        }
        if (hpKey.size != 16) { // Assuming AES-128 for HP key
             return@memScoped CryptoResult.Error("HP Key for AES-128 must be 16 bytes, got ${hpKey.size}")
        }

        val ctx = EVP_CIPHER_CTX_new() ?: return@memScoped CryptoResult.Error("Failed to create EVP_CIPHER_CTX for HP mask")
        try {
            // Initialize encryption operation with AES-128-ECB
            hpKey.usePinned { pinnedKey ->
                if (EVP_EncryptInit_ex(ctx, EVP_aes_128_ecb(), null, pinnedKey.addressOf(0).reinterpret(), null /* ECB has no IV */) != 1) {
                    return@memScoped CryptoResult.Error("EVP_EncryptInit_ex (HP Key) failed: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
                }
            }

            // EVP_CIPHER_CTX_set_padding(ctx, 0) // Not strictly needed for single block ECB if input is block size

            // Output buffer for the encrypted sample (keystream for HP)
            val keystreamBuffer = UByteArray(AES_BLOCK_SIZE) // AES_BLOCK_SIZE (16 bytes)
            val outlen = alloc<IntVar>()

            sample.usePinned { pinnedSample ->
                keystreamBuffer.usePinned { pinnedKeystream ->
                     if (EVP_EncryptUpdate(ctx, pinnedKeystream.addressOf(0), outlen.ptr, pinnedSample.addressOf(0).reinterpret(), sample.size) != 1) {
                        return@memScoped CryptoResult.Error("EVP_EncryptUpdate (HP sample) failed: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
                    }
                }
            }
            // EVP_EncryptFinal_ex is usually called, but for a single block ECB with no padding, Update might be enough.
            // However, it's safer to call it. It should not produce more output if padding is off and input is block size.
            val outlenFinal = alloc<IntVar>()
            keystreamBuffer.usePinned{ pinnedKeystream -> // Pin again
                if (EVP_EncryptFinal_ex(ctx, pinnedKeystream.addressOf(outlen.value), outlenFinal.ptr) != 1) {
                     return@memScoped CryptoResult.Error("EVP_EncryptFinal_ex (HP) failed: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
                }
            }

            val totalEncryptedLength = outlen.value + outlenFinal.value
            if (totalEncryptedLength < HP_MASK_OUTPUT_LENGTH) {
                 return@memScoped CryptoResult.Error("AES-ECB encryption produced less than $HP_MASK_OUTPUT_LENGTH bytes for HP mask.")
            }

            CryptoResult.Success(keystreamBuffer.copyOfRange(0, HP_MASK_OUTPUT_LENGTH).asByteArray())

        } finally {
            EVP_CIPHER_CTX_free(ctx)
        }
    }
}
