@file:JsExport
import kotlinx.serialization.Serializable
import kotlin.js.JsExport

// Placeholder for TrikeShed Series
@JsExport
@Serializable
class Series<T>( initialItems: List<T> = emptyList() ) { // Primary constructor for initialization
    // Make items publicly accessible for tests but immutable from outside to encourage using 'add'
    val items: List<T> = initialItems.toList() // Store as an immutable list internally

    // Returns a new Series with the event added
    fun add(event: T): Series<T> = Series(items + event)

    fun <R> alpha(transform: (T) -> R): Series<R> = Series(items.map(transform))

    infix fun <B> join(other: Series<B>): Series<Join<T, B>> {
        // Simple zip-like behavior
        val newSize = minOf(this.items.size, other.items.size)
        val joinedItems = mutableListOf<Join<T,B>>()
        for (i in 0 until newSize) {
            joinedItems.add(Join(this.items[i], other.items[i]))
        }
        return Series(joinedItems)
    }
}

// Placeholder for TrikeShed Join
@JsExport
@Serializable
// Made properties nullable and var for easier test setup as requested, though 'val' with default null is also an option.
// Using 'val' with nullable types and default values for a more immutable style, matching data class benefits.
// The test can still instantiate with nulls if needed.
data class Join<A, B>(val first: A?, val second: B?) {
    // Added default nulls to allow construction like Join() in tests if specific values aren't immediately necessary.
    // Constructor with non-nullable parameters if values are always expected at call site:
    // data class Join<A, B>(val first: A, val second: B)

    // Illustrative split function
    fun split(): Pair<A?, B?> = Pair(first, second)
}

// Top-level join operator
infix fun <A, B> A.join(other: B): Join<A, B> = Join(this, other)


@JsExport
@Serializable
enum class ClineAskType {
    followup,
    command,
    command_output,
    completion_result,
    tool,
    api_req_failed,
    resume_task,
    resume_completed_task,
    mistake_limit_reached,
    browser_action_launch,
    use_mcp_server,
    auto_approval_max_req_reached
}

@JsExport
@Serializable
enum class ClineSayType {
    error,
    api_req_started,
    api_req_finished,
    api_req_retried,
    api_req_retry_delayed,
    api_req_deleted,
    text,
    reasoning,
    completion_result,
    user_feedback,
    user_feedback_diff,
    command_output,
    shell_integration_warning,
    browser_action,
    browser_action_result,
    mcp_server_request_started,
    mcp_server_response,
    subtask_result,
    checkpoint_saved,
    rooignore_error,
    diff_error,
    condense_context,
    condense_context_error,
    codebase_search_result
}

@JsExport
@Serializable
data class ToolProgressStatus(
    val icon: String? = null,
    val text: String? = null
)

@JsExport
@Serializable
data class ContextCondense(
    val cost: Double,
    val prevContextTokens: Int,
    val newContextTokens: Int,
    val summary: String
)

@JsExport
@Serializable
data class TokenUsage(
    val totalTokensIn: Int,
    val totalTokensOut: Int,
    val totalCacheWrites: Int? = null,
    val totalCacheReads: Int? = null,
    val totalCost: Double,
    val contextTokens: Int
)

@JsExport
@Serializable
data class TaskEvent(
    val ts: Long,
    val type: String,
    val ask: ClineAskType? = null,
    val say: ClineSayType? = null,
    val text: String? = null,
    val images: List<String>? = null,
    val partial: Boolean? = null,
    val reasoning: String? = null,
    val conversationHistoryIndex: Int? = null,
    val checkpoint: Map<String, String>? = null,
    val progressStatus: ToolProgressStatus? = null,
    val contextCondense: ContextCondense? = null,
    val tokenUsage: TokenUsage? = null
)

typealias TaskHistory = Series<TaskEvent>

@JsExport
@Serializable
enum class ProviderName {
    anthropic, glama, openrouter, bedrock, vertex, openai, ollama, `vscode-lm`, lmstudio,
    gemini, `openai-native`, mistral, deepseek, unbound, requesty, `human-relay`,
    `fake-ai`, xai, groq, chutes, litellm
}

