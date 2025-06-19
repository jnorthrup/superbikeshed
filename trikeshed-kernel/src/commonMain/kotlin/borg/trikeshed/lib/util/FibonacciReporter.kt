package borg.trikeshed.lib

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.jvm.JvmOverloads
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

// Integrated Usable interface locally
interface Usable {
    fun open()
    fun close()
}

fun <T : Usable, R> T.use(block: (T) -> R): R {
    open()
    try {
        return block(this)
    } finally {
        close()
    }
}

@JvmOverloads
tailrec fun fib(n: Int, a: Int = 1, b: Int = 1): Int = when (n) {
    0 -> a
    1 -> b
    else -> fib(n - 1, b, a + b)
}

@OptIn(ExperimentalTime::class)
/**
 * A logger which reports at Fibonacci sequence intervals (1, 1, 2, 3, 5, 8, ...).
 * The report method returns a string if the current count matches a Fibonacci number.
 */
class FibonacciReporter(
    /** if we know the size beforehand we provide estimation */
    val size: Int? = null,
    /** what do we report? */
    val noun: String = "rows",
) : Usable {

    private var lastReported = 0
    private var nextFib = 1
    val begin: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow()
    var count: Int = 0

    override fun toString(): String =
        "FibonacciReporter(size=$size, noun='$noun', lastReported=$lastReported, nextFib=$nextFib, begin=$begin, count=$count)"

    override fun open() = logDebug { "debug: $noun FibonacciReporter opened @$begin" }
    
    override fun close() = logDebug {
        "debug:FibonacciReporter closed ${report()} @ ${TimeSource.Monotonic.markNow()-begin} "
    }

    fun report(): String? = (count++).run {
        if (count >= nextFib) {
            lastReported = count
            nextFib = fib(lastReported + 1)
            val l = begin.elapsedNow()
            val slice = l / max(1, count)
            val secondsSinceBegin = l.inWholeSeconds

            "logged $count $noun in $l ${(count.toDouble() / (secondsSinceBegin.toDouble())).toFloat()}/s " + (size?.let {
                val ticksLeft = size - count
                val remaining: Duration = slice * ticksLeft
                "remaining: $remaining est ${
                    Clock.System.now().plus(remaining).toLocalDateTime(TimeZone.currentSystemDefault())
                }"
            } ?: "")
        } else null
    }
}