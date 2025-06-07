@file:Suppress("NOTHING_TO_INLINE")

package core

import borg.trikeshed.lib.Join // Tensor is a Join
import borg.trikeshed.lib.Series // For TensorSeries constructor if used by DSL methods
import borg.trikeshed.lib.Tensor // Assuming typealias Tensor<T> = Join<IntArray, (IntArray) -> T> is available via this
import borg.trikeshed.lib.j // For Join creation syntax: tensor j window.value
import borg.trikeshed.lib.TensorRangeSpec // For slicing
import borg.trikeshed.lib.TensorCursor // For example usage
import borg.trikeshed.lib.all // For example usage
import borg.trikeshed.lib.range // For example usage
import borg.trikeshed.lib.sliceAdvanced // Assuming this is the slice function for Tensor
import borg.trikeshed.lib.reshape // Assuming this is an extension for Tensor
import borg.trikeshed.lib.transpose // Assuming this is an extension for Tensor
import borg.trikeshed.lib.matmul // Assuming this is an extension for Tensor (or matmul_placeholder)
import borg.trikeshed.lib.oneHot // Assuming this is an extension for Tensor
import borg.trikeshed.lib.rank // Assuming tensor.rank property
import borg.trikeshed.lib.shape // Assuming tensor.shape property (IntArray)
import borg.trikeshed.lib.totalSize // Assuming tensor.totalSize property
import borg.trikeshed.lib.invoke // Assuming tensor(i) or tensor(i,j) invoke operator for element access
import borg.trikeshed.lib.TensorSeries // As used in DSLStairway.kt
import borg.trikeshed.lib.α // For example usage

import core.WindowSize // From TensorDslTypes.kt
import core.StandardDeviations
import core.LearningRate
import core.Epochs // Not used yet, but for completeness
import core.BatchSize // Not used yet
import core.ItemCount
import core.LayerUnits
import core.ActivationFunction

// Import new core operations
import core.matmul
import core.dot
import core.mean
import core.norm

import kotlin.jvm.*
import kotlin.math.sqrt


/**
 * Stairway of Inline Classes - The DSL Transformation Pattern
 *
 * Each step in the stairway represents a different level of abstraction,
 * with zero runtime cost but powerful compile-time transformations.
 *
 * This creates a "transformative DSL" where each operation fundamentally
 * changes what operations are available next.
 */

/**
 * Level 0: Raw Tensor (Base Reality)
 */
@JvmInline
value class RawTensor<T>(val tensor: Tensor<T>) {
    /**
     * Shape inspection - moves to ShapedTensor
     */
    fun shaped(): ShapedTensor<T> = ShapedTensor(tensor)

    /**
     * Data inspection - moves to TypedTensor
     */
    fun typed(): TypedTensor<T> = TypedTensor(tensor)

    /**
     * Performance path - moves to HotTensor
     */
    fun hot(): HotTensor<T> = HotTensor(tensor)

    /**
     * One-hot encode the tensor.
     * @param depth The depth of the one-hot encoding.
     * @param onValue The value to use for the 'on' state.
     * @param offValue The value to use for the 'off' state.
     * Assumes T is typically Int for one-hot encoding input.
     */
    @Suppress("UNCHECKED_CAST")
    fun oneHot(depth: Int, onValue: T, offValue: T): RawTensor<T> {
        // Assuming tensor is Tensor<Int> for oneHot, then cast result if T is not Int
        // This is a common pattern but might need type constraints or safer casting.
        // Also assuming 'oneHot' is an available extension function on Tensor<T>
        val oneHotEncoded = tensor.oneHot(depth, onValue as Any, offValue as Any) as Tensor<T>
        return RawTensor(oneHotEncoded)
    }
}

/**
 * Level 1: ShapedTensor (Dimensional Awareness)
 */
