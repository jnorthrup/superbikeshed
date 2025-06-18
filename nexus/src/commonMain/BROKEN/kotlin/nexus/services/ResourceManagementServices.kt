package nexus.services

// Assuming Series is defined in TrikeShed.kt or as a common typealias.
// For this file, if not available from an import, use:
typealias Series<T> = List<T>
// import nexus.core.TrikeShed.Series // Uncomment if Series is defined in TrikeShed.kt

/**
 * Represents the configuration for a specific Large Language Model (LLM) accessible by Nexus.
 *
 * @property id A unique identifier for this LLM configuration entry.
 * @property modelId The specific model identifier used by the provider (e.g., "claude-3-opus-20240229", "gpt-4o").
 * @property apiEndpoint The base URL for the LLM provider's API.
 * @property apiKeySecretRef A reference string to securely retrieve the API key (e.g., from a secrets manager).
 * @property providerType A string identifying the LLM provider (e.g., "OpenAI", "Anthropic", "OpenRouter", "AzureOpenAI").
 * @property additionalHeaders Optional map of additional HTTP headers to include in requests to this LLM.
 */
data class LlmConfig(
    val id: String,
    val modelId: String,
    val apiEndpoint: String,
    val apiKeySecretRef: String,
    val providerType: String,
    val additionalHeaders: Map<String, String>? = null
)

/**
 * Service interface for managing LLM configurations within Nexus.
 * Allows adding, retrieving, removing, and listing LLM configurations.
 */
interface LlmConfigurationService {
    /**
     * Adds a new LLM configuration to the system.
     * @param config The [LlmConfig] object to add.
     */
    suspend fun addConfiguration(config: LlmConfig)

    /**
     * Retrieves an LLM configuration by its unique ID.
     * @param id The unique identifier of the LLM configuration.
     * @return The [LlmConfig] if found, otherwise null.
     */
    suspend fun getConfiguration(id: String): LlmConfig?

    /**
     * Removes an LLM configuration from the system.
     * @param id The unique identifier of the LLM configuration to remove.
     */
    suspend fun removeConfiguration(id: String)

    /**
     * Lists all available LLM configurations.
     * @return A [Series] of [LlmConfig] objects.
     */
    suspend fun listConfigurations(): Series<LlmConfig>

    /**
     * Updates the Gauntlet score associated with a specific LLM configuration ID.
     * This is typically called when a new Gauntlet test run completes for an LLM.
     * The score itself is stored in the GauntletScoreRegistryService and linked to LlmResource.
     * @param configId The unique identifier of the LLM configuration.
     * @param score The new Gauntlet score.
     */
    suspend fun updateGauntletScore(configId: String, score: Double)
}

/**
 * Represents the result of an LLM Agility Gauntlet test for a specific LLM configuration.
 *
 * @property configId The unique identifier of the [LlmConfig] that was tested.
 * @property score The overall Gauntlet score achieved (e.g., 0-100).
 * @property detailedScores An optional map providing a breakdown of scores (e.g., TER, CDS, LCP).
 * @property lastTestedTimestamp Unix epoch milliseconds indicating when the test was performed.
 * @property fullReportUrl An optional URL pointing to a detailed report of the Gauntlet run.
 */
data class GauntletScore(
    val configId: String,
    val score: Double,
    val detailedScores: Map<String, Double>? = null,
    val lastTestedTimestamp: Long,
    val fullReportUrl: String? = null
)

/**
 * Service interface for recording and retrieving LLM Agility Gauntlet scores.
 * This registry helps in selecting the best LLMs based on their operational agility.
 */
interface GauntletScoreRegistryService {
    /**
     * Records a new Gauntlet score for an LLM configuration.
     * @param score The [GauntletScore] object to record.
     */
    suspend fun recordScore(score: GauntletScore)

    /**
     * Retrieves the latest Gauntlet score for a specific LLM configuration ID.
     * @param configId The unique identifier of the LLM configuration.
     * @return The [GauntletScore] if found, otherwise null.
     */
    suspend fun getScore(configId: String): GauntletScore?

