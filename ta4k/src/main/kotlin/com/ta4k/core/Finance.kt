package com.ta4k.core

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ln

object Finance {
    fun grossReturn(prices: List<BigDecimal>): List<BigDecimal> {
        return prices.windowed(2) { (prev, curr) ->
            if (curr == prev) BigDecimal.ONE
            else curr.divide(prev, 8, RoundingMode.HALF_UP)
        }
    }

    fun logReturn(prices: List<BigDecimal>): List<BigDecimal> {
        return grossReturn(prices).map { gross ->
            if (gross <= BigDecimal.ZERO) BigDecimal.ZERO
            else BigDecimal(ln(gross.toDouble()))
        }.runningFold(BigDecimal.ZERO) { acc, value ->
            acc.add(value)
        }
    }

    fun netReturn(prices: List<BigDecimal>, strict: Boolean = true): List<BigDecimal> {
        val returns = prices.windowed(2) { (prev, curr) ->
            if (curr == prev) BigDecimal.ZERO
            else curr.subtract(prev).divide(prev, 8, RoundingMode.HALF_UP)
        }

        return if (strict) returns
        else listOf(BigDecimal.ZERO) + returns
    }

    fun compoundReturn(prices: List<BigDecimal>): List<BigDecimal> {
        return grossReturn(prices).runningFold(BigDecimal.ONE) { acc, value ->
            acc.multiply(value)
        }
    }

    fun percentReturn(prices: List<BigDecimal>): List<BigDecimal> {
        return compoundReturn(prices).map { compound ->
            compound.subtract(BigDecimal.ONE)
                .multiply(BigDecimal(100))
                .setScale(2, RoundingMode.HALF_UP)
        }
    }

    fun calculateReturns(
        prices: List<BigDecimal>,
        returnType: ReturnType = ReturnType.NET
    ): List<BigDecimal> {
        return when (returnType) {
            ReturnType.GROSS -> grossReturn(prices)
            ReturnType.LOG -> logReturn(prices)
            ReturnType.NET -> netReturn(prices)
            ReturnType.COMPOUND -> compoundReturn(prices)
            ReturnType.PERCENT -> percentReturn(prices)
        }
    }
}

enum class ReturnType {
    GROSS,
    LOG,
    NET,
    COMPOUND,
    PERCENT
} 