package nexus.core.ccek.services

// No specific imports needed for a stub that throws NotImplementedError

actual interface ProcessExecutionService {
    actual suspend fun executeProcess(
        command: List<String>,
        workingDirectory: String?,
        timeoutMillis: Long,
        environmentVars: Map<String, String>,
        context: CCEKContext
    ): ProcessResult {
        println("Warning: ProcessExecutionService.executeProcess is not implemented for WasmJs target.")
        // For a more robust stub, one might return a specific error ProcessResult:
        // return ProcessResult(
        //     exitCode = -1, // Or a specific error code
        //     stdout = "",
        //     stderr = "Process execution is not available on the WasmJs platform.",
        //     timedOut = false,
        //     exception = "NotImplementedError"
        // )
        throw NotImplementedError("Process execution is not available on the WasmJs platform.")
    }

    actual companion object Key : CoroutineContext.Key<ProcessExecutionService>
}

// Concrete class for instantiation if needed, though typically resolved via context
class WasmJsProcessExecutionService : ProcessExecutionService by object : ProcessExecutionService {}
