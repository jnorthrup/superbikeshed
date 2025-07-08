// NEXUS PROVIDER DEMONSTRATION
// Import from demo.kt
import extractCurrentScope
import extractCurrentCapabilities
import substringBetween

>>>>>>> origin/jules_wip_12008771546559725757
fun main() {
    println("=== NEXUS PROVIDER DEMONSTRATION ===")
    
    // Test provider creation
    val mockProvider = MockProvider()
    val anthropicProvider = AnthropicProvider("fake-key", "claude-3-5-sonnet")
    val openaiProvider = OpenAIProvider("fake-key", "gpt-4")
    
    // Test CCEK context
    val context = buildTestContext()
    
    println("\n--- Mock Provider Test ---")
    testProvider(mockProvider, context)
    
    println("\n--- Anthropic Provider Test ---")
    testProvider(anthropicProvider, context)
    
    println("\n--- OpenAI Provider Test ---")
    testProvider(openaiProvider, context)
    
    println("\n--- Enhanced Nexus Test ---")
    testEnhancedNexus()
    
    println("\n=== PROVIDER DEMO COMPLETE ===")
}

fun testProvider(provider: NexusProvider, context: CCEKContext) {
    println("Provider: ${provider.name}")
    
    // Test generation (synchronous for demo)
    val prompt = "create a user authentication function"
    println("Generate: $prompt")
    val response = if (provider is MockProvider) {
        "Generated response for: $prompt\nUsing context: ${context.extractCurrentScope()}"
    } else {
        "Provider ${provider.name} would generate response for: $prompt"
    }
    println("Response: ${response.take(100)}...")
    
    // Test analysis
    val code = "fun login(username: String, password: String) { /* TODO */ }"
    println("Analyze: $code")
    val analysis = if (provider is MockProvider) {
        "Analysis of content (${code.length} chars): Mock analysis complete"
    } else {
        "Provider ${provider.name} would analyze: $code"
    }
    println("Analysis: ${analysis.take(100)}...")
}

fun testEnhancedNexus() {
    val nexus = buildSimpleNexus()
    val provider = MockProvider()
    val enhanced = nexus.withProvider(provider)
    
    println("Enhanced Nexus with ${provider.name}")
    
    val prompts = listOf(
        "create a REST API endpoint",
        "optimize database queries",
        "implement error handling"
    )
    
    prompts.forEach { prompt ->
        println("Request: $prompt")
        val response = enhanced.generate(prompt)
        println("Response: ${response.take(80)}...")
        println()
    }
}

// Support implementations
// NEXUS PROVIDER DEMONSTRATION
// Import from demo.kt
import extractCurrentScope
import extractCurrentCapabilities
import substringBetween

fun main() {
    println("=== NEXUS PROVIDER DEMONSTRATION ===")
    
    // Test provider creation
    val mockProvider = MockProvider()
    val nvidiaProvider = NVIDIAProvider("fake-key", "nvidia-model")
    val geminiProvider = GeminiProvider("fake-key", "gemini-model")
    
    // Test CCEK context
    val context = buildTestContext()
    
    println("\n--- Mock Provider Test ---")
    testProvider(mockProvider, context)
    
    println("\n--- NVIDIA Provider Test ---")
    testProvider(nvidiaProvider, context)
    
    println("\n--- Gemini Provider Test ---")
    testProvider(geminiProvider, context)
    
    println("\n--- Enhanced Nexus Test ---")
    testEnhancedNexus()
    
    println("\n=== PROVIDER DEMO COMPLETE ===")
}

fun testProvider(provider: NexusProvider, context: CCEKContext) {
    println("Provider: ${provider.name}")
    
    // Test generation (synchronous for demo)
    val prompt = "create a user authentication function"
    println("Generate: $prompt")
    val response = if (provider is MockProvider) {
        "Generated response for: $prompt\nUsing context: ${context.extractCurrentScope()}"
    } else {
        "Provider ${provider.name} would generate response for: $prompt"
    }
    println("Response: ${response.take(100)}...")
    
    // Test analysis
    val code = "fun login(username: String, password: String) { /* TODO */ }"
    println("Analyze: $code")
    val analysis = if (provider is MockProvider) {
        "Analysis of content (${code.length} chars): Mock analysis complete"
    } else {
        "Provider ${provider.name} would analyze: $code"
    }
    println("Analysis: ${analysis.take(100)}...")
}

