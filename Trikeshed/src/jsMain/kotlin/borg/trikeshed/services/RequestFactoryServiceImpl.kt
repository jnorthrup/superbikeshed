package borg.trikeshed.services

import borg.trikeshed.lib.Series
import borg.trikeshed.lib.j

internal actual class RequestFactoryServiceImpl actual constructor() : RequestFactoryService {

    actual override suspend fun process(payload: Series<Byte>): Series<Byte> {
        // Return an empty Series for now
        return 0 j { 0.toByte() }
    }

    actual override fun registerServiceLocator(serviceClass: String, locator: () -> Any) {
        // No-op stub
    }

    actual override fun registerMethodValidator(methodName: String, validator: (Series<Any?>) -> Boolean) {
        // No-op stub
    }
}