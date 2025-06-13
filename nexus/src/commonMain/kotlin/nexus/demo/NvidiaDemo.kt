package nexus.demo

import nexus.config.NvidiaConfig
import nexus.providers.NvidiaProvider
import nexus.models.NvidiaModelManager
import nexus.models.ModelCapability
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Load configuration
    val config = NvidiaConfig.load()
    
    // Create provider and manager
    val provider = NvidiaProvider(config.apiKey, config.endpoint)
    val manager = NvidiaModelManager(provider, config)
    
    // Get available models
    println("Available models:")
    val models = manager.getAvailableModels().first()
    models.forEach { model: String ->
        println("- $model")
        
        // Get model capabilities
        val capabilities = manager.getModelCapabilities(model)
        println("  Capabilities:")
        capabilities.forEach { capability: ModelCapability ->
            println("  - $capability")
        }
    }
    
    // Generate response using Meta Scout model
    println("\nGenerating response with Meta Scout:")
    val response = manager.generateResponse(
        modelId = config.models.metaScout,
        prompt = "Write a function to calculate fibonacci numbers"
    ).first()
    
    println("Response:")
    response.forEach { line: String ->
        println(line)
    }
} 