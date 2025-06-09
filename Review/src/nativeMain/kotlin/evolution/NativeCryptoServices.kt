package evolution

import kotlinx.cinterop.*
import platform.posix.memcpy
import platform.posix.size_t
import libopenssl.*
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalForeignApi::class)
actual class NativeHkdfService actual constructor() : HkdfService {
    actual override val key: CoroutineContext.Key<*> get() = HkdfServiceKey

    actual override suspend fun extract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val saltPinned = salt.pin()
        val ikmPinned = ikm.pin()
        val resultBuffer = ByteArray(EVP_MAX_MD_SIZE)
        val resultLen = memScoped { alloc<UIntVar>() }

        // Using .convert() for sizes passed to C functions expecting int or size_t
        val prk = HMAC(
            EVP_sha256(),
            saltPinned.addressOf(0), salt.size.convert(),
            ikmPinned.addressOf(0).reinterpret(), ikm.size.convert(),
            resultBuffer.refTo(0).getPointer(memScope).reinterpret(),
            resultLen.ptr
        )
        saltPinned.unpin()
        ikmPinned.unpin()

        if (prk == null) {
            throw RuntimeException("HMAC_extract failed. Error: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
        }
        return resultBuffer.copyOfRange(0, resultLen.value.toInt())
    }

    actual override suspend fun expand(prk: ByteArray, info: ByteArray, len: Int): ByteArray {
        val prkPinned = prk.pin()
        val infoPinned = info.pin()

        val result = ByteArray(len)
        var bytesProduced = 0
        var t = byteArrayOf()
        var i: UByte = 1u

        val tScratch = ByteArray(EVP_MAX_MD_SIZE)
        val tLen = memScoped { alloc<UIntVar>() }

        while (bytesProduced < len) {
            val currentTPinned = t.pin()

            val dataToHmac = stabilité.constructDataForHmac(
                if (t.isNotEmpty()) currentTPinned.addressOf(0) else null, // Pass null if t is empty
                t.size.convert(),
                if (info.isNotEmpty()) infoPinned.addressOf(0) else null, // Pass null if info is empty
                info.size.convert(),
                i
            )
            val dataToHmacPinned = dataToHmac.pin()

            HMAC(
                EVP_sha256(),
                prkPinned.addressOf(0), prk.size.convert(),
                dataToHmacPinned.addressOf(0).reinterpret(), dataToHmac.size.convert(),
                tScratch.refTo(0).getPointer(memScope).reinterpret(),
                tLen.ptr
            )
            currentTPinned.unpin()
            dataToHmacPinned.unpin()

            if (tLen.value == 0u) {
                 throw RuntimeException("HMAC_expand step failed. Error: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
            }

            t = tScratch.copyOfRange(0, tLen.value.toInt())
            val toCopy = minOf(t.size, len - bytesProduced)
            t.copyInto(result, bytesProduced, 0, toCopy)
            bytesProduced += toCopy

            if (bytesProduced < len && i == UByte.MAX_VALUE) {
                 throw IllegalArgumentException("Requested length $len is too large for HKDF-Expand with SHA-256 (max iterations reached)")
            }
            i++
        }
        prkPinned.unpin()
        infoPinned.unpin()
        return result
    }
}

@OptIn(ExperimentalForeignApi::class)
private object stabilité {
    fun constructDataForHmac(
        prevTAddress: CValuesRef<ByteVar>?,
        prevTSize: size_t,
        infoAddress: CValuesRef<ByteVar>?,
        infoSize: size_t,
        counter: UByte
    ): ByteArray {
        // Ensure size_t arithmetic is safe, cast to Long for sum then Int for ByteArray size
        val totalSizeCalculated = prevTSize.toLong() + infoSize.toLong() + 1L
        if (totalSizeCalculated > Int.MAX_VALUE) throw OutOfMemoryError("Requested HMAC data too large")
        val totalSize = totalSizeCalculated.toInt()

        val data = ByteArray(totalSize)
        var offset = 0
        if (prevTSize > 0u && prevTAddress != null) {
            memcpy(data.refTo(offset), prevTAddress, prevTSize)
            offset += prevTSize.toInt()
        }
        if (infoSize > 0u && infoAddress != null) {
            memcpy(data.refTo(offset), infoAddress, infoSize)
            offset += infoSize.toInt()
        }
        data[offset] = counter.toByte()
        return data
    }
}

@OptIn(ExperimentalForeignApi::class)
actual class NativeAesService actual constructor() : AesService {
    actual override val key: CoroutineContext.Key<*> get() = AesServiceKey

    private fun doAesOperation(
        keyBytes: ByteArray,
        iv: ByteArray,
        data: ByteArray,
        aad: ByteArray?,
        isEncrypt: Boolean,
        cipherMode: () -> ConstPointerTo<EVP_CIPHER>?,
        gcmAuthTag: ByteArray? = null
    ): ByteArray {
        require(iv.size == 12 || aad == null) { "IV length must be 12 bytes for GCM mode" }
        if (!isEncrypt && aad != null && gcmAuthTag == null) {
            throw IllegalArgumentException("GCM authentication tag must be provided for decryption.")
        }
        if (gcmAuthTag != null && gcmAuthTag.size != 16) {
             throw IllegalArgumentException("GCM authentication tag must be 16 bytes.")
        }

        val keyPinned = keyBytes.pin()
        val ivPinned = iv.pin()
        val dataPinned = data.pin()
        val aadPinned = aad?.pin()
        val gcmAuthTagPinned = gcmAuthTag?.pin()

        val cipherCtx = EVP_CIPHER_CTX_new()
        if (cipherCtx == null) throw RuntimeException("Failed to create EVP_CIPHER_CTX. Error: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")

        try {
            if (EVP_CipherInit_ex(cipherCtx, cipherMode(), null, null, null, if (isEncrypt) 1 else 0) != 1) {
                throw RuntimeException("EVP_CipherInit_ex (mode) failed. Error: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
            }

            if (aad != null) { // GCM mode
                 if (EVP_CIPHER_CTX_ctrl(cipherCtx, EVP_CTRL_GCM_SET_IVLEN, iv.size.convert(), null) != 1) { // Used .convert() for iv.size
                    throw RuntimeException("EVP_CTRL_GCM_SET_IVLEN failed. Error: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
                }
            }

            if (EVP_CipherInit_ex(cipherCtx, null, null, keyPinned.addressOf(0).reinterpret(), ivPinned.addressOf(0).reinterpret(), if (isEncrypt) 1 else 0) != 1) {
                 throw RuntimeException("EVP_CipherInit_ex (key/iv) failed. Error: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
            }

            if (aad != null && aadPinned != null) {
                memScoped {
                    val outlen = alloc<IntVar>()
                    if (EVP_CipherUpdate(cipherCtx, null, outlen.ptr, aadPinned.addressOf(0).reinterpret(), aad.size.convert()) != 1) { // Used .convert() for aad.size
                        throw RuntimeException("EVP_CipherUpdate (AAD) failed. Error: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
                    }
                }
            }

            val outputBuffer = ByteArray(data.size + EVP_MAX_BLOCK_LENGTH)
            val outlen1 = memScoped { alloc<IntVar>() }

            // When decrypting GCM, 'data' is the pure ciphertext. Tag is in 'gcmAuthTag'.
            // So data.size is correct for EVP_CipherUpdate.
            if (EVP_CipherUpdate(cipherCtx, outputBuffer.refTo(0).getPointer(memScope).reinterpret(), outlen1.ptr, dataPinned.addressOf(0).reinterpret(), data.size.convert()) != 1) { // Used .convert() for data.size
                throw RuntimeException("EVP_CipherUpdate (data) failed. Error: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
            }
            var currentSize = outlen1.value

            // For GCM decryption, set the tag before EVP_CipherFinal_ex
            if (aad != null && !isEncrypt && gcmAuthTagPinned != null && gcmAuthTag != null) { // Check gcmAuthTag not null before accessing .size
                if (EVP_CIPHER_CTX_ctrl(cipherCtx, EVP_CTRL_GCM_SET_TAG, gcmAuthTag.size.convert(), gcmAuthTagPinned.addressOf(0).getPointer(memScope)) != 1) { // Used .convert() for gcmAuthTag.size
                    throw RuntimeException("EVP_CTRL_GCM_SET_TAG failed. Error: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
                }
            }

            val outlen2 = memScoped { alloc<IntVar>() }
            if (EVP_CipherFinal_ex(cipherCtx, outputBuffer.refTo(currentSize).getPointer(memScope).reinterpret(), outlen2.ptr) != 1) {
                val errorMsg = ERR_error_string(ERR_get_error(), null)?.toKString() ?: "Unknown OpenSSL error"
                if (aad != null && !isEncrypt) { // GCM Decryption failure
                    throw DecryptionFailedException("AES-GCM decryption failed (possibly GCM tag mismatch or corrupt data): $errorMsg")
                } else {
                    throw RuntimeException("EVP_CipherFinal_ex failed: $errorMsg")
                }
            }
            currentSize += outlen2.value

            var finalResult = outputBuffer.copyOfRange(0, currentSize)

            // For GCM encryption, get the tag after EVP_CipherFinal_ex and append it
            if (aad != null && isEncrypt) {
                val tag = ByteArray(16)
                val tagPinnedLocal = tag.pin()
                if (EVP_CIPHER_CTX_ctrl(cipherCtx, EVP_CTRL_GCM_GET_TAG, 16, tagPinnedLocal.addressOf(0).getPointer(memScope)) != 1) {
                    tagPinnedLocal.unpin()
                    throw RuntimeException("EVP_CTRL_GCM_GET_TAG failed. Error: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
                }
                tagPinnedLocal.unpin()
                finalResult += tag
            }

            return finalResult

        } finally {
            EVP_CIPHER_CTX_free(cipherCtx)
            keyPinned.unpin()
            ivPinned.unpin()
            dataPinned.unpin()
            aadPinned?.unpin()
            gcmAuthTagPinned?.unpin()
        }
    }

    actual override suspend fun gcmEncrypt(keyBytes: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        // Tag is appended by doAesOperation
        return doAesOperation(keyBytes, iv, plaintext, aad, isEncrypt = true, cipherMode = { EVP_aes_128_gcm() })
    }

    actual override suspend fun gcmDecrypt(keyBytes: ByteArray, iv: ByteArray, ciphertextWithTag: ByteArray, aad: ByteArray): ByteArray {
        if (ciphertextWithTag.size < 16) throw IllegalArgumentException("Ciphertext with tag is too short for GCM (must include 16-byte tag).")
        // Separate ciphertext and tag
        val ciphertext = ciphertextWithTag.copyOfRange(0, ciphertextWithTag.size - 16)
        val gcmAuthTag = ciphertextWithTag.copyOfRange(ciphertextWithTag.size - 16, ciphertextWithTag.size)

        // Pass pure ciphertext and separate tag to doAesOperation
        return doAesOperation(keyBytes, iv, ciphertext, aad, isEncrypt = false, cipherMode = { EVP_aes_128_gcm() }, gcmAuthTag = gcmAuthTag)
    }

    actual override suspend fun ecbEncrypt(keyBytes: ByteArray, plaintext: ByteArray): ByteArray {
        // IV is not used by EVP_aes_128_ecb(), but API expects a non-null IV.
        // Provide a dummy one. Size can be anything, 12 is common for AES IVs if one were used.
        val dummyIv = ByteArray(12)
        return doAesOperation(keyBytes, dummyIv, plaintext, null, isEncrypt = true, cipherMode = { EVP_aes_128_ecb() })
    }
}

actual class DecryptionFailedException actual constructor(actual val message: String, actual val cause: Throwable?) : Exception(message, cause)
