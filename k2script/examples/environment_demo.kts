#!/usr/bin/env k2script

// Environment and system integration demo

println("=== Environment Demo ===", AnsiColor.CYAN)

// Script information
println("Script file: ${env("K2SCRIPT_FILE")}")
println("K2script home: ${env("K2SCRIPT_HOME") ?: "auto-detected"}")

// Java environment
println("\n=== Java Environment ===")
println("Java version: ${System.getProperty("java.version")}")
println("Java home: ${System.getProperty("java.home")}")
println("Working directory: ${System.getProperty("user.dir")}")

// Custom environment variables
println("\n=== Custom Environment ===")
val customVars = listOf(
    "K2SCRIPT_LOG_LEVEL" to EnvironmentManager.K2Script.getLogLevel(),
    "K2SCRIPT_VERBOSE" to EnvironmentManager.K2Script.isVerbose().toString(),
    "JAVA_OPTS" to EnvironmentManager.Java.getOpts(),
    "PYTHON_CMD" to EnvironmentManager.Python.getCommand()
)

customVars.forEach { (name, value) ->
    println("$name = $value")
}

// File system operations with context
println("\n=== File System Demo ===")
val tempDir = File(System.getProperty("java.io.tmpdir"))
println("Temp directory: ${tempDir.absolutePath}")

val testFile = File(tempDir, "k2script-test.txt")
testFile.writeText("Hello from K2script! Timestamp: ${System.currentTimeMillis()}")
println("Created test file: ${testFile.name}")

val content = testFile.readText()
println("File content: $content")

// Clean up
testFile.delete()
println("Cleaned up test file")

// Process execution example
println("\n=== Process Demo ===")
try {
    val result = ProcessBuilder("echo", "Hello from subprocess!")
        .start()
        .inputStream
        .bufferedReader()
        .readText()
        .trim()
    
    println("Subprocess output: $result")
} catch (e: Exception) {
    log.error("Process execution failed", e)
}

// System information
println("\n=== System Information ===")
val runtime = Runtime.getRuntime()
println("Available processors: ${runtime.availableProcessors()}")
println("Max memory: ${runtime.maxMemory() / 1024 / 1024} MB")
println("Total memory: ${runtime.totalMemory() / 1024 / 1024} MB")
println("Free memory: ${runtime.freeMemory() / 1024 / 1024} MB")

// Network check (basic)
println("\n=== Network Demo ===")
try {
    val address = java.net.InetAddress.getByName("google.com")
    println("Can resolve google.com: ✓")
} catch (e: Exception) {
    println("Cannot resolve google.com: ✗")
}

println("\nEnvironment demo complete! 🌍", AnsiColor.GREEN)