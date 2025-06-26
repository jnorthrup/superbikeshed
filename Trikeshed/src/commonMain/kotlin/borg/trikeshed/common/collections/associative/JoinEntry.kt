@file:Suppress("UNCHECKED_CAST")

package borg.trikeshed.common.collections.associative
@file:OptIn(ExperimentalUnsignedTypes::class)



import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.lib.Join
import kotlin.jvm.JvmInline

@JvmInline
value class JoinEntry<A, B>(val join: Join<A, B>) : Map.Entry<A, B> {
    override val key: A get() = join.a
    override val value: B get() = join.b
    override fun toString(): String = join.run { "($a, $b)" }
}


inline val <A, B> Join<A, B>.entry: JoinEntry<A, B> get() = JoinEntry(this)

