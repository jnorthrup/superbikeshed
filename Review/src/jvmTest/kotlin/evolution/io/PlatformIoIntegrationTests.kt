package evolution.io.test

import evolution.io.JvmPlatformIoService
import evolution.io.PlatformIoService
import kotlinx.coroutines.CoroutineScope

class JvmPlatformIoIntegrationTests : AbstractPlatformIoIntegrationTests() {
    override fun getPlatformIoService(scope: CoroutineScope): PlatformIoService = JvmPlatformIoService()
    // For JVM, we can pass the test coroutine scope if JvmPlatformIoService is designed to use it,
    // but the current JvmPlatformIoService constructor doesn't take a scope.
    // It internally uses Dispatchers.IO for its operations.
    // If JvmPlatformIoService needs a scope for launching its selector loop or other tasks,
    // its constructor or methods would need to accept it.
    // The provided JvmPlatformIoService uses withContext(Dispatchers.IO) for runSelectorLoop,
    // and its internal selector management doesn't require an external scope to be passed at construction.
}
