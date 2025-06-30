package nexus.core.dgm.services

import nexus.core.dgm.BenchmarkId
import nexus.core.dgm.FilePath
import java.io.File // For working directory File object

// Re-using JsonPlaceholder concept from JvmExternalSolutionProposer.
// In a real project, this would be a shared utility or kotlinx.serialization.
internal object JsonPlaceholderBenchmark {
    fun encodeBenchmarkInvocation(value: BenchmarkInvocation): String {
        println("Warning: Using placeholder JSON serialization for BenchmarkInvocation")
        return """{"benchmarkId":"${value.benchmarkId}","workspacePath":"${value.workspacePath.replace("\\", "\\\\")}","timeoutSeconds":${value.timeoutSeconds}}"""
    }

    fun decodeBenchmarkResult(value: String): BenchmarkResultFromPython {
        println("Warning: Using placeholder JSON deserialization for BenchmarkResultFromPython")
        // Simplified parsing, assumes a very specific structure
        val benchmarkId = value.substringAfter("benchmarkId\":\"").substringBefore("\"")
        val status = value.substringAfter("status\":\"").substringBefore("\"")
        val logOutput = value.substringAfter("logOutput\":\"","null").substringBefore("\"")
        val errorDetails = value.substringAfter("errorDetails\":\"","null").substringBefore("\"")

        val scoresStr = value.substringAfter("scores\":[").substringBefore("]")
        val scores = mutableListOf<BenchmarkScoreFromPython>()
        if (scoresStr.isNotEmpty()) {
            scoresStr.split("},{").map { it.trim('{', '}') }.forEach { scoreStr ->
                val metricName = scoreStr.substringAfter("metricName\":\"").substringBefore("\"")
                val metricValue = scoreStr.substringAfter("value\":").substringBefore("}").toDouble()
                scores.add(BenchmarkScoreFromPython(metricName, metricValue))
            }
        }
        return BenchmarkResultFromPython(
            benchmarkId = benchmarkId,
            status = status,
            scores = scores,
            logOutput = if(logOutput == "null") null else logOutput.replace("\\n", "\n").replace("\\\"", "\""),
            errorDetails = if(errorDetails == "null") null else errorDetails.replace("\\n", "\n").replace("\\\"", "\"")
        )
    }
}

actual interface ExternalBenchmarkExecutor {
    actual suspend fun execute(request: BenchmarkInvocation, context: CCEKContext): BenchmarkResultFromPython {
        // This actual implementation will be provided by JvmExternalBenchmarkExecutor
        throw NotImplementedError("This actual should be implemented by a concrete class like JvmExternalBenchmarkExecutor for JVM target.")
    }
}

class JvmExternalBenchmarkExecutor(
    private val processExecutionService: ProcessExecutionService,
    private val scriptPath: String // Full path to dgm_benchmark_service.py or similar
) : ExternalBenchmarkExecutor {

    override suspend fun execute(request: BenchmarkInvocation, context: CCEKContext): BenchmarkResultFromPython {
        val requestJson = try {
            JsonPlaceholderBenchmark.encodeBenchmarkInvocation(request)
        } catch (e: Exception) {
            return BenchmarkResultFromPython(
                benchmarkId = request.benchmarkId,
                status = "ERROR",
                scores = emptyList(),
                logOutput = null,
                errorMessage = "Failed to serialize BenchmarkInvocation to JSON: ${e.message}"
            )
        }

        val scriptFile = File(scriptPath)
        // The working directory for the benchmark script IS the workspacePath from the request.
        val workingDir = request.workspacePath

        // Ensure working directory exists, ProcessExecutionService might also do this
        // but an early check can provide a clearer error.
        val wdFile = File(workingDir)
        if (!wdFile.exists() || !wdFile.isDirectory) {
             return BenchmarkResultFromPython(
                benchmarkId = request.benchmarkId,
                status = "ERROR",
                scores = emptyList(),
                logOutput = null,
                errorMessage = "Workspace directory '$workingDir' does not exist or is not a directory."
            )
        }


        val command = listOf("python3", scriptFile.name, requestJson) // Script name, not full path, as WD is script's dir
        println("Executing benchmark: $command in $workingDir with JSON: $requestJson")

        val processResult = processExecutionService.executeProcess(
            command = command,
            workingDirectory = workingDir, // Crucially, this is the workspacePath for the benchmark
            timeoutMillis = request.timeoutSeconds * 1000L, // Convert seconds from request to millis
            context = context
        )

        if (processResult.exitCode != 0 || processResult.timedOut || processResult.stderr.isNotBlank()) {
            val errorMsg = """
                Error executing Python benchmark executor:
                Exit Code: ${processResult.exitCode}
                Timed Out: ${processResult.timedOut}
                Stderr: ${processResult.stderr.trim()}
                Stdout: ${processResult.stdout.trim()}
                Exception: ${processResult.exception ?: "None"}
            """.trimIndent()
            return BenchmarkResultFromPython(
                benchmarkId = request.benchmarkId,
                status = "ERROR", // Or a more specific error status based on exit code/timeout
                scores = emptyList(),
                logOutput = processResult.stdout.ifBlank { null },
                errorMessage = errorMsg
            )
        }

        return try {
            JsonPlaceholderBenchmark.decodeBenchmarkResult(processResult.stdout)
        } catch (e: Exception) {
            BenchmarkResultFromPython(
                benchmarkId = request.benchmarkId,
                status = "ERROR",
                scores = emptyList(),
                logOutput = processResult.stdout,
                errorMessage = "Failed to deserialize benchmark response from JSON: ${e.message}. Raw output: ${processResult.stdout}"
            )
        }
    }
}

// Helper, as `errorMessage` is not a field in `BenchmarkResultFromPython`
// but it is useful for conveying errors from this execution layer.
// We'll use logOutput or errorDetails if they are null in the original object.
private fun BenchmarkResultFromPython(
    benchmarkId: BenchmarkId,
    status: String,
    scores: List<BenchmarkScoreFromPython>,
    logOutput: String?,
    errorMessage: String? // This is for execution errors, not benchmark errors parsed from JSON
): BenchmarkResultFromPython {
    return BenchmarkResultFromPython(
        benchmarkId = benchmarkId,
        status = status,
        scores = scores,
        logOutput = logOutput ?: (if (status == "ERROR") errorMessage else null),
        errorDetails = if (status == "ERROR") errorMessage else null
    )
}
