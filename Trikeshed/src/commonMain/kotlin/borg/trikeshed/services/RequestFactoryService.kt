package borg.trikeshed.services

import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmInline

/**
 * KMP RequestFactory Service Interface - No google/gwt JVM dependencies
 * Integrated with reactor HTTP server toolkit for JSON arsenal and wireproto
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

    /**
     * Invoke service with tokenized binary JSON cursor wireproto
     */
    suspend fun invokeService(serviceName: String, data: Indexed<Byte>): Indexed<Byte>
}

// Taxonomical Typealiases - RequestFactory alien types
@JvmInline value class ServiceClass(val value: String)
@JvmInline value class MethodName(val value: String)
@JvmInline value class RequestContext(val json: String)
@JvmInline value class RequestData(val payload: Indexed<Byte>)
@JvmInline value class ResponseData(val payload: Indexed<Byte>)

// Join types for RequestFactory operations
typealias ServiceMethod = borg.trikeshed.lib.Join<ServiceClass, MethodName>
typealias ServiceRegistry = borg.trikeshed.lib.Join<String, () -> Any>
typealias MethodValidator = borg.trikeshed.lib.Join<String, (Any) -> Boolean>

/**
 * RequestFactory Registry - Service discovery and validation
 */
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
        
    /**
     * Get all registered services as Indexed
     */
    fun getServices(): Indexed<ServiceRegistry> {
        val services = serviceLocators.toList()
        return services.size j { i: Int -> services[i].first j services[i].second }
    }
    
    /**
     * Get all validators as Indexed
     */
    fun getValidators(): Indexed<MethodValidator> {
        val validators = methodValidators.toList()
        return validators.size j { i: Int -> validators[i].first j validators[i].second }
    }
}

/**
 * DealService interface for integration with reactor HTTP server
 */
interface DealService : CoroutineContext.Element {
    suspend fun process(data: Indexed<Byte>): Indexed<Byte>
    fun getDealInfo(dealId: String): String
    fun createDeal(dealData: Indexed<Byte>): String

    override val key: CoroutineContext.Key<*> get() = Key

    companion object Key : CoroutineContext.Key<DealService>
}