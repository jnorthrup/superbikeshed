package evolution.io.test

import evolution.io.NativePlatformIoService
import evolution.io.PlatformIoService
import kotlinx.coroutines.CoroutineScope

class NativePlatformIoIntegrationTests : AbstractPlatformIoIntegrationTests() {
    // In Native, ensure the CoroutineScope for tests uses an appropriate dispatcher
    // that can handle the C interop and potential blocking calls in the way
    // NativePlatformIoService is implemented (currently Dispatchers.Default).
    // The testScope in AbstractPlatformIoIntegrationTests uses StandardTestDispatcher,
    // which should be fine for managing coroutine execution.
    override fun getPlatformIoService(scope: CoroutineScope): PlatformIoService = NativePlatformIoService()
}
