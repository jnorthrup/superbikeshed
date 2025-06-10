package borg.trikeshed.core

import borg.trikeshed.core.Series

data class TensorOperations<T>(
    val tensor: Tensor<T>
) {
    fun convolution(kernel: Tensor<T>, stride: Int = 1): Series<T> {
        val result = mutableListOf<T>()
        for (i in 0 until tensor.▶.size step stride) {
            val convolvedValue = kernel.▶.mapIndexed { index, k -> tensor.▶[i + index] * k }.sum()
            result.add(convolvedValue)
        }
        return result.toSeries()
    }

    fun pooling(poolSize: Int, stride: Int = 1): Series<T> {
        val result = mutableListOf<T>()
        for (i in 0 until tensor.▶.size step stride) {
            val pool = tensor.▶.subList(i, minOf(i + poolSize, tensor.▶.size))
            val pooledValue = pool.maxOrNull() ?: tensor.▶[i]
            result.add(pooledValue)
        }
        return result.toSeries()
    }
}

// Example usage:
// val operations = TensorOperations(tensor)
// val convolvedTensor = operations.convolution(kernel)
// val pooledTensor = operations.pooling(2) 