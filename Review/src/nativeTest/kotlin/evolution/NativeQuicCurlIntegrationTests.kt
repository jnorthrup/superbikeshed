package evolution.test

import evolution.* // For NativeHkdfService, NativeAesService, and common QuicCurl functions/types

class NativeQuicCurlIntegrationTests : AbstractQuicCurlIntegrationTests() {
    override fun getHkdfService(): HkdfService = NativeHkdfService()
    override fun getAesService(): AesService = NativeAesService()
}
