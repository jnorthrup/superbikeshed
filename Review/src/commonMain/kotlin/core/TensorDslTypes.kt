package core

import kotlin.jvm.JvmInline

/**
 * Represents the size of a window, typically for operations like sliding windows or pooling.
 * Must be a positive integer.
 */
@JvmInline
value class WindowSize(val value: Int) {
    init {
        require(value > 0) { "WindowSize must be positive, but was $value" }
    }
}

/**
 * Represents a number of standard deviations, often used for statistical calculations or thresholds.
 * Must be a positive double.
 */
@JvmInline
value class StandardDeviations(val value: Double) {
    init {
        require(value > 0) { "StandardDeviations must be positive, but was $value" }
    }
}

/**
 * Represents a learning rate for optimization algorithms in machine learning.
 * Must be a positive double, typically between 0.0 (exclusive) and 1.0 (inclusive).
 */
@JvmInline
value class LearningRate(val value: Double) {
    init {
        require(value > 0 && value <= 1.0) { "LearningRate must be > 0 and <= 1.0, but was $value" }
    }
}

/**
 * Represents the number of epochs (iterations over a dataset) for training a model.
 * Must be a positive integer.
 */
@JvmInline
value class Epochs(val count: Int) {
    init {
        require(count > 0) { "Epochs count must be positive, but was $count" }
    }
}

/**
 * Represents the size of a batch of data used in one iteration of training or processing.
 * Must be a positive integer.
 */
@JvmInline
value class BatchSize(val count: Int) {
    init {
        require(count > 0) { "BatchSize count must be positive, but was $count" }
    }
}

/**
 * Represents the number of units (e.g., neurons) in a layer of a neural network.
 * Must be a positive integer.
 */
@JvmInline
value class LayerUnits(val count: Int) {
    init {
        require(count > 0) { "LayerUnits count must be positive, but was $count" }
    }
}

/**
 * Represents the name of an activation function used in neural networks.
 * While a String is used for flexibility, common activation functions are provided
 * as constants in the companion object for convenience and consistency.
 */
@JvmInline
value class ActivationFunction(val name: String) {
    companion object {
        const val RELU = "relu"
        const val SIGMOID = "sigmoid"
        const val TANH = "tanh"
        const val SOFTMAX = "softmax"
        const val LINEAR = "linear" // Or "identity"
        const val LEAKY_RELU = "leaky_relu"
        const val ELU = "elu"
        const val SWISH = "swish"
        // Add other common activation functions as needed
    }
}

// Other potential DSL-specific types could include:
// @JvmInline value class KernelSize(val value: Int) { init { require(value > 0 && value % 2 != 0) { "KernelSize must be positive and odd" } } }
// @JvmInline value class Stride(val value: Int) { init { require(value > 0) } }
// @JvmInline value class Padding(val value: String) { init { require(value in listOf("valid", "same")) } } // Or an enum
// @JvmInline value class DropoutRate(val value: Double) { init { require(value >= 0 && value < 1.0) } }
// enum class LossFunction { MSE, MAE, BINARY_CROSSENTROPY, CATEGORICAL_CROSSENTROPY }
// enum class Optimizer { ADAM, SGD, RMSPROP }
