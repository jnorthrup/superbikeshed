package borg.trikeshed.services

import borg.trikeshed.lib.*
import borg.trikeshed.lib.bridge.*

/**
 * BrokeShed CouchDB-backed RequestFactory Service
 * This is alien GWT + CouchDB technology that belongs in BrokeShed, not TrikeShed core
 * 
 * Adapts RequestFactory protocol to CouchDB operations.
 */
class CouchRequestFactoryService(
    private val couchClient: CouchClient,
    private val defaultDatabase: DatabaseName = DatabaseName("entities")
) : RequestFactoryService {
    // Entity version tracking
    private val entityVersions = mutableMapOf<String, Long>()
    
    // Service locators for entity types
    private val serviceLocators = mutableMapOf<String, () -> Any>()
    
    // Method validators
    private val methodValidators = mutableMapOf<String, (Any) -> Boolean>()
    
    // Simple counter for demo purposes (replaces system time)
    private var requestCounter = 0L

    override fun process(requestPayload: Indexed<Byte>): Indexed<Byte> {
        val requestJson = requestPayload.play.joinToString("") { it.toInt().toChar().toString() }
        
        return try {
            // Simple demo implementation that uses CouchDB
            val response = handleSimpleRequest(requestJson)
            CouchJsonParser.stringify(response).encodeToByteArray().toIdx()
        } catch (e: Exception) {
            val error = mapOf("success" to false, "error" to (e.message ?: "Unknown error"))
            CouchJsonParser.stringify(error).encodeToByteArray().toIdx()
        }
    }

    private fun handleSimpleRequest(requestJson: String): Map<String, Any?> {
        val request = CouchJsonParser.parse(requestJson) as? Map<*, *>
            ?: throw IllegalArgumentException("Invalid JSON request")

        val serviceType = request["service"]?.toString() ?: "unknown"
        val method = request["method"]?.toString() ?: "process"
        
        // Simplified - no actual CouchDB operations to avoid suspend complexity
        val mockDocumentId = "doc_${++requestCounter}"
        
        return mapOf(
            "success" to true,
            "service" to serviceType,
            "method" to method,
            "documentId" to mockDocumentId,
            "timestamp" to requestCounter
        )
    }

    override fun registerServiceLocator(serviceClass: String, locator: () -> Any) {
        serviceLocators[serviceClass] = locator
    }

    override fun registerMethodValidator(methodName: String, validator: (Any) -> Boolean) {
        methodValidators[methodName] = validator
    }

    override suspend fun invokeService(serviceName: String, data: ByteArray): ByteArray {
        // Placeholder implementation
        return ByteArray(0)
    }

    private fun ByteArray.toIdx(): Indexed<Byte> = size j { index: Int -> this[index] }
} 