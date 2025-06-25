package borg.trikeshed.lib

/**
 * Delegate that wires FibonacciReporter to automatically trace packing decisions
 * at fibonacci intervals during operations.
 */
class PackingReportDelegate(
    private val size: Int,
    private val noun: String = "packing_ops",
) {
    private val fibReporter = FibonacciReporter(size, noun)

    /**
     * Execute a packing operation with automatic fibonacci interval reporting
     */
    fun <T> pack(operation: (Int) -> T): PackingResult<T> = PackingResult(operation, fibReporter)

    /**
     * Result wrapper that provides both the operation result and optional progress reports
     */
    class PackingResult<T>(
        private val operation: (Int) -> T,
        private val reporter: FibonacciReporter,
    ) {
        operator fun invoke(index: Int): T {
            val report = reporter.report()
            return operation(index)
        }

        fun getWithReport(index: Int): Pair<T, String?> {
            val report = reporter.report()
            val result = operation(index)
            return result to report
        }
    }
}

/**
 * Extension for creating packing delegates with specific nouns
 */
fun packingDelegate(
    size: Int,
    noun: String,
) = PackingReportDelegate(size, noun)
