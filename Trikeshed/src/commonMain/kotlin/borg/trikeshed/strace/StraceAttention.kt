package borg.trikeshed.strace

import kotlinx.coroutines.flow.Flow

/**
 * Data class representing a system call event captured by strace
 */
data class SyscallEvent(
    val timestamp: Long,
    val pid: Int,
    val syscall: String,
    val args: List<String>,
    val result: Long,
    val duration: Long,
    val error: String? = null
)

/**
 * Data class representing attention weights for LLM analysis
 */
data class AttentionWeight(
    val syscall: String,
    val weight: Double,
    val reason: String
)

/**
 * Configuration for strace execution
 */
data class StraceConfig(
    val followForks: Boolean = true,
    val showTimestamps: Boolean = true,
    val showSyscallNumbers: Boolean = false,
    val maxEvents: Int = 10000,
    val filterSyscalls: Set<String> = emptySet(),
    val attentionThreshold: Double = 0.1
)

/**
 * Data acquisition pattern detected from syscalls
 */
data class DataAcquisitionPattern(
    val pattern: String,
    val frequency: Int,
    val files: List<String>,
    val networkConnections: List<String>,
    val dataSize: Long,
    val confidence: Double
)

/**
 * Complete analysis result
 */
data class StraceAnalysis(
    val events: List<SyscallEvent>,
    val attentionWeights: List<AttentionWeight>,
    val dataPatterns: List<DataAcquisitionPattern>,
    val llmPrompt: String,
    val summary: String
)

/**
 * Minimal StraceAttention interface to fix missing references
 */
interface StraceAttention {
    val summary: String
    val attentionWeights: Map<String, Double>
    val dataPatterns: List<DataPattern>
    val llmPrompt: String
    
    suspend fun monitorWithAttention(command: String): Flow<StraceEvent>
    suspend fun execWithStrace(command: String): Flow<StraceEvent>
    suspend fun analyzeAttention(events: Flow<StraceEvent>): AttentionAnalysis
    suspend fun generateLLMPrompt(events: Flow<StraceEvent>): String
}

data class StraceEvent(
    val timestamp: Long,
    val pid: Int,
    val syscall: String,
    val args: List<String>,
    val result: String,
    val error: String?
)

data class StraceDataPattern(
    val pattern: String,
    val dataSize: Int,
    val confidence: Double
)

data class AttentionAnalysis(
    val summary: String,
    val attentionWeights: Map<String, Double>,
    val dataPatterns: List<DataPattern>
)

expect class StraceSummary(
    totalSyscalls: Int,
    uniqueSyscalls: Int,
    duration: Long,
    patterns: List<StraceDataPattern>
)

/**
 * Common utilities for strace analysis
 */
object StraceUtils {
    
    /**
     * Common syscall categories for attention analysis
     */
    object SyscallCategories {
        val FILE_OPERATIONS = setOf("open", "read", "write", "close", "stat", "fstat", "lstat")
        val NETWORK_OPERATIONS = setOf("socket", "connect", "accept", "send", "recv", "bind", "listen")
        val PROCESS_OPERATIONS = setOf("fork", "exec", "wait", "exit", "kill", "signal")
        val MEMORY_OPERATIONS = setOf("mmap", "munmap", "brk", "mprotect")
        val SECURITY_OPERATIONS = setOf("chmod", "chown", "setuid", "setgid", "capset")
        val DATA_ACQUISITION = setOf("read", "recv", "pread", "preadv", "readv")
    }
    
    /**
     * Calculate attention weight based on syscall characteristics
     */
    fun calculateAttentionWeight(
        syscall: String,
        frequency: Int,
        dataSize: Long,
        errorRate: Double
    ): Double {
        val baseWeight = when {
            SyscallCategories.DATA_ACQUISITION.contains(syscall) -> 0.8
            SyscallCategories.NETWORK_OPERATIONS.contains(syscall) -> 0.7
            SyscallCategories.FILE_OPERATIONS.contains(syscall) -> 0.6
            SyscallCategories.PROCESS_OPERATIONS.contains(syscall) -> 0.5
            else -> 0.3
        }
        
        val frequencyMultiplier = minOf(frequency / 10.0, 2.0)
        val dataSizeMultiplier = minOf(dataSize / 1024.0, 1.0)
        val errorPenalty = 1.0 - (errorRate * 0.5)
        
        return baseWeight * frequencyMultiplier * dataSizeMultiplier * errorPenalty
    }
    
    /**
     * Generate LLM prompt from strace events
     */
    fun generatePrompt(
        events: List<SyscallEvent>,
        attentionWeights: List<AttentionWeight>
    ): String {
        val topEvents = events.take(20)
        val topWeights = attentionWeights.sortedByDescending { it.weight }.take(10)
        
        return buildString {
            appendLine("Analyze the following system call trace for data acquisition patterns:")
            appendLine()
            appendLine("Top system calls by attention weight:")
            topWeights.forEach { weight ->
                appendLine("- ${weight.syscall}: ${weight.weight} (${weight.reason})")
            }
            appendLine()
            appendLine("Recent system call events:")
            topEvents.forEach { event ->
                appendLine("[${event.timestamp}] ${event.pid} ${event.syscall}(${event.args.joinToString(", ")}) = ${event.result}")
            }
            appendLine()
            appendLine("Please analyze:")
            appendLine("1. Data acquisition patterns")
            appendLine("2. Network communication")
            appendLine("3. File operations")
            appendLine("4. Security implications")
            appendLine("5. Performance characteristics")
        }
    }
} 