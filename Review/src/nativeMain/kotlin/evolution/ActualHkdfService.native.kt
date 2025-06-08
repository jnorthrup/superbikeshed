package evolution

import kotlinx.cinterop.*
import platform.openssl.*
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class HkdfService actual constructor() : CoroutineContext.Element {
    actual override val key: CoroutineContext.Key<*> = HkdfServiceKey

    actual suspend fun extract(salt: ByteArray, ikm: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        memScoped {
            val saltPtr = salt.pin()
            val ikmPtr = ikm.pin()
            val prkLen = EVP_MAX_MD_SIZE // Max digest size
            val prkBuf = allocArray<UByteVar>(prkLen)
            val prkOutLen = alloc<size_tVar>()
            prkOutLen.value = prkLen.convert()

            val result = HKDF_extract(
                prkBuf, prkOutLen.ptr,
                EVP_sha256(), // Using SHA-256 for HKDF
                ikmPtr.addressOf(0).reinterpret(), ikm.size.convert(),
                saltPtr.addressOf(0).reinterpret(), salt.size.convert()
            )
            saltPtr.unpin()
            ikmPtr.unpin()

            if (result != 1) {
                throw RuntimeException("HKDF_extract failed: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
            }
            return@withContext prkBuf.readBytes(prkOutLen.value.toInt())
        }
    }

    actual suspend fun expand(prk: ByteArray, info: ByteArray, len: Int): ByteArray = withContext(Dispatchers.Default) {
        memScoped {
            val prkPtr = prk.pin()
            val infoPtr = info.pin()
            val okmBuf = allocArray<UByteVar>(len)

            val result = HKDF_expand(
                okmBuf, len.convert(),
                EVP_sha256(), // Using SHA-256 for HKDF
                prkPtr.addressOf(0).reinterpret(), prk.size.convert(),
                infoPtr.addressOf(0).reinterpret(), info.size.convert()
            )
            prkPtr.unpin()
            infoPtr.unpin()

            if (result != 1) {
                throw RuntimeException("HKDF_expand failed: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
            }
            return@withContext okmBuf.readBytes(len)
        }
    }
}
