#!/usr/bin/env kotlin

import java.io.File
import kotlinx.coroutines.runBlocking

// Test script to verify the SurrogateMavenLauncher works with nvidia-tasker.main.kts

fun main(args: Array<String>) {
    println("Testing SurrogateMavenLauncher with nvidia-tasker.main.kts")
    println("=" * 60)
    
    val scriptPath = File("../nexus/nvidia-tasker.main.kts").absolutePath
    val kotlinPath = "k2script/src/jvmMain/kotlin/k2script/launcher/SurrogateMavenLauncher.kt"
    
    // First, let's compile the SurrogateMavenLauncher
    println("\n1. Compiling SurrogateMavenLauncher...")
    val compileResult = ProcessBuilder(
        "kotlinc",
        "-cp", "trikeshed-lib/build/classes/kotlin/jvm/main:k2script/build/classes/kotlin/jvm/main",
        kotlinPath,
        "-d", "k2script/build/classes/kotlin/jvm/test"
    ).inheritIO().start().waitFor()
    
    if (compileResult != 0) {
        println("Failed to compile SurrogateMavenLauncher")
        System.exit(1)
    }
    
    // Now run the launcher with nvidia-tasker.main.kts
    println("\n2. Running nvidia-tasker.main.kts through SurrogateMavenLauncher...")
    val runResult = ProcessBuilder(
        "kotlin",
        "-cp", "k2script/build/classes/kotlin/jvm/test:k2script/build/classes/kotlin/jvm/main:trikeshed-lib/build/classes/kotlin/jvm/main",
        "k2script.launcher.SurrogateMavenLauncherKt",
        scriptPath,
        "--", // Args separator
        "test query: what is 2+2?"
    ).inheritIO().start().waitFor()
    
    println("\nTest completed with exit code: $runResult")
}