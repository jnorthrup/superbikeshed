package borg.trikeshed.context

import borg.trikeshed.lib.*
import kotlinx.coroutines.CoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext.Element

/**
 * Context Deck DSL - Stack contexts like a deck of cards
 * 
 * Each context is a "card" that can be:
 * - Pushed onto the deck (added to top)
 * - Popped from the deck (removed from top)
 * - Shuffled (reordered)
 * - Dealt (distributed to different execution contexts)
 * - Combined (merged with other cards)
 */

// Core types using TrikeShed structures
typealias ContextCard<T> = Join<ContextMeta, T>
typealias ContextDeck<T> = Indexed<ContextCard<T>>
typealias ContextHand<T> = Join<String, ContextDeck<T>> // Named collection of cards

data class ContextMeta(
    val name: String,
    val priority: Int = 0,
    val tags: Set<String> = emptySet(),
    val createdAt: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
)

// DSL entry point
inline fun <T> contextDeck(builder: ContextDeckBuilder<T>.() -> Unit): ContextDeck<T> {
    return ContextDeckBuilder<T>().apply(builder).build()
}

// Builder for the deck
class ContextDeckBuilder<T> {
    private val cards = mutableListOf<ContextCard<T>>()
    
    // Add a card to the deck
    fun card(name: String, value: T, config: ContextMeta.() -> Unit = {}) {
        val meta = ContextMeta(name).apply(config)
        cards.add(meta j value)
    }
    
    // Add multiple cards at once
    fun cards(vararg pairs: Pair<String, T>) {
        pairs.forEach { (name, value) ->
            card(name, value)
        }
    }
    
    // Add a high-priority card (goes to top)
    fun trump(name: String, value: T) {
        card(name, value) {
            priority = 100
        }
    }
    
    // Add a tagged card
    fun tagged(tag: String, name: String, value: T) {
        card(name, value) {
            tags = setOf(tag)
        }
    }
    
    // Import cards from another deck
    fun fromDeck(other: ContextDeck<T>) {
        for (i in 0 until other.a) {
            cards.add(other.b(i))
        }
    }
    
    fun build(): ContextDeck<T> {
        // Sort by priority (highest first)
        val sorted = cards.sortedByDescending { it.a.priority }
        return sorted.size j { i -> sorted[i] }
    }
}

// Deck operations
interface DeckOperations<T> {
    // Stack operations
    fun push(card: ContextCard<T>): ContextDeck<T>
    fun pop(): Join<ContextCard<T>?, ContextDeck<T>>
    fun peek(): ContextCard<T>?
    
    // Deck manipulation
    fun shuffle(): ContextDeck<T>
    fun reverse(): ContextDeck<T>
    fun sort(by: (ContextCard<T>) -> Comparable<*>): ContextDeck<T>
    
    // Filtering
    fun filter(predicate: (ContextCard<T>) -> Boolean): ContextDeck<T>
    fun filterByTag(tag: String): ContextDeck<T>
    fun filterByName(pattern: Regex): ContextDeck<T>
    
    // Dealing cards
    fun deal(hands: Int): Indexed<ContextDeck<T>>
    fun dealByTag(): Map<String, ContextDeck<T>>
}

// Extension functions for deck operations
fun <T> ContextDeck<T>.push(card: ContextCard<T>): ContextDeck<T> {
    return (this.a + 1) j { i ->
        if (i == 0) card else this.b(i - 1)
    }
}

fun <T> ContextDeck<T>.pop(): Join<ContextCard<T>?, ContextDeck<T>> {
    return if (this.a == 0) {
        null j this
    } else {
        val top = this.b(0)
        val remaining = (this.a - 1) j { i -> this.b(i + 1) }
        top j remaining
    }
}

fun <T> ContextDeck<T>.peek(): ContextCard<T>? {
    return if (this.a > 0) this.b(0) else null
}

fun <T> ContextDeck<T>.shuffle(): ContextDeck<T> {
    val indices = (0 until this.a).shuffled()
    return this.a j { i -> this.b(indices[i]) }
}

fun <T> ContextDeck<T>.reverse(): ContextDeck<T> {
    return this.a j { i -> this.b(this.a - 1 - i) }
}

fun <T> ContextDeck<T>.filterByTag(tag: String): ContextDeck<T> {
    val filtered = mutableListOf<ContextCard<T>>()
    for (i in 0 until this.a) {
        val card = this.b(i)
        if (tag in card.a.tags) {
            filtered.add(card)
        }
    }
    return filtered.size j { i -> filtered[i] }
}

fun <T> ContextDeck<T>.deal(hands: Int): Indexed<ContextDeck<T>> {
    val dealt = Array(hands) { mutableListOf<ContextCard<T>>() }
    for (i in 0 until this.a) {
        dealt[i % hands].add(this.b(i))
    }
    return hands j { h -> 
        dealt[h].size j { i -> dealt[h][i] }
    }
}

// Context switching DSL
class ContextSwitcher<T> {
    private var currentDeck: ContextDeck<T> = 0 j { _ -> throw IndexOutOfBoundsException() }
    private val history = mutableListOf<ContextDeck<T>>()
    
