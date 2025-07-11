@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
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
import k2script.cli.InstallCommand
import k2script.cli.SandboxCommand
import k2script.platform.FileSystemOperationsKey
import k2script.platform.JvmFileSystemOperations
import k2script.platform.ProcessExecutorKey
import k2script.platform.JvmProcessExecutor
import k2script.platform.File as K2File
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
    
    fun main(args: Array<String>) = runBlocking(FileSystemOperationsKey(JvmFileSystemOperations(PlatformFileIOImpl())) + ProcessExecutorKey(JvmProcessExecutor())) {
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
                args[0] == "--install" -> handleInstallCommand(args)
                args[0] == "--pwd" || args[0] == "--cwd" -> handleWorkingDirectoryCommand(args)
                args[0] == "--sandbox" -> handleSandboxCommand(args)
                args[0] == "--no-sandbox" -> executeScript(args.drop(1).toTypedArray(), sandboxed = false)
                args[0] == "--terminal" -> handleTerminalCommand(args.drop(1).toTypedArray())
                args[0] == "--tmux" -> handleTmuxCommand(args.drop(1).toTypedArray())
                args[0] == "--byobo" -> handleByoboCommand(args.drop(1).toTypedArray())
                args[0] == "--strace" -> handleStraceCommand(args.drop(1).toTypedArray())
                args[0] == "--gradle-surrogate" -> handleGradleSurrogateCommand(args)
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
    
    internal fun showHelp() {
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
              --install <prefix> Install k2script to Unix prefix
              --pwd <dir>       Set working directory for script execution
              --cwd <dir>       Set working directory for script execution
              --sandbox <command> Manage sandboxed environments (create, list, run, destroy)
              --no-sandbox      Disable sandboxed execution (unsafe)
              --terminal        Launch a new terminal with stdio
              --tmux            Launch a new tmux session
              --byobo           Bring Your Own Build Output (placeholder)
              --strace <cmd>    Run a command with strace (Linux only)
              --gradle-surrogate <script.kts> [args...]  Run script using Gradle surrogate driven by Maven surrogate
        """.trimIndent())
    }
    
    internal fun showVersion() {
        println("k2script 1.0.0")
        println("Kotlin ${KotlinVersion.CURRENT}")
    }
    
    internal fun showEnvironment() {
        EnvironmentManager.printEnvironmentSummary()
    }
    
    internal fun checkEnvironment() {
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
    
    internal fun showComponents(engine: DefaultScriptEngine) {
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
    
    internal suspend fun executeScript(args: Array<String>, sandboxed: Boolean = true) {
        val scriptPath = args[0]
        val scriptArgs = args.drop(1).toTypedArray()

        val originalScriptFile = K2File(scriptPath)
        if (!originalScriptFile.exists()) {
            System.err.println("Script file not found: $scriptPath")
            exitProcess(1)
        }

        val scriptFileToExecute: K2File
        var tempDir: K2File? = null

        if (sandboxed) {
            if (EnvironmentManager.K2Script.isVerbose()) {
                println("Running script in a sandboxed environment.")
            }
            val fileSystem = coroutineContext.fileSystemOperations
            val tempSandboxDir = fileSystem.createTempDir(prefix = "k2script-exec-sandbox-")
            fileSystem.copyFile(originalScriptFile, K2File(tempSandboxDir.absolutePath + "/" + originalScriptFile.name), overwrite = true)
            scriptFileToExecute = K2File(tempSandboxDir.absolutePath + "/" + originalScriptFile.name)
            // Set working directory to the sandbox for execution
            System.setProperty("user.dir", tempSandboxDir.absolutePath)
            tempDir = tempSandboxDir // Keep track of tempDir to delete later
        } else {
            if (EnvironmentManager.K2Script.isVerbose()) {
                println("Running script without sandboxing (--no-sandbox). This is potentially unsafe.")
            }
            scriptFileToExecute = originalScriptFile
        }

        if (EnvironmentManager.K2Script.isVerbose()) {
            println("Executing: ${scriptFileToExecute.name}")
        }

        try {
            // Validate script via the router
            val validationErrors = Router.dispatch(ValidateScriptQuery(scriptFileToExecute)).await()
            if (validationErrors.isNotEmpty()) {
                System.err.println("Script validation failed:")
                validationErrors.forEach { System.err.println("  $it") }
                exitProcess(1)
            }

            // Parse dependencies via the router
            val dependencies = Router.dispatch(ParseDependenciesQuery(scriptFileToExecute)).await()
            if (dependencies.size > 0 && EnvironmentManager.K2Script.isVerbose()) {
                println("Dependencies: ${dependencies.play.joinToString(", ")}")
            }

            // Execute the script via the router
            val success = Router.dispatch(ExecuteScriptCommand(scriptFileToExecute to scriptArgs)).await()

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
        } finally {
            // Clean up the temporary sandbox directory if it was created
            tempDir?.deleteRecursively()
            if (sandboxed) {
                // Restore original working directory if it was changed
                System.setProperty("user.dir", System.getProperty("user.home")) // Or a more robust way to restore
            }
        }
    }

    internal suspend fun handleAiFeature(args: Array<String>) {
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

    internal suspend fun explainScript(scriptContent: String) {
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

    internal suspend fun generateScript(prompt: String) {
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

    internal suspend fun handleClasspathCommand(args: Array<String>) {
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

    internal suspend fun handleMCPCommand(args: Array<String>) {
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

    internal suspend fun handleGitCommand(args: Array<String>) {
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
    
    internal suspend fun handleInstallCommand(args: Array<String>) {
        try {
            when (args.size) {
                1 -> {
                    System.err.println("Error: No prefix specified for --install")
                    System.err.println("Usage: k2script --install <prefix> [script.kts]")
                    System.err.println("Examples:")
                    System.err.println("  k2script --install ~/.local                    # Install k2script itself")
                    System.err.println("  k2script --install ~/.local script.kts         # Install a script")
                    exitProcess(1)
                }
                2 -> {
                    val prefix = args[1]
                    InstallCommand.handle(prefix)
                }
                else -> {
                    val prefix = args[1]
                    val scriptPath = args[2]
                    InstallCommand.handle(prefix, scriptPath)
                }
            }
        } catch (e: Exception) {
            System.err.println("Install command error: ${e.message}")
            if (EnvironmentManager.K2Script.isVerbose()) {
                e.printStackTrace()
            }
            exitProcess(1)
        }
    }
    
    internal suspend fun handleWorkingDirectoryCommand(args: Array<String>) {
        try {
            if (args.size < 3) {
                System.err.println("Error: No working directory and script specified")
                System.err.println("Usage: k2script --pwd <dir> <script.kts> [args...]")
                System.err.println("Usage: k2script --cwd <dir> <script.kts> [args...]")
                System.err.println("Example: k2script --pwd /tmp script.kts arg1 arg2")
                exitProcess(1)
            }
            
            val workingDir = args[1]
            val scriptArgs = args.drop(2).toTypedArray()
            
            val workingDirFile = K2File(workingDir)
            if (!coroutineContext.fileSystemOperations.fileExists(workingDirFile) || !coroutineContext.fileSystemOperations.isDirectory(workingDirFile)) {
                System.err.println("Error: Working directory does not exist or is not a directory: $workingDir")
                exitProcess(1)
            }
            
            // Change to working directory
            System.setProperty("user.dir", workingDirFile.absolutePath)
            
            if (EnvironmentManager.K2Script.isVerbose()) {
                println("Changed working directory to: ${workingDirFile.absolutePath}")
            }
            
            // Execute script with remaining arguments
            executeScript(scriptArgs)
        } catch (e: Exception) {
            System.err.println("Working directory command error: ${e.message}")
            if (EnvironmentManager.K2Script.isVerbose()) {
                e.printStackTrace()
            }
            exitProcess(1)
        }
    }

    internal suspend fun handleTerminalCommand(args: Array<String>) {
        val processExecutor = coroutineContext.processExecutor
        val os = System.getProperty("os.name").lowercase()
        val command = when {
            os.contains("mac") -> "open -a Terminal ."
            os.contains("linux") -> "x-terminal-emulator" // Common on many Linux distros
            os.contains("windows") -> "start cmd.exe"
            else -> {
                System.err.println("Unsupported OS for --terminal command: $os")
                exitProcess(1)
            }
        }
        println("Launching terminal...")
        val result = processExecutor.runCommand(command)
        if (result.exitCode != 0) {
            System.err.println("Error launching terminal: ${result.stderr}")
            exitProcess(1)
        }
    }

    internal suspend fun handleTmuxCommand(args: Array<String>) {
        val processExecutor = coroutineContext.processExecutor
        println("Launching tmux session...")
        val result = processExecutor.runCommand("tmux new-session")
        if (result.exitCode != 0) {
            System.err.println("Error launching tmux: ${result.stderr}")
            System.err.println("Please ensure tmux is installed and available in your PATH.")
            exitProcess(1)
        }
    }

    internal suspend fun handleByoboCommand(args: Array<String>) {
        println("BYOBO (Bring Your Own Build Output) command initiated.")
        println("This feature is intended to integrate external build outputs into k2script's analysis or execution.")
        println("Further parameters would define the source and type of build output (e.g., --path <dir>, --format <json|xml>).")
        println("Currently, this is a placeholder. Please provide more specific requirements for BYOBO functionality.")
    }

    internal suspend fun handleStraceCommand(args: Array<String>) {
        val processExecutor = coroutineContext.processExecutor
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("linux")) {
            System.err.println("Error: --strace is only supported on Linux systems.")
            exitProcess(1)
        }

        if (args.isEmpty()) {
            System.err.println("Error: No command provided for strace.")
            System.err.println("Usage: k2script --strace <command> [args...]")
            exitProcess(1)
        }

        val straceCommand = "strace ${args.joinToString(" ")}"
        println("Running strace: $straceCommand")
        val result = processExecutor.runCommand(straceCommand)
        println("Strace Output:\n${result.stdout}")
        if (result.exitCode != 0) {
            System.err.println("Strace command failed with exit code ${result.exitCode}: ${result.stderr}")
            exitProcess(1)
        }
    }

    internal suspend fun handleGradleSurrogateCommand(args: Array<String>) {
        if (args.size < 2) {
            println("Usage: k2script --gradle-surrogate <script.kts> [args...]")
            return
        }
        val scriptFile = File(args[1])
        if (!scriptFile.exists()) {
            println("Script not found: ${args[1]}")
            return
        }
        val scriptArgs = args.drop(2).toTypedArray()
        // 1. Create Maven surrogate project if needed
        val mavenLauncher = k2script.launcher.StandaloneSurrogateMavenLauncher()
        val scriptHash = scriptFile.readText().hashCode().toString(16)
        val surrogateName = "${'$'}{scriptFile.nameWithoutExtension}-${'$'}scriptHash"
        val surrogatesDir = java.nio.file.Paths.get(System.getProperty("user.home"), ".k2script", "surrogates")
        val surrogateDir = surrogatesDir.resolve(surrogateName)
        if (!java.nio.file.Files.exists(surrogateDir.resolve("pom.xml"))) {
            mavenLauncher.runScript(scriptFile, scriptArgs) // This will create the surrogate project
        }
        // 2. Parse pom.xml and generate build.gradle.kts/settings.gradle.kts
        k2script.util.MavenToGradleConverter.generateGradleFromPom(surrogateDir)
        // 3. Run the Gradle surrogate
        kotlinx.coroutines.runBlocking {
            val gradleLauncher = k2script.launcher.SurrogateGradleLauncher()
            gradleLauncher.runScript(scriptFile, scriptArgs)
        }
    }
}