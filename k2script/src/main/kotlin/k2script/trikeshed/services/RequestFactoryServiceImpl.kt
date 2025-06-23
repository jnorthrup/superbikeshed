package k2script.trikeshed.services

import borg.trikeshed.lib.Series as TrikeSeries
import borg.trikeshed.lib.j as trikeJ
import borg.trikeshed.lib.play as trikePlay
import k2script.trikeshed.lib.bridge.*
import kotlin.jvm.JvmInline

/**
 * BrokeShed Implementation of RequestFactoryService 
 * This is alien GWT technology that belongs in BrokeShed, not TrikeShed core
 * 
 * Processes GWT RequestFactory calls using TrikeShed's native Indexed<T> and Join<A,B> patterns.
 */
internal class BrokeShedRequestFactoryServiceImpl : RequestFactoryService {
    // Maps service class names to their locator functions
    private val serviceLocators = mutableMapOf<String, () -> Any>()
    
    // Maps method names to their validator functions
    private val methodValidators = mutableMapOf<String, (Any) -> Boolean>()

    // Cached service instances for better performance
    private val serviceInstances = mutableMapOf<String, Any>()
    
    // Simple counter for demo purposes (replaces system time)
    private var requestCounter = 0L

    override fun process(requestPayload: TrikeSeries<Byte>): TrikeSeries<Byte> {
        val requestJson = requestPayload.trikePlay.joinToString("") { it.toInt().toChar().toString() }
        
        return try {
            // Simple demo implementation - just process any payload and return success
            val serviceClass = "DemoService"
            val methodName = "process"

            // Serialize the result using basic JSON
            val responseJson = buildString {
                append("""{"success":true,"service":"$serviceClass","method":"$methodName","timestamp":${++requestCounter}}""")
            }
            responseJson.encodeToByteArray().toTrikeSeries()

        } catch (e: Exception) {
            createErrorResponse(500, e.message ?: "Unknown error").encodeToByteArray().toTrikeSeries()
        }
    }

    override fun registerServiceLocator(serviceClass: String, locator: () -> Any) {
        serviceLocators[serviceClass] = locator
    }

    override fun registerMethodValidator(methodName: String, validator: (Any) -> Boolean) {
        methodValidators[methodName] = validator
    }

    private fun createErrorResponse(code: Int, message: String): String {
        return """{"success":false,"error":"$message","code":$code}"""
    }

    private fun ByteArray.toTrikeSeries(): TrikeSeries<Byte> = size trikeJ { index: Int -> this[index] }
} 