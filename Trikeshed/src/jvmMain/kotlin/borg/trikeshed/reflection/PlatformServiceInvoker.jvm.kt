package borg.trikeshed.reflection

import borg.trikeshed.lib.Series
import kotlin.reflect.KCallable
import kotlin.reflect.full.members

actual class PlatformServiceInvoker {
    actual fun findMethod(service: Any, methodName: String): Any? {
        return try {
            // Use Kotlin reflection to find the method
            service::class.members.find { callable ->
                callable.name == methodName && callable.parameters.isNotEmpty() // Ensure it takes at least 'this' parameter
            }
        } catch (e: Exception) {
            // If reflection fails, return null
            null
        }
    }

    actual fun callMethod(method: Any, service: Any, args: Series<Any?>): Any? {
        val kCallable = method as? KCallable<*> 
            ?: throw IllegalArgumentException("Method must be a KCallable on JVM, got: ${method::class.simpleName}")
        
        return try {
            val argsList = args.play.toList()
            val allArgs: List<Any?> = listOf(service) + argsList // Include 'this' parameter
            
            // Validate parameter count
            if (kCallable.parameters.size != allArgs.size) {
                throw IllegalArgumentException(
                    "Parameter count mismatch: method expects ${kCallable.parameters.size} parameters, " +
                    "got ${allArgs.size} (including receiver)"
                )
            }
            
            kCallable.call(*allArgs.toTypedArray())
        } catch (e: Exception) {
            when (e) {
                is IllegalArgumentException -> throw e
                else -> throw RuntimeException("Failed to invoke method '${kCallable.name}': ${e.message}", e)
            }
        }
    }
    
    // Additional utility methods for JVM reflection
    fun getMethodNames(service: Any): List<String> {
        return try {
            service::class.members
                .filter { it.parameters.isNotEmpty() } // Has at least 'this' parameter
                .map { it.name }
                .distinct()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun hasMethod(service: Any, methodName: String): Boolean {
        return findMethod(service, methodName) != null
    }
}