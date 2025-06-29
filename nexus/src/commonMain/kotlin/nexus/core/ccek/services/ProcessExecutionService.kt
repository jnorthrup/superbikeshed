package nexus.core.ccek.services

import kotlin.coroutines.CoroutineContext

// Reusing CCEKContext placeholder from DgmInterfaces.kt
// typealias CCEKContext = CoroutineContext

data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
    val exception: String? = null // For capturing exceptions during execution
)

expect interface ProcessExecutionService {
    suspend fun executeProcess(
        command: List<String>,
        workingDirectory: String? = null,
        timeoutMillis: Long = 30000L, // Default timeout 30 seconds
        environmentVars: Map<String, String> = emptyMap(),
        context: CCEKContext
    ): ProcessResult

    companion object Key : CoroutineContext.Key<ProcessExecutionService>
}
