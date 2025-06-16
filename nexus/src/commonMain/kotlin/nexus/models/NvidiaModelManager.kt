package nexus.models

import nexus.providers.NvidiaProvider
import nexus.config.NvidiaConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class NvidiaModelManager(
    private val provider: NvidiaProvider,
    private val config: NvidiaConfig
) {
    fun getAvailableModels(): Flow<Series<String>> = flow {
        provider.listModels().collect { models ->
            emit(Series(models.size) { i -> models[i] })
        }
    }
    
    fun generateResponse(
        modelId: String,
        prompt: String,
        temperature: Double = 0.7,
        maxTokens: Int = 2048
    ): Flow<Series<String>> = flow {
        provider.generateResponse(modelId, prompt, temperature, maxTokens)
            .map { response -> Series(1) { response } }
            .collect { series -> emit(series) }
    }
    
    fun getModelCapabilities(modelId: String): Series<ModelCapability> {
        return when (modelId) {
            config.models.metaScout -> Series(3) { i ->
                when (i) {
                    0 -> ModelCapability.CODE_GENERATION
                    1 -> ModelCapability.PROBLEM_SOLVING
                    2 -> ModelCapability.REFACTORING
                    else -> throw IndexOutOfBoundsException()
                }
            }
            config.models.nemotronUltra -> Series(2) { i ->
                when (i) {
                    0 -> ModelCapability.CODE_GENERATION
                    1 -> ModelCapability.PROBLEM_SOLVING
                    else -> throw IndexOutOfBoundsException()
                }
            }
            config.models.nemotronSuper -> Series(4) { i ->
                when (i) {
                    0 -> ModelCapability.CODE_GENERATION
                    1 -> ModelCapability.PROBLEM_SOLVING
                    2 -> ModelCapability.REFACTORING
                    3 -> ModelCapability.ANALYSIS
                    else -> throw IndexOutOfBoundsException()
                }
            }
            else -> Series(0) { throw IndexOutOfBoundsException() }
        }
    }
}

internal enum class ModelCapability {
    CODE_GENERATION,
    PROBLEM_SOLVING,
    REFACTORING,
    ANALYSIS
} 