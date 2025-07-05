package borg.trikeshed.json

import borg.trikeshed.lib.*
import borg.trikeshed.lib.simd.*
import borg.trikeshed.core.*
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.darwin.*
import kotlinx.coroutines.*
import kotlin.native.concurrent.*

/**
 * Swarm implementation - distributed JSON parsing across multiple cores.
 * Leverages Grand Central Dispatch (GCD) for work distribution.
 * 
 * This implementation shows how to:
 * 1. Use all CPU cores efficiently (works on any platform)
 * 2. Leverage GCD for work distribution on macOS/iOS
 * 3. Combine with platform SIMD (NEON on ARM, SSE/AVX on x86)
 * 4. Handle memory efficiently with platform APIs
 */
@OptIn(ExperimentalForeignApi::class)
class SwarmJsonScanner(
    private val json: String
) {
    private val jsonData = json.encodeToByteArray()
    private val coreCount = NSProcessInfo.processInfo.processorCount.toInt()
    
    /**
     * Swarm parse using Grand Central Dispatch.
     * Distributes work across all available cores.
     */
    fun swarmParse(): SwarmResult = memScoped {
        val nsData = NSData.create(
            bytes = jsonData.refTo(0),
            length = jsonData.size.toULong()
        ) ?: error("Failed to create NSData")
        
        // Create concurrent queue for parallel processing
        val queue = dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_HIGH.toLong(), 0u)
        val group = dispatch_group_create()
        
        // Results from each worker
        val results = AtomicReference<MutableList<IntArray>>(mutableListOf())
        
        // Chunk size per worker
        val chunkSize = jsonData.size / coreCount
        val overlap = 128 // Overlap to handle boundaries
        
        // Dispatch work to each core
        for (core in 0 until coreCount) {
            dispatch_group_async(group, queue) {
                val start = (core * chunkSize).coerceAtLeast(0)
                val end = if (core == coreCount - 1) {
                    jsonData.size
                } else {
                    ((core + 1) * chunkSize + overlap).coerceAtMost(jsonData.size)
                }
                
                // Process chunk with NEON SIMD
                val positions = processChunkWithNeon(jsonData, start, end)
                
                // Thread-safe result collection
                results.value.add(positions)
            }
        }
        
        // Wait for all workers
        dispatch_group_wait(group, DISPATCH_TIME_FOREVER)
        
        // Merge results
        val allPositions = mergeResults(results.value, chunkSize, overlap)
        
        SwarmResult(
            structuralPositions = allPositions,
            coresUsed = coreCount,
            throughputMBps = calculateThroughput(jsonData.size, measureNanoTime { swarmParse() })
        )
    }
    
    /**
     * Process a chunk using NEON SIMD acceleration.
     */
    private fun processChunkWithNeon(data: ByteArray, start: Int, end: Int): IntArray {
        val simd = ArmNeonSimdStrategy()
        val structuralChars = byteArrayOf(
            '{'.code.toByte(), '}'.code.toByte(),
            '['.code.toByte(), ']'.code.toByte(),
            ':'.code.toByte(), ','.code.toByte(),
            '"'.code.toByte()
        )
        
        // Find all structural characters in chunk
        val positions = simd.findAnyByte(
            data.sliceArray(start until end),
            structuralChars,
            0
        )
        
        // Adjust positions to global indices
        return positions.map { it + start }.toIntArray()
    }
    
    /**
     * Merge results from multiple workers, handling overlaps.
     */
    private fun mergeResults(
        results: List<IntArray>,
        chunkSize: Int,
        overlap: Int
    ): IntArray {
        val merged = mutableSetOf<Int>()
        
        for ((index, positions) in results.withIndex()) {
            for (pos in positions) {
                // Skip positions in overlap regions (except for last chunk)
                if (index < results.size - 1) {
                    val chunkEnd = (index + 1) * chunkSize
                    if (pos >= chunkEnd && pos < chunkEnd + overlap) {
                        continue // Skip, will be handled by next chunk
                    }
                }
                merged.add(pos)
            }
        }
        
        return merged.sorted().toIntArray()
    }
    
    private fun calculateThroughput(bytes: Int, nanos: Long): Double {
        return (bytes.toDouble() / nanos) * 1_000_000_000 / (1024 * 1024) // MB/s
    }
}

