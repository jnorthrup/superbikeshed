package borg.trikeshed.services

import borg.trikeshed.lib.*
import kotlin.jvm.JvmInline

interface RequestFactoryService {
 suspend fun process(payload: Series<Byte>): Series<Byte>
 fun registerServiceLocator(serviceClass: String, locator: () -> Any)
 fun registerMethodValidator(methodName: String, validator: (args: Series<Any?>) -> Boolean)
 companion object {
 fun create(): RequestFactoryService = RequestFactoryServiceImpl()
 }
}

internal expect class RequestFactoryServiceImpl() : RequestFactoryService

@JvmInline value class ServiceMethodName(val value: String)
@JvmInline value class ServiceClassName(val value: String)
@JvmInline value class RequestFactoryResult(val value: Series<Byte>) 
