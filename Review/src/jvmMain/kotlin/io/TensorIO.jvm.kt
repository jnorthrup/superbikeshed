@file:Suppress("NOTHING_TO_INLINE")

package io

import borg.trikeshed.lib.*
import core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.jvm.*

/**
 * JVM-specific tensor I/O implementations
 * 
 * Leverages NIO, memory-mapped files, and high-performance libraries
 * for optimal tensor I/O performance on the JVM.
 */

actual class TensorFile actual constructor(private val path: String) {
    private val nioPath = Path(path)
    
    actual suspend fun size(): Long = nioPath.fileSize()
    actual suspend fun exists(): Boolean = nioPath.exists()
    actual suspend fun delete() { java.io.File(path).delete() }
    actual suspend fun createNewFile() { java.io.File(path).createNewFile() }
}

actual class MemoryMappedTensorSource<T> actual constructor(
    private val file: TensorFile,
    override val elementType: TensorType<T>
) : TensorSource<T> {
    
    private lateinit var channel: FileChannel
    private lateinit var buffer: ByteBuffer
    
    // Will be initialized when first accessed
    override lateinit var shape: IntArray
    
    private suspend fun initialize() {
        if (!::channel.isInitialized) {
            channel = FileChannel.open(
                Path(file.toString()),
                StandardOpenOption.READ
            )
            buffer = channel.map(
                FileChannel.MapMode.READ_ONLY,
                0,
                channel.size()
            ).order(ByteOrder.nativeOrder())
            
            // Read shape from file header
            val rank = buffer.int
            shape = IntArray(rank) { buffer.int }
        }
    }
    
    override suspend fun readChunk(coords: IntArray, size: IntArray): Tensor<T> {
        initialize()
        
        return Tensor(size) { chunkCoords ->
            val globalCoords = coords.zip(chunkCoords) { offset, local -> offset + local }.toIntArray()
            val linearIndex = coordsToLinear(globalCoords, shape)
            val byteOffset = 4 + shape.size * 4 + linearIndex * elementType.size // Skip header
            
            buffer.position(byteOffset)
            when (elementType) {
                is DoubleType -> buffer.double as T
                is FloatType -> buffer.float as T
                is IntType -> buffer.int as T
                else -> error("Unsupported type: $elementType")
            }
        }
    }
    
    override suspend fun readAll(): Tensor<T> {
        initialize()
        return readChunk(IntArray(shape.size) { 0 }, shape)
    }
    
    override fun stream(chunkSize: IntArray): Flow<Tensor<T>> = flow {
        initialize()
        
        // Calculate number of chunks along each dimension
        val numChunks = shape.zip(chunkSize) { dim, chunk -> 
            (dim + chunk - 1) / chunk 
        }.toIntArray()
        
        val totalChunks = numChunks.fold(1, Int::times)
        
        var i = 0
        while (i < totalChunks) {
            val chunkCoords = linearToCoords(i, numChunks)
            val startCoords = chunkCoords.zip(chunkSize) { coord, size -> coord * size }.toIntArray()
            val actualSize = startCoords.zip(chunkSize) { start, size ->
                minOf(size, shape[startCoords.indexOf(start)] - start)
            }.toIntArray()
            
            emit(readChunk(startCoords, actualSize))
            i++
        }
    }
    
    private fun linearToCoords(linearIndex: Int, shape: IntArray): IntArray {
        val coords = IntArray(shape.size)
        var remaining = linearIndex
        
        var i = shape.size - 1
        while (i >= 0) {
            coords[i] = remaining % shape[i]
            remaining /= shape[i]
            i--
        }
        
        return coords
    }
}

class JvmTensorSink<T>(
    private val file: TensorFile,
    private val elementType: TensorType<T>
) : TensorSink<T> {
    
    private lateinit var channel: FileChannel
    private lateinit var buffer: ByteBuffer
    
    override suspend fun write(tensor: Tensor<T>) {
        if (!::channel.isInitialized) {
            channel = FileChannel.open(
                Path(file.toString()),
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            )
            
            val totalSize = 4 + tensor.rank * 4 + tensor.totalSize * elementType.size
            buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.nativeOrder())
        }
        
        // Write header
        buffer.putInt(tensor.rank)
        tensor.shape.forEach { buffer.putInt(it) }
        
        // Write data
        tensor.forEachCoords { _, value ->
            when (elementType) {
                is DoubleType -> buffer.putDouble(value as Double)
                is FloatType -> buffer.putFloat(value as Float) 
                is IntType -> buffer.putInt(value as Int)
                else -> error("Unsupported type: $elementType")
            }
        }
        
        buffer.flip()
        channel.write(buffer)
    }
    
    override suspend fun writeChunk(tensor: Tensor<T>, offset: IntArray) {
        TODO("Implement chunk writing")
    }
    
    override suspend fun flush() {
        if (::channel.isInitialized) {
            channel.force(true)
        }
    }
    
    override suspend fun close() {
        if (::channel.isInitialized) {
            channel.close()
        }
    }
}

// Factory functions
actual fun <T> createSource(file: TensorFile): TensorSource<T> {
    @Suppress("UNCHECKED_CAST")
    return MemoryMappedTensorSource(file, DoubleType as TensorType<T>)
}

actual fun <T> createSink(file: TensorFile): TensorSink<T> {
    @Suppress("UNCHECKED_CAST")
    return JvmTensorSink(file, DoubleType as TensorType<T>)
}

// Byte operations using ByteBuffer for performance
actual fun ByteArray.asDoubleArray(offset: Int): Double = 
    ByteBuffer.wrap(this, offset, 8).order(ByteOrder.nativeOrder()).double

actual fun ByteArray.asFloatArray(offset: Int): Float = 
    ByteBuffer.wrap(this, offset, 4).order(ByteOrder.nativeOrder()).float

actual fun ByteArray.asIntArray(offset: Int): Int = 
    ByteBuffer.wrap(this, offset, 4).order(ByteOrder.nativeOrder()).int

actual fun ByteArray.asInt(offset: Int): Int = 
    ByteBuffer.wrap(this, offset, 4).order(ByteOrder.nativeOrder()).int

actual fun Double.toBytes(buffer: ByteArray, offset: Int) {
    ByteBuffer.wrap(buffer, offset, 8).order(ByteOrder.nativeOrder()).putDouble(this)
}

actual fun Float.toBytes(buffer: ByteArray, offset: Int) {
    ByteBuffer.wrap(buffer, offset, 4).order(ByteOrder.nativeOrder()).putFloat(this)
}

actual fun Int.toBytes(buffer: ByteArray, offset: Int) {
    ByteBuffer.wrap(buffer, offset, 4).order(ByteOrder.nativeOrder()).putInt(this)
}

actual fun Tensor<ByteArray>.toByteArray(): ByteArray {
    val totalSize = this.tensorFold(0) { acc, bytes -> acc + bytes.size }
    val result = ByteArray(totalSize)
    var offset = 0
    
    var i = 0
    while (i < this.totalSize) {
        val coords = this.tensorLinearToCoords(i)
        val bytes = this.invoke(coords)
        bytes.copyInto(result, offset)
        offset += bytes.size
        i++
    }
    
    return result
}

actual fun ByteArray.toTensor(): Tensor<ByteArray> = 
    intArrayOf(1) j { coords -> this }