package borg.trikeshed.services
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j
import borg.trikeshed.lib.α
import borg.trikeshed.lib.play
import borg.trikeshed.lib.toSeries
import borg.trikeshed.lib.a
import borg.trikeshed.lib.b

import borg.trikeshed.lib.*
import borg.trikeshed.lib.bridge.*
import kotlin.jvm.JvmInline

/**
 * BrokeShed Implementation of RequestFactoryService 
 * This is alien GWT technology that belongs in BrokeShed, not TrikeShed core
 * 
 * Processes GWT RequestFactory calls using TrikeShed's native Series<T> and Join<A,B> patterns.
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

    override fun process(requestPayload: Series<Byte>): Series<Byte> {
        val requestJson = requestPayload.play.joinToString("") { it.toInt().toChar().toString() }
        
        return try {
            // Simple demo implementation - just process any payload and return success
            val serviceClass = "DemoService"
            val methodName = "process"

            // Serialize the result using basic JSON
            val responseJson = buildString {
                append("""{"success":true,"service":"$serviceClass","method":"$methodName","timestamp":${++requestCounter}}""")
            }
            responseJson.encodeToByteArray().toSeries()

        } catch (e: Exception) {
            createErrorResponse(500, e.message ?: "Unknown error").encodeToByteArray().toSeries()
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

    private fun ByteArray.toSeries(): Series<Byte> = size j { index: Int -> this[index] }
} 