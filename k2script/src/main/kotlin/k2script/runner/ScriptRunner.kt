@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package k2script.runner

import java.io.File
import javax.script.ScriptEngineManager
import kotlin.system.exitProcess

/**
 * Simple script runner that executes Kotlin scripts with proper classpath
 */
object ScriptRunner {
    
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            System.err.println("Usage: ScriptRunner <script.kts> [args...]")
            exitProcess(1)
        }
        
        val scriptPath = args[0]
        val scriptFile = File(scriptPath)
        
        if (!scriptFile.exists()) {
            System.err.println("Script not found: $scriptPath")
            exitProcess(1)
        }
        
        // Pass remaining arguments to the script
        val scriptArgs = args.drop(1).toTypedArray()
        
        try {
            // Use Kotlin scripting engine
            val engine = ScriptEngineManager().getEngineByExtension("kts")
            if (engine == null) {
                System.err.println("Kotlin script engine not available")
                exitProcess(1)
            }
            
            // Set script arguments
            engine.put("args", scriptArgs)
            
            // Execute script
            engine.eval(scriptFile.reader())
            
        } catch (e: Exception) {
            System.err.println("Script execution failed: ${e.message}")
            e.printStackTrace()
            exitProcess(1)
        }
    }
}