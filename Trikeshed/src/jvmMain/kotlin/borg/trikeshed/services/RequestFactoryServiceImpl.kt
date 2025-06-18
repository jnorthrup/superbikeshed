package borg.trikeshed.services

import borg.trikeshed.lib.*
import borg.trikeshed.lib.`▶`
import borg.trikeshed.parse.json.JsonImpl
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredFunctions

actual class RequestFactoryServiceImpl actual constructor() : RequestFactoryService {
 private val serviceLocators = mutableMapOf<ServiceClassName, () -> Any>()
 private val methodValidators = mutableMapOf<ServiceMethodName, (Series<Any?>) -> Boolean>()
 private val serviceInstances = mutableMapOf<ServiceClassName, Any>()

 actual override suspend fun process(payload: Series<Byte>): Series<Byte> {
 val requestJson = payload.`▶`.toByteArray().decodeToString()
 
 return try {
 val request = JsonImpl.parse(requestJson) as? Map<String, Any>
 ?: throw IllegalArgumentException("Invalid request format")
 
 val serviceClass = ServiceClassName(request["serviceClass"] as String)
 val methodName = ServiceMethodName(request["methodName"] as String)
 val argsList = request["args"] as? List<Any?> ?: emptyList()
 val args = argsList.toSeries()

 methodValidators[methodName]?.let { validator ->
 if (\!validator(args)) {
 return createErrorResponse("Method validation failed")
 }
 }

 val service = serviceInstances.getOrPut(serviceClass) {
 serviceLocators[serviceClass]?.invoke() 
 ?: throw IllegalStateException("No locator registered for service: ${serviceClass.value}")
 }

 val method = service::class.declaredFunctions.find { it.name == methodName.value }
 ?: throw IllegalStateException("Method not found: ${methodName.value}")

 val result = if (method.isSuspend) {
 method.callSuspend(service, *args.`▶`.toList().toTypedArray())
 } else {
 method.call(service, *args.`▶`.toList().toTypedArray())
 }

 val response = mapOf("success" to true, "result" to result)
 JsonImpl.stringify(response).encodeToByteArray().toSeries()

 } catch (e: Exception) {
 createErrorResponse(e.message ?: "Unknown error")
 }
 }

 actual override fun registerServiceLocator(serviceClass: String, locator: () -> Any) {
 serviceLocators[ServiceClassName(serviceClass)] = locator
 }

 actual override fun registerMethodValidator(methodName: String, validator: (Series<Any?>) -> Boolean) {
 methodValidators[ServiceMethodName(methodName)] = validator
 }

 private fun createErrorResponse(message: String): Series<Byte> {
 val error = mapOf(
 "success" to false,
 "error" to message
 )
 return JsonImpl.stringify(error).encodeToByteArray().toSeries()
 }
}