    fun switchTo(deck: ContextDeck<T>) {
        history.add(currentDeck)
        currentDeck = deck
    }
    
    fun back(): Boolean {
        if (history.isEmpty()) return false
        currentDeck = history.removeAt(history.lastIndex)
        return true
    }
    
    fun current(): ContextDeck<T> = currentDeck
    
    inline fun <R> withDeck(deck: ContextDeck<T>, action: () -> R): R {
        val previous = currentDeck
        currentDeck = deck
        return try {
            action()
        } finally {
            currentDeck = previous
        }
    }
}

// Coroutine context integration
class DeckCoroutineContext<T>(
    val deck: ContextDeck<T>
) : AbstractCoroutineContextElement(DeckCoroutineContext) {
    companion object Key : CoroutineContext.Key<DeckCoroutineContext<*>>
    
    fun <R> withCard(index: Int, action: (T) -> R): R? {
        return if (index < deck.a) {
            action(deck.b(index).b)
        } else null
    }
    
    fun findCard(name: String): T? {
        for (i in 0 until deck.a) {
            val card = deck.b(i)
            if (card.a.name == name) {
                return card.b
            }
        }
        return null
    }
}

// Suspend functions for async context operations
suspend fun <T> ContextDeck<T>.forEachCard(action: suspend (ContextCard<T>) -> Unit) {
    for (i in 0 until this.a) {
        action(this.b(i))
    }
}

suspend fun <T, R> ContextDeck<T>.mapCards(transform: suspend (ContextCard<T>) -> R): Indexed<R> {
    return this.a j { i ->
        transform(this.b(i))
    }
}

// Practical example contexts
sealed class AppContext {
    data class User(val id: String, val name: String, val roles: Set<String>) : AppContext()
    data class Request(val id: String, val path: String, val headers: Map<String, String>) : AppContext()
    data class Database(val connectionString: String, val poolSize: Int) : AppContext()
    data class Feature(val name: String, val enabled: Boolean, val config: Map<String, Any>) : AppContext()
}

// Example usage builder
fun exampleDeck() = contextDeck<AppContext> {
    // User context with high priority
    trump("user", AppContext.User("123", "Alice", setOf("admin", "user")))
    
    // Request context
    card("request", AppContext.Request("req-456", "/api/users", mapOf("Accept" to "application/json"))) {
        tags = setOf("http", "api")
    }
    
    // Database contexts with tags
    tagged("database", "primary-db", AppContext.Database("postgres://primary", 10))
    tagged("database", "cache-db", AppContext.Database("redis://cache", 5))
    
    // Feature flags
    cards(
        "feature-darkmode" to AppContext.Feature("darkMode", true, emptyMap()),
        "feature-beta" to AppContext.Feature("betaFeatures", false, emptyMap())
    )
}

// Merge multiple decks
fun <T> mergeDeck(vararg decks: ContextDeck<T>): ContextDeck<T> {
    val totalSize = decks.sumOf { it.a }
    var offset = 0
    return totalSize j { globalIndex ->
        var deckIndex = 0
        var localIndex = globalIndex
        
        while (deckIndex < decks.size && localIndex >= decks[deckIndex].a) {
            localIndex -= decks[deckIndex].a
            deckIndex++
        }
        
        decks[deckIndex].b(localIndex)
    }
}

// Pattern matching on context cards
inline fun <T, R> ContextCard<T>.match(
    block: ContextCardMatcher<T, R>.() -> Unit
): R? {
    val matcher = ContextCardMatcher<T, R>(this)
    matcher.block()
    return matcher.result
}

class ContextCardMatcher<T, R>(private val card: ContextCard<T>) {
    var result: R? = null
        private set
    
    fun byName(name: String, action: (T) -> R) {
        if (result == null && card.a.name == name) {
            result = action(card.b)
        }
    }
    
    fun byTag(tag: String, action: (T) -> R) {
        if (result == null && tag in card.a.tags) {
            result = action(card.b)
        }
    }
    
    fun byPriority(minPriority: Int, action: (T) -> R) {
        if (result == null && card.a.priority >= minPriority) {
            result = action(card.b)
        }
    }
    
    fun otherwise(action: (T) -> R) {
        if (result == null) {
            result = action(card.b)
        }
    }
}

// Context deck composition
infix fun <T> ContextDeck<T>.then(other: ContextDeck<T>): ContextDeck<T> {
    return mergeDeck(this, other)
}

// Slicing operations
fun <T> ContextDeck<T>.slice(range: IntRange): ContextDeck<T> {
    val start = range.first.coerceAtLeast(0)
    val end = range.last.coerceAtMost(this.a - 1)
    val size = if (end >= start) end - start + 1 else 0
    return size j { i -> this.b(start + i) }
}

// Top N cards by priority
fun <T> ContextDeck<T>.topCards(n: Int): ContextDeck<T> {
    val sorted = (0 until this.a)
        .map { i -> this.b(i) }
        .sortedByDescending { it.a.priority }
        .take(n)
    return sorted.size j { i -> sorted[i] }
}