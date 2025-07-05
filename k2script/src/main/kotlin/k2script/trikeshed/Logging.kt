package k2script.trikeshed

import k2script.env.EnvironmentManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * TrikeShed logging with CCEK context integration
 * Zero-bloat logging that leverages context capture
 */
object Log {
    
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    
    @HotPath
    fun debug(message: () -> String) {
        if (EnvironmentManager.K2Script.getLogLevel() == "DEBUG") {
            println("[${timestamp()}] DEBUG: ${message()}")
        }
    }
    
    @HotPath  
    fun info(message: () -> String) {
        val level = EnvironmentManager.K2Script.getLogLevel()
        if (level in listOf("DEBUG", "INFO")) {
            println("[${timestamp()}] INFO: ${message()}")
        }
    }
    
    @HotPath
    fun warn(message: () -> String) {
        val level = EnvironmentManager.K2Script.getLogLevel()
        if (level in listOf("DEBUG", "INFO", "WARN")) {
            System.err.println("[${timestamp()}] WARN: ${message()}")
        }
    }
    
    @HotPath
    fun error(message: String, throwable: Throwable? = null) {
        System.err.println("[${timestamp()}] ERROR: $message")
        throwable?.let { it: Throwable ->
            if (EnvironmentManager.K2Script.isVerbose()) {
                throwable.printStackTrace()
            } else {
                System.err.println("  ${it.javaClass.simpleName}: ${it.message}")
            }
        }
    }
    
    @ColdPath
    fun context(ctx: Context): ContextualLogger = ContextualLogger(ctx)
    
    fun timestamp(): String = LocalDateTime.now().format(timeFormatter)
}

/**
 * Context-aware logging that captures execution context
 */
@kotlin.jvm.JvmInline
value class ContextualLogger(private val context: Context) {
    
    fun debug(message: String) {
        Log.debug { "[$context] $message" }
    }
    
    fun info(message: String) {
        Log.info { "[$context] $message" }
    }
    
    fun warn(message: String) {
        Log.warn { "[$context] $message" }
    }
    
    fun error(message: String, throwable: Throwable? = null) {
        Log.error("[$context] $message", throwable)
    }
}

/**
 * Performance monitoring with context capture
 */
@kotlin.jvm.JvmInline
value class PerfMonitor(val context: Context) {
    
    @HotPath
    fun <T> measure(operation: String, block: () -> T): T {
        val start = System.nanoTime()
        return try {
            block()
        } finally {
            val duration = (System.nanoTime() - start) / 1_000_000.0
            Log.debug { "[$context] $operation took ${duration}ms" }
        }
    }
    
    companion object {
        fun forContext(context: Context): PerfMonitor = PerfMonitor(context)
    }
}

/**
 * Resource management with automatic cleanup
 */
class ResourceManager(private val context: Context) {
    
    private val resources = mutableListOf<AutoCloseable>()
    
    fun <T : AutoCloseable> manage(resource: T): T {
        resources.add(resource)
        return resource
    }
    
    @ColdPath
    fun cleanup() {
        resources.asReversed().forEach { resource ->
            try {
                resource.close()
            } catch (e: Exception) {
                Log.error("Failed to close resource", e)
            }
        }
        resources.clear()
        context.cleanup()
    }
}

/**
 * Memory management with GC hints
 */
object Memory {
    
    @ColdPath
    fun suggest() {
        Log.debug { "Suggesting GC" }
        System.gc()
    }
    
    @ColdPath
    fun report(): String {
        val runtime = Runtime.getRuntime()
        val total = runtime.totalMemory()
        val free = runtime.freeMemory()
        val used = total - free
        val max = runtime.maxMemory()
        
        return "Memory: ${used / 1024 / 1024}MB used, ${free / 1024 / 1024}MB free, ${max / 1024 / 1024}MB max"
    }
    
    @HotPath
    fun <T> withCleanup(block: () -> T): T {
        return try {
            block()
        } finally {
            suggest()
        }
    }
}