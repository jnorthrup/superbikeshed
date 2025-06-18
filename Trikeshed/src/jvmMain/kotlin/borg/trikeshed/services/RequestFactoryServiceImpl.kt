package borg.trikeshed.services

import borg.trikeshed.lib.Series

actual class RequestFactoryServiceImpl actual constructor() : RequestFactoryService {

    actual override suspend fun process(payload: Series<Byte>): Series<Byte> {
        TODO("JVM RequestFactory processing")
    }

    actual override fun registerServiceLocator(serviceClass: String, locator: () -> Any) {
        TODO("Service locator registration")
    }

    actual override fun registerMethodValidator(methodName: String, validator: (Series<Any?>) -> Boolean) {
        TODO("Method validator registration")
    }
}