    /**
     * Retrieves all Gauntlet scores for a given provider type.
     * @param providerType The type of the LLM provider (e.g., "Anthropic", "OpenAI").
     * @return A [Series] of [GauntletScore] objects for that provider.
     */
    suspend fun getScoresForProvider(providerType: String): Series<GauntletScore>

    /**
     * Retrieves a ranked list of LLM configurations based on their Gauntlet scores.
     * @param limit The maximum number of ranked configurations to return.
     * @return A [Series] of pairs, each containing an [LlmConfig] and its optional [GauntletScore].
     *         Configs without scores might be included at the end or excluded, depending on implementation.
     */
    suspend fun getRankedConfigs(limit: Int = 5): Series<Pair<LlmConfig, GauntletScore?>>
}

/**
 * A sealed interface representing metadata for various types of resources Nexus can manage.
 * These resources can be LLMs, DGM agents, external tools, etc.
 *
 * @property resourceId Unique identifier for the resource.
 * @property resourceType A string indicating the type of resource (e.g., "LLM", "DGM_AGENT", "TOOL").
 * @property name A human-readable name for the resource.
 * @property description An optional detailed description of the resource.
 * @property capabilities A list of strings describing what the resource can do (e.g., "code_generation", "python_execution", "file_system_access").
 */
sealed interface ResourceMetadata {
    val resourceId: String
    val resourceType: String
    val name: String
    val description: String?
    val capabilities: Series<String>
}

/**
 * Metadata for an LLM resource.
 * @param id The unique ID for this LLM resource instance (can be same as llmConfigId or different if multiple instances use same config).
 * @param nameDisplay Human-readable name.
 * @param caps Capabilities of this LLM.
 * @param llmConfigId The ID of the [LlmConfig] used by this LLM resource.
 * @param currentGauntletScore The latest Gauntlet score for this LLM resource, updated periodically.
 * @param desc Optional description.
 */
data class LlmResource(
    val id: String,
    val nameDisplay: String,
    val caps: Series<String>,
    val llmConfigId: String,
    var currentGauntletScore: Double? = null,
    val desc: String? = null
) : ResourceMetadata {
    override val resourceId: String get() = id
    override val resourceType: String get() = "LLM"
    override val name: String get() = nameDisplay
    override val capabilities: Series<String> get() = caps
    override val description: String? get() = desc
}

/**
 * Metadata for a DGM (Darwin Gödel Machine) agent resource.
 * @param id Unique ID for the DGM agent.
 * @param nameDisplay Human-readable name.
 * @param caps Capabilities of this DGM agent.
 * @param dgmCommitId The commit ID or version of the DGM agent's codebase.
 * @param dgmPerformanceMetrics Optional map of performance metrics specific to this DGM agent.
 * @param desc Optional description.
 */
data class DgmAgentResource(
    val id: String,
    val nameDisplay: String,
    val caps: Series<String>,
    val dgmCommitId: String,
    val dgmPerformanceMetrics: Map<String, Any>? = null,
    val desc: String? = null
) : ResourceMetadata {
    override val resourceId: String get() = id
    override val resourceType: String get() = "DGM_AGENT"
    override val name: String get() = nameDisplay
    override val capabilities: Series<String> get() = caps
    override val description: String? get() = desc
}

/**
 * Metadata for an external or internal tool resource.
 * @param id Unique ID for the tool.
 * @param nameDisplay Human-readable name.
 * @param caps Capabilities of this tool.
 * @param toolExecutorInfo Information needed to execute the tool (e.g., command line, API endpoint).
 * @param desc Optional description.
 */
data class ToolResource(
    val id: String,
    val nameDisplay: String,
    val caps: Series<String>,
    val toolExecutorInfo: Map<String, Any>,
    val desc: String? = null
) : ResourceMetadata {
    override val resourceId: String get() = id
    override val resourceType: String get() = "TOOL"
    override val name: String get() = nameDisplay
    override val capabilities: Series<String> get() = caps
    override val description: String? get() = desc
}

