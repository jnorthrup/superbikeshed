package com.moneyfan.demo.data

import java.time.Instant

data class TradingData(
    val symbol: String,
    val timeframe: String,
    val candles: List<Candle>,
    val indicators: Map<String, List<Double>> = emptyMap()
)

data class Candle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
) {
    val date: Instant get() = Instant.ofEpochMilli(timestamp)
}

object TradingDataParser {
    fun parse(json: String): TradingData {
        val scanner = JsonScanner(json)
        return parseTradingData(scanner)
    }

    internal fun parseTradingData(scanner: JsonScanner): TradingData {
        var symbol = ""
        var timeframe = ""
        val candles = mutableListOf<Candle>()
        val indicators = mutableMapOf<String, List<Double>>()

        scanner.expectObjectStart()
        while (!scanner.isObjectEnd()) {
            when (scanner.nextKey()) {
                "symbol" -> symbol = scanner.nextString()
                "timeframe" -> timeframe = scanner.nextString()
                "candles" -> {
                    scanner.expectArrayStart()
                    while (!scanner.isArrayEnd()) {
                        candles.add(parseCandle(scanner))
                    }
                }
                "indicators" -> {
                    scanner.expectObjectStart()
                    while (!scanner.isObjectEnd()) {
                        val key = scanner.nextKey()
                        indicators[key] = parseDoubleArray(scanner)
                    }
                }
            }
        }
        return TradingData(symbol, timeframe, candles, indicators)
    }

    internal fun parseCandle(scanner: JsonScanner): Candle {
        var timestamp = 0L
        var open = 0.0
        var high = 0.0
        var low = 0.0
        var close = 0.0
        var volume = 0.0

        scanner.expectObjectStart()
        while (!scanner.isObjectEnd()) {
            when (scanner.nextKey()) {
                "timestamp" -> timestamp = scanner.nextLong()
                "open" -> open = scanner.nextDouble()
                "high" -> high = scanner.nextDouble()
                "low" -> low = scanner.nextDouble()
                "close" -> close = scanner.nextDouble()
                "volume" -> volume = scanner.nextDouble()
            }
        }
        return Candle(timestamp, open, high, low, close, volume)
    }

    internal fun parseDoubleArray(scanner: JsonScanner): List<Double> {
        val result = mutableListOf<Double>()
        scanner.expectArrayStart()
        while (!scanner.isArrayEnd()) {
            result.add(scanner.nextDouble())
        }
        return result
    }
}

class JsonScanner(internal val json: String) {
    internal var pos = 0
    internal val length = json.length

    fun expectObjectStart() {
        skipWhitespace()
        if (json[pos] != '{') throw JsonParseException("Expected '{' at position $pos")
        pos++
    }

    fun expectArrayStart() {
        skipWhitespace()
        if (json[pos] != '[') throw JsonParseException("Expected '[' at position $pos")
        pos++
    }

    fun isObjectEnd(): Boolean {
        skipWhitespace()
        if (json[pos] == '}') {
            pos++
            return true
        }
        return false
    }

    fun isArrayEnd(): Boolean {
        skipWhitespace()
        if (json[pos] == ']') {
            pos++
            return true
        }
        return false
    }

    fun nextKey(): String {
        skipWhitespace()
        if (json[pos] != '"') throw JsonParseException("Expected '\"' at position $pos")
        pos++
        val start = pos
        while (json[pos] != '"') pos++
        val key = json.substring(start, pos)
        pos++
        skipWhitespace()
        if (json[pos] != ':') throw JsonParseException("Expected ':' at position $pos")
        pos++
        return key
    }

    fun nextString(): String {
        skipWhitespace()
        if (json[pos] != '"') throw JsonParseException("Expected '\"' at position $pos")
        pos++
        val start = pos
        while (json[pos] != '"') pos++
        val result = json.substring(start, pos)
        pos++
        return result
    }

    fun nextDouble(): Double {
        skipWhitespace()
        val start = pos
        while (pos < length && (json[pos].isDigit() || json[pos] == '.' || json[pos] == '-')) pos++
        return json.substring(start, pos).toDouble()
    }

    fun nextLong(): Long {
        skipWhitespace()
        val start = pos
        while (pos < length && json[pos].isDigit()) pos++
        return json.substring(start, pos).toLong()
    }

    internal fun skipWhitespace() {
        while (pos < length && json[pos].isWhitespace()) pos++
    }
}

class JsonParseException(message: String) : Exception(message) 