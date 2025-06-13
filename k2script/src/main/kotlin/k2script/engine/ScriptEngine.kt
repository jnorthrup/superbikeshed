package k2script.engine

import java.io.*
import k2script.engine.*
import k2script.env.*
import k2script.engine.Memory
import k2script.trikeshed.Series
import k2script.trikeshed.α
import k2script.trikeshed.▶
import k2script.trikeshed.Log
import k2script.trikeshed.ContextualLogger
import k2script.trikeshed.PerfMonitor
import k2script.trikeshed.HotPath
import k2script.trikeshed.ColdPath
import k2script.trikeshed.Context
import kotlin.reflect.KClass

/**
 * Simplified script engine with TrikeShed integration
 */
class ScriptEngine {
    
    private val globalContext = Context.global()
    
    init {
        // Provide core services in global context  
        globalContext
            .provide(Log, Log::class)
            .provide(Memory, Memory::class)
            .provide(EnvironmentManager, EnvironmentManager::class)
    }
    
    /**
     * Execute a Kotlin script file
     */
    @HotPath
    fun executeScript(scriptFile: File, args: Array<String> = emptyArray()): Boolean {
        val scriptContext = Context.script(scriptFile.absolutePath)
        val logger = Log.context(scriptContext)
        val perf = PerfMonitor.forContext(scriptContext)
        val resources = ResourceManager(scriptContext)
        
        return perf.measure("script execution") {
            Memory.withCleanup {
                executeScriptWithContext(scriptFile, args, scriptContext, logger, resources)
            }
        }
    }
    
    /**
     * Execute script with context
     */
    @ColdPath
    private fun executeScriptWithContext(
        scriptFile: File,
        args: Array<String>,
        scriptContext: Context,
        logger: ContextualLogger,
        resources: ResourceManager
    ): Boolean {
        
        logger.info("Starting script execution: ${scriptFile.name}")
        
        return try {
            val processBuilder = ProcessBuilder(
                "kotlin", 
                "-J--enable-native-access=ALL-UNNAMED",
                scriptFile.absolutePath, 
                *args
            )
            processBuilder.environment().putAll(EnvironmentManager.getAllAsMap())
            processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
            
            val process = processBuilder.start()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                logger.info("Script completed")
                true
            } else {
                logger.error("Script failed: $exitCode")
                false
            }
        } catch (e: Exception) {
            logger.error("Script execution failed", e)
            false
        } finally {
            resources.cleanup()
        }
    }
    
    /**
     * Execute a Kotlin script from a string
     */
    fun executeScriptText(scriptText: String, args: Array<String> = emptyArray()): Boolean {
        // Create temporary file and execute it
        val tempFile = File.createTempFile("k2script", ".kts")
        tempFile.writeText(scriptText)
        tempFile.deleteOnExit()
        
        return executeScript(tempFile, args)
    }
    
    /**
     * Parse and resolve script dependencies (for @DependsOn annotations)
     * Returns Series<String> for TrikeShed compatibility
     */
    @ColdPath
    fun parseDependencies(scriptFile: File): Series<String> {
        val lines = Series.of(*scriptFile.readLines().toTypedArray())
        
        return lines
            .α { line ->
                if (line.startsWith("@file:DependsOn(")) {
                    val dependencyRegex = """@file:DependsOn\("([^"]+)"\)""".toRegex()
                    dependencyRegex.find(line)?.groupValues?.get(1)
                } else null
            }
            .▶
            .filterNotNull()
            .let { filtered -> Series.of(*filtered.toTypedArray()) }
    }
    
    /**
     * Validate a script file for common issues
     */
    @ColdPath
    fun validateScript(scriptFile: File): Series<String> {
        var errors = Series.of<String>()
        
        if (!scriptFile.exists()) {
            errors = errors + Series.of("Script file does not exist: ${scriptFile.absolutePath}")
        }
        
        if (!scriptFile.canRead()) {
            errors = errors + Series.of("Cannot read script file: ${scriptFile.absolutePath}")
        }
        
        if (!scriptFile.name.endsWith(".kts")) {
            errors = errors + Series.of("Script file should have .kts extension: ${scriptFile.name}")
        }
        
        return errors
    }
}