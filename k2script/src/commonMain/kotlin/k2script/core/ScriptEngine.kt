package k2script.core

import k2script.lib.*

/**
 * K2Script Engine - Common interface for script execution
 * The cue ball that initiates the causality chain
 */
interface ScriptEngine {
    suspend fun execute(script: String, args: Array<String> = emptyArray()): ScriptResult
    suspend fun validate(script: String): ValidationResult
    suspend fun compile(script: String): CompiledScript
}

/**
 * Script execution result
 */
sealed class ScriptResult {
    data class Success(val output: String, val exitCode: Int = 0) : ScriptResult()
    data class Failure(val error: String, val exitCode: Int = 1, val cause: Throwable? = null) : ScriptResult()
}

/**
 * Script validation result
 */
data class ValidationResult(
    val valid: Boolean,
    val errors: Indexed<String> = emptySeries(),
    val warnings: Indexed<String> = emptySeries()
)

/**
 * Compiled script representation
 */
data class CompiledScript(
    val bytecode: ByteArray,
    val metadata: Join<String, Any>,
    val dependencies: Indexed<String>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CompiledScript) return false
        return bytecode.contentEquals(other.bytecode) && 
               metadata == other.metadata && 
               dependencies == other.dependencies
    }
    
    override fun hashCode(): Int {
        var result = bytecode.contentHashCode()
        result = 31 * result + metadata.hashCode()
        result = 31 * result + dependencies.hashCode()
        return result
    }
}

/**
 * Script engine configuration
 */
data class EngineConfig(
    val classpath: Indexed<String> = emptySeries(),
    val systemProperties: Map<String, String> = emptyMap(),
    val compilerOptions: Indexed<String> = emptySeries(),
    val runtimeOptions: Indexed<String> = emptySeries()
)