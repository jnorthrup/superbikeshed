package borg.trikeshed.acapulco.model

import java.math.BigDecimal

/**
 * Represents a row in the portfolio display.
 */
data class PortfolioRow(
    val symbol: String,
    val currency: String,
    val quantity: BigDecimal,
    val price: BigDecimal?,
    val value: BigDecimal?,
    val baseline: Double?,
    val deviation: Double?,
    val absoluteDifference: BigDecimal?,
    val priceChange: BigDecimal? = null
) 