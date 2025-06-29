package borg.trikeshed.services
<<<<<<< HEAD


import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmInline

/**
 * KMP RequestFactory Service Interface - No google/gwt JVM dependencies
 * Integrated with reactor HTTP server toolkit for JSON arsenal and wireproto
=======
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j
import borg.trikeshed.lib.α
import borg.trikeshed.lib.play
import borg.trikeshed.lib.toSeries
import borg.trikeshed.lib.*
import kotlin.jvm.JvmInline
import kotlinx.coroutines.flow.Flow

/**
 * BrokeShed RequestFactory Service Interface
 * This is alien GWT technology that belongs in BrokeShed, not TrikeShed core
>>>>>>> origin/feat/core-serialization-impl
 */
interface RequestFactoryService {
    /**
     * Process a RequestFactory call and return response payload
     */
<<<<<<< HEAD
    fun process(requestPayload: Indexed<Byte>): Indexed<Byte>
=======
    fun process(requestPayload: Series<Byte>): Series<Byte>
>>>>>>> origin/feat/core-serialization-impl
    
    /**
     * Register a service locator for dependency injection
     */
    fun registerServiceLocator(serviceClass: String, locator: () -> Any)
    
    /**
     * Register a method validator for security
     */
    fun registerMethodValidator(methodName: String, validator: (Any) -> Boolean)

<<<<<<< HEAD
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
=======
    suspend fun invokeService(serviceName: String, data: ByteArray): ByteArray
}

// === REQUESTFACTORY ALIEN TYPES ===

@JvmInline value class ServiceClass(val value: String)
@JvmInline value class MethodName(val value: String)
data class ServiceMethod(val service: ServiceClass, val method: MethodName)

// === GWT-SPECIFIC TYPES ===

@JvmInline value class RequestContext(val json: String)
@JvmInline value class RequestData(val payload: Series<Byte>)
@JvmInline value class ResponseData(val payload: Series<Byte>)

// === SERVICE REGISTRY ===

>>>>>>> origin/feat/core-serialization-impl
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
<<<<<<< HEAD
    
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
=======
    fun validateMethod(methodName: String, params: Any): Boolean = 
        methodValidators[methodName]?.invoke(params) ?: true
>>>>>>> origin/feat/core-serialization-impl
}