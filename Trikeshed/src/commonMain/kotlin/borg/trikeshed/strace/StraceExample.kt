package borg.trikeshed.strace

import kotlinx.coroutines.flow.*

/**
 * Example usage of the strace attention system for LLM and data acquisition
 */
object StraceExample {
    
    /**
     * Example: Monitor a simple command with strace attention
     */
    suspend fun monitorSimpleCommand() {
        val strace = StraceAttention()
        
        // Monitor 'ls -la' command
        strace.monitorWithAttention(
            command = "ls",
            args = listOf("-la"),
            config = StraceConfig(
                followForks = true,
                showTimestamps = true,
                maxEvents = 1000,
                attentionThreshold = 0.3
            )
        ).collect { analysis ->
            println("=== Strace Analysis ===")
            println(analysis.summary)
            println()
            println("Top attention syscalls:")
            analysis.attentionWeights.take(5).forEach { weight ->
                println("- ${weight.syscall}: ${weight.weight} (${weight.reason})")
            }
            println()
            println("Data patterns:")
            analysis.dataPatterns.forEach { pattern ->
                println("- ${pattern.pattern}: ${pattern.dataSize} bytes, confidence: ${pattern.confidence}")
            }
            println()
            println("LLM Prompt:")
            println(analysis.llmPrompt)
        }
    }
    
    /**
     * Example: Analyze data acquisition patterns
     */
    suspend fun analyzeDataAcquisition() {
        val strace = StraceAttention()
        
        // Monitor a data-intensive command
        strace.execWithStrace(
            command = "curl",
            args = listOf("https://api.github.com/users/octocat"),
            config = StraceConfig(
                followForks = true,
                showTimestamps = true,
                filterSyscalls = setOf("read", "write", "socket", "connect", "recv", "send")
            )
        ).collect { event ->
            val category = SyscallCategory.fromSyscall(event.syscall)
            val attentionLevel = AttentionLevel.fromWeight(category.attentionWeight)
            
            println("[${event.timestamp}] ${event.pid} ${event.syscall} = ${event.result}")
            println("  Category: ${category.name} (weight: ${category.attentionWeight})")
            println("  Attention: ${attentionLevel.name} - ${attentionLevel.description}")
            
            if (event.error != null) {
                val error = StraceError.fromErrno(event.result.toInt())
                println("  Error: ${error.description} (${error.severity.description})")
            }
            println()
        }
    }
    
    /**
     * Example: Real-time monitoring with attention feedback
     */
    suspend fun realTimeMonitoring() {
        val strace = StraceAttention()
        
        // Monitor a long-running process
        strace.execWithStrace(
            command = "ping",
            args = listOf("-c", "5", "8.8.8.8"),
            config = StraceConfig(
                followForks = true,
                showTimestamps = true,
                maxEvents = 5000
            )
        ).buffer(10) // Buffer events for batch processing
        .collect { events ->
            val batch = events.toList()
            
            // Analyze batch for patterns
            val frequencies = batch.groupBy { it.syscall }.mapValues { it.value.size }
            val pattern = DataPattern.detectPattern(batch.map { it.syscall }, frequencies)
            
            println("=== Batch Analysis ===")
            println("Events in batch: ${batch.size}")
            println("Detected pattern: ${pattern.pattern} (confidence: ${pattern.confidence})")
            println("Top syscalls: ${frequencies.entries.sortedByDescending { it.value }.take(3)}")
            println()
        }
    }
    
    /**
     * Example: Generate LLM prompt from strace analysis
     */
    suspend fun generateLLMPrompt() {
        val strace = StraceAttention()
        
        // Collect events from a command
        val events = mutableListOf<SyscallEvent>()
        strace.execWithStrace(
            command = "wget",
            args = listOf("https://example.com/file.txt"),
            config = StraceConfig(showTimestamps = true)
        ).collect { event ->
            events.add(event)
        }
        
        // Analyze attention weights
        val attentionWeights = mutableListOf<AttentionWeight>()
        strace.analyzeAttention(events.asFlow(), "wget download").collect { weight ->
            attentionWeights.add(weight)
        }
        
        // Generate LLM prompt
        val prompt = strace.generateLLMPrompt(events.asFlow(), attentionWeights.asFlow())
        
        println("=== Generated LLM Prompt ===")
        println(prompt)
    }
    
    /**
     * Example: Security audit with strace
     */
    suspend fun securityAudit() {
        val strace = StraceAttention()
        
        strace.execWithStrace(
            command = "sudo",
            args = listOf("ls", "/root"),
            config = StraceConfig(
                followForks = true,
                showTimestamps = true,
                filterSyscalls = SyscallCategory.SECURITY_OPERATIONS.syscalls
            )
        ).collect { event ->
            val category = SyscallCategory.fromSyscall(event.syscall)
            
            if (category == SyscallCategory.SECURITY_OPERATIONS) {
                println("🚨 SECURITY EVENT: ${event.syscall}")
                println("  PID: ${event.pid}")
                println("  Args: ${event.args}")
                println("  Result: ${event.result}")
                println("  Attention Weight: ${category.attentionWeight}")
                println()
            }
        }
    }
    
    /**
     * Example: Performance profiling
     */
    suspend fun performanceProfiling() {
        val strace = StraceAttention()
        
        strace.execWithStrace(
            command = "dd",
            args = listOf("if=/dev/zero", "of=/tmp/test", "bs=1M", "count=10"),
            config = StraceConfig(
                showTimestamps = true,
                showSyscallNumbers = false
            )
        ).collect { event ->
            // Focus on I/O operations
            if (event.syscall in SyscallCategory.FILE_OPERATIONS.syscalls) {
                val duration = event.duration
                val dataSize = event.args.getOrNull(2)?.toLongOrNull() ?: 0L
                
                if (duration > 1000) { // Operations taking more than 1ms
                    println("🐌 SLOW I/O: ${event.syscall}")
                    println("  Duration: ${duration}ms")
                    println("  Data size: ${dataSize} bytes")
                    println("  Throughput: ${if (duration > 0) dataSize / duration else 0} bytes/ms")
                    println()
                }
            }
        }
    }
} 