@JvmInline
value class ShapedTensor<T>(val tensor: Tensor<T>) {
    val rank: Int get() = tensor.rank
    val shape: IntArray get() = tensor.shape

    /**
     * Dimensional operations only available at this level
     */
    fun as1D(): Vector<T> {
        require(tensor.rank == 1) { "Tensor is not 1D. Shape: ${tensor.shape.joinToString()}" }
        return Vector(tensor)
    }

    fun as2D(): Matrix<T> {
        require(tensor.rank == 2) { "Tensor is not 2D. Shape: ${tensor.shape.joinToString()}" }
        return Matrix(tensor)
    }

    fun as3D(): Volume<T> {
        require(tensor.rank == 3) { "Tensor is not 3D. Shape: ${tensor.shape.joinToString()}" }
        return Volume(tensor)
    }

    /**
     * Reshape - creates new ShapedTensor with different dimensions
     */
    fun reshape(vararg newShape: Int): ShapedTensor<T> =
        ShapedTensor(tensor.reshape(*newShape)) // Assumes tensor.reshape exists

    /**
     * Move to slicing mode
     */
    fun sliceable(): SliceableTensor<T> = SliceableTensor(tensor)
}

/**
 * Level 2A: Vector (1D Specific Operations)
 */
@JvmInline
value class Vector<T>(val tensor: Tensor<T>) {
    init {
        require(tensor.rank == 1) { "Input tensor for Vector must be 1D. Shape: ${tensor.shape.joinToString()}" }
    }
    val size: Int get() = tensor.shape[0]

    /**
     * Vector-specific operations
     */
    fun dot(other: Vector<T>): T = tensor.dot(other.tensor) // Calls core.dot
    fun norm(p: Int = 2): Double = tensor.norm(p) // Calls core.norm, default L2
    fun mean(): Double = tensor.mean() // New method, calls core.mean

    /**
     * Time series operations (only available for vectors)
     * Uses WindowSize from TensorDslTypes.kt
     */
    fun rolling(window: WindowSize): RollingVector<T> = RollingVector(tensor j window.value)

    /**
     * Statistical operations
     */
    fun statistics(): VectorStats<T> = VectorStats(tensor)

    // Example of an element-wise operation if available on Tensor<T>
    // fun map(transform: (T) -> T): Vector<T> = Vector(tensor.mapElements(transform)) // Assuming mapElements exists
}

/**
 * Level 2B: Matrix (2D Specific Operations)
 */
@JvmInline
value class Matrix<T>(val tensor: Tensor<T>) {
    init {
        require(tensor.rank == 2) { "Input tensor for Matrix must be 2D. Shape: ${tensor.shape.joinToString()}" }
    }
    val rows: Int get() = tensor.shape[0]
    val cols: Int get() = tensor.shape[1]

    /**
     * Matrix-specific operations
     */
    fun matmul(other: Matrix<T>): Matrix<T> =
        Matrix(tensor.matmul(other.tensor)) // Calls core.matmul

    fun transpose(): Matrix<T> = Matrix(tensor.transpose()) // Assumes tensor.transpose exists
    fun inverse(): Matrix<T> = TODO("Matrix inverse - needs specific implementation")

    /**
     * Move to linear algebra mode
     */
    fun linearAlgebra(): LinearAlgebraTensor<T> = LinearAlgebraTensor(tensor)

    /**
     * Move to ML feature mode
     */
    fun features(): FeatureTensor<T> = FeatureTensor(tensor)
}

/**
 * Level 2C: Volume (3D Specific Operations)
 */
@JvmInline
value class Volume<T>(val tensor: Tensor<T>) {
    init {
        require(tensor.rank == 3) { "Input tensor for Volume must be 3D. Shape: ${tensor.shape.joinToString()}" }
    }

    // Assuming shape convention (e.g., depth, height, width or height, width, channels)
    // The original had height, width, channels from shape[0], shape[1], shape[2]
    // This matches common [H, W, C] convention.
    val height: Int get() = tensor.shape[0]
    val width: Int get() = tensor.shape[1]
    val channels: Int get() = tensor.shape[2]


    /**
     * Image processing operations
     */
    fun convolve(): ConvolutionTensor<T> = ConvolutionTensor(tensor)
}

/**
 * Level 3A: RollingVector (Time Series Operations)
 */
