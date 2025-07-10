@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package k2script.trikeshed

import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * CCEK (Context Capture and Execute Kit) implementation for k2script
 * Provides clean dependency injection and context management without bloat
 */
@kotlin.jvm.JvmInline
value class Context(val scope: String) {
    
    companion object {
        internal val contexts = ConcurrentHashMap<String, MutableMap<KClass<*>, Any>>()
        
        fun create(scope: String): Context = Context(scope).also {
            contexts.putIfAbsent(scope, ConcurrentHashMap())
        }
        
        fun global(): Context = create("global")
        fun script(scriptPath: String): Context = create("script:$scriptPath")
    }
    
    fun <T : Any> provide(instance: T, clazz: KClass<T>): Context {
        contexts[scope]?.put(clazz, instance)
        return this
    }
    
    fun <T : Any> get(clazz: KClass<T>): T? {
        return contexts[scope]?.get(clazz) as? T
    }
    
    fun <T : Any> require(clazz: KClass<T>): T {
        return get(clazz) ?: throw IllegalStateException("Required context ${clazz.simpleName} not found in scope '$scope'")
    }
    
    fun cleanup() {
        contexts.remove(scope)
    }
}

/**
 * Tensor<T> = Join<IntArray, (IntArray) -> T>
 * For tensor-first columnar processing
 */
typealias Tensor<T> = Join<IntArray, (IntArray) -> T>

/**
 * Legacy compatibility typealiases - point to proper TrikeShed types
 * Use Join<A,B> and Indexed<T> directly in new code
 */
typealias Pai2<A, B> = Join<A, B>  // Legacy Pair -> Join
typealias Vect0r<T> = Indexed<T>    // Legacy Vector -> Indexed

/**
 * Hot/cold path optimization marker
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class HotPath

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)  
@Retention(AnnotationRetention.SOURCE)
annotation class ColdPath

/**
 * DCE (Dead Code Elimination) marker
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class KeepAlive

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Eliminate