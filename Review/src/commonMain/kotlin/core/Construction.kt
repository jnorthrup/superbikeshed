@file:Suppress("NOTHING_TO_INLINE")

package core

/**
 * Vararg-biased construction patterns with TrikeShed Join elegance
 * 
 * Line-centric approach with strong vararg bias for ergonomic tensor creation.
 */

/**
 * T_ - Tensor construction object with vararg support
 */
object T_ {
    /**
     * Shape-first construction
     */
    operator fun <T> get(vararg dimensions: Int): TensorBuilder<T> = 
        TensorBuilder(dimensions)
    
    /**
     * Data-first construction (1D)
     */
    operator fun <T> invoke(vararg data: T): Tensor<T> = 
        TensorSeries(data.size) { i -> data[i] }
    
    /**
     * Range construction (1D)
     */
    operator fun invoke(range: IntRange): Tensor<Int> =
        TensorSeries(range.last - range.first + 1) { i -> range.first + i }
}

/**
 * TensorBuilder - Deferred construction with shape (Join-based)
 */
@kotlin.jvm.JvmInline
value class TensorBuilder<T>(private val shape: IntArray) {
    /**
     * Build tensor with accessor function
     */
    operator fun invoke(accessor: (IntArray) -> T): Tensor<T> = 
        TensorConstruct(shape, accessor)
    
    /**
     * Build with vararg accessor for small dimensions
     */
    operator fun invoke(accessor: (Int) -> T): Tensor<T> {
        require(shape.size == 1) { "Vararg accessor only for 1D tensors" }
        return TensorConstruct(shape) { coords -> accessor(coords[0]) }
    }
    
    operator fun invoke(accessor: (Int, Int) -> T): Tensor<T> {
        require(shape.size == 2) { "2-arg accessor only for 2D tensors" }
        return TensorConstruct(shape) { coords -> accessor(coords[0], coords[1]) }
    }
    
    operator fun invoke(accessor: (Int, Int, Int) -> T): Tensor<T> {
        require(shape.size == 3) { "3-arg accessor only for 3D tensors" }
        return TensorConstruct(shape) { coords -> accessor(coords[0], coords[1], coords[2]) }
    }
}

/**
 * Common construction patterns
 */

/**
 * zeros - Zero-filled tensor
 */
inline fun zeros(vararg shape: Int): Tensor<Double> = 
    TensorConstruct(shape) { 0.0 }

/**
 * ones - One-filled tensor  
 */
inline fun ones(vararg shape: Int): Tensor<Double> = 
    TensorConstruct(shape) { 1.0 }

/**
 * arange - Sequential values (like numpy.arange)
 */
fun arange(start: Int, stop: Int, step: Int = 1): Tensor<Int> {
    val size = (stop - start + step - 1) / step
    return TensorSeries(size) { i -> start + i * step }
}

/**
 * linspace - Linear spacing
 */
fun linspace(start: Double, stop: Double, num: Int): Tensor<Double> {
    val step = (stop - start) / (num - 1)
    return TensorSeries(num) { i -> start + i * step }
}

/**
 * Array conversions (TrikeShed pattern)
 */
fun IntArray.toTensor(): Tensor<Int> = TensorSeries(size) { i -> this[i] }
fun DoubleArray.toTensor(): Tensor<Double> = TensorSeries(size) { i -> this[i] }
fun <T> Array<T>.toTensor(): Tensor<T> = TensorSeries(size) { i -> this[i] }
fun <T> List<T>.toTensor(): Tensor<T> = TensorSeries(size) { i -> this[i] }

/**
 * 2D construction from nested structures
 */
fun Array<IntArray>.toTensor(): Tensor<Int> = 
    TensorCursor(size, this[0].size) { i, j -> this[i][j] }

fun Array<DoubleArray>.toTensor(): Tensor<Double> = 
    TensorCursor(size, this[0].size) { i, j -> this[i][j] }