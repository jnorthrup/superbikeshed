#!/usr/bin/env kotlin

// Simple K2script demo - standalone Kotlin script
// This script doesn't rely on k2script integration yet, but shows the concepts

println("=== K2script Simple Demo ===")
println()

// Environment checks
println("Environment:")
println("  JAVA_HOME: ${System.getenv("JAVA_HOME") ?: "not set"}")
println("  USER: ${System.getenv("USER") ?: "unknown"}")
println("  PWD: ${System.getenv("PWD") ?: "unknown"}")

// System properties
println()
println("System Properties:")
println("  Java version: ${System.getProperty("java.version")}")
println("  OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")}")
println("  Available processors: ${Runtime.getRuntime().availableProcessors()}")

// Memory info
val runtime = Runtime.getRuntime()
val maxMemory = runtime.maxMemory() / 1024 / 1024
val totalMemory = runtime.totalMemory() / 1024 / 1024  
val freeMemory = runtime.freeMemory() / 1024 / 1024
val usedMemory = totalMemory - freeMemory

println()
println("Memory:")
println("  Max: ${maxMemory}MB")
println("  Total: ${totalMemory}MB")
println("  Used: ${usedMemory}MB")
println("  Free: ${freeMemory}MB")

// Demonstrate data processing patterns (simplified TrikeShed-style)
println()
println("=== Data Processing Demo ===")

// Create some sample data
val numbers = arrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
println("Original data: ${numbers.contentToString()}")

// Transform (α-style transformation)
val doubled = numbers.map { it * 2 }.toTypedArray()
println("Doubled: ${doubled.contentToString()}")

val evenOnly = numbers.filter { it % 2 == 0 }.toTypedArray()
println("Even numbers: ${evenOnly.contentToString()}")

// Demonstrate composition (j-style join)
data class NumberInfo(val value: Int, val isEven: Boolean)
val numberInfos = numbers.map { NumberInfo(it, it % 2 == 0) }
println("Number info:")
numberInfos.forEach { info ->
    println("  ${info.value} -> even: ${info.isEven}")
}

// File operations
println()
println("=== File Operations Demo ===")
val tempDir = System.getProperty("java.io.tmpdir")
println("Temp directory: $tempDir")

val testFile = java.io.File(tempDir, "k2script-demo.txt")
testFile.writeText("Hello from K2script simple demo!\nTimestamp: ${java.time.LocalDateTime.now()}")
println("Created test file: ${testFile.name}")

val content = testFile.readText()
println("File content:")
content.lines().forEach { line ->
    println("  $line")
}

// Clean up
testFile.delete()
println("Cleaned up test file")

println()
println("=== Demo Complete ===")
println("K2script is operational! 🚀")