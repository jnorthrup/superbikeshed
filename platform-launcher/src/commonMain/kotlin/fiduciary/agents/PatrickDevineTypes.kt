package fiduciary.agents

import kotlinx.serialization.*
import kotlinx.datetime.*

// ===== CONVERSATION COLLECTION TYPES =====

@Serializable
data class ConversationSource(
    val id: String,
    val type: SourceType,
    val url: String? = null,
    val path: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
enum class SourceType {
    ARCHIVE_ORG,
    LOCAL_FILES,
    API,
    DATABASE
}

@Serializable
data class ConversationText(
    val id: String,
    val sourceId: String,
    val content: String,
    val timestamp: Instant,
    val metadata: Map<String, String> = emptyMap()
)

// ===== AUDIO AND WHISPER TYPES =====

@Serializable
data class AudioFile(
    val id: String,
    val path: String,
    val format: AudioFormat,
    val sampleRate: Int,
    val channels: Int,
    val duration: Double
)

@Serializable
enum class AudioFormat {
    WAV,
    MP3,
    FLAC,
    M4A
}

@Serializable
data class WhisperConfig(
    val model: String,
    val language: String,
    val diarization: Boolean = false,
    val timestampFormat: TimestampFormat = TimestampFormat.HH_MM_SS,
    val outputFormat: OutputFormat = OutputFormat.VTT
)

@Serializable
enum class TimestampFormat {
    HH_MM_SS,
    HH_MM_SS_MS,
    SECONDS
}

@Serializable
enum class OutputFormat {
    VTT,
    TXT,
    JSON
}

@Serializable
data class TranscriptSegment(
    val startTime: Double,
    val endTime: Double,
    val speaker: String,
    val text: String,
    val confidence: Double = 1.0
)

@Serializable
data class Transcript(
    val id: String,
    val audioFileId: String,
    val content: String,
    val segments: List<TranscriptSegment>,
    val metadata: Map<String, String> = emptyMap()
)

// ===== COUCHDB TYPES =====

@Serializable
data class CouchDBConfig(
    val url: String,
    val database: String,
    val username: String,
    val password: String,
    val timeout: Long = 30000
)

// ===== K2SCRIPT SANDBOX TYPES =====

@Serializable
data class K2ScriptConfig(
    val sandboxDir: String,
    val isolationLevel: IsolationLevel,
    val resourceLimits: ResourceLimits? = null,
    val environment: Map<String, String> = emptyMap()
)

@Serializable
enum class IsolationLevel {
    PROCESS,
    CONTAINER,
    VM
}

@Serializable
data class ResourceLimits(
    val maxMemory: String,
    val maxCpu: Int,
    val maxDisk: String
)

@Serializable
data class AgentOperation(
    val id: String,
    val type: OperationType,
    val input: Map<String, String>,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
enum class OperationType {
    TRANSCRIPTION,
    TEXT_CLEANING,
    CACHE_UPDATE,
    GIT_COMMIT
}

// ===== GIT INTEGRATION TYPES =====

@Serializable
data class GitConfig(
    val repositoryPath: String,
    val branch: String,
    val authorName: String,
    val authorEmail: String,
    val autoCommit: Boolean = false,
    val commitMessageTemplate: String = "Update conversation data",
    val initializeIfNotExists: Boolean = false
)

@Serializable
data class ConversationData(
    val id: String,
    val conversations: List<ConversationText>,
    val metadata: Map<String, String> = emptyMap()
)

// ===== PIPELINE CONFIGURATION TYPES =====

@Serializable
data class PatrickDevinePipelineConfig(
    val sources: List<ConversationSource>,
    val whisperConfig: WhisperConfig,
    val couchConfig: CouchDBConfig,
    val k2scriptConfig: K2ScriptConfig,
    val gitConfig: GitConfig
) 