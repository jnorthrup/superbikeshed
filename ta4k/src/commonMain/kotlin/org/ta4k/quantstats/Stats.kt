package org.ta4k.quantstats

// import org.ta4k.quantstats.Utils // Assuming Utils.kt is in the same package
import kotlin.math.sqrt

// Placeholder for data series type (e.g., ta4k's Indexed<T> or a simple List<Double>)
// For stubs, 'Any' will often be used to represent these complex data types for now.

/**
 * Calculates total compounded returns from a series of returns.
 * Python equivalent: `returns.add(1).prod(axis=0) - 1`
 *
 * This function assumes `returns` is a collection of fractional returns (e.g., 0.01 for 1%).
 * It will be adapted to use ta4k's Indexed<T> and its 'α' operator once available.
 *
 * @param returns A collection (e.g., List<Double> or eventually ta4k's Indexed<Double>) of returns.
 * @return The total compounded return, or 0.0 if the input is not a compatible list of doubles.
 */
fun comp(returns: Any): Double {
    // Type checking and casting for the placeholder 'Any'.
    // In a typed environment with ta4k's Indexed<T>, this check would be different or handled by the type system.
    if (returns !is List<*>) {
        println("WARN: comp(returns) expects a List of numbers. Received: ${returns::class}. Returning 0.0")
        return 0.0
    }
    if (returns.any { it !is Number }) {
        println("WARN: comp(returns) expects all elements in the list to be Numbers. Found non-number. Returning 0.0")
        return 0.0
    }

    @Suppress("UNCHECKED_CAST")
    val numericReturns = returns as List<Number>

    if (numericReturns.isEmpty()) {
        return 0.0
    }

    // CLAUDE.md mandates using 'α' for transformations on Indexed<T>.
    // If 'numericReturns' were a ta4k.Indexed<Double>, the logic would be:
    // val plusOneSeries = numericReturns.α { it + 1.0 }
    // val product = plusOneSeries.fold(1.0) { acc, value -> acc * value } // Or a specific 'prod' operation on Indexed.
    // For List<Double>, we use standard Kotlin collection operations:

    var product = 1.0
    for (retNum in numericReturns) {
        val ret = retNum.toDouble() // Ensure it's a Double
        product *= (1.0 + ret)
    }

    return product - 1.0
}

/**
 * Calculates the cumulative compounded returns from a series of returns.
 * Python equivalent: `returns.add(1).cumprod(axis=0) - 1`
 *
 * This function assumes `returns` is a collection of fractional returns (e.g., 0.01 for 1%).
 * It will be adapted to use ta4k's Indexed<T> and its 'α' operator once available.
 *
 * @param returns A collection (e.g., List<Double> or eventually ta4k's Indexed<Double>) of returns.
 * @return A list of cumulative compounded returns, or an empty list if input is invalid.
 *         Eventually, this should return a ta4k's Indexed<Double>.
 */
fun compsum(returns: Any): List<Double> { // Return type changed to List<Double> for clarity.
                                         // Eventually ta4k's Indexed<Double>.
    if (returns !is List<*>) {
        println("WARN: compsum(returns) expects a List of numbers. Received: ${returns::class}. Returning empty list.")
        return emptyList()
    }
    if (returns.any { it !is Number }) {
        println("WARN: compsum(returns) expects all elements in the list to be Numbers. Found non-number. Returning empty list.")
        return emptyList()
    }

    @Suppress("UNCHECKED_CAST")
    val numericReturns = returns as List<Number>

    if (numericReturns.isEmpty()) {
        return emptyList()
    }

    // CLAUDE.md mandates using 'α' for transformations on Indexed<T>.
    // If 'numericReturns' were a ta4k.Indexed<Double>, the logic might involve:
    // val plusOneSeries = numericReturns.α { it + 1.0 }
    // val cumulativeProductSeries = plusOneSeries.scan(1.0) { acc, value -> acc * value } // Hypothetical 'scan' or 'cumprod'
    // val resultSeries = cumulativeProductSeries.α { it - 1.0 }
    // For List<Double>, we use standard Kotlin collection operations:

    val result = mutableListOf<Double>()
    var cumulativeProduct = 1.0
    for (retNum in numericReturns) {
        val ret = retNum.toDouble() // Ensure it's a Double
        cumulativeProduct *= (1.0 + ret)
        result.add(cumulativeProduct - 1.0)
    }

    return result
}

/**
 * Calculates the Sortino ratio. Stub.
 */
fun sortino(returns: Any, rf: Double = 0.0, periods: Int = 252, annualize: Boolean = true, smart: Boolean = false): Double {
    println("WARN: sortino(...) is a stub. Complex calculation requiring translated utils and numerical operations.")
    return 0.0 // Default stub return
}

/**
 * Calculates the adjusted Sortino ratio.
 * Python: data / _sqrt(2) where data is sortino result.
 */
