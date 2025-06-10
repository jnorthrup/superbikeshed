package k2script

import k2script.env.EnvironmentManager
import k2script.engine.ScriptEngine
import k2script.trikeshed.*
import java.io.File
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.system.exitProcess

/**
 * K2script - Modern Kotlin scripting
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
            k2script - Modern Kotlin scripting
            
            Usage: k2script <script.kts> [args...]
            
            Options:
              --help, -h        Show help
              --version, -v     Show version  
              --env-check       Check environment
        """.trimIndent())
    }
    
    private fun showVersion() {
        println("k2script 1.0.0")
        println("Kotlin ${KotlinVersion.CURRENT}")
    }
    
    private fun showEnvironment() {
        EnvironmentManager.printEnvironmentSummary()
    }
    
    private fun checkEnvironment() {
        println("Environment Check:")
        val javaVersion = System.getProperty("java.version")
        println("✓ Java: $javaVersion")
        
        val providers = EnvironmentManager.AI.getAvailableProviders()
        if (providers.isNotEmpty()) {
            println("✓ AI providers: ${providers.joinToString(", ")}")
        } else {
            println("⚠ No AI provider keys found")
        }
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
            println("Executing: ${scriptFile.name}")
        }
        
        val engine = ScriptEngine()
        
        // Validate script
        val validationErrors = engine.validateScript(scriptFile)
        if (validationErrors.isNotEmpty()) {
            System.err.println("Script validation failed:")
            validationErrors.forEach { System.err.println("  $it") }
            exitProcess(1)
        }
        
        val dependencies = engine.parseDependencies(scriptFile)
        if (dependencies.size > 0 && EnvironmentManager.K2Script.isVerbose()) {
            println("Dependencies: ${dependencies.`▶`.joinToString(", ")}")
        }
        
        // Execute the script
        try {
            val success = engine.executeScript(scriptFile, scriptArgs)
            
            if (!success) {
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