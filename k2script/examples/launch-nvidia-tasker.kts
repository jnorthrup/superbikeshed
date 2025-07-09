#!/usr/bin/env kotlin

/**
 * NVIDIA Tasker Launcher - Simple k2script wrapper
 * 
 * This script provides a robust way to launch the NVIDIA tasker
 * even when the full k2script build system has issues.
 * 
 * Usage:
 *   ./k2script examples/launch-nvidia-tasker.kts <arguments...>
 *   ./k2script examples/launch-nvidia-tasker.kts "Explain Kotlin coroutines"
 *   ./k2script examples/launch-nvidia-tasker.kts --edit
 */

import java.io.File
import java.nio.file.Paths

fun main(args: Array<String>) {
    println("🚀 NVIDIA Tasker Launcher")
    println("==========================")
    
    // Find the nvidia-tasker.main.kts file
    val projectRoot = findProjectRoot()
    val nvidiaTaskerPath = File(projectRoot, "nexus/nvidia-tasker.main.kts")
    
    if (!nvidiaTaskerPath.exists()) {
        println("❌ Error: nvidia-tasker.main.kts not found at: ${nvidiaTaskerPath.absolutePath}")
        println("   Expected location: nexus/nvidia-tasker.main.kts")
        System.exit(1)
    }
    
    println("✅ Found NVIDIA tasker at: ${nvidiaTaskerPath.absolutePath}")
    
    // Check if API keys are configured
    val apiKeys = listOf(
        System.getenv("NVIDIA_API_KEY"),
        System.getenv("NVIDIA_API_KEY_2"), 
        System.getenv("NVIDIA_API_KEY_3")
    ).filterNotNull().filter { it.isNotBlank() }
    
    if (apiKeys.isEmpty()) {
        println("⚠️  Warning: No NVIDIA API keys found in environment")
        println("   Set one of: NVIDIA_API_KEY, NVIDIA_API_KEY_2, NVIDIA_API_KEY_3")
        println("   Continuing anyway - tasker may fail if keys are required...")
    } else {
        println("✅ Found ${apiKeys.size} API key(s)")
    }
    
    // Build the command to run the tasker
    val command = buildList {
        add("kotlin")
        add(nvidiaTaskerPath.absolutePath)
        addAll(args.toList())
    }
    
    println("\n🎯 Launching NVIDIA tasker with args: ${args.joinToString(" ")}")
    println("📋 Command: ${command.joinToString(" ")}")
    println()
    
    // Execute the tasker
    try {
        val processBuilder = ProcessBuilder(command)
        processBuilder.inheritIO() // Connect stdin/stdout/stderr
        
        val process = processBuilder.start()
        val exitCode = process.waitFor()
        
        if (exitCode != 0) {
            println("\n❌ NVIDIA tasker exited with code: $exitCode")
            System.exit(exitCode)
        }
    } catch (e: Exception) {
        println("❌ Failed to launch NVIDIA tasker: ${e.message}")
        e.printStackTrace()
        System.exit(1)
    }
}

/**
 * Find the project root directory by looking for common project files
 */
fun findProjectRoot(): String {
    var current = Paths.get("").toAbsolutePath()
    
    while (current != current.parent) {
        // Check for project root indicators
        val gradleFile = current.resolve("build.gradle.kts")
        val nexusDir = current.resolve("nexus")
        val k2scriptDir = current.resolve("k2script")
        
        if (gradleFile.toFile().exists() && 
            nexusDir.toFile().exists() && 
            k2scriptDir.toFile().exists()) {
            return current.toString()
        }
        
        current = current.parent
    }
    
    // Fallback to current directory
    return Paths.get("").toAbsolutePath().toString()
} 