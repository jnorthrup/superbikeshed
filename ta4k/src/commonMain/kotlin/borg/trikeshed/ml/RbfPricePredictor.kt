package borg.trikeshed.ml

import borg.trikeshed.core.CoreTensorCursorWithMeta
import borg.trikeshed.core.Tensor
import borg.trikeshed.core.Join
import borg.trikeshed.core.TypeMemento
// It seems klineCursorMeta is not directly used in these functions,
// but was requested as an import. If it's needed for other parts of this file later, it's here.
// import borg.trikeshed.util.klineCursorMeta

// Smile imports
import smile.regression.RBFNetwork
// import smile.regression.rbfnet // This is implicitly imported when calling the function

// Additional imports for addPredictionsToCursor
import borg.trikeshed.core.TensorCursor
import borg.trikeshed.core.TensorSeries
import borg.trikeshed.core.IOMemento
import borg.trikeshed.core.j


fun extractColumnData(cursor: CoreTensorCursorWithMeta<Double>, columnName: String): DoubleArray? {
    val meta = cursor.b // Assuming cursor.b is Tensor<Join<String, TypeMemento>>
    var columnIndex = -1
    // Assuming meta.totalSize is available and is the number of columns in the metadata
    for (idx in 0 until meta.totalSize) {
        val colMetaJoin = meta[idx]
        if (colMetaJoin.a == columnName) {
            columnIndex = idx
            break
        }
    }

    if (columnIndex == -1) {
        println("Error: Column '$columnName' not found in cursor metadata.")
        return null
    }

    val dataTensor = cursor.a // Assuming cursor.a is TensorCursor<Double> or similar
    val columnData = DoubleArray(dataTensor.rows)
    for (rowIndex in 0 until dataTensor.rows) {
        columnData[rowIndex] = dataTensor[rowIndex, columnIndex]
    }
    return columnData
}

fun createSlidingWindowFeatures(data: DoubleArray, windowSize: Int): Pair<Array<DoubleArray>, DoubleArray>? {
    if (data.size < windowSize + 1) {
        println("Error: Data too short for window size. Need at least ${windowSize + 1} elements, got ${data.size}.")
        return null
    }

    val features: MutableList<DoubleArray> = mutableListOf()
    val labels: MutableList<Double> = mutableListOf()

    for (i in 0 .. (data.size - 1 - windowSize)) {
        val window = data.sliceArray(i until i + windowSize)
        features.add(window)
        labels.add(data[i + windowSize])
    }

    return Pair(features.toTypedArray(), labels.toDoubleArray())
}

fun trainRbfPredictor(
    features: Array<DoubleArray>,
    labels: DoubleArray,
    numberOfNeurons: Int = 0
): RBFNetwork<DoubleArray>? {
    if (features.isEmpty() || labels.isEmpty()) {
        println("Error: Features or labels are empty, cannot train RBFNetwork.")
        return null
    }
    return try {
        smile.regression.rbfnet(features, labels, numberOfNeurons)
    } catch (e: Exception) {
        println("Error training RBFNetwork: ${e.message}")
        null
    }
}

fun predictNext(model: RBFNetwork<DoubleArray>, recentFeatures: DoubleArray): Double {
    return model.predict(recentFeatures)
}

fun addPredictionsToCursor(
    klineCursor: CoreTensorCursorWithMeta<Double>,
    model: RBFNetwork<DoubleArray>,
    closePriceColumnName: String,
    windowSize: Int,
    predictionColumnName: String = "prediction"
): CoreTensorCursorWithMeta<Double>? {
    val originalDataTensor = klineCursor.a
    val originalMetaTensor = klineCursor.b // This is Tensor<Join<String, TypeMemento>>

    val closePrices = extractColumnData(klineCursor, closePriceColumnName)
    if (closePrices == null) {
        println("Error: Could not extract close prices ('$closePriceColumnName') for prediction.")
        return null
    }

    // Assuming originalMetaTensor.totalSize and originalDataTensor.cols are valid properties
    // For TensorCursor, cols is part of the constructor and usually a direct property.
    // For Tensor (interface for meta), totalSize was used. Let's assume it means number of meta entries.

    if (closePrices.size <= windowSize) {
        println("Error: Not enough data rows (${closePrices.size}) to make any predictions (need more than windowSize $windowSize). Adding NaN column.")
        // Add a NaN prediction column to the original cursor structure
        val newMeta = TensorSeries(originalMetaTensor.totalSize + 1) { idx ->
            if (idx < originalMetaTensor.totalSize) originalMetaTensor[idx]
            else predictionColumnName j IOMemento.IoDouble
        }
        // originalDataTensor.cols should give the number of columns in the data part
        val newData = TensorCursor(originalDataTensor.rows, newMeta.totalSize) { r, c ->
            if (c < originalDataTensor.cols) originalDataTensor[r,c]
            else Double.NaN
        }
        return newData j newMeta
    }

    val predictions = DoubleArray(originalDataTensor.rows) { Double.NaN } // Initialize with NaN

    // Predictions start from index `windowSize` because we need `windowSize` prior data points
    for (i in windowSize until closePrices.size) {
        val currentWindow = closePrices.sliceArray((i - windowSize) until i)
        // The check currentWindow.size == windowSize is implicitly true due to sliceArray behavior and loop bounds
        predictions[i] = predictNext(model, currentWindow)
    }

    // Create new meta (original + prediction column)
    val newMetaSize = originalMetaTensor.totalSize + 1
    val newMeta = TensorSeries(newMetaSize) { idx ->
        if (idx < originalMetaTensor.totalSize) {
            originalMetaTensor[idx]
        } else {
            predictionColumnName j IOMemento.IoDouble
        }
    }

    // Create new data tensor (original data + prediction column)
    val newDataTensor = TensorCursor(originalDataTensor.rows, newMetaSize) { r, c ->
        // originalDataTensor.cols is the number of columns in the original data
        if (c < originalDataTensor.cols) {
            originalDataTensor[r, c]
        } else { // This is the new prediction column (the last one)
            predictions[r] // predictions array is same length as rows, with NaNs at start
        }
    }
    return newDataTensor j newMeta
}
