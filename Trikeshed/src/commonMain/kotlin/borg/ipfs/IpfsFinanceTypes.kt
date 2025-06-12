package borg.ipfs

import java.math.BigDecimal
import java.math.RoundingMode
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.Join

/**
 * Type aliases and value classes for financial data in TrikeShed.
 */

/**
 * Represents a price value in the financial system.
 */
@JvmInline value class Price(val value: BigDecimal) {
    companion object {
        val ZERO = Price(BigDecimal.ZERO)
        val ONE = Price(BigDecimal.ONE)
    }
}

/**
 * Represents a return value in the financial system.
 */
@JvmInline value class Return(val value: BigDecimal) {
    companion object {
        val ZERO = Return(BigDecimal.ZERO)
        val ONE = Return(BigDecimal.ONE)
    }
}

/**
 * Represents a percentage value in the financial system.
 */
@JvmInline value class Percentage(val value: BigDecimal) {
    companion object {
        val ZERO = Percentage(BigDecimal.ZERO)
        val ONE = Percentage(BigDecimal.ONE)
        val HUNDRED = Percentage(BigDecimal(100))
    }
}

/**
 * Represents a standard deviation value in the financial system.
 */
@JvmInline value class StandardDeviation(val value: BigDecimal) {
    companion object {
        val ZERO = StandardDeviation(BigDecimal.ZERO)
    }
}

/**
 * Represents a variance value in the financial system.
 */
@JvmInline value class Variance(val value: BigDecimal) {
    companion object {
        val ZERO = Variance(BigDecimal.ZERO)
    }
}

/**
 * Represents a drawdown value in the financial system.
 */
@JvmInline value class Drawdown(val value: BigDecimal) {
    companion object {
        val ZERO = Drawdown(BigDecimal.ZERO)
        val ONE = Drawdown(BigDecimal.ONE)
    }
}

/**
 * Represents a duration in periods.
 */
@JvmInline value class Period(val value: Int) {
    companion object {
        val ZERO = Period(0)
        val ONE = Period(1)
    }
}

/**
 * Represents a window size for calculations.
 */
@JvmInline value class Window(val value: Int) {
    companion object {
        val ONE = Window(1)
    }
}

/**
 * Represents a high price in a period.
 */
@JvmInline value class HighPrice(val value: BigDecimal)

/**
 * Represents a low price in a period.
 */
@JvmInline value class LowPrice(val value: BigDecimal)

/**
 * Represents a close price in a period.
 */
@JvmInline value class ClosePrice(val value: BigDecimal)

/**
 * Represents a type of return calculation.
 */
enum class ReturnType {
    GROSS,
    LOG,
    NET,
    COMPOUND,
    PERCENT
}

/**
 * Represents a series of prices.
 */
typealias PriceSeries = Series<Price>

/**
 * Represents a series of returns.
 */
typealias ReturnSeries = Series<Return>

/**
 * Represents a series of percentages.
 */
typealias PercentageSeries = Series<Percentage>

/**
 * Represents a series of standard deviations.
 */
typealias StandardDeviationSeries = Series<StandardDeviation>

/**
 * Represents a series of variances.
 */
typealias VarianceSeries = Series<Variance>

/**
 * Represents a series of drawdowns.
 */
typealias DrawdownSeries = Series<Drawdown>

/**
 * Represents a series of high prices.
 */
typealias HighPriceSeries = Series<HighPrice>

/**
 * Represents a series of low prices.
 */
typealias LowPriceSeries = Series<LowPrice>

/**
 * Represents a series of close prices.
 */
typealias ClosePriceSeries = Series<ClosePrice>

/**
 * Represents a join of high, low, and close prices.
 */
typealias PriceData = Join<HighPrice, Join<LowPrice, ClosePrice>>

/**
 * Represents a series of price data.
 */
typealias PriceDataSeries = Series<PriceData>

/**
 * Represents a join of a return and its timestamp.
 */
typealias TimestampedReturn = Join<Return, UnixTimestamp>

/**
 * Represents a series of timestamped returns.
 */
typealias TimestampedReturnSeries = Series<TimestampedReturn>

/**
 * Represents a join of a drawdown and its duration.
 */
typealias DrawdownWithDuration = Join<Drawdown, Period>

/**
 * Represents a series of drawdowns with durations.
 */
typealias DrawdownWithDurationSeries = Series<DrawdownWithDuration> 