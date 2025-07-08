#!/usr/bin/env kotlin

@file:Repository("https://repo.maven.apache.org/maven2")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
@file:DependsOn("org.jetbrains.kotlin:kotlin-scripting-jvm:2.2.0")
@file:DependsOn("org.jetbrains.kotlin:kotlin-scripting-jvm-host:2.2.0")

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File
import java.net.URL
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.*
import kotlin.script.experimental.jvmhost.*

/**
 * Nexus - Universal Development Agent
 * 
 * Bare metal implementation combining:
 * - AgenticOrchestrator pattern from TrikeShed
 * - CCEK service composition
 * - Trie menu navigation
 * - Script execution capabilities
 */

// === Core TrikeShed Patterns ===
typealias Series<T> = List<T>
typealias Join<A,B> = Pair<A,B>
infix fun <A,B> A.j(other: B): Join<A,B> = this to other

// === CCEK Context ===
typealias Context = Series<Join<String, String>>
typealias CCEKContext = CoroutineContext

// === Agent Types ===
data class AgentAction(val command: String, val args: List<String> = emptyList())
data class AgentResult(val success: Boolean, val output: String = "", val data: Any? = null)

// === Trie Menu Navigation ===
class TrieNode<T>(
    val value: T? = null,
    val children: MutableMap<Char, TrieNode<T>> = mutableMapOf()
)

class TrieMenu<T>(private val items: Map<String, T>) {
    private val root = TrieNode<T>()
    
    init {
        items.forEach { (key, value) ->
            var node = root
            key.forEach { char ->
                node = node.children.getOrPut(char) { TrieNode() }
            }
            node.value = value
        }
    }
    
    fun select(prefix: String): T? {
        var node = root
        prefix.forEach { char ->
            node = node.children[char] ?: return null
        }
        return node.value
    }
    
    fun completions(prefix: String): List<String> {
        var node = root
        prefix.forEach { char ->
            node = node.children[char] ?: return emptyList()
        }
        return collectKeys(node, prefix)
    }
    
    private fun collectKeys(node: TrieNode<T>, prefix: String): List<String> {
        val results = mutableListOf<String>()
        if (node.value != null) results.add(prefix)
        node.children.forEach { (char, child) ->
            results.addAll(collectKeys(child, prefix + char))
        }
        return results
    }
}

// === Maven Dependency Resolution ===
data class MavenCoordinate(
    val groupId: String,
    val artifactId: String,
    val version: String
) {
    companion object {
        fun parse(coord: String): MavenCoordinate {
            val parts = coord.split(":")
            require(parts.size == 3) { "Invalid coordinate: $coord" }
            return MavenCoordinate(parts[0], parts[1], parts[2])
        }
    }
    
    val path: String get() = "${groupId.replace('.', '/')}/$artifactId/$version/$artifactId-$version.jar"
}

