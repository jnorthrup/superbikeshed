@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package k2script.jvm

import k2script.lib.*
import k2script.core.*
import kotlinx.coroutines.*
import java.io.File
import kotlin.system.exitProcess

/**
 * Simple JVM Script Engine - Process-based execution only
 * This is the minimal viable k2script that can stand alone
 */
class SimpleJvmEngine {
    
    suspend fun execute(scriptFile: File, args: Array<String> = emptyArray()): ScriptResult {
        return try {
            val runner = JvmScriptRunner()
            runner.runScript(scriptFile, args)
        } catch (e: Exception) {
            ScriptResult.Failure(
                error = "Script execution failed: ${e.message}",
                exitCode = 1,
                cause = e
            )
        }
    }
    
    suspend fun compile(scriptFile: File, outputJar: File): CompilationResult {
        return try {
            val runner = JvmScriptRunner()
            runner.compileToJar(scriptFile, outputJar)
        } catch (e: Exception) {
            CompilationResult.Failure(
                error = "Compilation failed: ${e.message}",
                exitCode = 1
            )
        }
    }
}

/**
 * Standalone K2Script Main - The executable entry point
 */
object K2ScriptStandalone {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        try {
            when {
                args.isEmpty() -> showHelp()
                args[0] == "--help" || args[0] == "-h" -> showHelp()
                args[0] == "--version" || args[0] == "-v" -> showVersion()
                args[0] == "--compile" -> compileScript(args)
                else -> executeScript(args)
            }
        } catch (e: Exception) {
            System.err.println("K2Script error: ${e.message}")
            if (System.getenv("K2SCRIPT_VERBOSE") == "true") {
                e.printStackTrace()
            }
            exitProcess(1)
        }
    }
    
    internal fun showHelp() {
        println("""
            K2Script - Kotlin Script Runner (Standalone)
            
            Usage: k2script [options] <script.kts> [args...]
            
            Options:
              --help, -h          Show this help
              --version, -v       Show version
              --compile <script>  Compile script to JAR
              
            Environment Variables:
              K2SCRIPT_VERBOSE    Enable verbose output
              KOTLIN_HOME         Path to Kotlin installation
              JAVA_HOME           Path to Java installation
              
            Examples:
              k2script hello.kts
              k2script hello.kts arg1 arg2
              k2script --compile hello.kts
        """.trimIndent())
    }
    
    internal fun showVersion() {
        println("K2Script 1.0.0 (Standalone)")
        println("Kotlin ${KotlinVersion.CURRENT}")
        println("Simple process-based execution")
    }
    
    internal suspend fun executeScript(args: Array<String>) {
        val scriptPath = args[0]
        val scriptArgs = args.drop(1).toTypedArray()
        val scriptFile = File(scriptPath)
        
        if (!scriptFile.exists()) {
            System.err.println("Script file not found: $scriptPath")
            exitProcess(1)
        }
        
        val engine = SimpleJvmEngine()
        
        if (System.getenv("K2SCRIPT_VERBOSE") == "true") {
            println("Executing: ${scriptFile.name}")
        }
        
        when (val result = engine.execute(scriptFile, scriptArgs)) {
            is ScriptResult.Success -> {
                exitProcess(result.exitCode)
            }
            is ScriptResult.Failure -> {
                System.err.println(result.error)
                exitProcess(result.exitCode)
            }
        }
    }
    
    internal suspend fun compileScript(args: Array<String>) {
        if (args.size < 2) {
            System.err.println("Script file required for --compile option")
            exitProcess(1)
        }
        
        val scriptPath = args[1]
        val scriptFile = File(scriptPath)
        
        if (!scriptFile.exists()) {
            System.err.println("Script file not found: $scriptPath")
            exitProcess(1)
        }
        
        val outputJar = File(scriptFile.nameWithoutExtension + ".jar")
        val engine = SimpleJvmEngine()
        
        println("Compiling: ${scriptFile.name} -> ${outputJar.name}")
        
        when (val result = engine.compile(scriptFile, outputJar)) {
            is CompilationResult.Success -> {
                println("Compilation successful: ${result.jarFile.absolutePath}")
                if (result.output.isNotBlank()) {
                    println(result.output)
                }
            }
            is CompilationResult.Failure -> {
                System.err.println("Compilation failed: ${result.error}")
                exitProcess(result.exitCode)
            }
        }
    }
}

// Main function for executable JAR
fun main(args: Array<String>) = K2ScriptStandalone.main(args)