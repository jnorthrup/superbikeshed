package k2script

import k2script.api.models.ExecuteScriptCommand
import k2script.api.models.ParseDependenciesQuery
import k2script.api.models.ValidateScriptQuery
import k2script.bus.Router
import k2script.engine.DefaultScriptEngine
import k2script.env.EnvironmentManager
import k2script.trikeshed.*
import borg.trikeshed.lib.play
import borg.trikeshed.lib.size
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess

/**
 * K2script - Modern Kotlin scripting
 * The implementation IS the DSL and creates its own vines
 */
object K2script {
    
    fun main(args: Array<String>) = runBlocking {
        try {
            // The DefaultScriptEngine creates its own vines and self-registers
            // Using the DSL-style builder to demonstrate how the implementation IS the DSL
            val engine = DefaultScriptEngine.build {
                // The engine IS the DSL - it knows what it needs and creates it
                // No external configuration needed - the implementation creates its own vines
            }
            
            // Load .env file if it exists
            EnvironmentManager.loadDotEnv()
            
            when {
                args.isEmpty() -> showHelp()
                args[0] == "--help" || args[0] == "-h" -> showHelp()
                args[0] == "--version" || args[0] == "-v" -> showVersion()
                args[0] == "--env" -> showEnvironment()
                args[0] == "--env-check" -> checkEnvironment()
                args[0] == "--components" -> showComponents(engine)
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
              --components      Show engine components (DSL vines)
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
    
    private fun showComponents(engine: DefaultScriptEngine) {
        println("Engine Components (DSL Vines):")
        val components = engine.listComponents()
        components.forEach { component ->
            println("  ✓ $component")
        }
        
        val resolvers = engine.getResolvers()
        if (resolvers.isNotEmpty()) {
            println("\nDependency Resolvers:")
            resolvers.forEach { resolver ->
                println("  ✓ ${resolver.name}")
            }
        }
    }
    
    private suspend fun executeScript(args: Array<String>) {
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
        
        // Validate script via the router
        val validationErrors = Router.dispatch(ValidateScriptQuery(scriptFile)).await()
        if (validationErrors.isNotEmpty()) {
            System.err.println("Script validation failed:")
            validationErrors.forEach { System.err.println("  $it") }
            exitProcess(1)
        }
        
        // Parse dependencies via the router
        val dependencies = Router.dispatch(ParseDependenciesQuery(scriptFile)).await()
        if (dependencies.size > 0 && EnvironmentManager.K2Script.isVerbose()) {
            println("Dependencies: ${dependencies.play.joinToString(", ")}")
        }
        
        // Execute the script via the router
        try {
            val success = Router.dispatch(ExecuteScriptCommand(scriptFile to scriptArgs)).await()
            
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