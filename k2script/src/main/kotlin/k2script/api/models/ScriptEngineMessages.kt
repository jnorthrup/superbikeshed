package k2script.api.models

import borg.trikeshed.lib.Indexed
import k2script.bus.Address
import k2script.bus.Command
import k2script.bus.Query
import kotlinx.coroutines.CompletableDeferred
import java.io.File

/**
 * Defines the address for the ScriptEngine service on the message bus.
 */
val SCRIPT_ENGINE_ADDRESS = Address("k2script.engine.script")

/**
 * A query to validate a script file.
 */
data class ValidateScriptQuery(
    override val payload: File,
    override val reply: CompletableDeferred<List<String>> = CompletableDeferred()
) : Query<File, List<String>> {
    override val address: Address = SCRIPT_ENGINE_ADDRESS
}

/**
 * A query to parse dependencies from a script file.
 */
data class ParseDependenciesQuery(
    override val payload: File,
    override val reply: CompletableDeferred<Indexed<String>> = CompletableDeferred()
) : Query<File, Indexed<String>> {
    override val address: Address = SCRIPT_ENGINE_ADDRESS
}

/**
 * A command to execute a script.
 */
data class ExecuteScriptCommand(
    override val payload: Pair<File, Array<String>>,
    override val reply: CompletableDeferred<Boolean> = CompletableDeferred()
) : Command<Pair<File, Array<String>>> {
    override val address: Address = SCRIPT_ENGINE_ADDRESS
} 