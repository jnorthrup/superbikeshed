package core

import borg.trikeshed.lib.Tensor
import borg.trikeshed.lib.TensorCursor // For creating result tensors
import borg.trikeshed.lib.rank
import borg.trikeshed.lib.shape
import borg.trikeshed.lib.get // For element access tensor(i,j)
import borg.trikeshed.lib.invoke // Enables tensor(i,j) like access
import kotlin.math.pow
import kotlin.math.sqrt
import borg.trikeshed.lib.totalSize // Needed for rolling window size calculation

// Assumes Tensor<T> is typealias Tensor<T> = Join<IntArray, (IntArray) -> T>
// and that T is a Number for these operations.

/**
 * Performs matrix multiplication (this * other).
 * Assumes T is a Number type.
 */
@Suppress("UNCHECKED_CAST")
fun <T : Number> Tensor<T>.matmul(other: Tensor<T>): Tensor<T> {
    require(this.rank == 2) { "This tensor must be 2D for matrix multiplication. Shape: ${this.shape.joinToString()}" }
    require(other.rank == 2) { "Other tensor must be 2D for matrix multiplication. Shape: ${other.shape.joinToString()}" }
    require(this.shape[1] == other.shape[0]) {
        "Matrix multiplication dimension mismatch: this.shape[1] (${this.shape[1]}) != other.shape[0] (${other.shape[0]})"
    }

    val n = this.shape[0] // Rows of this
    val m = this.shape[1] // Columns of this / Rows of other
    val p = other.shape[1] // Columns of other

    return TensorCursor(n, p) { r, c ->
        var sum = 0.0
        for (k in 0 until m) {
            sum += this(r, k).toDouble() * other(k, c).toDouble()
        }
        // This is a simplification. The result type T might not be Double.
        // A more robust solution would handle different Number types or require T to be Double.
        if (this(0,0) is Double) sum as T
        else if (this(0,0) is Float) sum.toFloat() as T
        else if (this(0,0) is Int) sum.toInt() as T
        else if (this(0,0) is Long) sum.toLong() as T
        else if (this(0,0) is Short) sum.toShort() as T
        else if (this(0,0) is Byte) sum.toByte() as T
        else throw UnsupportedOperationException("Unsupported Number type for matmul result")
    }
}

/**
 * Calculates the dot product of two rank-1 tensors (vectors).
 * Assumes T is a Number type.
 */
@Suppress("UNCHECKED_CAST")
fun <T : Number> Tensor<T>.dot(other: Tensor<T>): T {
    require(this.rank == 1) { "This tensor must be 1D (vector) for dot product. Shape: ${this.shape.joinToString()}" }
    require(other.rank == 1) { "Other tensor must be 1D (vector) for dot product. Shape: ${other.shape.joinToString()}" }
    require(this.shape[0] == other.shape[0]) {
        "Vector dot product dimension mismatch: this.length (${this.shape[0]}) != other.length (${other.shape[0]})"
    }

    var sum = 0.0
    for (i in 0 until this.shape[0]) {
        sum += this(i).toDouble() * other(i).toDouble()
    }

    // Simplification for result type, similar to matmul
    return when (this(0)) {
        is Double -> sum as T
        is Float -> sum.toFloat() as T
        is Int -> sum.toInt() as T
        is Long -> sum.toLong() as T
        is Short -> sum.toShort() as T
        is Byte -> sum.toByte() as T
        else -> throw UnsupportedOperationException("Unsupported Number type for dot product result")
    }
}

/**
 * Calculates the arithmetic mean of elements in a rank-1 tensor (vector).
 * Assumes elements T are convertible to Double.
 */
fun <T : Number> Tensor<T>.mean(): Double {
    require(this.rank == 1) { "Mean calculation requires a 1D tensor (vector). Shape: ${this.shape.joinToString()}" }
    if (this.shape[0] == 0) return Double.NaN // Or throw, or return 0.0, depending on desired behavior

    var sum = 0.0
    for (i in 0 until this.shape[0]) {
        sum += this(i).toDouble()
    }
    return sum / this.shape[0]
}

/**
 * Calculates the L_p norm of a rank-1 tensor (vector).
 * Defaults to L2 norm (Euclidean norm).
 * Assumes elements T are convertible to Double.
 */
