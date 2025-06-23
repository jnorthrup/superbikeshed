package k2script.api

import borg.trikeshed.lib.Series
import java.io.File

/**
 * Defines the public contract for a script engine.
 *
 * This interface provides the core functionalities for validating, parsing,
 * and executing Kotlin scripts, while hiding the implementation details. This follows
 * the FFmpeg principle of separating public API from internal implementation,
 * allowing for future extensions and alternative engines without breaking consumers.
 */
interface ScriptEngine {
    /**
     * Validates the given script file for syntax errors and other issues.
     *
     * @param scriptFile The .kts file to validate.
     * @return A list of validation error messages. An empty list indicates success.
     */
    fun validateScript(scriptFile: File): List<String>

    /**
     * Parses the script file to extract its dependency declarations.
     *
     * @param scriptFile The .kts file to parse.
     * @return A TrikeShed `Series` of dependency strings.
     */
    fun parseDependencies(scriptFile: File): Indexed<String>

    /**
     * Executes the script file with the given arguments.
     *
     * @param scriptFile The .kts file to execute.
     * @param scriptArgs The arguments to pass to the script.
     * @return `true` if the execution was successful, `false` otherwise.
     */
    fun executeScript(scriptFile: File, scriptArgs: Array<String>): Boolean
} 