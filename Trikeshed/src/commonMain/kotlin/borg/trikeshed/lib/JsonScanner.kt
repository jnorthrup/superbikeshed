package borg.trikeshed.lib

@JvmInline
value class QuoteState(val value: Int) {
    companion object {
        val OUTSIDE = QuoteState(0)
        val IN_STRING = QuoteState(1)
        val ESCAPED = QuoteState(2)
    }
    
    val isInString: Boolean get() = this == IN_STRING
}

@JvmInline
value class Depth(val value: Int)

class JsonScanner(private val input: String) {
    private var position = 0
    private var quoteState = QuoteState.OUTSIDE
    private var depth = Depth(0)
    
    fun next(): Char? = if (position < input.length) input[position++] else null
    
    fun peek(): Char? = if (position < input.length) input[position] else null
    
    fun reset() {
        position = 0
        quoteState = QuoteState.OUTSIDE
        depth = Depth(0)
    }
} 