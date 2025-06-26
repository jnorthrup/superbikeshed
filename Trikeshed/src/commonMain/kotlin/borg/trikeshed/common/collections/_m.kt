package borg.trikeshed.common.collections
@file:OptIn(ExperimentalUnsignedTypes::class)


/**
 * missing stdlib map convenience operator
 */
object _m {
    operator fun <K, V, P : Pair<K, V>> get(p: List<P>): Map<K, V> = (p).toMap()
    operator fun <K, V, P : Pair<K, V>> get(vararg p: P): Map<K, V> = mapOf(*p)
}