@JvmInline
value class RollingVector<T>(val config: Join<Tensor<T>, Int>) { // Window is Int here from window.value
    val tensor: Tensor<T> get() = config.a
    val window: Int get() = config.b
    /**
     * Time series specific operations
     */
    fun mean(): Vector<Double> { // This is effectively Simple Moving Average (SMA)
        require(tensor.rank == 1) { "Rolling.mean (SMA) operations require 1D Vector input." }
        return Vector(tensor.sma(WindowSize(this.window)))
    }

    fun std(): Vector<Double> {
        require(tensor.rank == 1) { "Rolling.std operations require 1D Vector input." }
        return Vector(tensor.rollingStdDev(WindowSize(this.window)))
    }

    fun momentum(alpha: Double): Vector<Double> { // alpha is LearningRate.value or raw double
        require(tensor.rank == 1) { "Rolling operations require 1D Vector input." }
        val N = tensor.totalSize
        if (N == 0 || window == 0 || N < window) return Vector(TensorSeries<Double>(0) {})

        val resultData = DoubleArray(N - window + 1)
        if (resultData.isEmpty()) return Vector(TensorSeries<Double>(0) {})

        var currentEma = (0 until window).sumOf { (tensor(it) as Number).toDouble() } / window.toDouble()
        resultData[0] = currentEma

        for (i in 1 until resultData.size) {
            val newValue = (tensor(i + window - 1) as Number).toDouble()
            currentEma = alpha * newValue + (1.0 - alpha) * currentEma
            resultData[i] = currentEma
        }
        return Vector(TensorSeries(resultData.size) { resultData[it] })
    }


    /**
     * Technical analysis (only available in rolling context)
     */
    fun sma(): Vector<Double>  = mean()
    fun ema(alpha: LearningRate): Vector<Double>   = momentum(alpha.value)
    fun bollinger(k: StandardDeviations): BollingerBands<T>  = BollingerBands((tensor j window) j k.value)

    /**
     * Volatility measures (only in time series context)
     */
    fun volatility(): Vector<Double>   = std()
    fun sharpe(riskFreeRate: Double = 0.0): Vector<Double>  {
        val returns = mean() // This is rolling mean, not item-wise returns
        val vol = std()
        if (returns.tensor.totalSize == 0 || vol.tensor.totalSize == 0 || returns.tensor.totalSize != vol.tensor.totalSize) {
            return Vector(TensorSeries<Double>(0) {})
        }
        return Vector(TensorSeries<Double>(returns.tensor.totalSize) { i ->
            val volVal = vol.tensor(i) // Element access
            if (volVal == 0.0) 0.0 else (returns.tensor(i) - riskFreeRate) / volVal // Element access
        })
    }
}

/**
 * Level 3B: VectorStats (Statistical Analysis)
 */
@JvmInline
value class VectorStats<T>(val tensor: Tensor<T>) {
    init { require(tensor.rank == 1) { "VectorStats requires a 1D tensor."} }
    // Renamed to avoid conflict with Vector.mean(), if this is a more statistical/descriptive mean
    fun descriptiveMean(): Double = TODO("Mean calculation for Tensor<T>")
    fun median(): Double = TODO("Median calculation for Tensor<T>")
    fun quantile(q: Double): Double = TODO("Quantile calculation for Tensor<T>")

    /**
     * Distribution analysis (only available in stats context)
     */
    fun histogram(bins: ItemCount): HistogramTensor<T> = HistogramTensor(tensor, bins.count)
    fun distribution(): DistributionTensor<T> = DistributionTensor(tensor)
}

/**
 * Level 3C: FeatureTensor (ML Feature Operations)
 */
