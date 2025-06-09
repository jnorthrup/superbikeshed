package k2script.engine

import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm

/**
 * K2script script template definition
 * 
 * This defines the base template for all .kts scripts executed by k2script
 */
@KotlinScript(
    fileExtension = "kts",
    compilationConfiguration = K2ScriptCompilationConfiguration::class
)
abstract class K2ScriptTemplate(val args: Array<String>) {
    
    // Built-in functions available to all scripts
    
    /**
     * Print with color support
     */
    fun println(message: Any?, color: AnsiColor = AnsiColor.NONE) {
        if (color != AnsiColor.NONE) {
            kotlin.io.println("${color.code}$message${AnsiColor.RESET.code}")
        } else {
            kotlin.io.println(message)
        }
    }
    
    /**
     * Environment variable access
     */
    fun env(name: String, default: String? = null): String? {
        return System.getenv(name) ?: default
    }
    
    /**
     * Require environment variable
     */
    fun requireEnv(name: String): String {
        return System.getenv(name) 
            ?: throw IllegalStateException("Required environment variable '$name' is not set")
    }
}

/**
 * ANSI color codes for terminal output
 */
enum class AnsiColor(val code: String) {
    NONE(""),
    RESET("\u001B[0m"),
    BLACK("\u001B[30m"),
    RED("\u001B[31m"),
    GREEN("\u001B[32m"),
    YELLOW("\u001B[33m"),
    BLUE("\u001B[34m"),
    PURPLE("\u001B[35m"),
    CYAN("\u001B[36m"),
    WHITE("\u001B[37m"),
    BOLD("\u001B[1m"),
    UNDERLINE("\u001B[4m")
}

/**
 * Compilation configuration for k2script templates
 */
object K2ScriptCompilationConfiguration : ScriptCompilationConfiguration({
    
    // Default imports available to all scripts
    defaultImports(
        "k2script.api.ai.*",
        "k2script.env.EnvironmentManager",
        "k2script.engine.AnsiColor",
        "kotlin.system.*",
        "kotlinx.coroutines.*",
        "java.io.*",
        "java.nio.file.*",
        "java.time.*",
        "java.util.*"
    )
    
    jvm {
        // Include current classpath for compilation
        dependenciesFromCurrentContext(wholeClasspath = true)
    }
    
    // IDE configuration for better development experience
    // Note: Some IDE features require additional setup
    
    // Configuration refinement for dependency resolution
    // TODO: Implement proper dependency resolution in future versions
})

/**
 * Annotation for script dependencies
 */
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class DependsOn(val value: String)

/**
 * Annotation for custom repositories
 */
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class Repository(val value: String)