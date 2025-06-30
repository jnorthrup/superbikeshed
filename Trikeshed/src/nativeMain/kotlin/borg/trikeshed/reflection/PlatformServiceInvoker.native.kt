package borg.trikeshed.reflection

import borg.trikeshed.lib.Series

// Native platform service invoker using a registry pattern
// This provides a working reflection alternative for Native platforms
actual class PlatformServiceInvoker {
    private val methodRegistry = mutableMapOf<Pair<String, String>, (Any, Series<Any?>) -> Any?>()
    
    actual fun findMethod(service: Any, methodName: String): Any? {
        val serviceClassName = service::class.simpleName ?: "Unknown"
        val key = serviceClassName to methodName
        return methodRegistry[key]
    }

    actual fun callMethod(method: Any, service: Any, args: Series<Any?>): Any? {
        val methodImpl = method as? ((Any, Series<Any?>) -> Any?)
            ?: throw IllegalArgumentException("Method must be a registered function implementation")
        
        return try {
            methodImpl(service, args)
        } catch (e: Exception) {
            throw RuntimeException("Failed to invoke method: ${e.message}", e)
        }
    }
    
    // Registry methods for registering service methods at runtime
    fun registerMethod(serviceClassName: String, methodName: String, implementation: (Any, Series<Any?>) -> Any?) {
        methodRegistry[serviceClassName to methodName] = implementation
    }
    
    fun registerServiceMethods(serviceClass: Any, methods: Map<String, (Any, Series<Any?>) -> Any?>) {
        val className = serviceClass::class.simpleName ?: "Unknown"
        methods.forEach { (methodName, impl) ->
            registerMethod(className, methodName, impl)
        }
    }
    
    companion object {
        // Shared instance for service registration
        val instance = PlatformServiceInvoker()
    }
}