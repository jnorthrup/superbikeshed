package borg.trikeshed.ccek

import borg.trikeshed.lib.Indexed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
// import kotlinx.datetime.Clock // Removed dependency
// import kotlinx.datetime.Instant // Removed dependency
// import kotlinx.datetime.DISTANT_PAST // Removed dependency
import kotlin.coroutines.CoroutineContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual

// Data classes for CcekContext remain the same
data class Control(
    val executionId: String,
    val phase: ExecutionPhase = ExecutionPhase.INIT
)
data class Context(val sourceIp: String)
data class Environment(val action: String, val payload: Any)
data class Knowledge(val validator: (Any) -> Boolean, val rules: Indexed<(Any) -> Any>)
enum class ExecutionPhase { INIT, VALIDATE, TRANSFORM, COMPLETE, ERROR }

/**
 * CcekContext is now a CoroutineContext.Element itself.
 */
data class CcekContext(
    val control: Control,
    val context: Context,
    val environment: Environment,
    val knowledge: Knowledge
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key
    companion object Key : CoroutineContext.Key<CcekContext>
}

sealed class ExecutionResult {
    data class Success(val data: Any) : ExecutionResult()
    data class Error(val message: String) : ExecutionResult()
}

/**
 * The CCEKEngine is now a suspend extension function on CoroutineScope,
 * which retrieves the CcekContext from its context.
 */
suspend fun CoroutineScope.executeCcekPipeline(initialData: Any): ExecutionResult {
    // Retrieve the CCEK context from the coroutine's context
    val ccek = coroutineContext[CcekContext.Key] ?: throw IllegalStateException("CcekContext not found.")

    println("CCEK Engine executing action '${ccek.environment.action}' with ID '${ccek.control.executionId}'")
    
    if (!ccek.knowledge.validator(initialData)) {
        return ExecutionResult.Error("Invalid payload for action: ${ccek.environment.action}")
    }

    val finalPayload = (0 until ccek.knowledge.rules.a).fold(initialData) { current, i ->
        ccek.knowledge.rules.b(i)(current)
    }
    
    return ExecutionResult.Success(finalPayload)
}