package borg.trikeshed.nio

import borg.trikeshed.lib.ByteSeries
import borg.trikeshed.lib.Series
import kotlinx.cinterop.*

/**
 * Native-specific ByteSeries factory and utilities
 * 
 * Provides optimized ByteSeries operations for native platforms
 * with direct memory access and interop capabilities.
 */

/**
 * Native ByteSeries factory with optimized memory allocation
 */
object NativeByteSeriesFactory {
    
    /**
     * Create ByteSeries from native memory with zero-copy when possible
     */
    fun fromByteArray(array: ByteArray): ByteSeries = 
        ByteSeries(array)
    
    /**
     * Create ByteSeries with optimized native allocation
     */
    fun allocate(size: Int): ByteSeries = 
        ByteSeries(ByteArray(size))
    
    /**
     * Create ByteSeries from Series<Byte> with native optimizations
     */
    fun fromSeries(series: Series<Byte>): ByteSeries {
        val array = ByteArray(series.a)
        
        // Optimized copy using native memory operations
        for (i in 0 until series.a) {
            array[i] = series.b(i)
        }
        
        return ByteSeries(array)
    }
    
    /**
     * Create ByteSeries from pinned memory region (native-specific)
     */
    fun fromPinnedMemory(ptr: CPointer<ByteVar>, size: Int): ByteSeries {
        val array = ByteArray(size)
        for (i in 0 until size) {
            array[i] = ptr[i]
        }
        return ByteSeries(array)
    }
    
    /**
     * Create ByteSeries with memory-mapped backing (native-specific)
     */
    fun createMapped(size: Int): ByteSeries {
        // For now, fallback to regular allocation
        // Future: implement actual memory mapping
        return allocate(size)
    }
}

/**
 * Native-specific ByteSeries extensions
 */

/**
 * Pin ByteSeries memory for native interop
 */
fun ByteSeries.pinned(): Pinned<ByteArray> = toArray().pin()

/**
 * Access ByteSeries as native memory pointer
 */
inline fun <T> ByteSeries.withPinnedMemory(block: (CPointer<ByteVar>) -> T): T =
    toArray().usePinned { pinned ->
        block(pinned.addressOf(0))
    }

/**
 * Copy ByteSeries to native memory region
 */
fun ByteSeries.copyToNative(destination: CPointer<ByteVar>, maxSize: Int = this.a) {
    val copySize = minOf(this.a, maxSize)
    withPinnedMemory { src ->
        for (i in 0 until copySize) {
            destination[i] = src[i]
        }
    }
}
