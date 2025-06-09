package evolution.test

import evolution.* // For JsHkdfService, JsAesService, and common QuicCurl functions/types

class JsQuicCurlIntegrationTests : AbstractQuicCurlIntegrationTests() {
    override fun getHkdfService(): HkdfService = JsHkdfService()
    override fun getAesService(): AesService = JsAesService()
}
