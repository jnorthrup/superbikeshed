package borg.trikeshed.strace

/**
 * Enum representing different categories of system calls for attention analysis
 */
enum class SyscallCategory(val syscalls: Set<String>, val attentionWeight: Double) {
    DATA_ACQUISITION(
        setOf("read", "recv", "pread", "preadv", "readv", "recvfrom", "recvmsg"),
        0.8
    ),
    NETWORK_OPERATIONS(
        setOf("socket", "connect", "accept", "send", "recv", "bind", "listen", "sendto", "sendmsg"),
        0.7
    ),
    FILE_OPERATIONS(
        setOf("open", "read", "write", "close", "stat", "fstat", "lstat", "fopen", "fclose"),
        0.6
    ),
    PROCESS_OPERATIONS(
        setOf("fork", "exec", "wait", "exit", "kill", "signal", "clone", "vfork"),
        0.5
    ),
    MEMORY_OPERATIONS(
        setOf("mmap", "munmap", "brk", "mprotect", "mlock", "munlock"),
        0.4
    ),
    SECURITY_OPERATIONS(
        setOf("chmod", "chown", "setuid", "setgid", "capset", "seccomp"),
        0.9
    ),
    IPC_OPERATIONS(
        setOf("pipe", "fifo", "shmget", "msgget", "semget"),
        0.3
    ),
    TIMER_OPERATIONS(
        setOf("alarm", "setitimer", "timer_create", "timer_settime"),
        0.2
    ),
    UNKNOWN(
        emptySet(),
        0.1
    );

    companion object {
        fun fromSyscall(syscall: String): SyscallCategory {
            return values().find { syscall in it.syscalls } ?: UNKNOWN
        }
    }
}

/**
 * Enum representing attention levels for LLM analysis
 */
enum class AttentionLevel(val threshold: Double, val description: String) {
    CRITICAL(0.9, "Critical system calls requiring immediate attention"),
    HIGH(0.7, "High priority system calls with significant impact"),
    MEDIUM(0.5, "Medium priority system calls with moderate impact"),
    LOW(0.3, "Low priority system calls with minimal impact"),
    MINIMAL(0.1, "Minimal attention required"),
    IGNORE(0.0, "System calls that can be ignored");

    companion object {
        fun fromWeight(weight: Double): AttentionLevel {
            return values().find { weight >= it.threshold } ?: IGNORE
        }
    }
}

/**
 * Enum representing data acquisition patterns
 */
enum class DataPattern(val pattern: String, val confidence: Double) {
    FILE_READ("Sequential file reading", 0.8),
    FILE_WRITE("Sequential file writing", 0.8),
    NETWORK_STREAM("Network data streaming", 0.9),
    MEMORY_MAPPING("Memory-mapped file access", 0.7),
    BUFFERED_IO("Buffered I/O operations", 0.6),
    DIRECT_IO("Direct I/O operations", 0.9),
    RANDOM_ACCESS("Random access patterns", 0.5),
    BATCH_OPERATIONS("Batch processing operations", 0.7),
    REAL_TIME_STREAM("Real-time data streaming", 0.9),
    ENCRYPTED_IO("Encrypted I/O operations", 0.8),
    COMPRESSED_IO("Compressed I/O operations", 0.7),
    UNKNOWN_PATTERN("Unknown data pattern", 0.1);

    companion object {
        fun detectPattern(syscalls: List<String>, frequencies: Map<String, Int>): DataPattern {
            val readCount = frequencies["read"] ?: 0
            val writeCount = frequencies["write"] ?: 0
            val socketCount = frequencies["socket"] ?: 0
            val mmapCount = frequencies["mmap"] ?: 0
            
            return when {
                socketCount > 5 && readCount > 10 -> NETWORK_STREAM
                readCount > 20 && writeCount < 5 -> FILE_READ
                writeCount > 20 && readCount < 5 -> FILE_WRITE
                mmapCount > 3 -> MEMORY_MAPPING
                readCount > 50 || writeCount > 50 -> BATCH_OPERATIONS
                else -> UNKNOWN_PATTERN
            }
        }
    }
}

/**
 * Enum representing strace output formats
 */
