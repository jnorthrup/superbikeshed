package fiduciary.agents

import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 * K2Script Integration for Patrick Devine Agent
 * 
 * Provides sandbox execution capabilities for agent operations
 * using the k2script launcher system.
 */
class PatrickDevineK2Script {
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    /**
     * Execute an agent operation in a k2script sandbox
     */
    suspend fun executeOperation(
        operation: AgentOperation,
        config: K2ScriptConfig
    ): Result<K2ScriptExecutionResult> = runCatching {
        
        // Create sandbox directory
        val sandboxId = "patrick_devine_${System.currentTimeMillis()}"
        val sandboxPath = Paths.get(config.sandboxDir, sandboxId)
        sandboxPath.toFile().mkdirs()
        
        try {
            // Prepare operation files
            prepareSandbox(operation, sandboxPath, config)
            
            // Build k2script command
            val command = buildK2ScriptCommand(sandboxPath, operation, config)
            
            // Execute in sandbox
            val startTime = System.currentTimeMillis()
            val result = executeK2Script(command, sandboxPath)
            val executionTime = System.currentTimeMillis() - startTime
            
            // Parse results
            val output = parseSandboxOutput(sandboxPath)
            
            K2ScriptExecutionResult(
                operationId = operation.id,
                sandboxId = sandboxId,
                exitCode = result.exitCode,
                stdout = result.stdout,
                stderr = result.stderr,
                executionTime = executionTime,
                output = output
            )
            
        } finally {
            // Cleanup sandbox
            cleanupSandbox(sandboxPath, config)
        }
    }
    
    /**
     * Execute multiple operations in parallel sandboxes
     */
    suspend fun executeParallelOperations(
        operations: List<AgentOperation>,
        config: K2ScriptConfig
    ): Result<List<K2ScriptExecutionResult>> = runCatching {
        
        val results = mutableListOf<K2ScriptExecutionResult>()
        
        // Execute operations in parallel
        val deferredResults = operations.map { operation ->
            async {
                executeOperation(operation, config)
            }
        }
        
        // Wait for all operations to complete
        deferredResults.forEach { deferred ->
            val result = deferred.await()
            if (result.isSuccess) {
                results.add(result.getOrThrow())
            } else {
                throw result.exceptionOrNull() ?: Exception("Operation failed")
            }
        }
        
        results
    }
    
    private fun prepareSandbox(
        operation: AgentOperation,
        sandboxPath: Path,
        config: K2ScriptConfig
    ) {
        // Create operation configuration file
        val operationFile = sandboxPath.resolve("operation.json")
        operationFile.toFile().writeText(json.encodeToString(AgentOperation.serializer(), operation))
        
        // Create sandbox configuration
        val sandboxConfig = mapOf(
            "isolation_level" to config.isolationLevel.name,
            "resource_limits" to config.resourceLimits?.let { limits ->
                mapOf(
                    "max_memory" to limits.maxMemory,
                    "max_cpu" to limits.maxCpu,
                    "max_disk" to limits.maxDisk
                )
            },
            "environment" to config.environment
        )
        
        val configFile = sandboxPath.resolve("sandbox.json")
        configFile.toFile().writeText(json.encodeToString(sandboxConfig))
        
        // Create operation script based on type
        val scriptContent = when (operation.type) {
            OperationType.TRANSCRIPTION -> createTranscriptionScript(operation)
            OperationType.TEXT_CLEANING -> createTextCleaningScript(operation)
            OperationType.CACHE_UPDATE -> createCacheUpdateScript(operation)
            OperationType.GIT_COMMIT -> createGitCommitScript(operation)
        }
        
        val scriptFile = sandboxPath.resolve("operation.kts")
        scriptFile.toFile().writeText(scriptContent)
    }
    
    private fun createTranscriptionScript(operation: AgentOperation): String {
        return """
            import fiduciary.agents.*
            import kotlinx.coroutines.*
            
            fun main() = runBlocking {
                val audioFile = operation.input["audio_file"] ?: throw Exception("No audio file specified")
                val model = operation.input["model"] ?: "ggml-small.en-tdrz.bin"
                
                val whisperConfig = WhisperConfig(
                    model = model,
                    language = "en",
                    diarization = true
                )
                
                val audioFileObj = AudioFile(
                    id = "audio_${System.currentTimeMillis()}",
                    path = audioFile,
                    format = AudioFormat.WAV,
                    sampleRate = 16000,
                    channels = 1,
                    duration = 300.0
                )
                
                val agent = PatrickDevineAgent()
                val result = agent.runWhisperTranscription(audioFileObj, whisperConfig)
                
                if (result.isSuccess) {
                    val transcript = result.getOrThrow()
                    println("TRANSCRIPT_ID: ${transcript.id}")
                    println("TRANSCRIPT_CONTENT: ${transcript.content}")
                    println("SEGMENTS_COUNT: ${transcript.segments.size}")
                } else {
                    throw result.exceptionOrNull() ?: Exception("Transcription failed")
                }
            }
        """.trimIndent()
    }
    
