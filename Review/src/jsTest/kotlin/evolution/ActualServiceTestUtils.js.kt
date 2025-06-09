package evolution

import kotlin.coroutines.CoroutineContext

actual fun getTestHkdfService(testContext: CoroutineContext): HkdfService {
    // On JS, directly instantiate the actual implementation.
    // Assumes ActualHkdfService.js.kt exists and has a no-arg constructor.
    return ActualHkdfService()
}

actual fun getTestAesService(testContext: CoroutineContext): AesService {
    // On JS, directly instantiate the actual implementation.
    // Assumes ActualAesService.js.kt exists and has a no-arg constructor.
    return ActualAesService()
}
