package evolution.test

import evolution.JvmAesService
import evolution.JvmHkdfService
import evolution.AesService
import evolution.HkdfService

class JvmHkdfServiceTests : AbstractHkdfServiceTests() {
    override val hkdfService: HkdfService by lazy { JvmHkdfService() }
}

class JvmAesServiceTests : AbstractAesServiceTests() {
    override val aesService: AesService by lazy { JvmAesService() }
    // JvmAesService supports ECB (with PKCS5Padding)
    override val supportsEcb: Boolean = true
}