class DependencyResolver(
    private val cacheDir: File = File(System.getProperty("user.home"), ".nexus/cache")
) {
    init { cacheDir.mkdirs() }
    
    suspend fun resolve(coordinate: String): File = withContext(Dispatchers.IO) {
        val coord = MavenCoordinate.parse(coordinate)
        val jarFile = File(cacheDir, "${coord.artifactId}-${coord.version}.jar")
        
        if (!jarFile.exists()) {
            println("Downloading: $coordinate")
            val url = URL("https://repo1.maven.org/maven2/${coord.path}")
            url.openStream().use { input ->
                jarFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        jarFile
    }
}

// === Script Execution Engine ===
class ScriptEngine {
    private val scriptingHost = BasicJvmScriptingHost()
    
    suspend fun execute(scriptFile: File, args: Array<String> = emptyArray()): AgentResult {
        return try {
            val compilationConfig = createJvmCompilationConfigurationFromTemplate<Any> {
                jvm { dependenciesFromCurrentContext(wholeClasspath = true) }
                defaultImports("kotlinx.coroutines.*", "java.io.*")
            }
            
            val evaluationConfig = createJvmEvaluationConfigurationFromTemplate<Any> {
                jvm { baseClassLoader(Thread.currentThread().contextClassLoader) }
                constructorArgs(args)
            }
            
            val result = scriptingHost.eval(
                scriptFile.toScriptSource(),
                compilationConfig,
                evaluationConfig
            )
            
            when (result) {
                is ResultWithDiagnostics.Success -> {
                    AgentResult(true, "Script executed successfully", result.value.returnValue)
                }
                is ResultWithDiagnostics.Failure -> {
                    val errors = result.reports.joinToString("\n") { it.message }
                    AgentResult(false, errors)
                }
            }
        } catch (e: Exception) {
            AgentResult(false, "Execution failed: ${e.message}")
        }
    }
}

// === Nexus Agent Orchestrator ===
class NexusAgent(
    private val workDir: File = File("."),
    context: CCEKContext = coroutineContext
) {
    private val scriptEngine = ScriptEngine()
    private val resolver = DependencyResolver()
    private val actionChannel = Channel<AgentAction>(Channel.UNLIMITED)
    
    // Command menu using Trie
    private val commands = TrieMenu(mapOf(
        "help" to ::showHelp,
        "run" to ::runScript,
        "install" to ::install,
        "deps" to ::resolveDeps,
        "repl" to ::startRepl,
        "watch" to ::watchScript,
        "status" to ::showStatus,
        "docker" to ::dockerCommand,
        "terminal" to ::launchTerminal,
        "clone" to ::hourlyClone,
        "cwd" to ::showCwd,
        "tmux" to ::tmuxCommand,
        "byobu" to ::byobuCommand
    ))
    
    suspend fun start() = coroutineScope {
        println("Nexus Agent - Universal Development Assistant")
        println("Type 'help' for commands or start typing to see completions")
        
        // Action processor
        launch {
            for (action in actionChannel) {
                processAction(action)
            }
        }
        
        // Interactive loop
        while (isActive) {
            print("\nnexus> ")
            val input = readlnOrNull() ?: break
            
            if (input == "exit" || input == "quit") break
            
            val parts = input.trim().split(" ")
            if (parts.isEmpty()) continue
            
            val cmd = parts[0]
            val args = parts.drop(1)
            
            // Try exact match first
            val handler = commands.select(cmd)
            if (handler != null) {
                handler(args)
            } else {
                // Show completions
                val completions = commands.completions(cmd)
                when {
                    completions.isEmpty() -> println("Unknown command: $cmd")
                    completions.size == 1 -> {
                        commands.select(completions[0])?.invoke(args)
                    }
                    else -> {
                        println("Did you mean: ${completions.joinToString(", ")}?")
                    }
                }
            }
        }
    }
    
    private suspend fun processAction(action: AgentAction) {
        when (action.command) {
            "execute" -> {
                val scriptFile = File(action.args.firstOrNull() ?: return)
                val result = scriptEngine.execute(scriptFile, action.args.drop(1).toTypedArray())
                println(if (result.success) "SUCCESS: ${result.output}" else "ERROR: ${result.output}")
            }
            else -> println("Unknown action: ${action.command}")
        }
    }
    
    // Command handlers
    private suspend fun showHelp(args: List<String>) {
        println("""
            Nexus Commands:
            - help              Show this help
            - run <script>      Execute a Kotlin script
            - install <dest>    Install Nexus to destination
            - deps <coord>      Resolve Maven dependencies
            - repl              Start interactive REPL
            - watch <script>    Watch and auto-run script
            - status            Show agent status
            - docker <cmd>      Docker operations (run, build, ps, etc.)
            - terminal [cmd]    Launch new terminal with optional command
            - clone <repo>      Hourly cloning of repository
            - cwd               Show current working directory
            - tmux [cmd]        Tmux session management
            - byobu [cmd]       Byobu session management
            - exit/quit         Exit Nexus
        """.trimIndent())
    }
    
    private suspend fun runScript(args: List<String>) {
        if (args.isEmpty()) {
            println("Usage: run <script.kts> [args...]")
            return
        }
        actionChannel.send(AgentAction("execute", args))
    }
    
    private suspend fun install(args: List<String>) {
        if (args.isEmpty()) {
            println("Usage: install <destination>")
            return
        }
        
        val dest = File(args[0])
        val nexusScript = File(dest, "nexus")
        
        println("Installing Nexus to ${dest.absolutePath}")
        
        // Create executable wrapper
        nexusScript.writeText("""
            #!/usr/bin/env kotlin
            ${File("nexus.kts").readText()}
        """.trimIndent())
        
        nexusScript.setExecutable(true)
        println("Nexus installed. Run with: ${nexusScript.absolutePath}")
    }
    
    private suspend fun resolveDeps(args: List<String>) {
        if (args.isEmpty()) {
            println("Usage: deps <group:artifact:version>")
            return
        }
        
        try {
            val jarFile = resolver.resolve(args[0])
            println("✅ Resolved: ${jarFile.absolutePath}")
        } catch (e: Exception) {
            println("❌ Failed to resolve: ${e.message}")
        }
    }
    
    private suspend fun startRepl(args: List<String>) {
        println("🔄 Starting Nexus REPL (type :quit to exit)")
        
        while (true) {
            print("kotlin> ")
            val code = readlnOrNull() ?: break
            
            if (code == ":quit") break
            
            val tempFile = File.createTempFile("repl", ".kts")
            tempFile.writeText(code)
            tempFile.deleteOnExit()
            
            val result = scriptEngine.execute(tempFile)
            if (result.success && result.data != null && result.data != Unit) {
                println("res: ${result.data}")
            } else if (!result.success) {
                println(result.output)
            }
        }
    }
    
    private suspend fun watchScript(args: List<String>) {
        if (args.isEmpty()) {
            println("Usage: watch <script.kts>")
            return
        }
        
        val scriptFile = File(args[0])
        var lastModified = scriptFile.lastModified()
        
        println("👁️ Watching ${scriptFile.name} for changes (Ctrl+C to stop)")
        
        coroutineScope {
            launch {
                while (isActive) {
                    val currentModified = scriptFile.lastModified()
                    if (currentModified > lastModified) {
                        lastModified = currentModified
                        println("\n🔄 File changed, re-executing...")
                        actionChannel.send(AgentAction("execute", args))
                    }
                    delay(1000)
                }
            }
        }
    }
    
    private suspend fun showStatus(args: List<String>) {
        println("""
            Nexus Agent Status:
            - Working Directory: ${workDir.absolutePath}
            - Cache Directory: ${File(System.getProperty("user.home"), ".nexus/cache").absolutePath}
            - Kotlin Version: ${KotlinVersion.CURRENT}
            - Coroutines: Active
            - Script Engine: Ready
        """.trimIndent())
    }
    
    private suspend fun dockerCommand(args: List<String>) {
        if (args.isEmpty()) {
            println("Usage: docker <command> [args...]")
            println("Examples:")
            println("  docker ps")
            println("  docker run -it alpine sh")
            println("  docker build -t myapp .")
            return
        }
        
        val command = listOf("docker") + args
        println("🐳 Running: ${command.joinToString(" ")}")
        
        try {
            val process = ProcessBuilder(command)
                .inheritIO()
                .start()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                println("❌ Docker command failed with exit code: $exitCode")
            }
        } catch (e: Exception) {
            println("❌ Docker command failed: ${e.message}")
        }
    }
    
    private suspend fun launchTerminal(args: List<String>) {
        val osName = System.getProperty("os.name").lowercase()
        
        val terminalCommand = when {
            osName.contains("mac") -> {
                if (args.isEmpty()) {
                    listOf("open", "-a", "Terminal", ".")
                } else {
                    listOf("osascript", "-e", "tell app \"Terminal\" to do script \"${args.joinToString(" ")}\"")
                }
            }
            osName.contains("linux") -> {
                if (args.isEmpty()) {
                    listOf("gnome-terminal")
                } else {
                    listOf("gnome-terminal", "--", "bash", "-c", args.joinToString(" ") + "; exec bash")
                }
            }
            osName.contains("windows") -> {
                if (args.isEmpty()) {
                    listOf("cmd", "/c", "start", "cmd")
                } else {
                    listOf("cmd", "/c", "start", "cmd", "/k", args.joinToString(" "))
                }
            }
            else -> {
                println("❌ Unsupported OS for terminal launching: $osName")
                return
            }
        }
        
        println("💻 Launching terminal...")
        try {
            ProcessBuilder(terminalCommand).start()
            println("✅ Terminal launched")
        } catch (e: Exception) {
            println("❌ Failed to launch terminal: ${e.message}")
        }
    }
    
    private suspend fun hourlyClone(args: List<String>) {
        if (args.isEmpty()) {
            println("Usage: clone <repository-url>")
            return
        }
        
        val repoUrl = args[0]
        val repoName = repoUrl.substringAfterLast("/").removeSuffix(".git")
        val cloneDir = File(System.getProperty("user.home"), ".nexus/clones")
        cloneDir.mkdirs()
        
        val targetDir = File(cloneDir, "${repoName}-${System.currentTimeMillis()}")
        
        println("📥 Cloning repository: $repoUrl")
        println("📂 Target directory: ${targetDir.absolutePath}")
        
        try {
            val cloneProcess = ProcessBuilder("git", "clone", repoUrl, targetDir.absolutePath)
                .inheritIO()
                .start()
            
            val exitCode = cloneProcess.waitFor()
            if (exitCode == 0) {
                println("✅ Repository cloned successfully")
                
                // Set up hourly updates
                coroutineScope {
                    launch {
                        while (isActive) {
                            delay(3600000) // 1 hour
                            println("🔄 Pulling latest changes for $repoName...")
                            
                            val pullProcess = ProcessBuilder("git", "-C", targetDir.absolutePath, "pull")
                                .inheritIO()
                                .start()
                            
                            if (pullProcess.waitFor() == 0) {
                                println("✅ Updated $repoName")
                            } else {
                                println("❌ Failed to update $repoName")
                            }
                        }
                    }
                }
            } else {
                println("❌ Failed to clone repository")
            }
        } catch (e: Exception) {
            println("❌ Clone failed: ${e.message}")
        }
    }
    
    private suspend fun showCwd(args: List<String>) {
        println("📂 Current working directory:")
        println("   ${workDir.absolutePath}")
    }
    
    private suspend fun tmuxCommand(args: List<String>) {
        when {
            args.isEmpty() -> {
                println("Tmux Commands:")
                println("  tmux list           List active sessions")
                println("  tmux new <name>     Create new session")
                println("  tmux attach <name>  Attach to session")
                println("  tmux kill <name>    Kill session")
                return
            }
            args[0] == "list" -> {
                println("🖥️  Active tmux sessions:")
                try {
                    val process = ProcessBuilder("tmux", "list-sessions")
                        .inheritIO()
                        .start()
                    process.waitFor()
                } catch (e: Exception) {
                    println("❌ Failed to list tmux sessions: ${e.message}")
                }
            }
            args[0] == "new" -> {
                val sessionName = args.getOrNull(1) ?: "nexus-${System.currentTimeMillis()}"
                println("🚀 Creating tmux session: $sessionName")
                try {
                    ProcessBuilder("tmux", "new-session", "-d", "-s", sessionName)
                        .start()
                        .waitFor()
                    println("✅ Session '$sessionName' created")
                    println("   Attach with: tmux attach $sessionName")
                } catch (e: Exception) {
                    println("❌ Failed to create tmux session: ${e.message}")
                }
            }
            args[0] == "attach" -> {
                val sessionName = args.getOrNull(1) ?: run {
                    println("Usage: tmux attach <session-name>")
                    return
                }
                println("🔗 Attaching to tmux session: $sessionName")
                try {
                    ProcessBuilder("tmux", "attach-session", "-t", sessionName)
                        .inheritIO()
                        .start()
                        .waitFor()
                } catch (e: Exception) {
                    println("❌ Failed to attach to tmux session: ${e.message}")
                }
            }
            args[0] == "kill" -> {
                val sessionName = args.getOrNull(1) ?: run {
                    println("Usage: tmux kill <session-name>")
                    return
                }
                println("💀 Killing tmux session: $sessionName")
                try {
                    ProcessBuilder("tmux", "kill-session", "-t", sessionName)
                        .start()
                        .waitFor()
                    println("✅ Session '$sessionName' killed")
                } catch (e: Exception) {
                    println("❌ Failed to kill tmux session: ${e.message}")
                }
            }
            else -> {
                println("Unknown tmux command: ${args[0]}")
                println("Use 'tmux' to see available commands")
            }
        }
    }
    
    private suspend fun byobuCommand(args: List<String>) {
        when {
            args.isEmpty() -> {
                println("Byobu Commands:")
                println("  byobu list          List active sessions")
                println("  byobu new <name>    Create new session")
                println("  byobu attach <name> Attach to session")
                println("  byobu kill <name>   Kill session")
                return
            }
            args[0] == "list" -> {
                println("🖥️  Active byobu sessions:")
                try {
                    val process = ProcessBuilder("byobu", "list-sessions")
                        .inheritIO()
                        .start()
                    process.waitFor()
                } catch (e: Exception) {
                    println("❌ Failed to list byobu sessions: ${e.message}")
                }
            }
            args[0] == "new" -> {
                val sessionName = args.getOrNull(1) ?: "nexus-${System.currentTimeMillis()}"
                println("🚀 Creating byobu session: $sessionName")
                try {
                    ProcessBuilder("byobu", "new-session", "-d", "-s", sessionName)
                        .start()
                        .waitFor()
                    println("✅ Session '$sessionName' created")
                    println("   Attach with: byobu attach $sessionName")
                } catch (e: Exception) {
                    println("❌ Failed to create byobu session: ${e.message}")
                }
            }
            args[0] == "attach" -> {
                val sessionName = args.getOrNull(1) ?: run {
                    println("Usage: byobu attach <session-name>")
                    return
                }
                println("🔗 Attaching to byobu session: $sessionName")
                try {
                    ProcessBuilder("byobu", "attach-session", "-t", sessionName)
                        .inheritIO()
                        .start()
                        .waitFor()
                } catch (e: Exception) {
                    println("❌ Failed to attach to byobu session: ${e.message}")
                }
            }
            args[0] == "kill" -> {
                val sessionName = args.getOrNull(1) ?: run {
                    println("Usage: byobu kill <session-name>")
                    return
                }
                println("💀 Killing byobu session: $sessionName")
                try {
                    ProcessBuilder("byobu", "kill-session", "-t", sessionName)
                        .start()
                        .waitFor()
                    println("✅ Session '$sessionName' killed")
                } catch (e: Exception) {
                    println("❌ Failed to kill byobu session: ${e.message}")
                }
            }
            else -> {
                println("Unknown byobu command: ${args[0]}")
                println("Use 'byobu' to see available commands")
            }
        }
    }
}

// === Main Entry Point ===
suspend fun main(args: Array<String>) = coroutineScope {
    when {
        args.isEmpty() -> {
            // Interactive mode
            val agent = NexusAgent()
            agent.start()
        }
        args[0] == "--install" && args.size >= 2 -> {
            // Direct install mode
            val dest = File(args[1])
            dest.mkdirs()
            
            val nexusScript = File(dest, "nexus")
            val scriptContent = File(args[0]).readText()
            
            nexusScript.writeText("""#!/usr/bin/env kotlin
$scriptContent""")
            
            nexusScript.setExecutable(true)
            println("✅ Nexus installed to: ${nexusScript.absolutePath}")
        }
        args[0].endsWith(".kts") -> {
            // Direct script execution
            val engine = ScriptEngine()
            val result = engine.execute(File(args[0]), args.drop(1).toTypedArray())
            if (!result.success) {
                System.err.println(result.output)
                System.exit(1)
            }
        }
        else -> {
            // Pass through to agent
            val agent = NexusAgent()
            agent.start()
        }
    }
}

// Run the main function
runBlocking {
    main(args)
}