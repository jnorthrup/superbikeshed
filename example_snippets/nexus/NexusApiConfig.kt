package nexus

import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.lang.System.getenv

data class ApiConfig(val baseUrl: String, val apiKeyEnvVar: String)
data class ArtificialAnalysisConfig(val api: ApiConfig)
data class FullConfig(val artificial_analysis: ArtificialAnalysisConfig)

object NexusArtificialAnalysisService {

    private var cachedConfig: FullConfig? = null
    private const val CONFIG_FILE_NAME = "nexus_config.yaml"

    fun loadConfig(): FullConfig? {
        if (cachedConfig != null) {
            return cachedConfig
        }
        try {
            val configFile = File(CONFIG_FILE_NAME)
            if (!configFile.exists()) {
                System.err.println("Error: Configuration file '$CONFIG_FILE_NAME' not found in the current directory.")
                return null
            }
            FileInputStream(configFile).use { inputStream ->
                val yaml = Yaml()
                // The Yaml().load() method can return null or throw exceptions for invalid YAML.
                // Modern SnakeYAML (e.g., 2.0+) returns Map<String, Any> or List<Any> by default.
                // We need to cast it carefully. For this example, we assume a specific structure.
                val rawConfig = yaml.load<Map<String, Any>>(inputStream)

                val artificialAnalysisMap = rawConfig?.get("artificial_analysis") as? Map<String, Any>
                val apiMap = artificialAnalysisMap?.get("api") as? Map<String, Any>
                val baseUrl = apiMap?.get("base_url") as? String
                val apiKeyEnvVar = apiMap?.get("api_key_env_var") as? String

                if (baseUrl != null && apiKeyEnvVar != null) {
                    cachedConfig = FullConfig(
                        ArtificialAnalysisConfig(
                            ApiConfig(baseUrl, apiKeyEnvVar)
                        )
                    )
                    return cachedConfig
                } else {
                    System.err.println("Error: Configuration file '$CONFIG_FILE_NAME' is missing required fields (artificial_analysis.api.base_url or artificial_analysis.api.api_key_env_var).")
                    return null
                }
            }
        } catch (e: FileNotFoundException) {
            System.err.println("Error: Configuration file '$CONFIG_FILE_NAME' not found.")
        } catch (e: Exception) {
            System.err.println("Error loading or parsing configuration file '$CONFIG_FILE_NAME': ${e.message}")
        }
        return null
    }

    fun getApiKey(): String? {
        val config = loadConfig()
        if (config == null) {
            System.err.println("Cannot retrieve API key: Configuration not loaded.")
            return null
        }

        val apiKeyEnvVar = config.artificial_analysis.api.apiKeyEnvVar
        val apiKey = getenv(apiKeyEnvVar)

        if (apiKey.isNullOrEmpty()) {
            System.err.println("Error: API key environment variable '$apiKeyEnvVar' is not set or is empty.")
            return null
        }
        return apiKey
    }

    fun getApiHeaders(): Map<String, String>? {
        val apiKey = getApiKey()
        if (apiKey == null) {
            System.err.println("Cannot prepare API headers: API key not available.")
            return null
        }
        return mapOf("Authorization" to "Bearer $apiKey")
    }
}

fun main() {
    System.err.println("--- Nexus API Configuration Example ---")
    System.err.println("Attempting to load configuration and API key...")

    val config = NexusArtificialAnalysisService.loadConfig()
    if (config == null) {
        System.err.println("Failed to load configuration. See previous errors.")
        System.err.println("Please ensure '$NexusArtificialAnalysisService.CONFIG_FILE_NAME' is in the current directory and correctly formatted.")
        System.err.println("And the environment variable for the API key (e.g., AIA_API_KEY) is set.")
        return
    }

    System.err.println("Configuration loaded successfully from '${NexusArtificialAnalysisService.CONFIG_FILE_NAME}'.")
    System.err.println("Using API key from environment variable: '${config.artificial_analysis.api.apiKeyEnvVar}'")
    System.err.println("Base URL: '${config.artificial_analysis.api.baseUrl}'")


    val apiKey = NexusArtificialAnalysisService.getApiKey()
    if (apiKey == null) {
        System.err.println("Failed to get API key.")
        System.err.println("Please ensure the environment variable '${config.artificial_analysis.api.apiKeyEnvVar}' is set.")
        return
    }
    // For security reasons, avoid printing the actual API key in production
    System.err.println("Successfully retrieved API key (first 5 chars): '${apiKey.take(5)}...'")

    val headers = NexusArtificialAnalysisService.getApiHeaders()
    if (headers == null) {
        System.err.println("Failed to generate API headers.")
        return
    }
    System.err.println("Generated API headers: $headers")
    System.err.println("--- End of Example ---")
    System.err.println("\nTo run this example:")
    System.err.println("1. Ensure you have Kotlin installed.")
    System.err.println("2. Add SnakeYAML to your project dependencies.")
    System.err.println("   For Gradle (build.gradle.kts or build.gradle):")
    System.err.println("     implementation(\"org.yaml:snakeyaml:2.0\") // Or the latest version")
    System.err.println("3. Create/place 'nexus_config.yaml' in the same directory as this script or its execution context:")
    System.err.println("   artificial_analysis:")
    System.err.println("     api:")
    System.err.println("       base_url: \"https://artificialanalysis.ai/api/v2\"")
    System.err.println("       api_key_env_var: \"AIA_API_KEY\"")
    System.err.println("4. Set the environment variable: export AIA_API_KEY=\"your_actual_api_key_here\"")
    System.err.println("5. Compile and run this Kotlin file.")
    System.err.println("   Example (from terminal in the directory containing example_snippets):")
    System.err.println("   kotlinc -cp \".:/path/to/snakeyaml.jar\" nexus/NexusApiConfig.kt -include-runtime -d nexus_example.jar")
    System.err.println("   java -jar nexus_example.jar")
    System.err.println("   (Adjust classpath and compilation steps based on your project setup if not using standalone compilation)")
}