enum class StraceFormat(val flags: List<String>, val description: String) {
    TIMESTAMP(listOf("-t"), "Include timestamps"),
    DURATION(listOf("-T"), "Include syscall duration"),
    FORK_FOLLOW(listOf("-f"), "Follow child processes"),
    VERBOSE(listOf("-v"), "Verbose output"),
    QUIET(listOf("-q"), "Quiet mode"),
    SIGNAL_INFO(listOf("-i"), "Show signal information"),
    ADDRESS_INFO(listOf("-a"), "Show address information"),
    COMPACT(listOf("-c"), "Compact output format"),
    SUMMARY(listOf("-S"), "Summary statistics"),
    CUSTOM_FILTER(listOf("-e"), "Custom syscall filter");

    companion object {
        fun buildStraceCommand(config: StraceConfig): List<String> {
            val flags = mutableListOf<String>()
            
            if (config.showTimestamps) flags.addAll(TIMESTAMP.flags)
            if (config.followForks) flags.addAll(FORK_FOLLOW.flags)
            
            return flags
        }
    }
}

/**
 * Enum representing analysis modes for the strace attention system
 */
enum class AnalysisMode(val description: String, val requiresRealTime: Boolean) {
    REALTIME_MONITORING("Real-time system call monitoring", true),
    POST_PROCESSING("Post-execution analysis", false),
    PATTERN_DETECTION("Pattern detection and classification", false),
    ATTENTION_ANALYSIS("Attention weight calculation", false),
    LLM_INTEGRATION("LLM prompt generation", false),
    DATA_ACQUISITION_TRACKING("Data acquisition pattern tracking", true),
    SECURITY_AUDIT("Security-focused analysis", false),
    PERFORMANCE_PROFILING("Performance profiling", false);

    companion object {
        fun getDefaultModes(): Set<AnalysisMode> = setOf(
            REALTIME_MONITORING,
            PATTERN_DETECTION,
            ATTENTION_ANALYSIS,
            LLM_INTEGRATION
        )
    }
}

/**
 * Enum representing error types in strace analysis
 */
enum class StraceError(val code: String, val description: String, val severity: ErrorSeverity) {
    PERMISSION_DENIED("EACCES", "Permission denied", ErrorSeverity.HIGH),
    FILE_NOT_FOUND("ENOENT", "File or directory not found", ErrorSeverity.MEDIUM),
    INTERRUPTED("EINTR", "System call interrupted", ErrorSeverity.LOW),
    TIMEOUT("ETIMEDOUT", "Operation timed out", ErrorSeverity.MEDIUM),
    CONNECTION_REFUSED("ECONNREFUSED", "Connection refused", ErrorSeverity.HIGH),
    RESOURCE_BUSY("EBUSY", "Resource busy", ErrorSeverity.MEDIUM),
    INVALID_ARGUMENT("EINVAL", "Invalid argument", ErrorSeverity.HIGH),
    OUT_OF_MEMORY("ENOMEM", "Out of memory", ErrorSeverity.CRITICAL),
    QUOTA_EXCEEDED("EDQUOT", "Disk quota exceeded", ErrorSeverity.MEDIUM),
    UNKNOWN_ERROR("UNKNOWN", "Unknown error", ErrorSeverity.LOW);

    companion object {
        fun fromErrno(errno: Int): StraceError {
            return when (errno) {
                1 -> PERMISSION_DENIED
                2 -> FILE_NOT_FOUND
                4 -> INTERRUPTED
                110 -> TIMEOUT
                111 -> CONNECTION_REFUSED
                16 -> RESOURCE_BUSY
                22 -> INVALID_ARGUMENT
                12 -> OUT_OF_MEMORY
                122 -> QUOTA_EXCEEDED
                else -> UNKNOWN_ERROR
            }
        }
    }
}

/**
 * Enum representing error severity levels
 */
enum class ErrorSeverity(val weight: Double, val description: String) {
    CRITICAL(1.0, "Critical error requiring immediate attention"),
    HIGH(0.8, "High severity error with significant impact"),
    MEDIUM(0.5, "Medium severity error with moderate impact"),
    LOW(0.2, "Low severity error with minimal impact"),
    INFO(0.0, "Informational message");

    companion object {
        fun fromWeight(weight: Double): ErrorSeverity {
            return when {
                weight >= 0.8 -> CRITICAL
                weight >= 0.6 -> HIGH
                weight >= 0.3 -> MEDIUM
                weight >= 0.1 -> LOW
                else -> INFO
            }
        }
    }
} 