/**
 * Service interface for managing metadata of various operational resources within Nexus.
 * Resources can include LLMs, DGM agents, specialized tools, etc.
 */
interface ResourceMetadataService {
    /**
     * Registers a new resource with Nexus.
     * @param metadata The [ResourceMetadata] object to register.
     */
    suspend fun registerResource(metadata: ResourceMetadata)

    /**
     * Retrieves the metadata for a specific resource by its ID.
     * @param resourceId The unique identifier of the resource.
     * @return The [ResourceMetadata] if found, otherwise null.
     */
    suspend fun getResource(resourceId: String): ResourceMetadata?

    /**
     * Finds all resources that possess a specific capability.
     * @param capability The capability string to search for.
     * @return A [Series] of [ResourceMetadata] objects that have the specified capability.
     */
    suspend fun findResourcesByCapability(capability: String): Series<ResourceMetadata>

    /**
     * Lists all resources of a particular type.
     * @param resourceType The type of resource (e.g., "LLM", "DGM_AGENT").
     * @return A [Series] of [ResourceMetadata] objects of the specified type.
     */
    suspend fun listResourcesByType(resourceType: String): Series<ResourceMetadata>

    /**
     * Updates the Gauntlet score for a specific LLM resource.
     * This is typically called by the [GauntletScoreRegistryService] when a score is updated,
     * to ensure the [LlmResource] object has the latest score.
     * @param llmConfigId The configuration ID of the LLM resource whose score needs updating.
     * @param score The new Gauntlet score.
     */
     suspend fun updateLlmResourceScore(llmConfigId: String, score: Double)
}

/**
 * Defines the requirements for a task that needs to be assigned to a resource.
 *
 * @property requiredCapabilities A list of capabilities essential for performing the task.
 * @property preferredResourceType An optional string indicating a preferred type of resource (e.g., "LLM", "DGM_AGENT").
 * @property minGauntletScore An optional minimum Gauntlet score, applicable if the preferred resource is an LLM.
 * @property otherConstraints An optional map for any other specific constraints or preferences for resource selection.
 */
data class TaskRequirements(
    val requiredCapabilities: Series<String>,
    val preferredResourceType: String? = null,
    val minGauntletScore: Double? = null,
    val otherConstraints: Map<String, Any>? = null
)

/**
 * Service interface for categorizing ("binning") available resources based on task requirements.
 * This helps in narrowing down suitable resources before final assignment.
 */
interface ResourceBinnerService {
    /**
     * Categorizes available resources into bins based on their suitability for given task requirements.
     * Example bins: "highly_suitable_llms", "suitable_tools_for_X", "requires_human_review".
     *
     * @param requirements The [TaskRequirements] for the task.
     * @param availableResources A [Series] of all [ResourceMetadata] currently available.
     * @return A map where keys are bin names (strings) and values are [Series] of [ResourceMetadata] fitting that bin.
     */
    suspend fun binResourcesForTask(
        requirements: TaskRequirements,
        availableResources: Series<ResourceMetadata>
    ): Map<String, Series<ResourceMetadata>>
}

/**
 * Service interface for assigning the most appropriate resource to a given task based on its requirements.
 */
interface TaskAssignmentService {
    /**
     * Assigns the best available resource from a pre-categorized (binned) set of resources.
     *
     * @param requirements The [TaskRequirements] for the task.
     * @param binnedResources A map of resource bins, typically from [ResourceBinnerService].
     * @return The [ResourceMetadata] of the best-suited resource, or null if no suitable resource is found.
     */
    suspend fun assignBestResource(
        requirements: TaskRequirements,
        binnedResources: Map<String, Series<ResourceMetadata>>
    ): ResourceMetadata?

    /**
     * Convenience method to assign the best resource by first finding all available resources
     * and then potentially using a [ResourceBinnerService] internally.
     *
     * @param requirements The [TaskRequirements] for the task.
     * @return The [ResourceMetadata] of the best-suited resource, or null if no suitable resource is found.
     */
    suspend fun assignBestResource(requirements: TaskRequirements): ResourceMetadata?
}
