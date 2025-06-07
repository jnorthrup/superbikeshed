package evolution.test

import evolution.NativeAesService
import evolution.NativeHkdfService
import evolution.AesService
import evolution.HkdfService

class NativeHkdfServiceTests : AbstractHkdfServiceTests() {
    override val hkdfService: HkdfService by lazy { NativeHkdfService() }
}

class NativeAesServiceTests : AbstractAesServiceTests() {
    override val aesService: AesService by lazy { NativeAesService() }
    // NativeAesService supports ECB (OpenSSL's EVP_aes_128_ecb typically defaults to no padding)
    override val supportsEcb: Boolean = true
}
