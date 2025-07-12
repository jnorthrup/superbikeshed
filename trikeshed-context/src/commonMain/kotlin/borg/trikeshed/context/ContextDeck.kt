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
        for (i in 0 until other.component1()) {
            cards.add(other.component2()(i))
        }
    }
    
    fun build(): ContextDeck<T> {
        // Sort by priority (highest first)
        val sorted = cards.sortedByDescending { it.component1().priority }
        return \1 j { \2: Int -> sorted[i] }
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
    return (this.component1() + \1 j { \2: Int ->
        if (i == 0) card else this.component2()(i - 1)
    }
}

fun <T> ContextDeck<T>.pop(): Join<ContextCard<T>?, ContextDeck<T>> {
    return if (this.component1() == 0) {
        null j this
    } else {
        val top = this.component2()(0)
        val remaining = (this.component1() - \1 j { \2: Int -> this.component2()(i + 1) }
        top j remaining
    }
}

fun <T> ContextDeck<T>.peek(): ContextCard<T>? {
    return if (this.component1() > 0) this.component2()(0) else null
}

fun <T> ContextDeck<T>.shuffle(): ContextDeck<T> {
    val indices = (0 until this.component1()).shuffled()
    return \1 j { \2: Int -> this.component2()(indices[i]) }
}

fun <T> ContextDeck<T>.reverse(): ContextDeck<T> {
    return \1 j { \2: Int -> this.component2()(this.component1() - 1 - i) }
}

fun <T> ContextDeck<T>.filterByTag(tag: String): ContextDeck<T> {
    val filtered = mutableListOf<ContextCard<T>>()
    for (i in 0 until this.component1()) {
        val card = this.component2()(i)
        if (tag in card.component1().tags) {
            filtered.add(card)
        }
    }
    return \1 j { \2: Int -> filtered[i] }
}

fun <T> ContextDeck<T>.deal(hands: Int): Indexed<ContextDeck<T>> {
    val dealt = Array(hands) { mutableListOf<ContextCard<T>>() }
    for (i in 0 until this.component1()) {
        dealt[i % hands].add(this.component2()(i))
    }
    return \1 j { \2: Int -> 
        \1 j { \2: Int -> dealt[h][i] }
    }
}

// Context switching DSL
class ContextSwitcher<T> {
    private var currentDeck: ContextDeck<T> = \1 j { \2: Int -> throw IndexOutOfBoundsException() }
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
        return if (index < deck.component1()) {
            action(deck.component2()(index).component2())
        } else null
    }
    
    fun findCard(name: String): T? {
        for (i in 0 until deck.component1()) {
            val card = deck.component2()(i)
            if (card.component1().name == name) {
                return card.component2()
            }
        }
        return null
    }
}

// Suspend functions for async context operations
suspend fun <T> ContextDeck<T>.forEachCard(action: suspend (ContextCard<T>) -> Unit) {
    for (i in 0 until this.component1()) {
        action(this.component2()(i))
    }
}

suspend fun <T, R> ContextDeck<T>.mapCards(transform: suspend (ContextCard<T>) -> R): Indexed<R> {
    return \1 j { \2: Int ->
        transform(this.component2()(i))
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
    val totalSize = decks.sumOf { it.component1() }
    var offset = 0
    return \1 j { \2: Int ->
        var deckIndex = 0
        var localIndex = globalIndex
        
        while (deckIndex < decks.size && localIndex >= decks[deckIndex].component1()) {
            localIndex -= decks[deckIndex].component1()
            deckIndex++
        }
        
        decks[deckIndex].component2()(localIndex)
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
        if (result == null && card.component1().name == name) {
            result = action(card.component2())
        }
    }
    
    fun byTag(tag: String, action: (T) -> R) {
        if (result == null && tag in card.component1().tags) {
            result = action(card.component2())
        }
    }
    
    fun byPriority(minPriority: Int, action: (T) -> R) {
        if (result == null && card.component1().priority >= minPriority) {
            result = action(card.component2())
        }
    }
    
    fun otherwise(action: (T) -> R) {
        if (result == null) {
            result = action(card.component2())
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
    val end = range.last.coerceAtMost(this.component1() - 1)
    val size = if (end >= start) end - start + 1 else 0
    return \1 j { \2: Int -> this.component2()(start + i) }
}

// Top N cards by priority
fun <T> ContextDeck<T>.topCards(n: Int): ContextDeck<T> {
    val sorted = (0 until this.component1())
        .map { i -> this.component2()(i) }
        .sortedByDescending { it.component1().priority }
        .take(n)
    return \1 j { \2: Int -> sorted[i] }
}