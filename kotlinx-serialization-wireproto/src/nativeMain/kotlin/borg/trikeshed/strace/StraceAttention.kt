package borg.trikeshed.strace

import kotlinx.coroutines.flow.*
import kotlinx.cinterop.*
import platform.posix.*
import platform.linux.*

actual class StraceAttention {
    
    private val syscallMap = mutableMapOf<String, Int>()
    private val eventBuffer = mutableListOf<SyscallEvent>()
    
    init {
        // Initialize syscall number mappings for common syscalls
        syscallMap["read"] = SYS_read
        syscallMap["write"] = SYS_write
        syscallMap["open"] = SYS_open
        syscallMap["close"] = SYS_close
        syscallMap["socket"] = SYS_socket
        syscallMap["connect"] = SYS_connect
        syscallMap["accept"] = SYS_accept
        syscallMap["fork"] = SYS_fork
        syscallMap["execve"] = SYS_execve
        syscallMap["stat"] = SYS_stat
        syscallMap["fstat"] = SYS_fstat
        syscallMap["lstat"] = SYS_lstat
        syscallMap["mmap"] = SYS_mmap
        syscallMap["munmap"] = SYS_munmap
        syscallMap["brk"] = SYS_brk
        syscallMap["mprotect"] = SYS_mprotect
        syscallMap["chmod"] = SYS_chmod
        syscallMap["chown"] = SYS_chown
        syscallMap["setuid"] = SYS_setuid
        syscallMap["setgid"] = SYS_setgid
    }
    
    actual suspend fun execWithStrace(
        command: String,
        args: List<String>,
        config: StraceConfig
    ): Flow<SyscallEvent> = flow {
        val fullArgs = listOf(command) + args
        val argv = fullArgs.map { it.cstr }.toCStringArray()
        
        try {
            // Create pipe for communication with strace
            val pipeFds = IntArray(2)
            if (pipe(pipeFds) == -1) {
                throw RuntimeException("Failed to create pipe: ${strerror(errno)?.toKString()}")
            }
            
            val pid = fork()
            when (pid) {
                -1 -> throw RuntimeException("Failed to fork: ${strerror(errno)?.toKString()}")
                0 -> {
                    // Child process - execute strace
                    close(pipeFds[0]) // Close read end
                    
                    // Redirect stdout to pipe
                    if (dup2(pipeFds[1], STDOUT_FILENO) == -1) {
                        exit(1)
                    }
                    
                    // Execute strace with the command
                    val straceArgs = mutableListOf("strace", "-f", "-t", "-T", "-e", "trace=all")
                    if (config.followForks) straceArgs.add("-f")
                    if (config.showTimestamps) straceArgs.add("-t")
                    straceArgs.addAll(fullArgs)
                    
                    val straceArgv = straceArgs.map { it.cstr }.toCStringArray()
                    execvp("strace", straceArgv)
                    exit(1) // Should not reach here
                }
                else -> {
                    // Parent process - read strace output
                    close(pipeFds[1]) // Close write end
                    
                    val buffer = ByteArray(4096)
                    val file = fdopen(pipeFds[0], "r")
                    
                    try {
                        while (true) {
                            val bytesRead = fread(buffer.refTo(0), 1, buffer.size.toULong(), file)
                            if (bytesRead == 0UL) break
                            
                            val output = buffer.take(bytesRead.toInt()).map { it.toChar() }.joinToString("")
                            val events = parseStraceOutput(output)
                            events.forEach { emit(it) }
                        }
                    } finally {
                        fclose(file)
                        close(pipeFds[0])
                    }
                }
            }
        } finally {
            argv.forEach { it.decRef() }
        }
    }
    
    actual suspend fun analyzeAttention(
        events: Flow<SyscallEvent>,
        context: String
    ): Flow<AttentionWeight> = flow {
        val eventList = events.toList()
        val syscallStats = eventList.groupBy { it.syscall }.mapValues { (_, events) ->
            val frequency = events.size
            val totalDataSize = events.sumOf { it.args.getOrNull(2)?.toLongOrNull() ?: 0L }
            val errorRate = events.count { it.error != null }.toDouble() / frequency
            Triple(frequency, totalDataSize, errorRate)
        }
        
        syscallStats.forEach { (syscall, stats) ->
            val (frequency, dataSize, errorRate) = stats
            val weight = StraceUtils.calculateAttentionWeight(syscall, frequency, dataSize, errorRate)
            
            if (weight >= 0.1) { // attentionThreshold
                val reason = when {
                    StraceUtils.SyscallCategories.DATA_ACQUISITION.contains(syscall) -> "High data acquisition activity"
                    StraceUtils.SyscallCategories.NETWORK_OPERATIONS.contains(syscall) -> "Network communication detected"
                    StraceUtils.SyscallCategories.FILE_OPERATIONS.contains(syscall) -> "File system operations"
                    StraceUtils.SyscallCategories.PROCESS_OPERATIONS.contains(syscall) -> "Process management activity"
                    else -> "System call with significant activity"
                }
                
                emit(AttentionWeight(syscall, weight, reason))
            }
        }
    }
    
    actual suspend fun extractDataAcquisition(
        events: Flow<SyscallEvent>
    ): Flow<DataAcquisitionPattern> = flow {
        val eventList = events.toList()
        
        // Group by patterns
        val readPatterns = eventList.filter { it.syscall in StraceUtils.SyscallCategories.DATA_ACQUISITION }
            .groupBy { it.args.getOrNull(0)?.toIntOrNull() ?: 0 }
        
        readPatterns.forEach { (fd, fdEvents) ->
            val frequency = fdEvents.size
            val totalDataSize = fdEvents.sumOf { 
                it.args.getOrNull(2)?.toLongOrNull() ?: 0L 
            }
            
            if (frequency > 0 && totalDataSize > 0) {
                emit(DataAcquisitionPattern(
                    pattern = "file_descriptor_$fd",
                    frequency = frequency,
                    files = emptyList(), // Would need to track file paths
                    networkConnections = emptyList(), // Would need to track network info
                    dataSize = totalDataSize,
                    confidence = minOf(frequency / 10.0, 1.0)
                ))
            }
        }
        
        // Network data acquisition patterns
        val networkPatterns = eventList.filter { 
            it.syscall in setOf("recv", "recvfrom", "recvmsg") 
        }.groupBy { it.args.getOrNull(0)?.toIntOrNull() ?: 0 }
        
        networkPatterns.forEach { (fd, fdEvents) ->
            val frequency = fdEvents.size
            val totalDataSize = fdEvents.sumOf { 
                it.args.getOrNull(2)?.toLongOrNull() ?: 0L 
            }
            
            if (frequency > 0 && totalDataSize > 0) {
                emit(DataAcquisitionPattern(
                    pattern = "network_socket_$fd",
                    frequency = frequency,
                    files = emptyList(),
                    networkConnections = listOf("socket_$fd"),
                    dataSize = totalDataSize,
                    confidence = minOf(frequency / 5.0, 1.0)
                ))
            }
        }
    }
    
    actual suspend fun generateLLMPrompt(
        events: Flow<SyscallEvent>,
        attentionWeights: Flow<AttentionWeight>
    ): String {
        val eventList = events.toList()
        val weightList = attentionWeights.toList()
        return StraceUtils.generatePrompt(eventList, weightList)
    }
    
    actual suspend fun monitorWithAttention(
        command: String,
        args: List<String>,
        config: StraceConfig
    ): Flow<StraceAnalysis> = flow {
        val events = execWithStrace(command, args, config).toList()
        val attentionWeights = analyzeAttention(events.asFlow(), "").toList()
        val dataPatterns = extractDataAcquisition(events.asFlow()).toList()
        val llmPrompt = generateLLMPrompt(events.asFlow(), attentionWeights.asFlow())
        
        val summary = buildString {
            appendLine("Strace Analysis Summary:")
            appendLine("Total events: ${events.size}")
            appendLine("High attention syscalls: ${attentionWeights.size}")
            appendLine("Data patterns detected: ${dataPatterns.size}")
            appendLine("Total data size: ${dataPatterns.sumOf { it.dataSize }} bytes")
        }
        
        emit(StraceAnalysis(events, attentionWeights, dataPatterns, llmPrompt, summary))
    }
    
    private fun parseStraceOutput(output: String): List<SyscallEvent> {
        val events = mutableListOf<SyscallEvent>()
        val lines = output.split("\n")
        
        for (line in lines) {
            if (line.isBlank()) continue
            
            // Parse strace output format: [timestamp] pid syscall(args) = result
            val regex = Regex("""\[(\d+\.\d+)\] (\d+) (\w+)\((.*?)\) = (-?\d+)(?: <(\d+\.\d+)>)?(?: (.*))?""")
            val match = regex.find(line)
            
            if (match != null) {
                val (timestamp, pid, syscall, argsStr, result, duration, error) = match.destructured
                
                val args = argsStr.split(",").map { it.trim() }
                val event = SyscallEvent(
                    timestamp = (timestamp.toDouble() * 1000).toLong(),
                    pid = pid.toInt(),
                    syscall = syscall,
                    args = args,
                    result = result.toLong(),
                    duration = (duration.toDoubleOrNull() ?: 0.0) * 1000L,
                    error = if (error.isNotBlank()) error else null
                )
                events.add(event)
            }
        }
        
        return events
    }
}

// Extension function to convert List<String> to C string array
private fun List<String>.toCStringArray(): Array<CPointer<ByteVar>> {
    return map { it.cstr }.toTypedArray()
} 