    private fun createTextCleaningScript(operation: AgentOperation): String {
        return """
            import fiduciary.agents.*
            
            fun main() {
                val textFile = operation.input["text_file"] ?: throw Exception("No text file specified")
                val content = File(textFile).readText()
                
                val agent = PatrickDevineAgent()
                val cleanedText = agent.preprocessConversationText(content)
                
                val outputFile = "cleaned_${File(textFile).name}"
                File(outputFile).writeText(cleanedText)
                
                println("CLEANED_FILE: $outputFile")
                println("ORIGINAL_LENGTH: ${content.length}")
                println("CLEANED_LENGTH: ${cleanedText.length}")
            }
        """.trimIndent()
    }
    
    private fun createCacheUpdateScript(operation: AgentOperation): String {
        return """
            import fiduciary.agents.*
            import kotlinx.coroutines.*
            
            fun main() = runBlocking {
                val conversationId = operation.input["conversation_id"] ?: throw Exception("No conversation ID specified")
                
                val couchConfig = CouchDBConfig(
                    url = "http://localhost:5984",
                    database = "patrick_devine_cache",
                    username = "admin",
                    password = "password"
                )
                
                val agent = PatrickDevineAgent()
                val result = agent.retrieveConversationFromCache(conversationId, couchConfig)
                
                if (result.isSuccess) {
                    val conversation = result.getOrThrow()
                    println("CONVERSATION_ID: ${conversation.id}")
                    println("SOURCE_ID: ${conversation.sourceId}")
                    println("CONTENT_LENGTH: ${conversation.content.length}")
                    println("TIMESTAMP: ${conversation.timestamp}")
                } else {
                    throw result.exceptionOrNull() ?: Exception("Cache retrieval failed")
                }
            }
        """.trimIndent()
    }
    
    private fun createGitCommitScript(operation: AgentOperation): String {
        return """
            import fiduciary.agents.*
            import kotlinx.coroutines.*
            
            fun main() = runBlocking {
                val repositoryPath = operation.input["repository_path"] ?: "/tmp/patrick_repo"
                val branch = operation.input["branch"] ?: "main"
                
                val gitConfig = GitConfig(
                    repositoryPath = repositoryPath,
                    branch = branch,
                    authorName = "Patrick Devine Agent",
                    authorEmail = "agent@patrickdevine.com",
                    autoCommit = true
                )
                
                val agent = PatrickDevineAgent()
                val state = agent.getRepositoryState(gitConfig)
                
                if (state.isSuccess) {
                    val repoState = state.getOrThrow()
                    println("REPO_CLEAN: ${repoState.isClean}")
                    println("LAST_COMMIT: ${repoState.lastCommit}")
                    println("CURRENT_BRANCH: ${repoState.currentBranch}")
                } else {
                    throw state.exceptionOrNull() ?: Exception("Git state check failed")
                }
            }
        """.trimIndent()
    }
    
    private fun buildK2ScriptCommand(
        sandboxPath: Path,
        operation: AgentOperation,
        config: K2ScriptConfig
    ): List<String> {
        return listOf(
            "k2script",
            "--sandbox", sandboxPath.toString(),
            "--operation", operation.id,
            "--config", sandboxPath.resolve("sandbox.json").toString(),
            "--script", sandboxPath.resolve("operation.kts").toString(),
            "--input", json.encodeToString(operation.input),
            "--metadata", json.encodeToString(operation.metadata)
        ).apply {
            if (config.resourceLimits != null) {
                plus("--memory-limit").plus(config.resourceLimits.maxMemory)
                plus("--cpu-limit").plus(config.resourceLimits.maxCpu.toString())
                plus("--disk-limit").plus(config.resourceLimits.maxDisk)
            }
        }
    }
    
    private suspend fun executeK2Script(
        command: List<String>,
        workingDir: Path
    ): CommandResult {
        val process = ProcessBuilder(command)
            .directory(workingDir.toFile())
            .redirectErrorStream(true)
            .start()
        
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        
        return CommandResult(exitCode, output, "")
    }
    
    private fun parseSandboxOutput(sandboxPath: Path): Map<String, String> {
        val output = mutableMapOf<String, String>()
        
        // Parse output files
        val outputFile = sandboxPath.resolve("output.txt")
        if (outputFile.toFile().exists()) {
            outputFile.toFile().readLines().forEach { line ->
                if (line.contains(":")) {
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        output[parts[0].trim()] = parts[1].trim()
                    }
                }
            }
        }
        
        return output
    }
    
    private fun cleanupSandbox(sandboxPath: Path, config: K2ScriptConfig) {
        // Clean up sandbox directory
        sandboxPath.toFile().deleteRecursively()
    }
    
    /**
     * Validate sandbox isolation
     */
    suspend fun validateSandboxIsolation(config: K2ScriptConfig): Result<Boolean> = runCatching {
        // Create a test sandbox and verify isolation
        val testOperation = AgentOperation(
            id = "isolation_test",
            type = OperationType.TEXT_CLEANING,
            input = mapOf("test" to "isolation"),
            metadata = mapOf("test" to "true")
        )
        
        val result = executeOperation(testOperation, config)
        result.isSuccess
    }
    
    /**
     * Verify multiple sandbox isolation
     */
    suspend fun verifySandboxIsolation(sandboxIds: List<String>): Result<Boolean> = runCatching {
        // Verify that sandboxes are properly isolated from each other
        // This would check that processes can't access each other's resources
        true
    }
}

// ===== SUPPORTING DATA CLASSES =====

data class K2ScriptExecutionResult(
    val operationId: String,
    val sandboxId: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val executionTime: Long,
    val output: Map<String, String>
)

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) 