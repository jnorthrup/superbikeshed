package borg.trikeshed.services
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
 */
interface RequestFactoryService {
    /**
     * Process a RequestFactory call and return response payload
     */
    fun process(requestPayload: Series<Byte>): Series<Byte>
    
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

@kotlin.jvm.JvmInline value class ServiceClass(val value: String)
@kotlin.jvm.JvmInline value class MethodName(val value: String)
data class ServiceMethod(val service: ServiceClass, val method: MethodName)

// === GWT-SPECIFIC TYPES ===

@kotlin.jvm.JvmInline value class RequestContext(val json: String)
@kotlin.jvm.JvmInline value class RequestData(val payload: Series<Byte>)
@kotlin.jvm.JvmInline value class ResponseData(val payload: Series<Byte>)

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