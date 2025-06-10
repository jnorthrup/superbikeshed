package k2script.engine

import java.io.*
import k2script.engine.*
import k2script.env.*
import k2script.engine.Memory

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
        
        val dependencies = lines
            .α { line ->
                if (line.startsWith("@file:DependsOn(")) {
                    val dependencyRegex = """@file:DependsOn\("([^"]+)"\)""".toRegex()
                    dependencyRegex.find(line)?.groupValues?.get(1)
                } else null
            }
            .`▶` // Gateway to stdlib
            .filterNotNull()
        
        return Series.of(*dependencies.toTypedArray())
    }
    
    /**
     * Validate a script file for common issues
     */
    @ColdPath
    fun validateScript(scriptFile: File): List<String> {
        val errors = mutableListOf<String>()
        
        if (!scriptFile.exists()) {
            errors.add("Script file does not exist: ${scriptFile.absolutePath}")
        }
        
        if (!scriptFile.canRead()) {
            errors.add("Cannot read script file: ${scriptFile.absolutePath}")
        }
        
        if (!scriptFile.name.endsWith(".kts")) {
            errors.add("Script file should have .kts extension: ${scriptFile.name}")
        }
        
        return errors
    }
}