@JvmInline
value class FeatureTensor<T>(val tensor: Tensor<T>) {
    init { require(tensor.rank >= 2) { "FeatureTensor typically expects at least 2D (samples, features)." } }
    /**
     * Feature engineering operations
     */
    fun normalize(): FeatureTensor<T> = TODO("Feature normalization for Tensor<T>")
    fun standardize(): FeatureTensor<T> = TODO("Z-score standardization for Tensor<T>")
    fun scale(min: Double, max: Double): FeatureTensor<T> = TODO("Min-max scaling for Tensor<T>")

    /**
     * Feature selection (only available in feature context)
     */
    fun selectKBest(k: ItemCount): SelectedFeatures<T> = SelectedFeatures(tensor, k.count)

    /**
     * Move to ML model building
     */
    fun modelBuilder(): ModelBuilder<T> = ModelBuilder(tensor)
}

/**
 * Level 4A: BollingerBands (Technical Analysis)
 */
@JvmInline
value class BollingerBands<T>(val config: Join<Join<Tensor<T>, Int>, Double>) { // k is StandardDeviations.value
    val tensor: Tensor<T> get() = config.a.a
    val window: Int get() = config.a.b
    val k: Double get() = config.b
    fun upper(): Vector<T> = TODO("Upper band for Tensor<T>")
    fun lower(): Vector<T> = TODO("Lower band for Tensor<T>")
    fun middle(): Vector<T> = TODO("Middle band (SMA) for Tensor<T>")

    /**
     * Trading signals (only available with bands)
     */
    fun signals(): TradingSignals<T> = TradingSignals(tensor)
}

/**
 * Level 4B: SelectedFeatures (Feature Selection Results)
 */
@JvmInline
value class SelectedFeatures<T>(val tensor: Tensor<T>, val k: Int) { // k is ItemCount.count
    fun indices(): IntArray = TODO("Selected feature indices from Tensor<T>")
    fun importance(): Vector<Double> = TODO("Feature importance scores from Tensor<T>")

    /**
     * Model building with selected features
     */
    fun train(): ModelBuilder<T> = ModelBuilder(tensor)
}

/**
 * Level 4C: ModelBuilder (ML Model Construction)
 */
@JvmInline
value class ModelBuilder<T>(val features: Tensor<T>) {
    /**
     * Model architecture decisions
     */
    fun dense(units: LayerUnits): DenseLayer<T> = DenseLayer(features, units.count)
    fun linear(): LinearModel<T> = LinearModel(features)
    fun randomForest(trees: ItemCount): RandomForestModel<T> = RandomForestModel(features, trees.count)

    /**
     * Automated model selection
     */
    fun autoML(): AutoMLBuilder<T> = AutoMLBuilder(features)
}

/**
 * Level 5A: DenseLayer (Neural Network Layer)
 */
@JvmInline
value class DenseLayer<T>(val features: Tensor<T>, val units: Int) { // units is LayerUnits.count
    fun activation(fn: ActivationFunction): ActivatedLayer<T> = ActivatedLayer(features, units, fn.name)

    /**
     * Layer stacking (only available after defining dense layer)
     */
    fun stack(): LayerStack<T> = LayerStack(features, listOf("dense_$units"))
}

/**
 * Level 5B: LinearModel (Simple Linear Model)
 */
@JvmInline
value class LinearModel<T>(val features: Tensor<T>) {
    fun fit(targets: Tensor<T>): TrainedLinearModel<T> = TrainedLinearModel(features, targets)

    /**
     * Regularization (only for linear models)
     */
    fun ridge(alpha: LearningRate): RegularizedModel<T> = RegularizedModel(features, "ridge", alpha.value)
    fun lasso(alpha: LearningRate): RegularizedModel<T> = RegularizedModel(features, "lasso", alpha.value)
}

/**
 * Level 6: TrainedLinearModel (Trained Model Operations)
 */
@JvmInline
value class TrainedLinearModel<T>(val features: Tensor<T>, val targets: Tensor<T>) {
    fun predict(newData: Tensor<T>): Tensor<T> = TODO("Linear prediction with Tensor<T>")
    fun coefficients(): Vector<T> = TODO("Model coefficients for Tensor<T>")

    /**
     * Model evaluation (only available after training)
     */
    fun evaluate(): ModelEvaluation<T> = ModelEvaluation(features, targets)
}

/**
 * Specialized inline classes for specific domains
 */

@JvmInline
value class TypedTensor<T>(val tensor: Tensor<T>)