// Support implementations
interface NexusProvider {
    val name: String
    suspend fun generate(prompt: String, context: CCEKContext): String
    suspend fun analyze(content: String, context: CCEKContext): String
    suspend fun complete(partial: String, context: CCEKContext): String
}

class MockProvider : NexusProvider {
    override val name = "mock"
    
    override suspend fun generate(prompt: String, context: CCEKContext): String {
        return "Generated response for: $prompt\nUsing context: ${context.extractCurrentScope()}"
    }
    
    override suspend fun analyze(content: String, context: CCEKContext): String {
        return buildString {
            appendLine("Analysis of content (${content.length} chars):")
            appendLine("Scope: ${context.extractCurrentScope()}")
            appendLine("Available capabilities: ${context.extractCurrentCapabilities()}")
            appendLine("Key findings: Mock analysis complete")
        }
    }
    
    override suspend fun complete(partial: String, context: CCEKContext): String {
        return "$partial... [completed by mock provider]"
    }
}

open class CommodityNexusProvider(
    internal val commodity: String,
    internal val apiKey: String,
    internal val model: String
) : NexusProvider {
    override val name = "commodity-$commodity"
    
    override suspend fun generate(prompt: String, context: CCEKContext): String {
        return buildContextualResponse(prompt, context, "Unified Commodity Provider")
    }
    
    override suspend fun analyze(content: String, context: CCEKContext): String {
        return buildContextualResponse("Analyze: $content", context, "Unified Commodity Provider")
    }
    
    override suspend fun complete(partial: String, context: CCEKContext): String {
        return buildContextualResponse("Complete: $partial", context, "Unified Commodity Provider")
    }
}

class NVIDIAProvider(apiKey: String, model: String) : CommodityNexusProvider("nvidia", apiKey, model)
class GeminiProvider(apiKey: String, model: String) : CommodityNexusProvider("gemini", apiKey, model)

fun buildContextualResponse(prompt: String, context: CCEKContext, providerName: String = "Mock"): String = buildString {
    appendLine("$providerName response for: ${prompt.take(50)}...")
    appendLine("Context scope: ${context.extractCurrentScope()}")
    appendLine("Available capabilities: ${context.extractCurrentCapabilities()}")
    appendLine()
    appendLine("Generated solution using ${providerName}:")
    appendLine("- Analyzed requirements from context")
    appendLine("- Applied best practices and patterns")
    appendLine("- Optimized for current environment")
    appendLine("- Included proper error handling")
}

// CCEK and Nexus support
data class Context(val data: String) { val scope = data.substringBetween("|scope|", "|/scope|") }
data class Configuration(val data: String)
data class Environment(val data: String) { val capabilities = data.substringBetween("|caps|", "|/caps|") }
data class Knowledge(val data: String)

typealias CCEKContext = Pair<Pair<Context, Configuration>, Pair<Environment, Knowledge>>
typealias CCEKNexus = CCEKContext
typealias EnhancedNexus = Pair<CCEKNexus, NexusProvider>

infix fun <A, B> A.j(b: B): Pair<A, B> = this to b

fun buildTestContext(): CCEKContext {
    val context = Context("|scope|nexus-demo|/scope|")
    val config = Configuration("")
    val env = Environment("|caps|analysis,generation,refactoring|/caps|")
    val knowledge = Knowledge("")
    return (context j config) j (env j knowledge)
}

fun CCEKContext.extractCurrentScope(): String = this.first.first.scope.ifEmpty { "default" }
fun CCEKContext.extractCurrentCapabilities(): String = this.second.first.capabilities.ifEmpty { "basic" }

fun String.substringBetween(start: String, end: String): String = 
    substringAfter(start).substringBefore(end)
interface NexusProvider {
    val name: String
    suspend fun generate(prompt: String, context: CCEKContext): String
    suspend fun analyze(content: String, context: CCEKContext): String
    suspend fun complete(partial: String, context: CCEKContext): String
}

class MockProvider : NexusProvider {
    override val name = "mock"
    
