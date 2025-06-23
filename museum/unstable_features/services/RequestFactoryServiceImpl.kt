package borg.trikeshed.services
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j
import borg.trikeshed.lib.α
import borg.trikeshed.lib.play
import borg.trikeshed.lib.toSeries
import borg.trikeshed.lib.*
import borg.trikeshed.lib.bridge.*
import kotlin.jvm.JvmInline
import kotlinx.coroutines.flow.Flow

/**
 * BrokeShed Implementation of RequestFactoryService 
 * This is alien GWT technology that belongs in BrokeShed, not TrikeShed core
 * 
 * Processes GWT RequestFactory calls using TrikeShed's native Indexed<T> and Join<A,B> patterns.
 */
internal class RequestFactoryServiceImpl : RequestFactoryService {
    // Maps service class names to their locator functions
    private val serviceLocators = mutableMapOf<String, () -> Any>()
    
    // Maps method names to their validator functions
    private val methodValidators = mutableMapOf<String, (Any) -> Boolean>()

    // Cached service instances for better performance
    private val serviceInstances = mutableMapOf<String, Any>()
    
    // Simple counter for demo purposes (replaces system time)
    private var requestCounter = 0L

    override fun process(requestPayload: Indexed<Byte>): Indexed<Byte> {
        val requestJson = requestPayload.play.joinToString("") { it.toInt().toChar().toString() }
        
        return try {
            // Simple demo implementation - just process any payload and return success
            val serviceClass = "DemoService"
            val methodName = "process"

            // Serialize the result using basic JSON
            val responseJson = buildString {
                append("""{"success":true,"service":"$serviceClass","method":"$methodName","timestamp":${++requestCounter}}""")
            }
            responseJson.encodeToByteArray().toIndexed()

        } catch (e: Exception) {
            createErrorResponse(500, e.message ?: "Unknown error").encodeToByteArray().toIndexed()
        }
    }

    override fun registerServiceLocator(serviceClass: String, locator: () -> Any) {
        serviceLocators[serviceClass] = locator
    }

    override fun registerMethodValidator(methodName: String, validator: (Any) -> Boolean) {
        methodValidators[methodName] = validator
    }

    override suspend fun invokeService(serviceName: String, data: ByteArray): ByteArray {
        // TODO: Implement actual service invocation logic
        return createErrorResponse(501, "Service invocation not implemented").encodeToByteArray()
    }

    private fun createErrorResponse(code: Int, message: String): String {
        return """{"success":false,"error":"$message","code":$code}"""
    }

    private fun ByteArray.toIndexed(): Indexed<Byte> = size j { index: Int -> this[index] }
} 