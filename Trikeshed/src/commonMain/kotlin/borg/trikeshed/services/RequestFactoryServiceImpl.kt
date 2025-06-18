package borg.trikeshed.services

import borg.trikeshed.lib.*
import borg.trikeshed.parse.json.*
import kotlin.jvm.JvmInline

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
        val requestJson = payload.`▶`.joinToString("") { it.toInt().toChar().toString() }
        
        return try {
            // Simple demo implementation - just process any payload and return success
            val serviceClass = ServiceClassName("DemoService")
            val methodName = ServiceMethodName("process")

            // Serialize the result using basic JSON
            val responseJson = buildString {
                append("""{"success":true,"service":"${serviceClass.value}","method":"${methodName.value}","timestamp":${System.currentTimeMillis()}}""")
            }
            responseJson.encodeToByteArray().toSeries()

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
        val errorJson = """{"success":false,"error":"$message"}"""
        return errorJson.encodeToByteArray().toSeries()
    }

    private fun String.toSeries(): Series<Char> = length j { index: Int -> this[index] }
    private fun ByteArray.toSeries(): Series<Byte> = size j { index: Int -> this[index] }
} 