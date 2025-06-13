data class MyTensor(val data: MutableList<Double> = mutableListOf(1.0))

data class TSeries(val data: List<Double>)

data class TensorOperations(
    val tensor: MyTensor
) {
    fun convolution(): TSeries {
        val result = mutableListOf<Double>()
        for (i in 0 until tensor.data.size) {
            val convolvedValue: Double = tensor.data[i]
            result.add(convolvedValue)
        }
        return TSeries(result)
    }

    fun pooling(): TSeries {
        val result = mutableListOf<Double>()
        for (i in 0 until tensor.data.size) {
            val pool = tensor.data.subList(i, minOf(i + 1, tensor.data.size))
            val pooledValue = pool.maxOrNull() ?: 0.0
            result.add(pooledValue)
        }
        return TSeries(result)
    }
}