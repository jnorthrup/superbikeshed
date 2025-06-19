package borg.trikeshed.consumers.couchdbipfs.services

import borg.trikeshed.lib.*
import borg.trikeshed.parse.json.*

/**
 * Implementation of RequestFactoryService that processes GWT RequestFactory calls
 * using TrikeShed's native Series<T> and Join<A,B> patterns.
 */
internal class RequestFactoryServiceImpl : RequestFactoryService {
    // Maps service class names to their locator functions
    private val serviceLocators = mutableMapOf<ServiceClassName, () -> Any>()
    
    // Maps method names to their validator functions
    private val methodValidators = mutableMapOf<ServiceMethodName, (Series<Any?>) -> Boolean>()

    // Cached service instances for better performance
    private val serviceInstances = mutableMapOf<ServiceClassName, Any>()

    override suspend fun process(payload: Series<Byte>): Series<Byte> {
        val requestJson = payload.▶.toByteArray().decodeToString()
        
        return try {
            // Parse the reque
            
            

            val request = JsonParser.parse(requestJson.toSeries())
            
            // Extract service and method info
            val serviceClass = ServiceClassName(request.getString("serviceClass"))
            val methodName = ServiceMethodName(request.getString("methodName"))
            val args = request.getArray("args").α { it }

            // Validate the method call if a validator is registered
            methodValidators[methodName]?.let { validator ->
                if (!validator(args)) {
                    return createErrorResponse("Method validation failed")
                }
            }

            // Get or create service instance
            val service = serviceInstances.getOrPut(serviceClass) {
                serviceLocators[serviceClass]?.invoke() 
                    ?: throw IllegalStateException("No locator registered for service: ${serviceClass.value}")
            }

            // Invoke the method using reflection
            val method = service::class.members.find { it.name == methodName.value }
                ?: throw IllegalStateException("Method not found: ${methodName.value}")

            // Convert args to the expected types
            val convertedArgs = args.α { arg ->
                when (arg) {
                    is Number -> arg
                    is String -> arg
                    is Boolean -> arg
                    is Map<*, *> -> JsonParser.parse(JsonSerializer.serialize(arg).toSeries())
                    else -> arg
                }
            }

            // Invoke the method and get result
            val result = method.call(service, *convertedArgs.▶.toList().toTypedArray())

            // Serialize the result
            val responseJson = JsonSerializer.serialize(result)
            responseJson.▶.joinToString("").encodeToByteArray().toSeries()

        } catch (e: Exception) {
            createErrorResponse(e.message ?: "Unknown error")
        }
    }

    override fun registerServiceLocator(serviceClass: String, locator: () -> Any) {
        serviceLocators[ServiceClassName(serviceClass)] = locator
    }

    override fun registerMethodValidator(methodName: String, validator: (Series<Any?>) -> Boolean) {
        methodValidators[ServiceMethodName(methodName)] = validator
    }

    private fun createErrorResponse(message: String): Series<Byte> {
        val error = mapOf(
            "success" to false,
            "error" to message
        )
        return JsonSerializer.serialize(error).▶.joinToString("").encodeToByteArray().toSeries()
    }
} 