    override suspend fun generate(prompt: String, context: CCEKContext): String {
        return "Generated response for: $prompt\nUsing context: ${context.extractCurrentScope()}"
    }
    
    override suspend fun analyze(content: String, context: CCEKContext): String {
        return buildString {
            appendLine("Analysis of content (${content.length} chars):")
            appendLine("Scope: ${context.extractCurrentScope()}")
            appendLine("Capabilities: ${context.extractCurrentCapabilities()}")
            appendLine("Key findings: Mock analysis complete")
        }
    }
    
    override suspend fun complete(partial: String, context: CCEKContext): String {
        return "$partial... [completed by mock provider]"
    }
}

class AnthropicProvider(internal val apiKey: String, internal val model: String) : NexusProvider {
    override val name = "anthropic"
    
    override suspend fun generate(prompt: String, context: CCEKContext): String {
        return buildContextualResponse(prompt, context, "Anthropic Claude")
    }
    
    override suspend fun analyze(content: String, context: CCEKContext): String {
        return buildContextualResponse("Analyze: $content", context, "Anthropic Claude")
    }
    
    override suspend fun complete(partial: String, context: CCEKContext): String {
        return buildContextualResponse("Complete: $partial", context, "Anthropic Claude")
    }
}

class OpenAIProvider(internal val apiKey: String, internal val model: String) : NexusProvider {
    override val name = "openai"
    
    override suspend fun generate(prompt: String, context: CCEKContext): String {
        return buildContextualResponse(prompt, context, "OpenAI GPT")
    }
    
    override suspend fun analyze(content: String, context: CCEKContext): String {
        return buildContextualResponse("Analyze: $content", context, "OpenAI GPT")
    }
    
    override suspend fun complete(partial: String, context: CCEKContext): String {
        return buildContextualResponse("Complete: $partial", context, "OpenAI GPT")
    }
}

fun buildContextualResponse(prompt: String, context: CCEKContext, providerName: String = "Mock"): String = buildString {
    appendLine("$providerName response for: ${prompt.take(50)}...")
    appendLine("Context scope: ${context.extractCurrentScope()}")
    appendLine("Available capabilities: ${context.extractCurrentCapabilities()}")
    appendLine()
    appendLine("Generated solution using ${providerName}:")
    appendLine("- Analyzed requirements from context")
    appendLine("- Applied best practices and patterns")
    appendLine("- Optimized for current environment")
    appendLine("- Included proper error handling")
}

// CCEK and Nexus support
data class Context(val data: String) { val scope = data.substringBetween("|scope|", "|/scope|") }
data class Configuration(val data: String)
data class Environment(val data: String) { val capabilities = data.substringBetween("|caps|", "|/caps|") }
data class Knowledge(val data: String)

typealias CCEKContext = Pair<Pair<Context, Configuration>, Pair<Environment, Knowledge>>
typealias CCEKNexus = CCEKContext
typealias EnhancedNexus = Pair<CCEKNexus, NexusProvider>

infix fun <A, B> A.j(b: B): Pair<A, B> = this to b

fun buildTestContext(): CCEKContext {
    val context = Context("|scope|nexus-demo|/scope|")
    val config = Configuration("")
    val env = Environment("|caps|analysis,generation,refactoring|/caps|")
    val knowledge = Knowledge("")
    return (context j config) j (env j knowledge)
}

fun buildSimpleNexus(): CCEKNexus = buildTestContext()

fun CCEKNexus.withProvider(provider: NexusProvider): EnhancedNexus = this j provider

fun EnhancedNexus.generate(prompt: String): String {
    val (nexus, provider) = this@generate
    return if (provider is MockProvider) {
        "Generated response for: $prompt\nUsing context: ${nexus.extractCurrentScope()}"
    } else {
        "Provider ${provider.name} would generate response for: $prompt"
    }
<<<<<<< HEAD
}
=======
}

fun CCEKContext.extractCurrentScope(): String = this.first.first.scope.ifEmpty { "default" }
fun CCEKContext.extractCurrentCapabilities(): String = this.second.first.capabilities.ifEmpty { "basic" }

fun String.substringBetween(start: String, end: String): String = 
    substringAfter(start).substringBefore(end)
>>>>>>> origin/jules_wip_12008771546559725757
