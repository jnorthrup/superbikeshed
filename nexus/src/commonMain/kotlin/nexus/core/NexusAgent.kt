package nexus.core

import borg.trikeshed.lib.*
import k2script.ai.llm.LiteLLMClient
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass

// NexusAgent: Main entry point for the Nexus agent, following k2script patterns.
class NexusAgent(
    private val config: NexusConfig,
    private val actionExecutor: ActionExecutor,
    private val llmClient: LiteLLMClient
) {

    companion object {
        fun build(block: Builder.() -> Unit): NexusAgent {
            val builder = Builder()
            builder.block()
            return builder.build()
        }
    }

    class Builder {
        private var config: NexusConfig? = null
        private var actionExecutor: ActionExecutor? = null
        private var llmClient: LiteLLMClient? = null

        fun withConfig(config: NexusConfig) = apply { this.config = config }
        fun withActionExecutor(executor: ActionExecutor) = apply { this.actionExecutor = executor }
        fun withLlmClient(client: LiteLLMClient) = apply { this.llmClient = client }

        fun build(): NexusAgent {
            val finalConfig = config ?: NexusConfigBuilder().build()
            val finalLlmClient = llmClient ?: LiteLLMClient
            val finalActionExecutor = actionExecutor ?: DefaultActionExecutor(finalLlmClient) // Default implementation

            return NexusAgent(finalConfig, finalActionExecutor, finalLlmClient)
        }
    }

    suspend fun start() {
        println("Starting Nexus Agent with config: ${config.nodeId}")
        // Initialize services, connect to network, etc.
        llmClient.startService()
    }

    suspend fun stop() {
        println("Stopping Nexus Agent: ${config.nodeId}")
        // Clean up resources, disconnect from network, etc.
        llmClient.stopService()
    }

    suspend fun executeTask(task: AgentTask): TaskResult {
        println("Executing task: ${task.type} with parameters: ${task.parameters}")
        return actionExecutor.execute(task)
    }

    suspend fun chat(prompt: String): String {
        println("Agent received chat prompt: $prompt")
        val messages = listOf(
            mapOf("role" to "user", "content" to prompt)
        )
        val response = llmClient.complete(
            model = config.llmModel,
            messages = messages,
            temperature = 0.7
        ).join() // Blocking call for simplicity, consider async in real app

        return response.content ?: "No response from LLM."
    }
}

// NexusConfig: Configuration for the Nexus Agent.
data class NexusConfig(
    val nodeId: NodeId,
    val networkId: NetworkId,
    val llmModel: String,
    val customConfig: MetaSeries<String, String>
)

// NexusConfigBuilder: Builder for NexusConfig, similar to kscript's ConfigBuilder.
class NexusConfigBuilder {
    private var nodeId: NodeId = NodeId("default-nexus-node")
    private var networkId: NetworkId = NetworkId("nexus-mainnet")
    private var llmModel: String = "gpt-4o"
    private var customConfig: MetaSeries<String, String> = MetaSeries()

    fun withNodeId(id: String) = apply { this.nodeId = NodeId(id) }
    fun withNetworkId(id: String) = apply { this.networkId = NetworkId(id) }
    fun withLlmModel(model: String) = apply { this.llmModel = model }
    fun withCustomConfig(key: String, value: String) = apply { this.customConfig = this.customConfig.put(key j value) }

    fun build(): NexusConfig {
        return NexusConfig(nodeId, networkId, llmModel, customConfig)
    }
}

// ActionExecutor: Interface for executing agent actions.
interface ActionExecutor {
    suspend fun execute(task: AgentTask): TaskResult
}

// DefaultActionExecutor: A basic implementation of ActionExecutor.
class DefaultActionExecutor(private val llmClient: LiteLLMClient) : ActionExecutor {
    override suspend fun execute(task: AgentTask): TaskResult {
        return when (task.type) {
            "analyze_project_structure" -> {
                // Simulate analysis using LLM
                val prompt = "Analyze the project structure based on the following context: ${task.parameters["context"]}"
                val messages = listOf(mapOf("role" to "user", "content" to prompt))
                val response = llmClient.complete(
                    model = "gpt-4o", // Use a capable model for analysis
                    messages = messages
                ).join()
                TaskResult.Success(response.content ?: "Analysis failed.")
            }
            "generate_code" -> {
                val prompt = "Generate Kotlin code for: ${task.parameters["description"]}"
                val messages = listOf(mapOf("role" to "user", "content" to prompt))
                val response = llmClient.complete(
                    model = "gpt-4o",
                    messages = messages
                ).join()
                TaskResult.Success(response.content ?: "Code generation failed.")
            }
            else -> TaskResult.Failure("Unknown task type: ${task.type}")
        }
    }
}

// Re-using existing data classes from DefaultNexusAgent.kt for now
// These would ideally be moved to a common 'nexus.shared.model' package or similar
// if they are truly shared across different Nexus implementations.

@Serializable
data class NodeId(val value: String)

@Serializable
data class NetworkId(val value: String)

@Serializable
data class AgentTask(
    val id: String,
    val type: String,
    val parameters: Map<String, String> = emptyMap(),
    val priority: Int = 0,
    val timeoutMs: Long = 60000,
)

@Serializable
sealed class TaskResult {
    @Serializable
    data class Success(val message: String, val data: Map<String, String> = emptyMap()) : TaskResult()

    @Serializable
    data class Failure(val error: String, val details: String? = null) : TaskResult()

    @Serializable
    data class Timeout(val taskId: String) : TaskResult()
}