@JvmInline
value class HotTensor<T>(val tensor: Tensor<T>) {
    /**
     * Performance operations only available in hot mode
     */
    fun simd(): SIMDTensor<T> = SIMDTensor(tensor)
    fun parallel(threads: ItemCount): ParallelTensor<T> = ParallelTensor(tensor, threads.count)
}

@JvmInline
value class SIMDTensor<T>(val tensor: Tensor<T>)

@JvmInline
value class ParallelTensor<T>(val tensor: Tensor<T>, val threads: Int) // threads is ItemCount.count

@JvmInline
value class SliceableTensor<T>(val tensor: Tensor<T>) {
    // Assuming tensor.sliceAdvanced is available from borg.trikeshed.lib
    fun slice(vararg specs: TensorRangeSpec): SliceableTensor<T> =
        SliceableTensor(tensor.sliceAdvanced(*specs))
}

@JvmInline
value class LinearAlgebraTensor<T>(val tensor: Tensor<T>)

@JvmInline
value class ConvolutionTensor<T>(val tensor: Tensor<T>)

@JvmInline
value class HistogramTensor<T>(val tensor: Tensor<T>, val bins: Int) // bins is ItemCount.count

@JvmInline
value class DistributionTensor<T>(val tensor: Tensor<T>)

@JvmInline
value class TradingSignals<T>(val tensor: Tensor<T>)

@JvmInline
value class RandomForestModel<T>(val features: Tensor<T>, val trees: Int) // trees is ItemCount.count

@JvmInline
value class AutoMLBuilder<T>(val features: Tensor<T>)

@JvmInline
value class ActivatedLayer<T>(val features: Tensor<T>, val units: Int, val activation: String) // activation is ActivationFunction.name

@JvmInline
value class LayerStack<T>(val features: Tensor<T>, val layers: List<String>)

@JvmInline
value class RegularizedModel<T>(val features: Tensor<T>, val type: String, val alpha: Double) // alpha is LearningRate.value

@JvmInline
value class ModelEvaluation<T>(val features: Tensor<T>, val targets: Tensor<T>)

/**
 * Entry points to the stairway
 */

/**
 * Start the stairway - transforms any Tensor into the DSL
 */
fun <T> Tensor<T>.dsl(): RawTensor<T> = RawTensor(this)

/**
 * Direct entry points for specific use cases
 */
fun <T> Tensor<T>.asFeatures(): FeatureTensor<T> = FeatureTensor(this)
fun <T> Tensor<T>.asTimeSeries(): Vector<T> {
    require(this.rank == 1) { "Tensor must be 1D to be used as TimeSeries. Shape: ${this.shape.joinToString()}" }
    return Vector(this)
}
fun <T> Tensor<T>.asMatrix(): Matrix<T> {
    require(this.rank == 2) { "Tensor must be 2D to be used as Matrix. Shape: ${this.shape.joinToString()}" }
    return Matrix(this)
}
fun <T> Tensor<T>.forPerformance(): HotTensor<T> = HotTensor(this)

/**
 * Example usage showing the transformative power
 */
fun demonstrateStairway() {
    // Assuming TensorCursor or a similar factory for Tensor<Double> exists.
    // And that Tensor<T> has an 'α' (alpha) operator for element-wise transform and a 'slice' method.
    // These are not part of this DSL file but assumed from the original example.
    val data: Tensor<Double> = TensorCursor<Double>(1000, 10) { _, _ -> kotlin.random.Random.nextDouble() }

    // Traditional approach - same operations throughout
    // val traditional = data.α { it * 2 }.slice(range(0, 500), all) // Assuming data.slice exists

    // Stairway approach - operations transform based on context
    val transformed = data.dsl()                    // RawTensor
        .shaped()                                   // ShapedTensor
        .as2D()                                     // Matrix
        .features()                                 // FeatureTensor
        .normalize()                                // Still FeatureTensor (method is TODO)
        .selectKBest(ItemCount(5))                 // SelectedFeatures (method is TODO)
        .train()                                    // ModelBuilder
        .dense(LayerUnits(64))                     // DenseLayer
        .activation(ActivationFunction(ActivationFunction.RELU)) // Use defined constant

    // Each step unlocks different operations!
}

