package k2script

import k2script.env.EnvironmentManager
import k2script.engine.ScriptEngine
import k2script.trikeshed.*
import java.io.File
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.system.exitProcess

/**
 * K2script - Enhanced Kotlin 2.x scripting engine
 * 
 * This is a simplified, modern Kotlin scripting solution focusing on:
 * - Kotlin 2.x compatibility
 * - AI/LLM integration via LiteLLM
 * - Clean, minimal codebase
 * - Focus on actual scripting needs vs complex features
 */
object K2script {
    
    fun main(args: Array<String>) {
        try {
            // Load .env file if it exists
            EnvironmentManager.loadDotEnv()
            
            when {
                args.isEmpty() -> showHelp()
                args[0] == "--help" || args[0] == "-h" -> showHelp()
                args[0] == "--version" || args[0] == "-v" -> showVersion()
                args[0] == "--env" -> showEnvironment()
                args[0] == "--env-check" -> checkEnvironment()
                else -> executeScript(args)
            }
        } catch (e: Exception) {
            System.err.println("K2script error: ${e.message}")
            if (EnvironmentManager.K2Script.isVerbose()) {
                e.printStackTrace()
            }
            exitProcess(1)
        }
    }
    
    private fun showHelp() {
        println("""
            K2script - Enhanced Kotlin 2.x scripting
            
            Usage: k2script [options] <script.kts> [script-args...]
            
            Options:
              --help, -h        Show this help message
              --version, -v     Show version information
              --env             Show environment variable summary
              --env-check       Check environment for common issues
              
            Examples:
              k2script myscript.kts
              k2script myscript.kts arg1 arg2
              k2script --env-check
              
            K2script provides modern Kotlin scripting with:
              - AI/LLM integration via LiteLLM
              - Clean dependency management  
              - Kotlin 2.x compatibility
              - Robust environment variable handling
              
            Environment Variables:
              AI/LLM: OPENAI_API_KEY, ANTHROPIC_API_KEY, COHERE_API_KEY, etc.
              Config: K2SCRIPT_HOME, K2SCRIPT_CONFIG_DIR, K2SCRIPT_CACHE_DIR
              Python: PYTHON_CMD (default: python3), VIRTUAL_ENV
        """.trimIndent())
    }
    
    private fun showVersion() {
        println("K2script version 1.0.0")
        println("Kotlin version: ${KotlinVersion.CURRENT}")
        println("Java version: ${System.getProperty("java.version")}")
        println("K2SCRIPT_HOME: ${EnvironmentManager.K2Script.getHome() ?: "auto-detected"}")
    }
    
    private fun showEnvironment() {
        EnvironmentManager.printEnvironmentSummary()
    }
    
    private fun checkEnvironment() {
        println("K2script Environment Check:")
        println("===========================")
        
        // Check Java
        val javaVersion = System.getProperty("java.version")
        println("✓ Java: $javaVersion")
        
        // Check Python (for LiteLLM)
        val pythonCmd = EnvironmentManager.Python.getCommand()
        println("✓ Python command: $pythonCmd")
        
        // Check AI providers
        val providers = EnvironmentManager.AI.getAvailableProviders()
        if (providers.isNotEmpty()) {
            println("✓ AI providers available: ${providers.joinToString(", ")}")
        } else {
            println("⚠ No AI provider API keys found")
            println("  Set environment variables like OPENAI_API_KEY, ANTHROPIC_API_KEY, etc.")
        }
        
        // Check k2script config
        val configDir = EnvironmentManager.K2Script.getConfigDir()
        println("✓ Config directory: $configDir")
        
        val cacheDir = EnvironmentManager.K2Script.getCacheDir()
        if (cacheDir != null) {
            println("✓ Cache directory: $cacheDir")
        }
        
        println("\nEnvironment check complete.")
    }
    
    private fun executeScript(args: Array<String>) {
        val scriptPath = args[0]
        val scriptArgs = args.drop(1).toTypedArray()
        
        val scriptFile = File(scriptPath)
        if (!scriptFile.exists()) {
            System.err.println("Script file not found: $scriptPath")
            exitProcess(1)
        }
        
        if (EnvironmentManager.K2Script.isVerbose()) {
            println("K2script: executing ${scriptFile.absolutePath} with ${scriptArgs.size} arguments")
        }
        
        val engine = ScriptEngine()
        
        // Validate script
        val validationErrors = engine.validateScript(scriptFile)
        if (validationErrors.isNotEmpty()) {
            System.err.println("Script validation failed:")
            validationErrors.forEach { System.err.println("  $it") }
            exitProcess(1)
        }
        
        // Parse dependencies using TrikeShed Series
        val dependencies = engine.parseDependencies(scriptFile)
        if (dependencies.size > 0 && EnvironmentManager.K2Script.isVerbose()) {
            println("Script dependencies: ${dependencies.`▶`.joinToString(", ")}")
        }
        
        // Execute the script
        try {
            val success = engine.executeScript(scriptFile, scriptArgs)
            
            if (success) {
                if (EnvironmentManager.K2Script.isVerbose()) {
                    println("Script completed successfully")
                }
            } else {
                System.err.println("Script execution failed")
                exitProcess(1)
            }
            
        } catch (e: Exception) {
            System.err.println("Error executing script: ${e.message}")
            if (EnvironmentManager.K2Script.isVerbose()) {
                e.printStackTrace()
            }
            exitProcess(1)
        }
    }
}

// Main function for application entry point
fun main(args: Array<String>) = K2script.main(args)