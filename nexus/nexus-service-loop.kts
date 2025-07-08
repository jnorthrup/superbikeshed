#!/usr/bin/env kotlin

@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
@file:DependsOn("io.ktor:ktor-client-core:2.3.7")
@file:DependsOn("io.ktor:ktor-client-cio:2.3.7")
@file:DependsOn("io.ktor:ktor-client-content-negotiation:2.3.7")
@file:DependsOn("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.*
import kotlin.random.Random
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * NEXUS SERVICE LOOP - Integrated Development Environment
 * 
 * Combines:
 * - Nexus autonomous agent system
 * - IntelliJ HTTP API integration  
 * - Nemotron LLM for intelligent responses
 * - Continuous service loop with monitoring
 */

// ═══════════════════════════════════════════════════════════════════════════════
// CORE TYPES AND UTILITIES
// ═══════════════════════════════════════════════════════════════════════════════

typealias Series<T> = List<T>
infix fun <A, B> A.j(b: B): Pair<A, B> = this to b
fun <T> Series<T>.α(transform: (T) -> T): Series<T> = this.map(transform)
val <T> Series<T>.`play`: List<T> get() = this

@JvmInline
value class CCEKContext(val data: Map<String, String>) {
    fun extractCurrentScope(): String = data["scope"] ?: "global"
    fun extractCurrentCapabilities(): List<String> = data["capabilities"]?.split(",") ?: emptyList()
    fun extractCurrentConstraints(): String = data["constraints"] ?: "none"
    fun extractCurrentPreferences(): String = data["preferences"] ?: "balanced"
}

@JvmInline  
value class Action(val data: String)

@JvmInline
value class Outcome(val data: String) {
    val success: Boolean get() = !data.contains("error", ignoreCase = true)
}

// ═══════════════════════════════════════════════════════════════════════════════
// NEMOTRON LLM INTEGRATION
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class LLMRequest(
    val model: String,
    val messages: List<Message>,
    val max_tokens: Int = 1000,
    val temperature: Double = 0.7
)

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class LLMResponse(
    val choices: List<Choice>
)

@Serializable
data class Choice(
    val message: Message
)

class NemotronClient(
    private val apiKey: String = System.getenv("NVIDIA_API_KEY") ?: System.getenv("HF_TOKEN") ?: "",
    private val useHuggingFace: Boolean = System.getenv("HF_TOKEN") != null
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
        }
    }
    
    private val model = if (useHuggingFace) {
        "nvidia/Llama-3.1-Nemotron-70B-Instruct-HF"
    } else {
        "nvidia/llama-3.1-nemotron-70b-instruct"
    }
    
    private val baseUrl = if (useHuggingFace) {
        "https://api-inference.huggingface.co/models"
    } else {
        "https://integrate.api.nvidia.com/v1"
    }
    
    suspend fun complete(prompt: String, systemPrompt: String? = null): String = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) return@withContext "[Error] No NVIDIA_API_KEY or HF_TOKEN found. Get free key at build.nvidia.com"
        
        try {
            val request = if (useHuggingFace) {
                val fullPrompt = buildString {
                    systemPrompt?.let { append("System: $it\n\n") }
                    append("User: $prompt\n\nAssistant:")
                }
                mapOf(
                    "inputs" to fullPrompt,
                    "parameters" to mapOf(
                        "max_new_tokens" to 1000,
                        "temperature" to 0.7
                    )
                )
            } else {
                val messages = mutableListOf<Message>()
                systemPrompt?.let { messages.add(Message("system", it)) }
                messages.add(Message("user", prompt))
                
                mapOf(
                    "model" to model,
                    "messages" to messages,
                    "temperature" to 0.7,
                    "max_tokens" to 1000
                )
            }
            
            val url = if (useHuggingFace) {
                "$baseUrl/$model"
            } else {
                "$baseUrl/chat/completions"
            }
            
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(request)
            }
            
            val responseText = response.bodyAsText()
            
            if (useHuggingFace) {
                // HF returns array: [{"generated_text": "..."}]
                val textMatch = """"generated_text"\s*:\s*"([^"\\]*(\\.[^"\\]*)*)"""".toRegex()
                    .find(responseText)
                textMatch?.groupValues?.get(1)?.replace("\\n", "\n") ?: responseText
            } else {
                // NVIDIA uses OpenAI format
                val contentMatch = """"content"\s*:\s*"([^"\\]*(\\.[^"\\]*)*)"""".toRegex()
                    .find(responseText)
                contentMatch?.groupValues?.get(1)?.replace("\\n", "\n") ?: "No response"
            }
        } catch (e: Exception) {
            "[Nemotron Error] ${e.message}\nTip: Get free API key at https://build.nvidia.com"
        }
    }
    
    fun isAvailable(): Boolean = apiKey.isNotEmpty()
}

