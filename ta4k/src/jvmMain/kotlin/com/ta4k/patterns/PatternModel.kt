package com.ta4k.patterns

import com.ta4k.core.model.Kline // Assuming SwingPoint needs Kline, but SwingPoint is in its own file.
                               // IdentifiedPattern itself does not directly hold KlineSeries to save memory.
import java.math.BigDecimal

/**
 * Represents a recognized chart pattern.
 *
 * @property name The name of the pattern (e.g., "Head and Shoulders", "Inverse Head and Shoulders").
 * @property points A map of named [SwingPoint]s that constitute the pattern
 *                  (e.g., "leftShoulder", "head", "rightShoulder", "neckline1", "neckline2").
 * @property keyLevels Additional important price levels related to the pattern
 *                     (e.g., "necklinePriceAtBreakout", "targetPrice").
 * @property startIndex The starting index of the pattern in the klineSeries (usually leftShoulder.index).
 * @property endIndex The ending index of the pattern in the klineSeries (usually rightShoulder.index or pattern completion).
 * @property breakoutIndex Optional index where a breakout confirming the pattern occurred.
 * @property isConfirmed Confirmation based on breakout or other criteria.
 */
data class IdentifiedPattern(
    val name: String,
    val points: Map<String, SwingPoint>, // SwingPoint itself contains the Kline and its index
    val keyLevels: Map<String, BigDecimal> = emptyMap(),
    val startIndex: Int,
    val endIndex: Int,
    val breakoutIndex: Int? = null,
    val isConfirmed: Boolean = false
)
