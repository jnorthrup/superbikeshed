package borg.trikeshed.lib

import kotlin.jvm.JvmName
import kotlinx.serialization.Serializable

// JVM-specific extensions with @JvmName to avoid signature clashes

@JvmName("toWireBytesInt")
fun Series<Int>.toWireBytes(): UByteArray {
    val data = (0 until this.size).map { this[it].toString() }.joinToString(",")
    return data.encodeToByteArray().toUByteArray()
}

@JvmName("toWireBytesString")
fun Series<String>.toWireBytes(): UByteArray {
    val data = (0 until this.size).map { this[it] }.joinToString(",")
    return data.encodeToByteArray().toUByteArray()
}

@JvmName("toWireBytesDataValue")
fun Series<DataValue>.toWireBytes(): UByteArray {
    val data = (0 until this.size).map { this[it].bytes.toByteArray().contentToString() }.joinToString(",")
    return data.encodeToByteArray().toUByteArray()
}

@JvmName("compressSeriesInt")
fun Series<Int>.compress(type: CompressionType): UByteArray {
    return this.toWireBytes().compress(type)
}

@JvmName("compressSeriesString")
fun Series<String>.compress(type: CompressionType): UByteArray {
    return this.toWireBytes().compress(type)
}

@JvmName("toCborSeriesString")
fun Series<String>.toCbor(): ByteArray {
    // TODO: Implement proper CBOR serialization
    return ByteArray(100)
}

@JvmName("toCborTensorDouble")
fun Tensor<Double>.toCbor(): ByteArray {
    // TODO: Implement proper CBOR serialization
    return ByteArray(100)
}

@JvmName("toCborJoinIntString")
fun Join<Int, String>.toCbor(): ByteArray {
    // TODO: Implement proper CBOR serialization
    return ByteArray(100)
}