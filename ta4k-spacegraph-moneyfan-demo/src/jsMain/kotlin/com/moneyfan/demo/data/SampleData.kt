package com.moneyfan.demo.data

import kotlin.random.Random

object SampleData {
    fun loadSampleData(): TradingData {
        val random = Random(42) // Fixed seed for reproducibility
        val candles = (0..100).map { index ->
            val basePrice = 100.0 + index * 0.1
            val volatility = 2.0
            val open = basePrice + random.nextDouble(-volatility, volatility)
            val close = basePrice + random.nextDouble(-volatility, volatility)
            val high = maxOf(open, close) + random.nextDouble(0.0, volatility)
            val low = minOf(open, close) - random.nextDouble(0.0, volatility)
            val volume = random.nextDouble(1000.0, 5000.0)
            
            Candle(
                timestamp = System.currentTimeMillis() - (100 - index) * 24 * 60 * 60 * 1000,
                open = open,
                high = high,
                low = low,
                close = close,
                volume = volume
            )
        }

        return TradingData(
            symbol = "BTC/USD",
            timeframe = "DAILY",
            candles = candles,
            indicators = mapOf(
                "SMA20" to calculateSMA(candles, 20),
                "SMA50" to calculateSMA(candles, 50),
                "RSI" to calculateRSI(candles, 14)
            )
        )
    }

    private fun calculateSMA(candles: List<Candle>, period: Int): List<Double> {
        return candles.mapIndexed { index, _ ->
            if (index < period - 1) {
                Double.NaN
            } else {
                candles.subList(index - period + 1, index + 1)
                    .map { it.close }
                    .average()
            }
        }
    }

    private fun calculateRSI(candles: List<Candle>, period: Int): List<Double> {
        val changes = candles.zipWithNext { a, b -> b.close - a.close }
        return changes.mapIndexed { index, _ ->
            if (index < period) {
                Double.NaN
            } else {
                val gains = changes.subList(index - period + 1, index + 1)
                    .map { maxOf(it, 0.0) }
                    .average()
                val losses = changes.subList(index - period + 1, index + 1)
                    .map { -minOf(it, 0.0) }
                    .average()
                
                if (losses == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + gains / losses))
            }
        }
    }
} 