package borg.trikeshed.collections

import borg.trikeshed.wireproto.RegisterJoin
import borg.trikeshed.wireproto.j  // Import all j operator overloads
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * CCEK Context for optimal register packing dispatch
 * 
 * This context element enables KSP-generated dispatch to optimal
 * register packing strategies based on type information.
 */
data class RegisterPackingContext(
    val strategy: PackingStrategy
) : CoroutineContext.Element {
    
    companion object Key : CoroutineContext.Key<RegisterPackingContext>
    
    override val key: CoroutineContext.Key<*> = Key
}

/**
 * Packing strategies for different type combinations
 */
sealed interface PackingStrategy {
    /**
     * Pack primitives into 64-bit registers when possible
     */
    object RegisterPacking : PackingStrategy
    
    /**
     * Use traditional heap allocation
     */
    object HeapAllocation : PackingStrategy
    
    /**
     * Adaptive strategy based on runtime analysis
     */
    data class Adaptive(
        val threshold: Int = 1000
    ) : PackingStrategy
}

/**
 * KSP annotation to generate optimal packing dispatch
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class OptimizeRegisterPacking(
    val strategy: String = "adaptive"
)

/**
 * Marker interface for register-packable nodes
 */
interface PackableNode<K, V> {
    fun canPack(key: K, value: V): Boolean
    fun pack(key: K, value: V): RegisterJoin<K, V>
    fun unpackKey(packed: RegisterJoin<K, V>): K
    fun unpackValue(packed: RegisterJoin<K, V>): V
}

/**
 * Type dispatch for node creation with packing optimization
 * KSP will generate implementations based on type analysis
 */
inline fun <K, V> createOptimalNode(
    key: K, 
    value: V,
    context: CoroutineContext = EmptyCoroutineContext
): Any {
    val packingContext = context[RegisterPackingContext]
    
    return when (packingContext?.strategy) {
        is PackingStrategy.RegisterPacking -> {
            // KSP generates these checks at compile time
            when {
                key is Int && value is Int -> key j value
                key is Short && value is Short -> key j value
                key is Byte && value is Byte -> key j value
                key is Boolean && value is Boolean -> key j value
                else -> TreeMapNode(key, value)
            }
        }
        is PackingStrategy.HeapAllocation -> TreeMapNode(key, value)
        is PackingStrategy.Adaptive -> {
            // Runtime decision based on heuristics
            if (shouldPackAtRuntime(key, value)) {
                tryRegisterPack(key, value) ?: TreeMapNode(key, value)
            } else {
                TreeMapNode(key, value)
            }
        }
        null -> TreeMapNode(key, value)
    }
}

/**
 * Runtime heuristic for packing decision
 */
fun <K, V> shouldPackAtRuntime(key: K, value: V): Boolean {
    return when {
        key is Number && value is Number -> true
        key is Boolean || value is Boolean -> true
        else -> false
    }
}

/**
 * Try to pack at runtime using RegisterJoin
 */
fun <K, V> tryRegisterPack(key: K, value: V): Any? {
    return try {
        when {
            key is Int && value is Int -> key j value
            key is Short && value is Short -> key j value
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Standard tree node for non-packable types
 */
data class TreeMapNode<K, V>(
    val key: K,
    val value: V,
    var left: TreeMapNode<K, V>? = null,
    var right: TreeMapNode<K, V>? = null,
    var parent: TreeMapNode<K, V>? = null,
    var color: Boolean = true // RED
)

// j operators are imported from wireproto package