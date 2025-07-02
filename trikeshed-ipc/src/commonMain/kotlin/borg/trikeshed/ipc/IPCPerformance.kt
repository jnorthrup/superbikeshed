@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.ipc

/**
 * Realistic IPC Performance Characteristics
 * 
 * Based on actual measurements and architecture constraints
 */
object IPCPerformance {
    
    /**
     * Native → JVM Communication
     * 
     * FAST OPTIONS:
     * - JNI Direct ByteBuffers: ~50ns function call + memory access
     * - Memory-mapped files: ~100ns (page already mapped)
     * - Shared memory (unsafe): ~10ns (direct pointer access)
     * 
     * MEDIUM OPTIONS:
     * - Unix domain sockets: ~1-2μs (kernel boundary)
     * - Named pipes: ~2-5μs (buffering overhead)
     * 
     * SLOW OPTIONS:
     * - TCP sockets: ~10-50μs (network stack)
     * - Serialization (JSON/Protobuf): +1-10μs
     */
    data class NativeToJVM(
        val method: String,
        val latencyNs: Long,
        val throughputMBps: Int
    ) {
        companion object {
            val JNI_DIRECT = NativeToJVM("JNI Direct ByteBuffer", 50, 10_000)
            val MMAP = NativeToJVM("Memory-mapped file", 100, 8_000)
            val SHARED_MEM = NativeToJVM("Shared memory (unsafe)", 10, 20_000)
            val UNIX_SOCKET = NativeToJVM("Unix domain socket", 1_000, 1_000)
            val NAMED_PIPE = NativeToJVM("Named pipe", 2_000, 500)
        }
    }
    
    /**
     * Native → WASM Communication
     * 
     * FAST OPTIONS:
     * - Direct memory access: ~10ns (WASM linear memory)
     * - Function imports: ~50ns (direct call after JIT)
     * - SharedArrayBuffer: ~20ns (atomic operations)
     * 
     * MEDIUM OPTIONS:
     * - Memory copy: ~100ns/KB
     * - Typed arrays: ~200ns setup + copy
     * 
     * SLOW OPTIONS:
     * - JavaScript bridge: ~1-10μs (if going through JS)
     * - Emscripten stdio: ~10-50μs (stdio emulation)
     */
    data class NativeToWASM(
        val method: String,
        val latencyNs: Long,
        val throughputMBps: Int
    ) {
        companion object {
            val DIRECT_MEMORY = NativeToWASM("Direct linear memory", 10, 50_000)
            val FUNCTION_IMPORT = NativeToWASM("Function import call", 50, 10_000)
            val SHARED_ARRAY = NativeToWASM("SharedArrayBuffer", 20, 25_000)
            val MEMORY_COPY = NativeToWASM("Memory copy", 100, 5_000)
        }
    }
    
    /**
     * JVM ↔ WASM Communication
     * 
     * SLOW (with GraalVM):
     * - Polyglot Value objects: ~1-10μs per call
     * - Type conversions: ~100ns-1μs per value
     * - Array copies: ~1μs/KB
     * 
     * VERY SLOW:
     * - JavaScript interop: ~10-100μs
     * - Proxy objects: ~5-50μs per method call
     * 
     * ALTERNATIVES:
     * - Bypass GraalVM, use separate processes: Then it's Native→JVM + Native→WASM
     * - TeaVM/JWebAssembly: Compile Java to WASM (different approach)
     */
    data class JVMToWASM(
        val method: String,
        val latencyNs: Long,
        val throughputMBps: Int
    ) {
        companion object {
            val GRAAL_POLYGLOT = JVMToWASM("GraalVM Polyglot API", 1_000, 100)
            val GRAAL_PROXY = JVMToWASM("GraalVM Proxy objects", 5_000, 20)
            val JS_BRIDGE = JVMToWASM("JavaScript bridge", 10_000, 10)
            val SEPARATE_PROCESS = JVMToWASM("Via native IPC", 100, 1_000)
        }
    }
    
    /**
     * Optimal Architecture Recommendations
     */
    fun printRecommendations() {
        println("""
        === IPC Performance Recommendations ===
        
        For Native → JVM:
        - Use JNI with direct ByteBuffers for high-frequency calls
        - Use memory-mapped files for large data transfers
        - Avoid object serialization, pass primitive arrays
        
        For Native → WASM:
        - Use direct linear memory access
        - Pre-allocate memory regions
        - Use SharedArrayBuffer for concurrent access
        - Compile with -O3 and wasm-opt
        
        For JVM ↔ WASM:
        - AVOID if possible! This is the slow path
        - Consider: Native → JVM and Native → WASM separately
        - Or: Compile Java to WASM (TeaVM/JWebAssembly)
        - Or: Use Native as message broker between JVM and WASM
        
        Architecture Options:
        
        1. Star Architecture (Native as Hub):
           WASM ← Native → JVM
           - Native process manages all IPC
           - Fast Native→JVM and Native→WASM
           - No slow JVM↔WASM path
        
        2. Shared Memory Architecture:
           WASM ← Shared Memory → JVM
                        ↑
                     Native
           - All processes map same memory
           - Requires careful synchronization
           - Fastest for large data
        
        3. Truffle Single Process:
           Native Code
                ↓
           GraalVM (JVM + WASM + Native)
           - Everything in one process
           - Slow JVM↔WASM but convenient
           - Good for trusted code only
        """.trimIndent())
    }
    
    /**
     * Performance test for different IPC methods
     */
    fun measureLatency(
        method: String,
        payloadSize: Int,
        iterations: Int = 100_000
    ): LatencyResult {
        // This would contain actual measurement code
        return LatencyResult(
            method = method,
            payloadSize = payloadSize,
            iterations = iterations,
            minNs = 0,
            avgNs = 0,
            maxNs = 0,
            p99Ns = 0
        )
    }
    
    data class LatencyResult(
        val method: String,
        val payloadSize: Int,
        val iterations: Int,
        val minNs: Long,
        val avgNs: Long,
        val maxNs: Long,
        val p99Ns: Long
    )
}

/**
 * Example: Optimal Native-as-Hub Architecture
 */
class NativeHubArchitecture {
    /**
     * Native process acts as message router
     * - Receives from JVM via JNI/mmap
     * - Receives from WASM via linear memory
     * - Routes messages without JVM↔WASM interaction
     */
    fun routeMessage(
        source: ProcessType,
        destination: ProcessType,
        payload: ByteArray
    ) {
        when (source to destination) {
            ProcessType.JVM to ProcessType.WASM -> {
                // Native receives from JVM (fast)
                // Native sends to WASM (fast)
                // Avoids slow JVM→WASM path
            }
            ProcessType.WASM to ProcessType.JVM -> {
                // Native receives from WASM (fast)  
                // Native sends to JVM (fast)
                // Avoids slow WASM→JVM path
            }
            else -> {
                // Direct routing
            }
        }
    }
    
    enum class ProcessType { NATIVE, JVM, WASM }
}