fun <T : Number> Tensor<T>.norm(p: Int = 2): Double {
    require(this.rank == 1) { "Norm calculation requires a 1D tensor (vector). Shape: ${this.shape.joinToString()}" }
    require(p >= 1) { "p-norm requires p >= 1" }
    if (this.shape[0] == 0) return 0.0

    if (p == 1) { // L1 norm
        var sum = 0.0
        for (i in 0 until this.shape[0]) {
            sum += kotlin.math.abs(this(i).toDouble())
        }
        return sum
    }

    if (p == 2) { // L2 norm
        var sumSq = 0.0
        for (i in 0 until this.shape[0]) {
            val value = this(i).toDouble()
            sumSq += value * value
        }
        return sqrt(sumSq)
    }

    // General L_p norm
    var sumPow = 0.0
    for (i in 0 until this.shape[0]) {
        sumPow += kotlin.math.abs(this(i).toDouble()).pow(p)
    }
    return sumPow.pow(1.0 / p)
}

// Placeholder for oneHot if it's a core tensor operation, otherwise it might be in a more specialized file.
// For now, assuming it's available via `import borg.trikeshed.lib.oneHot` in DSLStairway
// fun <T> Tensor<T>.oneHot(depth: Int, onValue: T, offValue: T): Tensor<T> { TODO() }

// Other operations like reshape, transpose, sliceAdvanced are assumed to be
// extension functions on Tensor<T> already available in borg.trikeshed.lib.*
// (as indicated by their usage in DSLStairway.kt's previous version).
// If they are not, they would need to be implemented here or in another appropriate file.
// For example:
// fun <T> Tensor<T>.reshape(vararg newShape: Int): Tensor<T> { /* ... */ }
// fun <T> Tensor<T>.transpose(): Tensor<T> { /* ... */ }
// fun <T> Tensor<T>.sliceAdvanced(vararg specs: TensorRangeSpec): Tensor<T> { /* ... */ }


// --- Rolling Window Operations ---

/**
 * Generic helper function to apply an operation over a rolling window of a rank-1 tensor.
 *
 * @param T The numeric type of the input tensor elements.
 * @param R The numeric type of the output tensor elements.
 * @param windowSize The size of the rolling window.
 * @param operation A lambda function that takes a window (as a Tensor<T>) and returns a single value of type R.
 * @return A new Tensor<R> containing the results of the operation applied to each window.
 */
@Suppress("UNCHECKED_CAST")
fun <T : Number, R : Number> Tensor<T>.rollingWindow(
    windowSize: WindowSize,
    operation: (Tensor<T>) -> R
): Tensor<R> {
    require(this.rank == 1) { "Rolling window operations require a 1D tensor (vector). Shape: ${this.shape.joinToString()}" }
    require(windowSize.value > 0) { "Window size must be positive." }

    val n = this.shape[0]
    val wSize = windowSize.value

    if (wSize > n) {
        // Window is larger than the tensor, result is empty or handle as error/specific case
        return TensorCursor(0) { throw IndexOutOfBoundsException("Window size $wSize is larger than tensor size $n") } as Tensor<R>
    }

    val resultSize = n - wSize + 1
    if (resultSize <= 0) {
        return TensorCursor(0) { throw IndexOutOfBoundsException("Result size for rolling window is non-positive.") } as Tensor<R>
    }

    // Create result tensor using TensorCursor for rank-1 output
    return TensorCursor(resultSize) { i ->
        // Create a temporary tensor for the current window
        // This creates a copy of the window data.
        val windowTensor = TensorCursor(wSize) { j ->
            this(i + j) // Accessing elements from the original tensor
        }
        operation(windowTensor)
    }
}

/**
 * Calculates the Simple Moving Average (SMA) for a rank-1 tensor.
 * Assumes elements T are convertible to Double.
 */
fun <T : Number> Tensor<T>.sma(windowSize: WindowSize): Tensor<Double> {
    return this.rollingWindow(windowSize) { window ->
        window.mean() // Use the existing .mean() operation for a rank-1 tensor
    }
}

/**
 * Calculates the rolling standard deviation for a rank-1 tensor.
 * Assumes elements T are convertible to Double.
 */
fun <T : Number> Tensor<T>.rollingStdDev(windowSize: WindowSize): Tensor<Double> {
    return this.rollingWindow(windowSize) { window ->
        if (window.shape[0] == 0) Double.NaN
        else {
            val mean = window.mean()
            if (mean.isNaN()) Double.NaN
            else {
                var sumSqDiff = 0.0
                for (j in 0 until window.shape[0]) {
                    val diff = window(j).toDouble() - mean
                    sumSqDiff += diff * diff
                }
                if (window.shape[0] < 2) Double.NaN // Stddev undefined for less than 2 samples, or 0.0 by some conventions
                else sqrt(sumSqDiff / (window.shape[0] -1)) // Sample standard deviation
                // else sqrt(sumSqDiff / window.shape[0]) // Population standard deviation
            }
        }
    }
}
