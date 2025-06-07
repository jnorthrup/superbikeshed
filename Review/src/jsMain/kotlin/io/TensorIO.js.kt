package io

import core.Tensor

actual fun ByteArray.asDoubleArray(offset: Int): Double {
    // JavaScript implementation for reading double from ByteArray
    val view = this.sliceArray(offset until offset + 8)
    val buffer = view.toTypedArray()
    
    // Convert bytes to double using DataView simulation
    var result = 0.0
    for (i in 0..7) {
        result += (buffer[i].toInt() and 0xFF) * kotlin.math.pow(256.0, i.toDouble())
    }
    return result
}

actual fun ByteArray.asFloatArray(offset: Int): Float {
    // JavaScript implementation for reading float from ByteArray
    val view = this.sliceArray(offset until offset + 4)
    val buffer = view.toTypedArray()
    
    var result = 0.0
    for (i in 0..3) {
        result += (buffer[i].toInt() and 0xFF) * kotlin.math.pow(256.0, i.toDouble())
    }
    return result.toFloat()
}

actual fun ByteArray.asIntArray(offset: Int): Int {
    val view = this.sliceArray(offset until offset + 4)
    return view[0].toInt() or 
           (view[1].toInt() shl 8) or 
           (view[2].toInt() shl 16) or 
           (view[3].toInt() shl 24)
}

actual fun ByteArray.asInt(offset: Int): Int = this.asIntArray(offset)

actual fun Double.toBytes(buffer: ByteArray, offset: Int) {
    // Convert double to bytes in JavaScript
    val bits = this.toBits()
    for (i in 0..7) {
        buffer[offset + i] = ((bits shr (i * 8)) and 0xFF).toByte()
    }
}

actual fun Float.toBytes(buffer: ByteArray, offset: Int) {
    // Convert float to bytes in JavaScript
    val bits = this.toBits()
    for (i in 0..3) {
        buffer[offset + i] = ((bits shr (i * 8)) and 0xFF).toByte()
    }
}

actual fun Int.toBytes(buffer: ByteArray, offset: Int) {
    buffer[offset] = this.toByte()
    buffer[offset + 1] = (this shr 8).toByte()
    buffer[offset + 2] = (this shr 16).toByte()  
    buffer[offset + 3] = (this shr 24).toByte()
}

actual fun Tensor<ByteArray>.toByteArray(): ByteArray {
    // Flatten tensor to ByteArray
    val totalSize = this.shape.fold(1) { acc, dim -> acc * dim }
    val result = ByteArray(totalSize)
    
    // Simple flattening - copy tensor data
    TODO("Implement tensor to ByteArray conversion")
}

actual fun ByteArray.toTensor(): Tensor<ByteArray> {
    // Create tensor from ByteArray
    TODO("Implement ByteArray to tensor conversion")
}

actual class MemoryMappedTensorSource<T> actual constructor(file: TensorFile, elementType: TensorType<T>) :
    TensorSource<T>