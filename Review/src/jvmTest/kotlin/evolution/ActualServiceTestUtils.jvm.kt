package evolution

import kotlin.coroutines.CoroutineContext

actual fun getTestHkdfService(testContext: CoroutineContext): HkdfService {
    // On JVM, directly instantiate the actual implementation.
    // Assumes ActualHkdfService.jvm.kt exists and has a no-arg constructor.
    return ActualHkdfService()
}

actual fun getTestAesService(testContext: CoroutineContext): AesService {
    // On JVM, directly instantiate the actual implementation.
    // Assumes ActualAesService.jvm.kt exists and has a no-arg constructor.
    return ActualAesService()
}
