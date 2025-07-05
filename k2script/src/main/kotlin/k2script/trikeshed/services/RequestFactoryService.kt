package k2script.trikeshed.services

import k2script.trikeshed.lib.*
import borg.trikeshed.lib.Indexed as TrikeSeries
import kotlin.jvm.JvmInline

/**
 * BrokeShed RequestFactory Service Interface
 * This is alien GWT technology that belongs in BrokeShed, not TrikeShed core
 */
interface RequestFactoryService {
    /**
     * Process a RequestFactory call and return response payload
     */
    fun process(requestPayload: TrikeSeries<Byte>): TrikeSeries<Byte>
    
    /**
     * Register a service locator for dependency injection
     */
    fun registerServiceLocator(serviceClass: String, locator: () -> Any)
    
    /**
     * Register a method validator for security
     */
    fun registerMethodValidator(methodName: String, validator: (Any) -> Boolean)
}

// === REQUESTFACTORY ALIEN TYPES ===

@kotlin.jvm.JvmInline value class ServiceClass(val value: String)
@kotlin.jvm.JvmInline value class MethodName(val value: String)
data class ServiceMethod(val service: ServiceClass, val method: MethodName)

// === GWT-SPECIFIC TYPES ===

@kotlin.jvm.JvmInline value class RequestContext(val json: String)
@kotlin.jvm.JvmInline value class RequestData(val payload: TrikeSeries<Byte>)
@kotlin.jvm.JvmInline value class ResponseData(val payload: TrikeSeries<Byte>)

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