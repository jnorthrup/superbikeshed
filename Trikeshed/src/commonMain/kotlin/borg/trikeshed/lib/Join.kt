package borg.trikeshed.lib

interface Join<A, B> {
 val a: A
 val b: B
 operator fun component1(): A = a
 operator fun component2(): B = b
 val pair: Pair<A, B> get() = Pair(a, b)
 
 companion object {
 operator fun <A, B> invoke(a: A, b: B): Join<A, B> = object : Join<A, B> {
 override val a: A get() = a
 override val b: B get() = b
 }
 }
}

typealias Twin<T> = Join<T, T>
inline infix fun <A, B> A.j(b: B) = Join.invoke(this, b) 