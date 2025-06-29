package nexus.core.dgm.services

import nexus.core.dgm.*
import java.io.File

// Placeholder for kotlinx.serialization.json.Json and encodeToString/decodeFromString
// In a real project, add 'org.jetbrains.kotlinx:kotlinx-serialization-json' dependency.
object JsonPlaceholder {
    fun <T> encodeToString(serializer: (T) -> String, value: T): String {
        println("Warning: Using placeholder JSON serialization for ${value!!::class.simpleName}")
        // This is a very basic and fragile manual JSON construction for DgmTaskForPython
        if (value is DgmTaskForPython) {
            val codeFilesJson = value.codeFiles.joinToString(",") { """{"filePath":"${it.filePath.replace("\\", "\\\\")}","content":"${it.content.replace("\"", "\\\"").replace("\n", "\\n")}"}""" }
            val langConfigJson = value.langchainAgentConfig?.let { """{"agentName":"${it.agentName}","tools":[${it.tools.joinToString(",") { "\"$it\""}}]}""" } ?: "null"
            return """{"taskId":"${value.taskId}","parentEntryId":"${value.parentEntryId}","benchmarkId":"${value.benchmarkId}","prompt":"${value.prompt.replace("\"", "\\\"").replace("\n", "\\n")}","codeFiles":[$codeFilesJson],"langchainAgentConfig":$langConfigJson}"""
        }
        return "{ \"error\": \"Serialization not implemented for this type in placeholder\" }"
    }

    inline fun <reified T> decodeFromString(serializer: (String) -> T, value: String): T {
        println("Warning: Using placeholder JSON deserialization for ${T::class.simpleName}")
        // This is a very basic and fragile manual JSON parsing for ProposedChangesFromPython
        if (T::class == ProposedChangesFromPython::class) {
            // Simplified parsing: assumes a very specific structure and no complex escaped chars in content for now
            val taskId = value.substringAfter("taskId\":\"").substringBefore("\"")
            val status = value.substringAfter("status\":\"").substringBefore("\"")
            val llmLog = value.substringAfter("llmOutputLog\":\"","null").substringBefore("\"")
            val errorMsg = value.substringAfter("errorMessage\":\"","null").substringBefore("\"")

            val filesStr = value.substringAfter("changedFiles\":[").substringBefore("]")
            val changedFiles = mutableListOf<ProposedChangeFileFromPython>()
            if (filesStr.isNotEmpty()) {
                 filesStr.split("},{").map { it.trim('{', '}') }.forEach { fileStr ->
                    val path = fileStr.substringAfter("filePath\":\"").substringBefore("\"")
                    val content = fileStr.substringAfter("content\":\"").substringBeforeLast("\"")
                    changedFiles.add(ProposedChangeFileFromPython(path, content.replace("\\n", "\n").replace("\\\"", "\"")))
                }
            }

            @Suppress("UNCHECKED_CAST")
            return ProposedChangesFromPython(
                taskId = taskId,
                changedFiles = changedFiles,
                llmOutputLog = if(llmLog == "null") null else llmLog,
                status = status,
                errorMessage = if(errorMsg == "null") null else errorMsg
            ) as T
        }
        throw NotImplementedError("Deserialization not implemented for this type in placeholder for ${T::class.simpleName}")
    }
}


actual interface ExternalSolutionProposer {
    actual suspend fun propose(request: DgmTaskForPython, context: CCEKContext): ProposedChangesFromPython {
        // This actual implementation will be provided by JvmExternalSolutionProposer
        // For non-JVM targets, they would provide their own actual or throw NotImplementedError
        throw NotImplementedError("This actual should be implemented by a concrete class like JvmExternalSolutionProposer for JVM target.")
    }
}


class JvmExternalSolutionProposer(
    private val processExecutionService: ProcessExecutionService,
    private val scriptPath: String // Full path to dgm_agent_service.py or similar
) : ExternalSolutionProposer {

    override suspend fun propose(request: DgmTaskForPython, context: CCEKContext): ProposedChangesFromPython {
        val requestJson = try {
            // In a real project, use kotlinx.serialization: Json.encodeToString(DgmTaskForPython.serializer(), request)
            JsonPlaceholder.encodeToString({ task -> JsonPlaceholder.encodeToString(::dummyDgmTaskForPythonSerializer, task as DgmTaskForPython) }, request)
        } catch (e: Exception) {
            return ProposedChangesFromPython(
                taskId = request.taskId,
                changedFiles = emptyList(),
                llmOutputLog = null,
                status = "ERROR",
                errorMessage = "Failed to serialize request to JSON: ${e.message}"
            )
        }

        val scriptFile = File(scriptPath)
        val workingDir = scriptFile.parent // Run the script from its own directory

        val command = listOf("python3", scriptFile.name, requestJson)
        println("Executing: $command in $workingDir with JSON: $requestJson")


        val processResult = processExecutionService.executeProcess(
            command = command,
            workingDirectory = workingDir,
            timeoutMillis = 120000L, // 2 minutes, adjust as needed
            context = context
        )

        if (processResult.exitCode != 0 || processResult.timedOut || processResult.stderr.isNotBlank()) {
            val errorMsg = """
                Error executing Python solution proposer:
                Exit Code: ${processResult.exitCode}
                Timed Out: ${processResult.timedOut}
                Stderr: ${processResult.stderr.trim()}
                Stdout: ${processResult.stdout.trim()}
                Exception: ${processResult.exception ?: "None"}
            """.trimIndent()
            return ProposedChangesFromPython(
                taskId = request.taskId,
                changedFiles = emptyList(),
                llmOutputLog = processResult.stdout.ifBlank { null }, // stdout might contain partial LLM log
                status = "ERROR",
                errorMessage = errorMsg
            )
        }

        return try {
            // In a real project, use kotlinx.serialization: Json.decodeFromString(ProposedChangesFromPython.serializer(), processResult.stdout)
            JsonPlaceholder.decodeFromString(::dummyProposedChangesFromPythonDeserializer, processResult.stdout)
        } catch (e: Exception) {
            ProposedChangesFromPython(
                taskId = request.taskId,
                changedFiles = emptyList(),
                llmOutputLog = processResult.stdout,
                status = "ERROR",
                errorMessage = "Failed to deserialize response from JSON: ${e.message}. Raw output: ${processResult.stdout}"
            )
        }
    }
}

// Dummy serializers for JsonPlaceholder - these would not exist if using kotlinx.serialization
private fun dummyDgmTaskForPythonSerializer(task: DgmTaskForPython): String = TODO("Replace with kotlinx.serialization")
private fun dummyProposedChangesFromPythonDeserializer(jsonString: String): ProposedChangesFromPython = TODO("Replace with kotlinx.serialization")