// Dummy/Placeholder for Tensor related properties and functions if not found in imports.
// These are assumed to be in borg.trikeshed.lib.*
// internal val <T> Tensor<T>.rank: Int get() = this.shape.size
// internal val <T> Tensor<T>.shape: IntArray get() = (this as Join<IntArray, *>).a
// internal val <T> Tensor<T>.totalSize: Int get() = shape.fold(1) { acc, i -> acc * i }
// internal operator fun <T> Tensor<T>.get(vararg indices: Int): T = (this as Join<IntArray, (IntArray) -> T>).b(indices)
// internal fun <T> Tensor<T>.reshape(vararg newShape: Int): Tensor<T> = TODO("reshape $newShape")
// internal fun <T> Tensor<T>.transpose(): Tensor<T> = TODO("transpose")
// internal fun <T> Tensor<T>.matmul(other: Tensor<T>): Tensor<T> = TODO("matmul")
// internal fun <T> Tensor<T>.sliceAdvanced(vararg specs: TensorRangeSpec): Tensor<T> = TODO("sliceAdvanced")
// internal fun <T> Tensor<T>.oneHot(depth: Int, onValue: Any, offValue: Any): Tensor<T> = TODO("oneHot") // Changed T to Any for on/off
// typealias Tensor<T> = Join<IntArray, (IntArray) -> T> // Already expected from borg.trikeshed.lib.Tensor
// data class TensorRangeSpec(val start: Int?, val end: Int?, val step: Int? = 1) // Example, real one from lib
// fun range(start: Int?, end: Int?, step: Int? = 1) = TensorRangeSpec(start, end, step) // Example
// val all: TensorRangeSpec? = null // Example for slicing
// class TensorCursor<T>(vararg dims: Int, init: (IntArray) -> T) : Tensor<T> { ... } // Example
// fun <T> Tensor<T>.mapElements(transform: (T) -> T): Tensor<T> = TODO() // Example
// typealias TensorSeries<T> = Series<T> // Example if TensorSeries is just Series for DSL methods
// This TensorSeries seems to be a constructor for a Series that can be used as a Tensor for DSL results
internal fun <T> TensorSeries(size: Int, initializer: (Int) -> T): Tensor<T> {
    val shape = intArrayOf(size)
    val data = Array(size, initializer)
    @Suppress("UNCHECKED_CAST") // Tensor is a Join, this cast is part of its assumed structure
    return shape j { indices -> data[indices[0]] } as Tensor<T>
}
internal fun <T> TensorSeries(rows: Int, cols: Int, initializer: (Int, Int) -> T): Tensor<T> {
    val shape = intArrayOf(rows, cols)
    val data = Array(rows) { r -> Array(cols) { c -> initializer(r, c) } }
    @Suppress("UNCHECKED_CAST")
    return shape j { indices -> data[indices[0]][indices[1]] } as Tensor<T>
}

// Assume these tensor operations are defined as extensions on Tensor<T> in borg.trikeshed.lib
// If not, the DSL methods calling them will remain TODO() or be incorrect.
// Example (these would not be in DSLStairway.kt but in the lib):
// internal expect val <T> Tensor<T>.rank: Int
// internal expect val <T> Tensor<T>.shape: IntArray
// internal expect val <T> Tensor<T>.totalSize: Int
// internal expect operator fun <T> Tensor<T>.get(vararg indices: Int): T
// internal expect fun <T> Tensor<T>.reshape(vararg newShape: Int): Tensor<T>
// internal expect fun <T> Tensor<T>.transpose(): Tensor<T>
// internal expect fun <T> Tensor<T>.matmul(other: Tensor<T>): Tensor<T>
// internal expect fun <T> Tensor<T>.sliceAdvanced(vararg specs: TensorRangeSpec): Tensor<T>
// internal expect fun <T> Tensor<T>.oneHot(depth: Int, onValue: Any, offValue: Any): Tensor<T>
