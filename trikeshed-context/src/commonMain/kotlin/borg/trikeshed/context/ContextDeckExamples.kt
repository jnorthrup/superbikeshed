package borg.trikeshed.context

import borg.trikeshed.lib.*
import kotlinx.coroutines.*

/**
 * Examples demonstrating the Context Deck DSL
 * 
 * Shows how to stack, manipulate, and use contexts like a deck of cards
 */

// Example 1: Web Request Context Stack
fun webRequestContextDeck() = contextDeck<WebContext> {
    // Bottom of deck: Application context
    card("app", WebContext.Application("MyApp", "1.0.0")) {
        priority = 0
        tags = setOf("global", "app")
    }
    
    // Middle: Session context
    card("session", WebContext.Session("sess-123", "user-456", kotlinx.datetime.Clock.System.now().toEpochMilliseconds())) {
        priority = 50
        tags = setOf("session", "auth")
    }
    
    // Top: Current request
    trump("request", WebContext.Request(
        id = "req-789",
        method = "GET",
        path = "/api/users/456",
        headers = mapOf("Authorization" to "Bearer xxx")
    ))
}

sealed class WebContext {
    data class Application(val name: String, val version: String) : WebContext()
    data class Session(val id: String, val userId: String, val startedAt: Long) : WebContext()
    data class Request(val id: String, val method: String, val path: String, val headers: Map<String, String>) : WebContext()
    data class Response(val statusCode: Int, val headers: Map<String, String>) : WebContext()
}

// Example 2: Multi-tenant Context Switching
class TenantContextManager {
    private val tenantDecks = mutableMapOf<String, ContextDeck<TenantContext>>()
    private val switcher = ContextSwitcher<TenantContext>()
    
    fun createTenantDeck(tenantId: String) = contextDeck<TenantContext> {
        trump("tenant", TenantContext.Tenant(tenantId, "Tenant $tenantId"))
        
        card("database", TenantContext.Database(
            schema = "tenant_$tenantId",
            connectionPool = 5
        )) {
            tags = setOf("infrastructure", "database")
        }
        
        card("storage", TenantContext.Storage(
            bucket = "tenant-$tenantId-files",
            quota = 1_000_000_000 // 1GB
        )) {
            tags = setOf("infrastructure", "storage")
        }
        
        card("features", TenantContext.Features(
            mapOf(
                "advanced_analytics" to true,
                "custom_branding" to true,
                "api_access" to false
            )
        )) {
            priority = 10
            tags = setOf("features", "config")
        }
    }
    
    fun switchToTenant(tenantId: String) {
        val deck = tenantDecks.getOrPut(tenantId) { createTenantDeck(tenantId) }
        switcher.switchTo(deck)
    }
    
    suspend fun <R> withTenant(tenantId: String, action: suspend (ContextDeck<TenantContext>) -> R): R {
        return switcher.withDeck(createTenantDeck(tenantId)) {
            action(switcher.current())
        }
    }
}

sealed class TenantContext {
    data class Tenant(val id: String, val name: String) : TenantContext()
    data class Database(val schema: String, val connectionPool: Int) : TenantContext()
    data class Storage(val bucket: String, val quota: Long) : TenantContext()
    data class Features(val flags: Map<String, Boolean>) : TenantContext()
}

// Example 3: Processing Pipeline with Context Cards
class DataPipeline {
    fun processingDeck() = contextDeck<ProcessingContext> {
        // Input configuration
        card("input", ProcessingContext.Input(
            source = "kafka://events",
            format = "json",
            batchSize = 1000
        )) {
            tags = setOf("source", "config")
        }
        
        // Transformation steps (ordered by priority)
        card("validate", ProcessingContext.Transform("validation", ::validateData)) {
            priority = 90
            tags = setOf("transform", "quality")
        }
        
        card("enrich", ProcessingContext.Transform("enrichment", ::enrichData)) {
            priority = 80
            tags = setOf("transform", "enhancement")
        }
        
        card("filter", ProcessingContext.Transform("filtering", ::filterData)) {
            priority = 70
            tags = setOf("transform", "quality")
        }
        
        // Output configuration
        card("output", ProcessingContext.Output(
            destination = "s3://processed-data",
            format = "parquet",
            compression = "snappy"
        )) {
            tags = setOf("sink", "config")
        }
    }
    
    suspend fun runPipeline(deck: ContextDeck<ProcessingContext>) {
        // Extract configurations
        val input = deck.findByName("input") as? ProcessingContext.Input
            ?: error("No input configuration")
        val output = deck.findByName("output") as? ProcessingContext.Output
            ?: error("No output configuration")
        
        // Get all transform steps in priority order
        val transforms = deck.filterByTag("transform")
            .let { transformDeck ->
                (0 until transformDeck.a).map { i -> transformDeck.b(i) }
                    .sortedByDescending { it.a.priority }
            }
        
        println("Pipeline: ${input.source} -> ${transforms.size} transforms -> ${output.destination}")
        
        // Process data through transform pipeline
        var data = loadData(input)
        transforms.forEach { card ->
            val transform = card.b as ProcessingContext.Transform
            data = transform.function(data)
            println("Applied ${transform.name}")
        }
        saveData(output, data)
    }
    
    private fun validateData(data: Any): Any = data
    private fun enrichData(data: Any): Any = data
    private fun filterData(data: Any): Any = data
    private fun loadData(input: ProcessingContext.Input): Any = "mock data"
    private fun saveData(output: ProcessingContext.Output, data: Any) {
        println("Saved to ${output.destination}")
    }
}

sealed class ProcessingContext {
    data class Input(val source: String, val format: String, val batchSize: Int) : ProcessingContext()
    data class Transform(val name: String, val function: (Any) -> Any) : ProcessingContext()
    data class Output(val destination: String, val format: String, val compression: String) : ProcessingContext()
}

