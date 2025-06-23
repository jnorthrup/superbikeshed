package moneyfan.json

import moneyfan.core.*
import borg.trikeshed.lib.*

/**
 * Simplified JSON Scanner DSEL using TrikeShed patterns
 * Demonstrates Indexed<T> and Join<A,B> for JSON structure analysis
 */

// Simple scanner results using TrikeShed types
typealias JsonBounds = Join<Int, Int> // start j end
typealias JsonStructure = Join<JsonBounds, Int> // bounds j elementCount

// Basic JSON scanner using TrikeShed patterns
object SimpleJsonScanner {
    
    fun scanStructure(jsonStr: String): JsonStructure {
        val chars = Indexed.of(jsonStr.length) { jsonStr[it] }
        var depth = 0
        var start = -1
        var end = -1
        var elementCount = 0
        
        chars.play.forEachIndexed { i, char ->
            when (char) {
                '{', '[' -> {
                    depth++
                    if (start == -1) start = i
                }
                '}', ']' -> {
                    depth--
                    if (depth == 0) end = i
                }
                ',' -> if (depth == 1) elementCount++
                ':' -> if (depth == 1) elementCount++
            }
        }
        
        return (start j end) j elementCount
    }
    
    // Extract numeric values using Series transforms
    fun extractNumbers(jsonStr: String): Indexed<Double> {
        val numbers = mutableListOf<Double>()
        var currentNumber = ""
        var inNumber = false
        
        for (char in jsonStr) {
            when {
                char.isDigit() || char == '.' || char == '-' -> {
                    inNumber = true
                    currentNumber += char
                }
                inNumber -> {
                    currentNumber.toDoubleOrNull()?.let { numbers.add(it) }
                    currentNumber = ""
                    inNumber = false
                }
            }
        }
        
        // Add final number if string ends with one
        if (inNumber) {
            currentNumber.toDoubleOrNull()?.let { numbers.add(it) }
        }
        
        return Indexed.of(numbers.size) { numbers[it] }
    }
    
    // Trading-specific analysis
    fun analyzeTradingStructure(jsonStr: String): TradingJsonAnalysis {
        val structure = scanStructure(jsonStr)
        val numbers = extractNumbers(jsonStr)
        val (bounds, elementCount) = structure
        val (start, end) = bounds
        
        return TradingJsonAnalysis(
            totalLength = jsonStr.length,
            structuralComplexity = end - start,
            elementCount = elementCount,
            numericValues = numbers.size,
            hasNestedData = elementCount > 5
        )
    }
}

// Analysis result using TrikeShed value class pattern
@JvmInline
value class JsonComplexity(val value: Int)

@JvmInline  
value class ElementCount(val value: Int)

data class TradingJsonAnalysis(
    val totalLength: Int,
    val structuralComplexity: Int,
    val elementCount: Int,
    val numericValues: Int,
    val hasNestedData: Boolean
) {
    // TrikeShed-style transforms
    fun complexity(): JsonComplexity = JsonComplexity(structuralComplexity)
    fun elements(): ElementCount = ElementCount(elementCount)
    
    // Combine metrics using Join
    fun metrics(): Join<JsonComplexity, ElementCount> = complexity() j elements()
}

// Demo function for the JSON scanner
fun demonstrateTradingJsonScanner() {
    println("=== Simple TrikeShed JSON Scanner Demo ===")
    
    // Sample trading data
    val priceJson = """{"symbol":"AAPL","price":150.25,"volume":1000}"""
    val candleJson = """[{"open":150,"high":152,"low":149,"close":151},{"open":151,"high":153,"low":150,"close":152}]"""
    
    // Analyze structure
    val priceAnalysis = SimpleJsonScanner.analyzeTradingStructure(priceJson)
    val candleAnalysis = SimpleJsonScanner.analyzeTradingStructure(candleJson)
    
    println("Price JSON Analysis:")
    println("  Length: ${priceAnalysis.totalLength}")
    println("  Complexity: ${priceAnalysis.structuralComplexity}")
    println("  Elements: ${priceAnalysis.elementCount}")
    println("  Numbers: ${priceAnalysis.numericValues}")
    
    println("\nCandle JSON Analysis:")
    println("  Length: ${candleAnalysis.totalLength}")
    println("  Complexity: ${candleAnalysis.structuralComplexity}")
    println("  Elements: ${candleAnalysis.elementCount}")
    println("  Numbers: ${candleAnalysis.numericValues}")
    
    // Extract numbers using Series
    val priceNumbers = SimpleJsonScanner.extractNumbers(priceJson)
    val candleNumbers = SimpleJsonScanner.extractNumbers(candleJson)
    
    println("\nExtracted Numbers:")
    println("  Price data: ${priceNumbers.play.joinToString(", ")}")
    println("  Candle data: ${candleNumbers.play.take(5).joinToString(", ")}...")
    
    // Use TrikeShed patterns to transform numbers
    val scaledPrices = priceNumbers.α { it * 1.02 } // 2% markup
    println("\nScaled prices (+2%): ${scaledPrices.play.joinToString(", ") { "%.2f".format(it) }}")
    
    // Combine analyses using Join
    val priceMetrics = priceAnalysis.metrics()
    val candleMetrics = candleAnalysis.metrics()
    val combinedMetrics = priceMetrics j candleMetrics
    
    val (priceComplexity, priceElements) = priceMetrics
    val (candleComplexity, candleElements) = candleMetrics
    
    println("\nCombined Metrics:")
    println("  Price complexity: ${priceComplexity.value}, elements: ${priceElements.value}")
    println("  Candle complexity: ${candleComplexity.value}, elements: ${candleElements.value}")
}