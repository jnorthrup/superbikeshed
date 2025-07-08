@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class, kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.core

/**
 * The ASPIRATION: A common, high-performance JSON scanner API.
 * This `expect` declaration defines the contract. It promises a `scan`
 * method that is intended to be hardware-accelerated.
 * 
 * The performance gap between platforms is not about optimization - it's about
 * categorical algorithmic differences. Only platforms with native SIMD access
 * can achieve GB/s scanning speeds.
 */
expect class HardwareAcceleratedJsonScanner(json: String) {

    /**
     * The core operation that MUST be implemented with platform-specific
     * acceleration (e.g., SIMD intrinsics) to be fast.
     * This builds the structural index of the JSON document.
     * 
     * Performance targets:
     * - Native SIMD: GB/s (gigabytes per second)
     * - Pure Kotlin: MB/s (megabytes per second)
     * 
     * The 50-100x performance gap is architectural, not accidental.
     */
    fun scan()

    /**
     * Queries the pre-computed index to find a value.
     * This operation relies on the speed of the initial `scan`.
     */
    fun query(path: String): String?
    
    /**
     * Returns performance characteristics of this implementation
     */
    fun getPerformanceProfile(): PerformanceProfile

    companion object {
        /**
         * Factory function to create a platform-specific instance.
         */
        fun create(json: String): HardwareAcceleratedJsonScanner
    }
}

/**
 * Performance characteristics of the scanner implementation
 */
data class PerformanceProfile(
    val hasNativeAcceleration: Boolean,
    val expectedThroughputMBps: Int,
    val algorithmType: String
)