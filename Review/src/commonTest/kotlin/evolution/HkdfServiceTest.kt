package evolution

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.test.runTest // For commonTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull

// Helper to get the actual service instance.
// This would ideally use a test-specific service locator or DI in a larger setup.
// For now, assume direct instantiation of 'actual' works for testing if context is not strictly needed by constructor.
// Or, we need a way to provide the platform-specific context with the service.
// Let's assume for now the actual constructor can be called or a test utility provides it.
expect fun getTestHkdfService(testContext: CoroutineContext): HkdfService


class HkdfServiceTest {

    // Example IKM and Salt from RFC 5869 Appendix A.1
    private val ikm = hexToByteArray("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b") // 22 bytes
    private val salt = hexToByteArray("000102030405060708090a0b0c") // 13 bytes
    private val info = hexToByteArray("f0f1f2f3f4f5f6f7f8f9") // 10 bytes
    private val L = 42 // Output length

    // Expected PRK from RFC 5869 Appendix A.1 (SHA-256)
    private val expectedPrkSha256 = hexToByteArray(
        "077709362c2e32df0ddc3f0dc47bba63" +
        "958716884f71fd06ecb2bf9ac8297164"
    )

    // Expected OKM from RFC 5869 Appendix A.1 (SHA-256)
    private val expectedOkmSha256 = hexToByteArray(
        "3cb25f25faacd57a90434f64d0362f2a" +
        "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
        "34007208d5b887185865"
    )

    @Test
    fun testHkdfExtractSha256() = runTest {
        val service = getTestHkdfService(this.coroutineContext + EmptyCoroutineContext) // Pass test coroutine context
        assertNotNull(service, "HkdfService should be available for testing")

        val prk = service.extract(salt, ikm)
        assertContentEquals(expectedPrkSha256, prk, "Extracted PRK does not match RFC 5869 example for SHA-256")
    }

    @Test
    fun testHkdfExpandSha256() = runTest {
        val service = getTestHkdfService(this.coroutineContext + EmptyCoroutineContext)
        assertNotNull(service, "HkdfService should be available for testing")

        // Use the known PRK for this test part
        val okm = service.expand(expectedPrkSha256, info, L)
        assertContentEquals(expectedOkmSha256, okm, "Expanded OKM does not match RFC 5869 example for SHA-256")
    }
}

// Hex to ByteArray helper (common for tests)
fun hexToByteArray(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "Hex string must have an even length" }
    return hex.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
