package k2script.launcher

import java.io.File

/**
 * Main entry point for k2script using surrogate Maven launcher
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("K2Script - Kotlin Script Runner with kscript compatibility")
        println("Usage: k2script <script.kts> [args...]")
        println()
        println("Features:")
        println("  - Automatic dependency resolution via @file:DependsOn")
        println("  - Compiler plugin auto-detection (e.g., serialization)")
        println("  - Script caching for fast re-runs")
        println("  - Maven-based execution for reliability")
        return
    }
    
    val scriptFile = File(args[0])
    if (!scriptFile.exists()) {
        System.err.println("Error: Script not found: ${args[0]}")
        System.exit(1)
    }
    
    val scriptArgs = args.drop(1).toTypedArray()
    
    // Use coroutines runBlocking to execute
    kotlinx.coroutines.runBlocking {
        try {
            val exitCode = SurrogateMavenLauncher().runScript(scriptFile, scriptArgs)
            System.exit(exitCode)
        } catch (e: Exception) {
            System.err.println("Script execution failed: ${e.message}")
            e.printStackTrace()
            System.exit(1)
        }
    }
}