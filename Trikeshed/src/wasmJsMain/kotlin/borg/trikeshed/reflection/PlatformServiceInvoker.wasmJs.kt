package borg.trikeshed.reflection

import borg.trikeshed.lib.Series

// WASM-JS platform service invoker using registry pattern for host integration
actual class PlatformServiceInvoker {
    private val methodRegistry = mutableMapOf<Pair<String, String>, (Any, Series<Any?>) -> Any?>()
    private val hostMethodRegistry = mutableMapOf<Pair<String, String>, String>() // Maps to host function names
    
    actual fun findMethod(service: Any, methodName: String): Any? {
        val serviceClassName = service::class.simpleName ?: "Unknown"
        val key = serviceClassName to methodName
        
        // First check for registered Kotlin implementations
        methodRegistry[key]?.let { return it }
        
        // Then check for host bindings
        hostMethodRegistry[key]?.let { hostFunctionName ->
            return WasmHostMethod(hostFunctionName)
        }
        
        return null
    }

    actual fun callMethod(method: Any, service: Any, args: Series<Any?>): Any? {
        return when (method) {
            is Function2<*, *, *> -> {
                try {
                    (method as (Any, Series<Any?>) -> Any?)(service, args)
                } catch (e: Exception) {
                    throw RuntimeException("Failed to invoke Kotlin method: ${e.message}", e)
                }
            }
            is WasmHostMethod -> {
                // Call through WASM host interface
                try {
                    callHostMethod(method.hostFunctionName, service, args)
                } catch (e: Exception) {
                    throw RuntimeException("Failed to invoke host method ${method.hostFunctionName}: ${e.message}", e)
                }
            }
            else -> throw IllegalArgumentException("Unknown method type: ${method::class.simpleName}")
        }
    }
    
    // Registry methods
    fun registerKotlinMethod(serviceClassName: String, methodName: String, implementation: (Any, Series<Any?>) -> Any?) {
        methodRegistry[serviceClassName to methodName] = implementation
    }
    
    fun registerHostMethod(serviceClassName: String, methodName: String, hostFunctionName: String) {
        hostMethodRegistry[serviceClassName to methodName] = hostFunctionName
    }
    
    // Placeholder for host method calls - would be implemented with actual WASM host bindings
    private fun callHostMethod(hostFunctionName: String, service: Any, args: Series<Any?>): Any? {
        // This would use actual WASM imports to call host functions
        // For now, return a placeholder result
        println("WASM: Would call host method $hostFunctionName with ${args.size} arguments")
        return null
    }
    
    companion object {
        val instance = PlatformServiceInvoker()
    }
}

// Helper class to represent host method references
data class WasmHostMethod(val hostFunctionName: String)