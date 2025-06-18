package borg.trikeshed.services

import borg.trikeshed.lib.Series

internal actual class RequestFactoryServiceImpl actual constructor() : RequestFactoryService {

    actual override suspend fun process(payload: Series<Byte>): Series<Byte> {
        TODO("JS RequestFactory processing not implemented")
    }

    actual override fun registerServiceLocator(serviceClass: String, locator: () -> Any) {
        TODO("Not yet implemented")
    }

    actual override fun registerMethodValidator(methodName: String, validator: (Series<Any?>) -> Boolean) {
        TODO("Not yet implemented")
    }
}