package borg.trikeshed.services
import borg.trikeshed.lib.Indexed
import kotlin.jvm.JvmInline

/**
 * BrokeShed RequestFactory Service Interface
 * This is alien GWT technology that belongs in BrokeShed, not TrikeShed core
 */
interface RequestFactoryService {
    /**
     * Process a RequestFactory call and return response payload
     */
    fun process(requestPayload: Indexed<Byte>): Indexed<Byte>
    
    /**
     * Register a service locator for dependency injection
     */
    fun registerServiceLocator(serviceClass: String, locator: () -> Any)
    
    /**
     * Register a method validator for security
     */
    fun registerMethodValidator(methodName: String, validator: (Any) -> Boolean)

    suspend fun invokeService(serviceName: String, data: ByteArray): ByteArray
}

// === REQUESTFACTORY ALIEN TYPES ===

@JvmInline value class ServiceClass(val value: String)
@JvmInline value class MethodName(val value: String)
data class ServiceMethod(val service: ServiceClass, val method: MethodName)

// === GWT-SPECIFIC TYPES ===

@JvmInline value class RequestContext(val json: String)
@JvmInline value class RequestData(val payload: Indexed<Byte>)
@JvmInline value class ResponseData(val payload: Indexed<Byte>)

// === SERVICE REGISTRY ===

object RequestFactoryRegistry {
    private val serviceLocators = mutableMapOf<String, () -> Any>()
    private val methodValidators = mutableMapOf<String, (Any) -> Boolean>()
    
    fun registerService(serviceClass: String, locator: () -> Any) {
        serviceLocators[serviceClass] = locator
    }
    
    fun registerValidator(methodName: String, validator: (Any) -> Boolean) {
        methodValidators[methodName] = validator
    }
    
    fun getService(serviceClass: String): Any? = serviceLocators[serviceClass]?.invoke()
    fun validateMethod(methodName: String, params: Any): Boolean = 
        methodValidators[methodName]?.invoke(params) ?: true
}

/**
 * Stub interface for DealService
 * TODO: Implement proper DealService
 */
interface DealService {
    fun process(data: ByteArray): ByteArray
}