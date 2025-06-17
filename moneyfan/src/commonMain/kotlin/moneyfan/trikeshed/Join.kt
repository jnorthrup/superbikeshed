package moneyfan.trikeshed

/**
 * A simple, immutable pair of values.
 *
 * Pronounced "Joint" like a "universal joint" or a "joint bank account".
 * The "j" operator is a mnemonic for "join".
 *
 * @param <A> The type of the first value.
 * @param <B> The type of the second value.
 */
interface Join<out A, out B> {
    val a: A
    val b: B

    operator fun component1(): A = a
    operator fun component2(): B = b

    val pair: Pair<A, B> get() = Pair(a, b)

    companion object {
        /** Factory to create a Join instance. */
        operator fun <A, B> invoke(a: A, b: B): Join<A, B> = object : Join<A, B> {
            override val a: A = a
            override val b: B = b
            // Consider overriding toString, equals, hashCode if this were a data class
            // For an interface implementation, they rely on the object's identity or further implementation.
            override fun toString(): String = "($a j $b)"
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Join<*, *>) return false
                return a == other.a && b == other.b
            }
            override fun hashCode(): Int = 31 * (a?.hashCode() ?: 0) + (b?.hashCode() ?: 0)
        }
    }
}

/** Infix factory to create a Join instance. */
infix fun <A, B> A.j(b: B): Join<A, B> = Join(this, b)

/** Typealias for a Join where both elements are of the same type. */
typealias Twin<T> = Join<T, T>

/** Extension property to get the first element of a Join. */
val <A> Join<A, *>.first: A get() = a

/** Extension property to get the second element of a Join. */
val <B> Join<*, B>.second: B get() = b
