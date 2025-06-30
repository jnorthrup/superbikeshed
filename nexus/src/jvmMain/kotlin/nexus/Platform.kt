package nexus

import nexus.pure.*
import kotlinx.coroutines.runBlocking

actual fun getPlatformName(): String = "JVM"

actual suspend fun runInteractivePlatform() {
    // Use the pure functional interactive system
    PureFunctionalNexus.main(arrayOf())
}

// Main entry point
fun main(args: Array<String>) {
    println("Starting Nexus Interactive LLM on ${getPlatformName()}")
    
    runBlocking {
        PureFunctionalNexus.main(args)
    }
}

// JVM-specific optimizations and configurations
object JvmNexusConfig {
    
    fun optimizedProduction(): NexusInteractive {
        val handler = handler {
            base(IOHandler())
            withLogging { message -> 
                System.err.println("[${java.time.LocalDateTime.now()}] $message")
            }
            withRetry(3, 1000L)
            withCircuitBreaker(5, 60000L)
            withRateLimit(1000) // Higher rate limit for JVM
        }
        
        return NexusInteractive(handler)
    }
    
    fun withFileLogging(logFile: String): NexusInteractive {
        val logWriter = java.io.FileWriter(logFile, true)
        
        val (handlerWithMetrics, metricsHandler) = handler {
            base(IOHandler())
            withLogging { message ->
                logWriter.write("[${java.time.LocalDateTime.now()}] $message\n")
                logWriter.flush()
            }
            withRetry(3)
        }.withMetrics()
        
        return NexusInteractive(handlerWithMetrics)
    }
    
    fun withCustomTools(tools: List<ToolDefinition<*, *>>): NexusInteractive {
        val interactive = NexusInteractive(CommonHandlers.development)
        val registry = interactive.getRegistry()
        
        tools.forEach { tool ->
            registry.register(tool)
        }
        
        return interactive
    }
}

// Example JVM-specific tools
object JvmTools {
    
    val javaVersion = tool<Unit, String> {
        spec {
            id("java-version")
            name("Java Version")
            description("Get the current Java version")
            inputType(TypeInfo.CustomType("unit") { Option.some(Unit) })
            outputType(TypeInfo.StringType())
            example(Example("basic", Unit, "17.0.1"))
        }
        pure { System.getProperty("java.version") }
    }
    
    val systemProperty = tool<String, String?> {
        spec {
            id("system-property")
            name("System Property")
            description("Get a system property value")
            inputType(TypeInfo.StringType())
            outputType(TypeInfo.StringType())
            example(Example("java-home", "java.home", "/usr/lib/jvm/java-17"))
        }
        pure { key -> System.getProperty(key) }
    }
    
    val memoryInfo = tool<Unit, String> {
        spec {
            id("memory-info")
            name("Memory Info")
            description("Get JVM memory information")
            inputType(TypeInfo.CustomType("unit") { Option.some(Unit) })
            outputType(TypeInfo.StringType())
            example(Example("basic", Unit, "Used: 128MB, Free: 384MB, Total: 512MB"))
        }
        pure { 
            val runtime = Runtime.getRuntime()
            val total = runtime.totalMemory() / 1024 / 1024
            val free = runtime.freeMemory() / 1024 / 1024
            val used = total - free
            "Used: ${used}MB, Free: ${free}MB, Total: ${total}MB"
        }
    }
    
    val executeCommand = tool<String, String> {
        spec {
            id("execute-command")
            name("Execute Command")
            description("Execute a system command")
            inputType(TypeInfo.StringType())
            outputType(TypeInfo.StringType())
            constraint(Constraint.Custom("safe-command") { input ->
                val cmd = input.toString()
                // Basic safety check - only allow safe commands
                cmd.startsWith("ls") || cmd.startsWith("pwd") || cmd.startsWith("echo")
            })
            example(Example("list-files", "ls -la", "total 16\ndrwxr-xr-x ..."))
        }
        effect { command ->
            try {
                val process = ProcessBuilder(*command.split(" ").toTypedArray())
                    .redirectErrorStream(true)
                    .start()
                
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                
                ret(output)
            } catch (e: Exception) {
                fail("Command execution failed: ${e.message}")
            }
        }
    }
}

// Enhanced JVM interactive session
class JvmNexusInteractive : NexusInteractive(CommonHandlers.development) {
    
    init {
        // Register JVM-specific tools
        getRegistry().apply {
            register(JvmTools.javaVersion)
            register(JvmTools.systemProperty)
            register(JvmTools.memoryInfo)
            register(JvmTools.executeCommand)
        }
    }
    
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runBlocking {
                val interactive = JvmNexusInteractive()
                interactive.start()
            }
        }
    }
}