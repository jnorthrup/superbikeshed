package borg.trikeshed.reflection

import borg.trikeshed.lib.Indexed

// JavaScript platform service invoker using dynamic reflection
actual class PlatformServiceInvoker {
    actual fun findMethod(service: Any, methodName: String): Any? {
        return try {
            val jsService = service.asDynamic()
            val method = jsService[methodName]
            
            // Check if the method is actually callable
            if (method != null) {
                val methodType = js("typeof method").toString()
                if (methodType == "function") {
                    method
                } else {
                    null // Property exists but is not a function
                }
            } else {
                null
            }
        } catch (e: Exception) {
            // If accessing the property throws an exception, the method doesn't exist
            null
        }
    }

    actual fun callMethod(method: Any, service: Any, args: Indexed<Any?>): Any? {
        return try {
            val jsMethod = method.asDynamic()
            val jsService = service.asDynamic()
            val jsArgs = args.play.toList().toTypedArray()
            
            // Validate that method is callable
            val methodType = js("typeof jsMethod").toString()
            if (methodType != "function") {
                throw IllegalArgumentException("Provided method is not a function, type: $methodType")
            }
            
            // Call the method with proper this binding
            jsMethod.apply(jsService, jsArgs)
        } catch (e: Throwable) {
            when (e) {
                is IllegalArgumentException -> throw e
                else -> throw RuntimeException("Failed to invoke JavaScript method: ${e.message}", e)
            }
        }
    }
    
    // Additional utility methods for JavaScript reflection
    fun hasMethod(service: Any, methodName: String): Boolean {
        return findMethod(service, methodName) != null
    }
    
    fun getMethodNames(service: Any): List<String> {
        return try {
            val jsService = service.asDynamic()
            val names = mutableListOf<String>()
            
            // Get all enumerable properties that are functions
            js("""
                for (let prop in jsService) {
                    if (typeof jsService[prop] === 'function') {
                        names.push(prop);
                    }
                }
            """)
            
            names
        } catch (e: Exception) {
            emptyList()
        }
    }
}