package evolution.io.test

import evolution.io.JsPlatformIoService
import evolution.io.PlatformIoService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class JsPlatformIoIntegrationTests : AbstractPlatformIoIntegrationTests() {
    // JsPlatformIoService's constructor takes a CoroutineScope.
    // We can pass the testScope provided by AbstractPlatformIoIntegrationTests,
    // or a new one if its lifecycle needs to be different.
    // The testScope from AbstractPlatformIoIntegrationTests uses StandardTestDispatcher.
    // JsPlatformIoService default constructor creates its own scope with Dispatchers.Default.
    // For consistency or specific needs, one might pass testScope here.
    // Passing testScope ensures that the service's internal coroutines are managed by the test dispatcher.
    override fun getPlatformIoService(scope: CoroutineScope): PlatformIoService = JsPlatformIoService(scope)
}