// ═══════════════════════════════════════════════════════════════════════════════
// INTELLIJ API CLIENT
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class RefactorRequest(
    val type: String,
    val file: String,
    val offset: Int? = null,
    val startOffset: Int? = null,
    val endOffset: Int? = null,
    val params: Map<String, String> = emptyMap(),
    val preview: Boolean = false
)

@Serializable
data class RefactorResponse(
    val success: Boolean,
    val filesChanged: Int = 0,
    val errors: List<String> = emptyList()
)

sealed class RefactorOperation {
    data class Rename(
        val file: String,
        val offset: Int,
        val newName: String
    ) : RefactorOperation()
    
    data class ExtractMethod(
        val file: String,
        val startOffset: Int,
        val endOffset: Int,
        val methodName: String,
        val visibility: String = "public"
    ) : RefactorOperation()
}

sealed class IntelliJError {
    object NotConnected : IntelliJError()
    object ProjectNotOpen : IntelliJError()
    data class RefactoringFailed(val reason: String) : IntelliJError()
    data class ApiError(val code: Int, val message: String) : IntelliJError()
}

@Serializable
data class InspectionResult(
    val file: String,
    val line: Int,
    val severity: String,
    val message: String,
    val quickFixes: List<String> = emptyList()
)

@Serializable
data class CompletionItem(
    val text: String,
    val type: String,
    val description: String? = null
)

