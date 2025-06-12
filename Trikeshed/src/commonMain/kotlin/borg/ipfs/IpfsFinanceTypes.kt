package borg.ipfs

import borg.trikeshed.lib.Series
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j
import kotlin.jvm.JvmInline

/**
 * Type aliases and value classes for financial data in TrikeShed.
 * Using Double instead of BigDecimal for multiplatform compatibility.
 */

/**
 * Represents a Unix timestamp in seconds.
 */
@JvmInline 
value class UnixTimestamp(val value: Long) {
    companion object {
        val ZERO = UnixTimestamp(0L)
    }
}

/**
 * Represents a price value in the financial system.
 */
@JvmInline 
value class Price(val value: Double) {
    companion object {
        val ZERO = Price(0.0)
        val ONE = Price(1.0)
    }
}

/**
 * Represents a return value in the financial system.
 */
@JvmInline 
value class Return(val value: Double) {
    companion object {
        val ZERO = Return(0.0)
        val ONE = Return(1.0)
    }
}

/**
 * Represents a percentage value in the financial system.
 */
@JvmInline 
value class Percentage(val value: Double) {
    companion object {
        val ZERO = Percentage(0.0)
        val ONE = Percentage(1.0)
        val HUNDRED = Percentage(100.0)
    }
}

/**
 * Represents trading volume.
 */
@JvmInline 
value class Volume(val value: Double) {
    companion object {
        val ZERO = Volume(0.0)
    }
}

/**
 * Represents a period for calculations.
 */
@JvmInline 
value class Period(val value: Int) {
    companion object {
        val ZERO = Period(0)
        val ONE = Period(1)
    }
}

/**
 * Core series types for financial analysis
 */
typealias PriceSeries = Series<Price>
typealias ReturnSeries = Series<Return>
typealias VolumeSeries = Series<Volume>

/**
 * Financial data structures using Join
 */
typealias PriceVolume = Join<Price, Volume>
typealias TimestampedPrice = Join<Price, UnixTimestamp>
typealias OHLC = Join<Price, Join<Price, Join<Price, Price>>> // Open, High, Low, Close

/**
 * Series of composite types
 */
typealias PriceVolumeSeries = Series<PriceVolume>
typealias TimestampedPriceSeries = Series<TimestampedPrice>
typealias OHLCSeries = Series<OHLC>

/**
 * Additional financial value classes for comprehensive analysis
 */
@JvmInline
value class HighPrice(val value: Double) {
    companion object {
        val ZERO = HighPrice(0.0)
    }
}

@JvmInline
value class LowPrice(val value: Double) {
    companion object {
        val ZERO = LowPrice(0.0)
    }
}

@JvmInline
value class OpenPrice(val value: Double) {
    companion object {
        val ZERO = OpenPrice(0.0)
    }
}

@JvmInline
value class ClosePrice(val value: Double) {
    companion object {
        val ZERO = ClosePrice(0.0)
    }
}

@JvmInline
value class StandardDeviation(val value: Double) {
    companion object {
        val ZERO = StandardDeviation(0.0)
    }
}

@JvmInline
value class Variance(val value: Double) {
    companion object {
        val ZERO = Variance(0.0)
    }
}

@JvmInline
value class Drawdown(val value: Double) {
    companion object {
        val ZERO = Drawdown(0.0)
    }
}

@JvmInline
value class Mean(val value: Double) {
    companion object {
        val ZERO = Mean(0.0)
    }
}

@JvmInline
value class Skewness(val value: Double) {
    companion object {
        val ZERO = Skewness(0.0)
    }
}

@JvmInline
value class Kurtosis(val value: Double) {
    companion object {
        val ZERO = Kurtosis(0.0)
    }
}

@JvmInline
value class Volatility(val value: Double) {
    companion object {
        val ZERO = Volatility(0.0)
    }
}

@JvmInline
value class Liquidity(val value: Double) {
    companion object {
        val ZERO = Liquidity(0.0)
    }
}

@JvmInline
value class Correlation(val value: Double) {
    companion object {
        val ZERO = Correlation(0.0)
        val ONE = Correlation(1.0)
        val NEGATIVE_ONE = Correlation(-1.0)
    }
}

@JvmInline
value class Asset(val value: String) {
    companion object {
        val EMPTY = Asset("")
    }
}

@JvmInline
value class Portfolio(val value: String) {
    companion object {
        val EMPTY = Portfolio("")
    }
}