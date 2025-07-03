package borg.trikeshed.services

import borg.trikeshed.lib.Indexed as Indexed
import borg.trikeshed.lib.j

internal actual class RequestFactoryServiceImpl actual constructor() : RequestFactoryService {

    actual override suspend fun process(payload: Indexed<Byte>): Indexed<Byte> {
        // Return an empty Indexed for now
        return 0 Indexed { 0.toByte() }
    }

    actual override fun registerServiceLocator(serviceClass: String, locator: () -> Any) {
        // No-op stub
    }

    actual override fun registerMethodValidator(methodName: String, validator: (Indexed<Any?>) -> Boolean) {
        // No-op stub
    }
}