fun adjustedSortino(returns: Any, rf: Double = 0.0, periods: Int = 252, annualize: Boolean = true, smart: Boolean = false): Double {
    val sortinoVal = sortino(returns, rf, periods, annualize, smart)
    return sortinoVal / sqrt(2.0)
}

/**
 * Calculates the Sharpe ratio. Stub.
 */
fun sharpe(returns: Any, rf: Double = 0.0, periods: Int = 252, annualize: Boolean = true, smart: Boolean = false): Double {
    println("WARN: sharpe(...) is a stub. Complex calculation requiring translated utils and numerical operations.")
    return 0.0 // Default stub return
}

/**
 * Calculates the maximum drawdown. Stub.
 */
fun maxDrawdown(prices: Any): Double {
    println("WARN: maxDrawdown(prices) is a stub. Requires implementation with ta4k's Indexed type and _prepare_prices from Utils.")
    return 0.0 // Default stub return, typically negative
}

/**
 * Calculates the communicative annualized growth return (CAGR). Stub.
 */
fun cagr(returns: Any, rf: Double = 0.0, compounded: Boolean = true, periods: Int = 252): Double {
    println("WARN: cagr(...) is a stub. Requires date handling, _prepare_returns, and comp logic.")
    return 0.0
}

/**
 * Calculates returns' skewness. Stub.
 */
fun skew(returns: Any, prepareReturns: Boolean = true): Double {
    println("WARN: skew(returns) is a stub. Pandas .skew() equivalent needed for ta4k Indexed type.")
    return 0.0
}

/**
 * Calculates returns' kurtosis. Stub.
 */
fun kurtosis(returns: Any, prepareReturns: Boolean = true): Double {
    println("WARN: kurtosis(returns) is a stub. Pandas .kurtosis() equivalent needed for ta4k Indexed type.")
    return 0.0
}

/**
 * Calculates the Calmar ratio (CAGR / MaxDD). Stub.
 * Assumes cagr and maxDrawdown are available (even if stubs).
 */
fun calmar(returns: Any, prepareReturns: Boolean = true): Double {
    // In Python, _prepare_returns is called for `returns` before cagr and max_drawdown
    // This logic needs to be decided for Kotlin version.
    val cagrRatio = cagr(returns /*, potentially prepared */)
    val maxDd = maxDrawdown(returns /*, potentially prepared prices from returns */)
    if (maxDd == 0.0) return 0.0 // Avoid division by zero; Python version might return NaN or Inf
    return cagrRatio / kotlin.math.abs(maxDd)
}

// Helper function to calculate standard deviation for a list of doubles
internal fun calculateStandardDeviation(data: List<Double>): Double {
    if (data.isEmpty()) {
        return 0.0
    }
    val mean = data.average()
    val sumOfSquaredDifferences = data.sumOf { (it - mean) * (it - mean) }
    // Using sample standard deviation (N-1 denominator) if list size > 1
    // Pandas default ddof=1 for std().
    return if (data.size > 1) {
        sqrt(sumOfSquaredDifferences / (data.size - 1))
    } else {
        0.0 // Or handle as NaN or error for single element list, pandas returns NaN, then 0 after fillna
    }
}

/**
 * Calculates the volatility (standard deviation) of returns.
 * Python equivalent: returns.std() and optional annualization.
 *
 * @param returns A collection (e.g., List<Double> or eventually ta4k's Indexed<Double>) of returns.
 * @param periods The number of periods in a year (e.g., 252 for daily returns).
 * @param annualize If true, annualizes the volatility.
 * @param prepareReturns If true, calls Utils.prepareReturns (currently a stub).
 * @return The volatility, or 0.0 if input is invalid or calculation fails.
 */
fun volatility(returns: Any, periods: Int = 252, annualize: Boolean = true, prepareReturns: Boolean = true): Double {
    val processedReturns = if (prepareReturns) {
        Utils.prepareReturns(returns) // This is currently a stub in Utils.kt
    } else {
        returns
    }

    if (processedReturns !is List<*>) {
        println("WARN: volatility expects a List of numbers after preparation. Received: ${processedReturns::class}. Returning 0.0")
        return 0.0
    }
    if (processedReturns.any { it !is Number }) {
        println("WARN: volatility expects all elements to be Numbers after preparation. Returning 0.0")
        return 0.0
    }

    @Suppress("UNCHECKED_CAST")
    val numericReturns = (processedReturns as List<Number>).map { it.toDouble() }

    if (numericReturns.isEmpty()) {
        return 0.0
    }

    // CLAUDE.md: If 'numericReturns' were ta4k.Indexed<Double>, it should have its own .std() or equivalent.
    // For List<Double>, we use our helper or stdlib if available and suitable.
    val stdDev = calculateStandardDeviation(numericReturns)

    return if (annualize) {
        stdDev * sqrt(periods.toDouble())
    } else {
        stdDev
    }
}

// Add more stubs as needed for other functions from stats.py
// For example:
// fun valueAtRisk(...)
// fun conditionalValueAtRisk(...)
// fun expectedReturn(...)
// etc.
