package nexus.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
internal data class NvidiaConfig(
    val apiKey: String = "",
    val endpoint: String = "https://integrate.api.nvidia.com/v1",
    val models: Models = Models()
) {
    @Serializable
    data class Models(
        val metaScout: String = "meta-llama/Llama-2-70b-chat-hf",
        val nemotronUltra: String = "nvidia/nemotron-3-8b",
        val nemotronSuper: String = "nvidia/llama-3.3-nemotron-super-49b-v1"
    )
    
    companion object {
        private const val CONFIG_FILE = ".env"
        private val json = Json { 
            ignoreUnknownKeys = true
            prettyPrint = true
        }
        
        fun load(): NvidiaConfig {
            val file = File(CONFIG_FILE)
            return if (file.exists()) {
                json.decodeFromString(NvidiaConfig.serializer(), file.readText())
            } else {
                val defaultConfig = NvidiaConfig()
                file.writeText(json.encodeToString(NvidiaConfig.serializer(), defaultConfig))
                defaultConfig
            }
        }
        
        fun save(config: NvidiaConfig) {
            File(CONFIG_FILE).writeText(json.encodeToString(NvidiaConfig.serializer(), config))
        }
    }
} 