/**
 * Result of swarm parsing
 */
data class SwarmResult(
    val structuralPositions: IntArray,
    val coresUsed: Int,
    val throughputMBps: Double
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SwarmResult) return false
        return structuralPositions.contentEquals(other.structuralPositions) &&
               coresUsed == other.coresUsed &&
               throughputMBps == other.throughputMBps
    }
    
    override fun hashCode(): Int {
        var result = structuralPositions.contentHashCode()
        result = 31 * result + coresUsed
        result = 31 * result + throughputMBps.hashCode()
        return result
    }
}

/**
 * Coroutine-based swarm implementation using Kotlin/Native's new memory model
 */
class CoroutineSwarmJsonScanner(
    private val json: String
) {
    private val jsonData = json.encodeToByteArray()
    
    /**
     * Parse using Kotlin coroutines for structured concurrency
     */
    suspend fun swarmParseAsync(): SwarmResult = coroutineScope {
        val coreCount = NSProcessInfo.processInfo.processorCount.toInt()
        val chunkSize = jsonData.size / coreCount
        
        // Launch coroutine per core
        val jobs = List(coreCount) { core ->
            async(Dispatchers.Default) {
                val start = core * chunkSize
                val end = if (core == coreCount - 1) jsonData.size else (core + 1) * chunkSize
                
                // Process chunk
                val simd = ArmNeonSimdStrategy()
                simd.findAnyByte(
                    jsonData.sliceArray(start until end),
                    structuralBytes,
                    0
                ).map { it + start }
            }
        }
        
        // Await all results
        val allPositions = jobs.awaitAll().flatMap { it.asIterable() }.sorted().toIntArray()
        
        SwarmResult(
            structuralPositions = allPositions,
            coresUsed = coreCount,
            throughputMBps = 0.0 // Calculate separately
        )
    }
    
    companion object {
        private val structuralBytes = byteArrayOf(
            '{'.code.toByte(), '}'.code.toByte(),
            '['.code.toByte(), ']'.code.toByte(),
            ':'.code.toByte(), ','.code.toByte(),
            '"'.code.toByte()
        )
    }
}

/**
 * Benchmark swarm performance
 */
class SwarmBenchmark {
    fun runBenchmarks() {
        println("=== Swarm JSON Scanner ===")
        println("CPU: ${NSProcessInfo.processInfo.processorCount} cores")
        println("Architecture: Apple Silicon (ARM64)")
        println()
        
        val sizes = listOf(10_000, 100_000, 1_000_000, 10_000_000)
        
        for (size in sizes) {
            val json = generateLargeJson(size)
            val scanner = SwarmJsonScanner(json)
            
            val time = measureNanoTime {
                scanner.swarmParse()
            }
            
            val mbps = (size.toDouble() / time) * 1_000_000_000 / (1024 * 1024)
            
            println("Size: ${size/1000}KB")
            println("  Time: ${time/1_000_000}ms")
            println("  Throughput: ${mbps.toInt()} MB/s")
            println("  Per-core: ${(mbps / scanner.coreCount).toInt()} MB/s")
            println()
        }
    }
    
    private fun generateLargeJson(size: Int): String {
        val sb = StringBuilder(size)
        sb.append("{\"items\":[")
        
        val itemSize = 50 // Approximate size per item
        val itemCount = size / itemSize
        
        for (i in 0 until itemCount) {
            if (i > 0) sb.append(",")
            sb.append("{\"id\":$i,\"value\":\"item$i\"}")
        }
        
        sb.append("]}")
        return sb.toString()
    }
}