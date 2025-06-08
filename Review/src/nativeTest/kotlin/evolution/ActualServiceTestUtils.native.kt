package evolution

import kotlin.coroutines.CoroutineContext

actual fun getTestHkdfService(testContext: CoroutineContext): HkdfService {
    // On Native, directly instantiate the actual implementation.
    // Assumes ActualHkdfService.native.kt exists and has a no-arg constructor.
    return ActualHkdfService()
}

actual fun getTestAesService(testContext: CoroutineContext): AesService {
    // On Native, directly instantiate the actual implementation.
    // Assumes ActualAesService.native.kt exists and has a no-arg constructor.
    return ActualAesService()
}