class IntelliJApiClient(
    private val baseUrl: String = "http://localhost:63342/api",
    private val projectPath: String,
    private val timeout: Long = 30000L
) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = timeout
        }
    }
    
    suspend fun isConnected(): Boolean = try {
        client.get("$baseUrl/status").status == HttpStatusCode.OK
    } catch (e: Exception) {
        false
    }
    
    suspend fun executeRefactoring(operation: RefactorOperation): Pair<RefactorResponse?, IntelliJError> {
        if (!isConnected()) return null to IntelliJError.NotConnected
        
        val request = when (operation) {
            is RefactorOperation.Rename -> RefactorRequest(
                type = "rename",
                file = operation.file,
                offset = operation.offset,
                params = mapOf("newName" to operation.newName)
            )
            
            is RefactorOperation.ExtractMethod -> RefactorRequest(
                type = "extractMethod",
                file = operation.file,
                startOffset = operation.startOffset,
                endOffset = operation.endOffset,
                params = mapOf(
                    "name" to operation.methodName,
                    "visibility" to operation.visibility
                )
            )
        }
        
        return try {
            val response: RefactorResponse = client.post("$baseUrl/refactor") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
            
            if (response.success) {
                response to null
            } else {
                null to IntelliJError.RefactoringFailed(
                    response.errors.joinToString(", ")
                )
            }
        } catch (e: Exception) {
            null to IntelliJError.ApiError(500, e.message ?: "Unknown error")
        }
    }
    
    suspend fun runInspections(path: String): List<InspectionResult> = try {
        client.get("$baseUrl/inspection/run") {
            parameter("path", path)
            parameter("project", projectPath)
        }.body()
    } catch (e: Exception) {
        emptyList()
    }
    
    suspend fun getCompletions(
        file: String,
        line: Int,
        column: Int
    ): List<CompletionItem> = try {
        client.get("$baseUrl/completion") {
            parameter("file", file)
            parameter("line", line)
            parameter("column", column)
            parameter("project", projectPath)
        }.body()
    } catch (e: Exception) {
        emptyList()
    }
    
    fun close() {
        client.close()
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// NEXUS AGENT SYSTEM
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class DevelopmentTask(
    val id: String,
    val type: String,
    val description: String,
    val context: CCEKContext,
    val priority: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class Observation(
    val context: CCEKContext,
    val action: String,
    val outcome: Outcome,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class LearnedPattern(
    val pattern: String,
    val successRate: Double,
    val usageCount: Int,
    val lastUsed: Long
)

class NexusServiceLoop(
    private val projectPath: String = "/Users/jim/work/v2superbikeshed",
    private val nemotronClient: NemotronClient = NemotronClient(),
    private val intellijClient: IntelliJApiClient = IntelliJApiClient(projectPath = projectPath)
) {
    private val learningChannel = Channel<Observation>(Channel.UNLIMITED)
    private val taskQueue = Channel<DevelopmentTask>(Channel.UNLIMITED)
    private val observations = mutableListOf<Observation>()
    private val patterns = mutableMapOf<String, LearnedPattern>()
    private val isRunning = Channel<Boolean>(Channel.CONFLATED)
    
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    
    /**
     * Start the integrated service loop
     */
    suspend fun startServiceLoop() = coroutineScope {
        println("🚀 Starting Nexus Service Loop")
        println("=" * 60)
        println("📁 Project: $projectPath")
        println("🤖 LLM: ${if (nemotronClient.isAvailable()) "Nemotron Available" else "Nemotron Unavailable"}")
        println("🔧 IntelliJ: ${if (intellijClient.isConnected()) "Connected" else "Not Connected"}")
        println("=" * 60)
        
        isRunning.send(true)
        
        // Launch service subsystems
        val learningJob = launch { autonomousLearning() }
        val taskJob = launch { autonomousTaskExecution() }
        val monitoringJob = launch { environmentMonitoring() }
        val llmJob = launch { llmIntegration() }
        val intellijJob = launch { intellijIntegration() }
        
        // Main service loop
        try {
            while (isRunning.receive()) {
                val currentTime = LocalDateTime.now().format(formatter)
                println("[$currentTime] 🔄 Service loop iteration")
                
                // Process any pending tasks
                processPendingTasks()
                
                // Generate autonomous tasks
                if (Random.nextDouble() < 0.2) { // 20% chance
                    generateAutonomousTask()
                }
                
                delay(5000) // 5 second cycle
            }
        } catch (e: Exception) {
            println("❌ Service loop error: ${e.message}")
        } finally {
            // Cleanup
            learningJob.cancel()
            taskJob.cancel()
            monitoringJob.cancel()
            llmJob.cancel()
            intellijJob.cancel()
            
            println("🏁 Service loop stopped")
        }
    }
    
    /**
     * Autonomous learning subsystem
     */
    private suspend fun autonomousLearning() {
        println("🧠 Learning subsystem started...")
        
        while (true) {
            try {
                val observation = learningChannel.receive()
                observations.add(observation)
                
                // Extract patterns from observations
                extractAndUpdatePatterns(observation)
                
                if (observations.size % 10 == 0) {
                    println("   📈 Learned ${patterns.size} patterns from ${observations.size} observations")
                }
                
            } catch (e: Exception) {
                println("   ⚠️ Learning error: ${e.message}")
            }
        }
    }
    
    /**
     * Autonomous task execution subsystem
     */
    private suspend fun autonomousTaskExecution() {
        println("⚡ Task execution subsystem started...")
        
        while (true) {
            try {
                val task = taskQueue.receive()
                
                println("   🎯 Executing task: ${task.description}")
                val outcome = executeTask(task)
                
                // Learn from execution
                val observation = Observation(
                    context = task.context,
                    action = task.type,
                    outcome = outcome,
                    timestamp = System.currentTimeMillis()
                )
                learningChannel.trySend(observation)
                
                delay(Random.nextLong(1000, 3000)) // Simulate task execution time
                
            } catch (e: Exception) {
                println("   ❌ Task execution error: ${e.message}")
            }
        }
    }
    
    /**
     * Environment monitoring subsystem
     */
    private suspend fun environmentMonitoring() {
        println("🖥️ Environment monitoring started...")
        
        while (true) {
            try {
                // Check IntelliJ connection
                val intellijConnected = intellijClient.isConnected()
                if (!intellijConnected) {
                    println("   ⚠️ IntelliJ connection lost")
                }
                
                // Check Nemotron availability
                if (!nemotronClient.isAvailable()) {
                    println("   ⚠️ Nemotron LLM unavailable")
                }
                
                // Generate monitoring tasks
                val context = CCEKContext(mapOf(
                    "scope" to "monitoring",
                    "capabilities" to "intellij,nemotron",
                    "intellij_connected" to intellijConnected.toString(),
                    "nemotron_available" to nemotronClient.isAvailable().toString()
                ))
                
                delay(10000) // Check every 10 seconds
                
            } catch (e: Exception) {
                println("   ❌ Monitoring error: ${e.message}")
            }
        }
    }
    
    /**
     * LLM integration subsystem
     */
    private suspend fun llmIntegration() {
        println("🤖 LLM integration started...")
        
        while (true) {
            try {
                // Process LLM requests
                if (nemotronClient.isAvailable()) {
                    // Example: Ask LLM for code suggestions
                    val prompt = "Suggest improvements for a Kotlin service loop architecture"
                    val response = nemotronClient.complete(
                        prompt = prompt,
                        systemPrompt = "You are a helpful AI assistant specializing in Kotlin development and system architecture."
                    )
                    
                    println("   💡 LLM Suggestion: ${response.take(100)}...")
                }
                
                delay(15000) // LLM queries every 15 seconds
                
            } catch (e: Exception) {
                println("   ❌ LLM integration error: ${e.message}")
            }
        }
    }
    
    /**
     * IntelliJ integration subsystem
     */
    private suspend fun intellijIntegration() {
        println("🔧 IntelliJ integration started...")
        
        while (true) {
            try {
                if (intellijClient.isConnected()) {
                    // Run inspections on the project
                    val inspections = intellijClient.runInspections(projectPath)
                    if (inspections.isNotEmpty()) {
                        println("   🔍 Found ${inspections.size} inspection issues")
                        inspections.take(3).forEach { issue ->
                            println("      ${issue.file}:${issue.line} - ${issue.message}")
                        }
                    }
                }
                
                delay(20000) // IntelliJ operations every 20 seconds
                
            } catch (e: Exception) {
                println("   ❌ IntelliJ integration error: ${e.message}")
            }
        }
    }
    
    /**
     * Execute a development task
     */
    private suspend fun executeTask(task: DevelopmentTask): Outcome {
        return when (task.type) {
            "refactor" -> executeRefactorTask(task)
            "inspect" -> executeInspectTask(task)
            "suggest" -> executeSuggestTask(task)
            else -> Outcome("Unknown task type: ${task.type}")
        }
    }
    
    private suspend fun executeRefactorTask(task: DevelopmentTask): Outcome {
        // Example refactoring task
        val operation = RefactorOperation.Rename(
            file = "$projectPath/nexus/src/commonMain/kotlin/nexus/CoreTypes.kt",
            offset = 100,
            newName = "ImprovedType"
        )
        
        val (response, error) = intellijClient.executeRefactoring(operation)
        
        return if (error == null) {
            Outcome("Refactoring successful: ${response?.filesChanged} files changed")
        } else {
            Outcome("Refactoring failed: $error")
        }
    }
    
    private suspend fun executeInspectTask(task: DevelopmentTask): Outcome {
        val inspections = intellijClient.runInspections(projectPath)
        return Outcome("Found ${inspections.size} inspection issues")
    }
    
    private suspend fun executeSuggestTask(task: DevelopmentTask): Outcome {
        if (!nemotronClient.isAvailable()) {
            return Outcome("LLM unavailable for suggestions")
        }
        
        val suggestion = nemotronClient.complete(
            prompt = task.description,
            systemPrompt = "You are a Kotlin development expert. Provide concise, actionable suggestions."
        )
        
        return Outcome("LLM suggestion: ${suggestion.take(200)}...")
    }
    
    /**
     * Generate autonomous tasks based on current state
     */
    private suspend fun generateAutonomousTask() {
        val taskTypes = listOf("refactor", "inspect", "suggest")
        val randomType = taskTypes.random()
        
        val task = DevelopmentTask(
            id = "auto_${System.currentTimeMillis()}",
            type = randomType,
            description = "Autonomous ${randomType} task",
            context = CCEKContext(mapOf(
                "scope" to "autonomous",
                "capabilities" to "intellij,nemotron",
                "priority" to "medium"
            )),
            priority = Random.nextInt(1, 5)
        )
        
        taskQueue.trySend(task)
        println("   🎲 Generated autonomous task: ${task.type}")
    }
    
    /**
     * Process any pending tasks
     */
    private suspend fun processPendingTasks() {
        // Process any tasks in the queue
        while (!taskQueue.isEmpty) {
            val task = taskQueue.tryReceive()
            if (task != null) {
                println("   📋 Processing pending task: ${task.description}")
                val outcome = executeTask(task)
                println("   ✅ Task completed: ${outcome.data}")
            }
        }
    }
    
    /**
     * Extract and update patterns from observations
     */
    private fun extractAndUpdatePatterns(observation: Observation) {
        val pattern = "${observation.action}_${observation.outcome.success}"
        val current = patterns[pattern]
        
        val newSuccessRate = if (current != null) {
            (current.successRate * current.usageCount + (if (observation.outcome.success) 1.0 else 0.0)) / (current.usageCount + 1)
        } else {
            if (observation.outcome.success) 1.0 else 0.0
        }
        
        patterns[pattern] = LearnedPattern(
            pattern = pattern,
            successRate = newSuccessRate,
            usageCount = (current?.usageCount ?: 0) + 1,
            lastUsed = System.currentTimeMillis()
        )
    }
    
    /**
     * Stop the service loop
     */
    suspend fun stopServiceLoop() {
        isRunning.send(false)
    }
    
    /**
     * Get service status
     */
    fun getStatus(): Map<String, Any> = mapOf(
        "running" to !isRunning.isEmpty,
        "observations" to observations.size,
        "patterns" to patterns.size,
        "nemotron_available" to nemotronClient.isAvailable(),
        "intellij_connected" to runBlocking { intellijClient.isConnected() }
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN SERVICE LOOP
// ═══════════════════════════════════════════════════════════════════════════════

fun main() = runBlocking {
    println("🚀 Nexus Service Loop - Integrated Development Environment")
    println("=" * 70)
    
    // Initialize service loop
    val serviceLoop = NexusServiceLoop()
    
    // Start the service
    val serviceJob = launch {
        serviceLoop.startServiceLoop()
    }
    
    // Run for demonstration (30 seconds)
    delay(30000)
    
    // Stop the service
    serviceLoop.stopServiceLoop()
    serviceJob.join()
    
    // Show final status
    val status = serviceLoop.getStatus()
    println("\n📊 Final Service Status:")
    status.forEach { (key, value) ->
        println("   $key: $value")
    }
    
    println("\n🏁 Service loop demonstration completed")
} 