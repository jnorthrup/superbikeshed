package k2script.trikeshed

import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * CCEK (Context Capture and Execute Kit) implementation for k2script
 * Provides clean dependency injection and context management without bloat
 */
@JvmInline
value class Context(val scope: String) {
    
    companion object {
        val contexts = ConcurrentHashMap<String, MutableMap<KClass<*>, Any>>()
        
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
 * TrikeShed Series for columnar data processing
 */
@JvmInline
value class Series<T>(val data: Array<T>) : Iterable<T> {
    
    val size: Int get() = data.size
    
    operator fun get(index: Int): T = data[index]
    
    override fun iterator(): Iterator<T> = data.iterator()
    
    /**
     * α (alpha) transformation operator - the ONLY transformation mechanism
     */
    @Suppress("UNCHECKED_CAST")
    fun <R> α(transform: (T) -> R): Series<R> {
        val result = arrayOfNulls<Any?>(data.size)
        for (i in data.indices) {
            result[i] = transform(data[i])
        }
        return Series(result as Array<R>)
    }
    
    /**
     * ▶ (play button) - gateway to stdlib collections
     */
    val `▶` : List<T> get() = data.toList()
    
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <T> of(vararg elements: T): Series<T> = Series(elements as Array<T>)
        
        @Suppress("UNCHECKED_CAST")
        fun <T> empty(): Series<T> = Series(emptyArray<Any?>() as Array<T>)
    }
}

/**
 * Join<A,B> - the ONLY composition operator
 */
@JvmInline
value class Join<A, B>(private val pair: Pair<A, B>) {
    
    val first: A get() = pair.first
    val second: B get() = pair.second
    
}

/**
 * j operator - creates Join<A,B>
 */
infix fun <A, B> A.j(other: B): Join<A, B> = Join(this to other)

/**
 * Tensor<T> = Join<IntArray, (IntArray) -> T>
 * For tensor-first columnar processing
 */
typealias Tensor<T> = Join<IntArray, (IntArray) -> T>

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