// Example 4: Game State Context Deck
class GameContextExample {
    fun createGameDeck() = contextDeck<GameContext> {
        // Game configuration (bottom of deck)
        card("config", GameContext.Config(
            difficulty = "normal",
            soundEnabled = true,
            graphicsQuality = "high"
        )) {
            priority = 0
            tags = setOf("settings")
        }
        
        // Player state
        card("player", GameContext.Player(
            id = "player1",
            health = 100,
            score = 0,
            inventory = listOf("sword", "potion")
        )) {
            priority = 50
            tags = setOf("player", "state")
        }
        
        // Current level (high priority)
        trump("level", GameContext.Level(
            id = "level_1",
            name = "The Beginning",
            enemies = 5,
            treasures = 3
        ))
        
        // Active effects
        tagged("effect", "speed_boost", GameContext.Effect(
            name = "Speed Boost",
            duration = 30,
            modifier = 1.5f
        ))
        
        tagged("effect", "shield", GameContext.Effect(
            name = "Shield",
            duration = 60,
            modifier = 0.5f
        ))
    }
    
    fun demonstrateOperations() {
        val deck = createGameDeck()
        
        // Deal effects to different subsystems
        val effects = deck.filterByTag("effect")
        println("Active effects: ${effects.a}")
        
        // Push a new event onto the deck
        val withEvent = deck.push(
            ContextMeta("event", priority = 100) j 
            GameContext.Event("enemy_defeated", mapOf("xp" to 50))
        )
        
        // Pop and process the top card (the event)
        val (event, remainingDeck) = withEvent.pop()
        event?.let { card ->
            println("Processing ${card.a.name}: ${card.b}")
        }
        
        // Pattern match on cards
        deck.peek()?.match<GameContext, Unit> {
            byName("level") { level ->
                println("Current level: ${(level as GameContext.Level).name}")
            }
            byTag("effect") { effect ->
                println("Active effect: ${(effect as GameContext.Effect).name}")
            }
            otherwise { context ->
                println("Other context: $context")
            }
        }
    }
}

sealed class GameContext {
    data class Config(val difficulty: String, val soundEnabled: Boolean, val graphicsQuality: String) : GameContext()
    data class Player(val id: String, val health: Int, val score: Int, val inventory: List<String>) : GameContext()
    data class Level(val id: String, val name: String, val enemies: Int, val treasures: Int) : GameContext()
    data class Effect(val name: String, val duration: Int, val modifier: Float) : GameContext()
    data class Event(val type: String, val data: Map<String, Any>) : GameContext()
}

// Example 5: Coroutine Context Integration
suspend fun coroutineContextExample() = coroutineScope {
    val aiDeck = contextDeck<AIContext> {
        card("model", AIContext.Model("gpt-4", 0.7f, 2048))
        card("conversation", AIContext.Conversation("conv-123", mutableListOf()))
        card("tools", AIContext.Tools(listOf("search", "calculate", "generate")))
        trump("user", AIContext.User("user-456", "Alice", "premium"))
    }
    
    // Launch with deck in coroutine context
    launch(DeckCoroutineContext(aiDeck)) {
        val deckContext = coroutineContext[DeckCoroutineContext]
        
        // Access cards from coroutine context
        val model = deckContext?.findCard("model") as? AIContext.Model
        println("Using model: ${model?.name}")
        
        // Process each card asynchronously
        deckContext?.deck?.forEachCard { card ->
            delay(100) // Simulate async processing
            println("Processing ${card.a.name}")
        }
    }
}

sealed class AIContext {
    data class Model(val name: String, val temperature: Float, val maxTokens: Int) : AIContext()
    data class Conversation(val id: String, val messages: MutableList<String>) : AIContext()
    data class Tools(val available: List<String>) : AIContext()
    data class User(val id: String, val name: String, val tier: String) : AIContext()
}

// Helper extension to find card by name
fun <T> ContextDeck<T>.findByName(name: String): T? {
    for (i in 0 until this.a) {
        val card = this.b(i)
        if (card.a.name == name) {
            return card.b
        }
    }
    return null
}

// Example 6: Context Deck Composition
fun compositionExample() {
    // Create base decks
    val authDeck = contextDeck<AuthContext> {
        card("user", AuthContext.User("123", "alice@example.com"))
        card("session", AuthContext.Session("sess-123", kotlinx.datetime.Clock.System.now().toEpochMilliseconds()))
        card("permissions", AuthContext.Permissions(setOf("read", "write")))
    }
    
    val featureDeck = contextDeck<FeatureContext> {
        card("feature_a", FeatureContext.Flag("feature_a", true))
        card("feature_b", FeatureContext.Flag("feature_b", false))
        card("experiment", FeatureContext.Experiment("exp_1", "variant_b"))
    }
    
    // Compose decks
    val combinedDeck = mergeDeck(
        authDeck as ContextDeck<Any>,
        featureDeck as ContextDeck<Any>
    )
    
    println("Combined deck size: ${combinedDeck.a}")
    
    // Chain decks with 'then'
    val fullDeck = (authDeck as ContextDeck<Any>) then (featureDeck as ContextDeck<Any>)
    println("Chained deck size: ${fullDeck.a}")
}

sealed class AuthContext {
    data class User(val id: String, val email: String) : AuthContext()
    data class Session(val id: String, val startedAt: Long) : AuthContext()
    data class Permissions(val granted: Set<String>) : AuthContext()
}

sealed class FeatureContext {
    data class Flag(val name: String, val enabled: Boolean) : FeatureContext()
    data class Experiment(val name: String, val variant: String) : FeatureContext()
}