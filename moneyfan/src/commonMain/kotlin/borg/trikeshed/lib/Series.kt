package borg.trikeshed.lib

/**
 * Minimal TrikeShed compatibility layer for moneyfan
 * Implements core Series<T> and Join<A,B> patterns
 */

/**
 * Series<T> - Core data structure for TrikeShed
 */
class Series<T>(private val data: List<T>) {
    val size: Int get() = data.size
    
    /**
     * The PLAY BUTTON play - materialization gateway to standard collections
     * DO NOT CHANGE THE BACKTICKS - THEY ARE KOTLIN IDENTIFIER SYNTAX
     */
    val `play`: List<T> get() = data
    
    // Legacy alias for compatibility
    val play: List<T> get() = data
    
    /**
     * α (alpha) transformation operator - the ONLY transformation operator
     */
    fun <R> α(transform: (T) -> R): Series<R> {
        return Series(data.map(transform))
    }
    
    operator fun get(index: Int): T = data[index]
    
    companion object {
        fun <T> of(size: Int, generator: (Int) -> T): Series<T> {
            return Series((0 until size).map(generator))
        }
        
        fun <T> of(vararg elements: T): Series<T> {
            return Series(elements.toList())
        }
        
        fun <T> empty(): Series<T> {
            return Series(emptyList())
        }
    }
}

/**
 * Join<A,B> - the ONLY composition operator
 */
data class Join<A, B>(val a: A, val b: B)

// j operator imported from canonical borg.trikeshed.lib.Join
// Use: import borg.trikeshed.lib.j