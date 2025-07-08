package borg.trikeshed.ml

import kotlin.jvm.JvmInline

// Simple data structures without external dependencies
data class PriceData(val prices: DoubleArray) {
    fun get(index: Int): Double = prices[index]
    fun size(): Int = prices.size
}

data class WindowFeatures(val values: DoubleArray)

typealias PredictionModel = (WindowFeatures) -> Double

// Core value classes following TrikeShed patterns
@kotlin.jvm.JvmInline
value class WindowSize(val value: Int)

@kotlin.jvm.JvmInline
value class ColumnName(val value: String)

@kotlin.jvm.JvmInline
value class PredictionValue(val value: Double)

// Indexed operations for price data
fun extractColumnData(data: Map<String, DoubleArray>, columnName: ColumnName): PriceData? {
    val column = data[columnName.value] ?: return null
    return PriceData(column)
}

fun createSlidingWindowFeatures(
    data: PriceData, 
    windowSize: WindowSize
): Pair<Array<WindowFeatures>, DoubleArray>? {
    val dataSize = data.size()
    if (dataSize < windowSize.value + 1) return null
    
    val numPairs = dataSize - windowSize.value
    val features = Array(numPairs) { i ->
        WindowFeatures(DoubleArray(windowSize.value) { j -> data.get(i + j) })
    }
    val labels = DoubleArray(numPairs) { i -> data.get(i + windowSize.value) }
    
    return Pair(features, labels)
}

// Simplified prediction without external dependencies
fun trainSimplePredictor(
    features: Array<WindowFeatures>,
    labels: DoubleArray
): PredictionModel? {
    if (features.isEmpty() || labels.isEmpty()) return null
    
    // Simple moving average predictor as fallback
    return { window -> window.values.average() }
}

fun predictNext(model: PredictionModel, recentFeatures: WindowFeatures): PredictionValue =
    PredictionValue(model(recentFeatures))

// Pure approach without external cursor dependencies
fun addPredictions(
    priceData: PriceData,
    windowSize: WindowSize,
    predictionColumnName: ColumnName
): List<Pair<Double, PredictionValue>>? {
    val slidingData = createSlidingWindowFeatures(priceData, windowSize) ?: return null
    val model = trainSimplePredictor(slidingData.first, slidingData.second) ?: return null
    
    return (0 until priceData.size()).map { i ->
        val price = priceData.get(i)
        val prediction = if (i >= windowSize.value) {
            val window = WindowFeatures(DoubleArray(windowSize.value) { j -> 
                priceData.get(i - windowSize.value + j) 
            })
            predictNext(model, window)
        } else {
            PredictionValue(0.0)
        }
        price to prediction
    }
}