@JsExport
@Serializable
data class ApiKeys(
    val anthropicApiKey: String? = null, val glamaApiKey: String? = null, val openRouterApiKey: String? = null,
    val awsAccessKey: String? = null, val vertexKeyFile: String? = null, val vertexJsonCredentials: String? = null,
    val openAiApiKey: String? = null, val geminiApiKey: String? = null, val openAiNativeApiKey: String? = null,
    val mistralApiKey: String? = null, val deepSeekApiKey: String? = null, val unboundApiKey: String? = null,
    val requestyApiKey: String? = null, val xaiApiKey: String? = null, val groqApiKey: String? = null,
    val chutesApiKey: String? = null, val litellmApiKey: String? = null, val codeIndexOpenAiKey: String? = null,
    val codeIndexQdrantApiKey: String? = null, val codebaseIndexOpenAiCompatibleApiKey: String? = null
)

@JsExport
@Serializable
enum class ReasoningEffort { low, medium, high }

@JsExport
@Serializable
data class ModelSettings(
    val apiModelId: String? = null, val includeMaxTokens: Boolean? = null, val diffEnabled: Boolean? = null,
    val fuzzyMatchThreshold: Double? = null, val modelTemperature: Double? = null, val rateLimitSeconds: Int? = null,
    val enableReasoningEffort: Boolean? = null, val reasoningEffort: ReasoningEffort? = null,
    val modelMaxTokens: Int? = null, val modelMaxThinkingTokens: Int? = null, val glamaModelId: String? = null,
    val openRouterModelId: String? = null, val openAiModelId: String? = null, val ollamaModelId: String? = null,
    val lmStudioModelId: String? = null, val unboundModelId: String? = null, val requestyModelId: String? = null,
    val litellmModelId: String? = null, val anthropicBaseUrl: String? = null, val openRouterBaseUrl: String? = null,
    val openAiBaseUrl: String? = null, val ollamaBaseUrl: String? = null, val lmStudioBaseUrl: String? = null,
    val googleGeminiBaseUrl: String? = null, val openAiNativeBaseUrl: String? = null, val mistralCodestralUrl: String? = null,
    val deepSeekBaseUrl: String? = null, val litellmBaseUrl: String? = null,
    val codebaseIndexOpenAiCompatibleBaseUrl: String? = null, val anthropicUseAuthToken: Boolean? = null,
    val openRouterSpecificProvider: String? = null, val openRouterUseMiddleOutTransform: Boolean? = null,
    val awsSecretKey: String? = null, val awsSessionToken: String? = null, val awsRegion: String? = null,
    val awsUseCrossRegionInference: Boolean? = null, val awsUsePromptCache: Boolean? = null,
    val awsProfile: String? = null, val awsUseProfile: Boolean? = null, val awsCustomArn: String? = null,
    val awsModelContextWindow: Int? = null, val awsBedrockEndpointEnabled: Boolean? = null,
    val awsBedrockEndpoint: String? = null, val vertexProjectId: String? = null, val vertexRegion: String? = null,
    val openAiLegacyFormat: Boolean? = null, val openAiR1FormatEnabled: Boolean? = null,
    val openAiCustomModelInfo: ModelInfo? = null, val openAiUseAzure: Boolean? = null,
    val azureApiVersion: String? = null, val openAiStreamingEnabled: Boolean? = null,
    val openAiHeaders: Map<String, String>? = null, val vsCodeLmModelSelector: VsCodeLmModelSelector? = null,
    val lmStudioDraftModelId: String? = null, val lmStudioSpeculativeDecodingEnabled: Boolean? = null,
    val codebaseIndexOpenAiCompatibleModelDimension: Int? = null
)

@JsExport
@Serializable
data class VsCodeLmModelSelector(
    val vendor: String? = null, val family: String? = null,
    val version: String? = null, val id: String? = null
)

