package nexus.core.dgm.services

import nexus.core.dgm.*
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Join

// Data structures for Kotlin-to-Python communication (as per design doc)

data class DgmTaskForPythonFile(
    val filePath: FilePath,
    val content: FileContent
)

data class LangchainAgentConfigForPython(
    val agentName: String,
    val tools: List<String>
)

data class DgmTaskForPython(
    val taskId: TaskId,
    val parentEntryId: ArchiveEntryId,
    val benchmarkId: BenchmarkId, // For context for the LLM
    val prompt: String, // Specific instruction for LLM (could be generated or passed through)
    val codeFiles: List<DgmTaskForPythonFile>, // Relevant files from CodeSnapshot
    val langchainAgentConfig: LangchainAgentConfigForPython? // Optional: specific Langchain agent/tool setup
)

data class ProposedChangeFileFromPython(
    val filePath: FilePath,
    val content: FileContent // Full new content or a patch string
)

data class ProposedChangesFromPython(
    val taskId: TaskId,
    val changedFiles: List<ProposedChangeFileFromPython>,
    val llmOutputLog: String?,
    val status: String, // "SUCCESS" or "ERROR"
    val errorMessage: String? // if status is ERROR
)

// Expect interface for the external solution proposer (Python/Langchain component)
expect interface ExternalSolutionProposer {
    suspend fun propose(request: DgmTaskForPython, context: CCEKContext): ProposedChangesFromPython
}

// Actual stub implementation for commonMain
// In a real KMP project, platform-specific actuals would handle HTTP/gRPC/IPC.
internal actual interface ExternalSolutionProposer {
    actual suspend fun propose(request: DgmTaskForPython, context: CCEKContext): ProposedChangesFromPython {
        println("MockExternalSolutionProposer: Simulating proposal for task: ${request.taskId}, prompt: ${request.prompt}")
        // Simulate some delay
        // kotlinx.coroutines.delay(200) // Would require kotlinx-coroutines-core

        // Return a default mock success response with a simple change
        val originalFile = request.codeFiles.firstOrNull()
        val changedContent = originalFile?.let { "// Modified by LLM\n${it.content}" } ?: "// New file created by LLM"
        val changedFilePath = originalFile?.filePath ?: "new_file_by_llm.kt"

        return ProposedChangesFromPython(
            taskId = request.taskId,
            changedFiles = listOf(ProposedChangeFileFromPython(changedFilePath, changedContent)),
            llmOutputLog = "LLM interaction log: Prompted with '${request.prompt}', produced a change for $changedFilePath.",
            status = "SUCCESS",
            errorMessage = null
        )
    }
}

// Concrete implementation of the executor for injection or default usage, using the stub
internal class MockExternalSolutionProposerImpl : ExternalSolutionProposer {
    // Leverages default methods in actual interface if any, or direct call.
    // For this stub, it directly calls the actual's logic.
    private val stub = object : ExternalSolutionProposer {}
    override suspend fun propose(request: DgmTaskForPython, context: CCEKContext): ProposedChangesFromPython {
        return stub.propose(request, context)
    }
}

class RemoteSolutionProposerService(
    private val proposerExecutor: ExternalSolutionProposer = MockExternalSolutionProposerImpl() // Injectable, defaults to mock
) : SolutionProposerService {

    override suspend fun proposeSolution(
        task: DgmTask,
        parentCode: CodeSnapshot,
        context: CCEKContext
    ): ImprovementCandidate {
        // 1. Prepare DgmTaskForPython
        val taskId = task.a.a
        val parentEntryId = task.a.b
        val benchmarkId = task.b.a
        // TODO: A more sophisticated prompt generation strategy would likely exist here.
        // For now, using a placeholder or a summary of task parameters.
        val promptForPython = "Improve code for task $taskId related to benchmark $benchmarkId. Parameters: ${task.b.b.joinToString()}"

        val codeFilesForPython = mutableListOf<DgmTaskForPythonFile>()
        parentCode.forEachIndexed { _, join ->
            codeFilesForPython.add(DgmTaskForPythonFile(join.a, join.b))
        }

        // Example Langchain config - this could be dynamic or part of the task.
        val langchainConfig = LangchainAgentConfigForPython(
            agentName = "code-refactor-agent",
            tools = listOf("file-read", "code-edit", "code-analysis")
        )

        val pythonRequest = DgmTaskForPython(
            taskId = taskId,
            parentEntryId = parentEntryId,
            benchmarkId = benchmarkId,
            prompt = promptForPython,
            codeFiles = codeFilesForPython,
            langchainAgentConfig = langchainConfig
        )

        // 2. Call ExternalSolutionProposer
        val pythonResponse = proposerExecutor.propose(pythonRequest, context)

        // 3. Convert ProposedChangesFromPython to ImprovementCandidate
        if (pythonResponse.status != "SUCCESS") {
            // Handle error case, potentially throwing an exception or returning a candidate with error info
            // For now, let's assume an error means no changes and log in rationale
            return ImprovementCandidate(
                Join(task, Indexed.empty()), // No changes
                Join(
                    "llm_error_handler", // ProposerId indicating error
                    Indexed.ofList(listOfNotNull("Proposal failed: ${pythonResponse.errorMessage}", pythonResponse.llmOutputLog))
                )
            )
        }

        val proposedCodeChanges = mutableListOf<Join<FilePath, FileContent>>()
        pythonResponse.changedFiles.forEach {
            proposedCodeChanges.add(Join(it.filePath, it.content))
        }
        val proposedCodeSnapshot = Indexed.ofList(proposedCodeChanges)

        // TODO: ProposerId should ideally come from the response or be configurable
        val proposerId: ProposerId = pythonResponse.langchainAgentConfig?.agentName ?: "unknown_python_agent"
        val rationale: Indexed<String> = Indexed.ofList(listOfNotNull(pythonResponse.llmOutputLog))

        return ImprovementCandidate(
            Join(task, proposedCodeSnapshot),
            Join(proposerId, rationale)
        )
    }
}
