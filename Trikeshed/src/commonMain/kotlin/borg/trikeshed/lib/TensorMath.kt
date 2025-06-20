package borg.trikeshed.lib

import kotlin.math.sqrt

// Basic element-wise operations
operator fun <T : Number> Tensor<T>.plus(other: Tensor<T>): Tensor<T> {
    require(shape.contentEquals(other.shape)) { "Tensors must have the same shape for addition" }
    return generateTensor(shape) { coords -> (this.accessor(coords).toDouble() + other.accessor(coords).toDouble()) as T }
}

operator fun <T : Number> Tensor<T>.minus(other: Tensor<T>): Tensor<T> {
    require(shape.contentEquals(other.shape)) { "Tensors must have the same shape for subtraction" }
    return generateTensor(shape) { coords -> (this.accessor(coords).toDouble() - other.accessor(coords).toDouble()) as T }
}

operator fun <T : Number> Tensor<T>.times(other: Tensor<T>): Tensor<T> {
    require(shape.contentEquals(other.shape)) { "Tensors must have the same shape for multiplication" }
    return generateTensor(shape) { coords -> (this(coords).toDouble() * other(coords).toDouble()) as T }
}

operator fun <T : Number> Tensor<T>.div(other: Tensor<T>): Tensor<T> {
    require(shape.contentEquals(other.shape)) { "Tensors must have the same shape for division" }
    return generateTensor(shape) { coords -> (this(coords).toDouble() / other(coords).toDouble()) as T }
}

// Scalar operations
operator fun <T : Number> Tensor<T>.plus(scalar: T): Tensor<T> =
    generateTensor(shape) { coords -> (this(coords).toDouble() + scalar.toDouble()) as T }

operator fun <T : Number> Tensor<T>.minus(scalar: T): Tensor<T> =
    generateTensor(shape) { coords -> (this(coords).toDouble() - scalar.toDouble()) as T }

operator fun <T : Number> Tensor<T>.times(scalar: T): Tensor<T> =
    generateTensor(shape) { coords -> (this(coords).toDouble() * scalar.toDouble()) as T }

operator fun <T : Number> Tensor<T>.div(scalar: T): Tensor<T> =
    generateTensor(shape) { coords -> (this(coords).toDouble() / scalar.toDouble()) as T }

// Unary operations
operator fun <T : Number> Tensor<T>.unaryMinus(): Tensor<T> =
    generateTensor(shape) { coords -> (-this(coords).toDouble()) as T }

// Aggregations (example: sum for 1D tensors)
fun <T : Number> Tensor<T>.sum(): Double {
    require(rank == 1) { "Sum is currently supported only for 1D tensors" }
    var total = 0.0
    for (i in 0 until shape[0]) {
        total += this(intArrayOf(i)).toDouble()
    }
    return total
}

fun <T : Number> Tensor<T>.mean(): Double {
    require(rank == 1) { "Mean is currently supported only for 1D tensors" }
    if (totalSize == 0) return 0.0
    return sum() / totalSize
}

fun <T : Number> Tensor<T>.sqrt(): Tensor<T> {
    return generateTensor(shape) { coords -> sqrt(this(coords).toDouble()) as T }
}