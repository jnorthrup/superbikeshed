package evolution.test

import evolution.JsAesService
import evolution.JsHkdfService
import evolution.AesService
import evolution.HkdfService

class JsHkdfServiceTests : AbstractHkdfServiceTests() {
    override val hkdfService: HkdfService by lazy { JsHkdfService() }
}

class JsAesServiceTests : AbstractAesServiceTests() {
    override val aesService: AesService by lazy { JsAesService() }
    // JsAesService (WebCrypto) does not support ECB.
    override val supportsEcb: Boolean = false
}
