package evolution.test

import evolution.* // For JvmHkdfService, JvmAesService

class JvmQuicCurlIntegrationTests : AbstractQuicCurlIntegrationTests() {
    override fun getHkdfService(): HkdfService = JvmHkdfService()
    override fun getAesService(): AesService = JvmAesService()
}
