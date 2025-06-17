package borg.trikeshed.lib

sealed class Peano {
    object Zero : Peano()
    data class Succ(val n: Peano) : Peano()
    
    fun toInt(): Int = when (this) {
        is Zero -> 0
        is Succ -> 1 + n.toInt()
    }
    
    companion object {
        fun fromInt(n: Int): Peano = when {
            n <= 0 -> Zero
            else -> Succ(fromInt(n - 1))
        }
    }
}

fun Peano.plus(other: Peano): Peano = when (this) {
    is Peano.Zero -> other
    is Peano.Succ -> Peano.Succ(n.plus(other))
}

fun Peano.times(other: Peano): Peano = when (this) {
    is Peano.Zero -> Peano.Zero
    is Peano.Succ -> other.plus(n.times(other))
} 