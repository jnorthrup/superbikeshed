package k2script

import k2script.api.models.ExecuteScriptCommand
import k2script.api.models.ParseDependenciesQuery
import k2script.api.models.ValidateScriptQuery
import k2script.bus.Router
import k2script.engine.DefaultScriptEngine
import k2script.env.EnvironmentManager
import k2script.trikeshed.*
import k2script.cli.ClasspathCommand
import k2script.cli.MCPCommand
import k2script.cli.GitFeatureCommand
import borg.trikeshed.lib.play
import borg.trikeshed.lib.size
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import k2script.ai.llm.LiteLLMClient
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
                args[0] == "--ai" -> handleAiFeature(args)
                args[0] == "--classpath" -> handleClasspathCommand(args)
                args[0] == "--mcp" -> handleMCPCommand(args)
                args[0] == "--git" -> handleGitCommand(args)
                else -> executeScript(args)
            }
            // LiteLLMClient.stopService() should be called here in a finally block
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
              --ai <prompt>     Generate or explain script using AI
              --classpath       Generate classpath from Maven coordinates or files
              --mcp             Manage MCP servers and hosting services
              --git             Git feature branch management with rapid cloning
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

    private suspend fun handleAiFeature(args: Array<String>) {
        // Ensure LiteLLM service is started
        if (!LiteLLMClient.isServiceRunning()) {
            if (EnvironmentManager.K2Script.isVerbose()) {
                println("Starting LiteLLM service...")
            }
            if (!LiteLLMClient.startService()) {
                System.err.println("Error: Failed to start AI service.")
                exitProcess(1)
            }
            if (EnvironmentManager.K2Script.isVerbose()) {
                println("LiteLLM service started.")
            }
        }

        if (args.size < 2) {
            System.err.println("Error: No prompt provided for --ai flag.")
            exitProcess(1)
        }
        val prompt = args[1]
        if (prompt == "explain this script" && System.`in`.available() > 0) {
            val reader = BufferedReader(InputStreamReader(System.`in`))
            val scriptContent = reader.readText()
            explainScript(scriptContent)
        } else if (prompt == "explain this script") {
            System.err.println("Error: No script content piped for explanation.")
            exitProcess(1)
        } else {
            generateScript(prompt)
        }
    }

    private suspend fun explainScript(scriptContent: String) {
        if (EnvironmentManager.K2Script.isVerbose()) {
            println("AI Action: Explain Script")
            println("Script Content:\n$scriptContent")
        }

        val messages = listOf(
            mapOf("role" to "system", "content" to "You are a helpful assistant that explains Kotlin scripts."),
            mapOf("role" to "user", "content" to "Explain the following Kotlin script:\n```kotlin\n$scriptContent\n```")
        )

        try {
            if (!LiteLLMClient.isServiceRunning()) {
                // This should ideally be handled in handleAiFeature or a higher level
                System.err.println("Error: AI service is not running. Attempting to start...")
                if (!LiteLLMClient.startService()) {
                     System.err.println("Error: Failed to start AI service.")
                     exitProcess(1) // Or handle more gracefully
                }
            }

            val modelName = EnvironmentManager.AI.getDefaultModel() ?: "gpt-3.5-turbo"
            val apiKey = EnvironmentManager.AI.getApiKey() // This might return null if no key is set for the default provider

            if (EnvironmentManager.K2Script.isVerbose()) {
                println("Using AI model: $modelName")
                if (apiKey == null) {
                    println("No API key found in environment for default provider. LiteLLM might use its own environment variables.")
                }
            }

            val responseFuture = LiteLLMClient.complete(
                model = modelName,
                messages = messages,
                apiKey = apiKey
                // temperature = 0.7 // Example: Add other parameters if desired
            )

            val llmResponse = responseFuture.join() // Wait for the response

            if (llmResponse.status == "success" && llmResponse.content != null) {
                println("\nScript Explanation:\n-------------------")
                println(llmResponse.content)
            } else {
                System.err.println("Error explaining script: ${llmResponse.error_message ?: "Unknown error from AI service."}")
                if (EnvironmentManager.K2Script.isVerbose()) {
                    System.err.println("Raw response: ${llmResponse.raw_response}")
                }
            }
        } catch (e: Exception) {
            System.err.println("Failed to get explanation from AI service: ${e.message}")
            if (EnvironmentManager.K2Script.isVerbose()) {
                e.printStackTrace()
            }
            // exitProcess(1) // Decide if failure here should terminate
        }
    }

    private suspend fun generateScript(prompt: String) {
        if (EnvironmentManager.K2Script.isVerbose()) {
            println("AI Action: Generate Script")
            println("Prompt: $prompt")
        }

        val messages = listOf(
            mapOf("role" to "system", "content" to "You are a helpful assistant that generates Kotlin scripts based on user prompts. Output only the raw Kotlin code for the script. Do not include any markdown formatting or explanations unless it's within comments in the code itself."),
            mapOf("role" to "user", "content" to "Generate a Kotlin script that does the following: $prompt")
        )

        try {
            // Ensure service is running (it should be if called from handleAiFeature after starting)
            if (!LiteLLMClient.isServiceRunning()) {
                 System.err.println("Error: AI service is not running. This should have been started by handleAiFeature.")
                 // Optionally attempt to restart, or instruct user
                 if (!LiteLLMClient.startService()) {
                     System.err.println("Error: Failed to start AI service.")
                     return // Exit this function, let caller decide on process exit
                 }
            }

            val modelName = EnvironmentManager.AI.getDefaultModel() ?: "gpt-3.5-turbo"
            val apiKey = EnvironmentManager.AI.getApiKey()

            if (EnvironmentManager.K2Script.isVerbose()) {
                println("AI Generate Script: Using model '$modelName'. API key provided: ${apiKey != null}")
                println("AI Generate Script: Prompt: '$prompt'")
            }

            val responseFuture = LiteLLMClient.complete(
                model = modelName,
                messages = messages,
                apiKey = apiKey,
                temperature = 0.5 // Optional: for more deterministic output
            )
            val llmResponse = responseFuture.join() // Block for the result

            if (llmResponse.status == "success" && llmResponse.content != null) {
                println("\nGenerated Script:\n-----------------")
                var scriptContent = llmResponse.content

                // Clean markdown code block delimiters
                scriptContent = scriptContent.removePrefix("```kotlin\n").removePrefix("```\n").removeSuffix("\n```").trim()

                println(scriptContent)
            } else {
                System.err.println("Error generating script: ${llmResponse.error_message ?: "Unknown error from AI service."}")
                if (EnvironmentManager.K2Script.isVerbose()) {
                    System.err.println("Raw response: ${llmResponse.raw_response}")
                }
            }
        } catch (e: Exception) {
            System.err.println("Failed to generate script from AI service: ${e.message}")
            if (EnvironmentManager.K2Script.isVerbose()) {
                e.printStackTrace()
            }
            // Do not call exitProcess(1) here
        }
    }

    private suspend fun handleClasspathCommand(args: Array<String>) {
        try {
            ClasspathCommand.execute(args.drop(1).toTypedArray())
        } catch (e: Exception) {
            System.err.println("Classpath command error: ${e.message}")
            if (EnvironmentManager.K2Script.isVerbose()) {
                e.printStackTrace()
            }
            exitProcess(1)
        }
    }

    private suspend fun handleMCPCommand(args: Array<String>) {
        try {
            MCPCommand.execute(args.drop(1).toTypedArray())
        } catch (e: Exception) {
            System.err.println("MCP command error: ${e.message}")
            if (EnvironmentManager.K2Script.isVerbose()) {
                e.printStackTrace()
            }
            exitProcess(1)
        }
    }

    private suspend fun handleGitCommand(args: Array<String>) {
        try {
            if (args.size < 2) {
                println("Error: Git command required")
                println("Usage: k2script --git <command> [args...]")
                return
            }
            
            val gitCommand = args[1]
            when (gitCommand) {
                "feature" -> GitFeatureCommand.execute(args.drop(2).toTypedArray())
                else -> {
                    println("Unknown Git command: $gitCommand")
                    println("Available commands: feature")
                }
            }
        } catch (e: Exception) {
            System.err.println("Git command error: ${e.message}")
            if (EnvironmentManager.K2Script.isVerbose()) {
                e.printStackTrace()
            }
            exitProcess(1)
        }
    }
}

// Main function for application entry point
fun main(args: Array<String>) = K2script.main(args)