@JsExport
@Serializable
data class ModelInfo(
    val maxTokens: Int? = null, val maxThinkingTokens: Int? = null, val contextWindow: Int,
    val supportsImages: Boolean? = null, val supportsComputerUse: Boolean? = null,
    val supportsPromptCache: Boolean, val supportsReasoningBudget: Boolean? = null,
    val requiredReasoningBudget: Boolean? = null, val supportsReasoningEffort: Boolean? = null,
    val supportedParameters: List<String>? = null, val inputPrice: Double? = null,
    val outputPrice: Double? = null, val cacheWritesPrice: Double? = null,
    val cacheReadsPrice: Double? = null, val description: String? = null,
    val reasoningEffort: ReasoningEffort? = null, val minTokensPerCachePoint: Int? = null,
    val maxCachePoints: Int? = null, val cachableFields: List<String>? = null,
    val tiers: List<ModelInfoTier>? = null
)

@JsExport
@Serializable
data class ModelInfoTier(
    val contextWindow: Int, val inputPrice: Double? = null, val outputPrice: Double? = null,
    val cacheWritesPrice: Double? = null, val cacheReadsPrice: Double? = null
)

typealias ProviderConfig = Join<ApiKeys?, ModelSettings?> // Made components nullable for easier test setup

@JsExport
@Serializable
data class ActiveSession(
    val currentTaskId: String? = null,
    val currentMode: ModeConfig? = null,
    val history: TaskHistory? = null
)

@JsExport
@Serializable
data class UserPreferences(
    val preferredLanguage: String? = null, val theme: String? = null, val allowTelemetry: Boolean? = null
)

@JsExport
@Serializable
data class EnvironmentContext(
    val workspaceRoot: String? = null,
    val openFiles: List<String>? = null,
    val vscodeVersion: String? = null,
    val extensionVersion: String? = null,
    val userPreferences: UserPreferences? = null,
    // Added ProviderConfig to EnvironmentContext as discussed for state representation
    val providerConfig: ProviderConfig? = null
)

typealias KotlinExtensionState = Join<ActiveSession?, EnvironmentContext?> // Made components nullable

@JsExport
@Serializable
enum class ToolGroupType { read, edit, browser, command, mcp, modes }

@JsExport
@Serializable
data class GroupOptions(val fileRegex: String? = null, val description: String? = null)

@JsExport
@Serializable
data class GroupEntry(val group: ToolGroupType, val options: GroupOptions? = null)

@JsExport
@Serializable
data class ModeConfig(
    val slug: String, val name: String, val roleDefinition: String, val whenToUse: String? = null,
    val customInstructions: String? = null, val groups: List<GroupEntry>, val source: String? = null
)

@JsExport
@Serializable
enum class ToolName {
    execute_command, read_file, write_to_file, apply_diff, insert_content, search_and_replace,
    search_files, list_files, list_code_definition_names, browser_action, use_mcp_tool,
    access_mcp_resource, ask_followup_question, attempt_completion, switch_mode, new_task,
    fetch_instructions, codebase_search
}

@JsExport
@Serializable
data class ToolUsageDetail(val attempts: Int, val failures: Int)

typealias ToolUsage = Map<ToolName, ToolUsageDetail>

@JsExport
@Serializable
enum class CodeActionId { explainCode, fixCode, improveCode, addToContext, newTask }

@JsExport
@Serializable
enum class TerminalActionId { terminalAddToContext, terminalFixCommand, terminalExplainCommand }

@JsExport
@Serializable
enum class CommandId {
    activationCompleted, plusButtonClicked, promptsButtonClicked, mcpButtonClicked, historyButtonClicked,
    popoutButtonClicked, accountButtonClicked, settingsButtonClicked, openInNewTab, showHumanRelayDialog,
    registerHumanRelayCallback, unregisterHumanRelayCallback, handleHumanRelayResponse, newTask,
    setCustomStoragePath, focusInput, acceptInput
}

@JsExport
@Serializable
enum class Language { ca, de, en, es, fr, hi, it, ja, ko, nl, pl, `pt-BR`, ru, tr, vi, `zh-CN`, `zh-TW` }

@JsExport
@Serializable
data class UserRequest(val text: String)

@JsExport
@Serializable
data class Response(val text: String)
