package nexus.trie

import borg.trikeshed.lib.*

/**
 * Trie-based menu selector for efficient character navigation
 * Allows 1-n character input to navigate and select options
 */

// Trie node for menu structure
data class TrieNode<T>(
    val char: Char? = null,
    val value: T? = null,
    val children: MutableMap<Char, TrieNode<T>> = mutableMapOf(),
    val isTerminal: Boolean = false,
    val path: String = ""
)

// Menu item with metadata
data class MenuItem<T>(
    val key: String,
    val value: T,
    val description: String = "",
    val shortcut: String? = null
)

// Selection result
sealed class SelectionResult<T> {
    data class Selected<T>(val item: MenuItem<T>) : SelectionResult<T>()
    data class Partial<T>(val options: List<MenuItem<T>>, val prefix: String) : SelectionResult<T>()
    data class NoMatch<T>(val input: String) : SelectionResult<T>()
}

// Trie-based menu selector
class TrieMenuSelector<T>(
    private val items: List<MenuItem<T>>
) {
    private val root = TrieNode<T>()
    
    init {
        // Build trie from menu items
        items.forEach { item ->
            insertItem(item)
            // Also insert shortcut if provided
            item.shortcut?.let { 
                insertPath(it, item)
            }
        }
    }
    
    private fun insertItem(item: MenuItem<T>) {
        insertPath(item.key, item)
    }
    
    private fun insertPath(path: String, item: MenuItem<T>) {
        var current = root
        path.forEachIndexed { index, char ->
            current = current.children.getOrPut(char) {
                TrieNode(
                    char = char,
                    path = path.substring(0, index + 1)
                )
            }
        }
        current.value = item.value
        current.isTerminal = true
    }
    
    // Select based on input characters
    fun select(input: String): SelectionResult<T> {
        var current = root
        
        // Navigate trie
        for (char in input) {
            current = current.children[char] 
                ?: return SelectionResult.NoMatch(input)
        }
        
        // Check if terminal node
        if (current.isTerminal && current.value != null) {
            val item = items.find { it.value == current.value }
                ?: return SelectionResult.NoMatch(input)
            return SelectionResult.Selected(item)
        }
        
        // Collect all possible completions
        val options = collectOptions(current, input)
        return if (options.isEmpty()) {
            SelectionResult.NoMatch(input)
        } else {
            SelectionResult.Partial(options, input)
        }
    }
    
    private fun collectOptions(node: TrieNode<T>, prefix: String): List<MenuItem<T>> {
        val results = mutableListOf<MenuItem<T>>()
        
        fun traverse(current: TrieNode<T>, path: String) {
            if (current.isTerminal && current.value != null) {
                items.find { it.value == current.value }?.let { 
                    results.add(it)
                }
            }
            current.children.forEach { (char, child) ->
                traverse(child, path + char)
            }
        }
        
        traverse(node, prefix)
        return results
    }
    
    // Get all options at current position
    fun getOptions(prefix: String = ""): List<MenuItem<T>> {
        if (prefix.isEmpty()) {
            return items
        }
        
        var current = root
        for (char in prefix) {
            current = current.children[char] ?: return emptyList()
        }
        
        return collectOptions(current, prefix)
    }
    
    // Render menu as tree
    fun renderMenu(prefix: String = ""): String {
        val options = getOptions(prefix)
        val sb = StringBuilder()
        
        if (prefix.isNotEmpty()) {
            sb.appendLine("Current: $prefix")
            sb.appendLine("─".repeat(40))
        }
        
        // Group by next character
        val grouped = options.groupBy { 
            it.key.drop(prefix.length).firstOrNull() ?: ' '
        }
        
        grouped.forEach { (nextChar, items) ->
            if (items.size == 1 && items[0].key == prefix + nextChar) {
                // Terminal option
                val item = items[0]
                sb.append("  $nextChar → ${item.value}")
                if (item.description.isNotEmpty()) {
                    sb.append(" (${item.description})")
                }
                if (item.shortcut != null) {
                    sb.append(" [${item.shortcut}]")
                }
                sb.appendLine()
            } else {
                // Branch with multiple options
                sb.appendLine("  $nextChar → (${items.size} options)")
            }
        }
        
        return sb.toString()
    }
}

// Interactive selector with state
class InteractiveTrieMenu<T>(
    private val selector: TrieMenuSelector<T>,
    private val prompt: String = "> "
) {
    private var currentInput = StringBuilder()
    
    fun processChar(char: Char): SelectionResult<T> {
        when (char) {
            '\b' -> { // Backspace
                if (currentInput.isNotEmpty()) {
                    currentInput.deleteCharAt(currentInput.length - 1)
                }
            }
            '\n', '\r' -> { // Enter - select if unique
                val options = selector.getOptions(currentInput.toString())
                if (options.size == 1) {
                    currentInput.clear()
                    return SelectionResult.Selected(options[0])
                }
            }
            else -> {
                currentInput.append(char)
            }
        }
        
        return selector.select(currentInput.toString())
    }
    
    fun reset() {
        currentInput.clear()
    }
    
    fun getCurrentInput(): String = currentInput.toString()
    
    fun render(): String {
        val menu = selector.renderMenu(currentInput.toString())
        return buildString {
            appendLine(menu)
            append(prompt)
            append(currentInput)
        }
    }
}

// Extension for creating menu from pairs
fun <T> trieMenuOf(vararg items: Pair<String, T>): TrieMenuSelector<T> {
    val menuItems = items.map { (key, value) ->
        MenuItem(key = key, value = value)
    }
    return TrieMenuSelector(menuItems)
}

// Builder DSL
class TrieMenuBuilder<T> {
    private val items = mutableListOf<MenuItem<T>>()
    
    fun item(key: String, value: T, block: MenuItemBuilder<T>.() -> Unit = {}) {
        val builder = MenuItemBuilder<T>().apply(block)
        items.add(MenuItem(
            key = key,
            value = value,
            description = builder.description,
            shortcut = builder.shortcut
        ))
    }
    
    fun build(): TrieMenuSelector<T> = TrieMenuSelector(items)
}

class MenuItemBuilder<T> {
    var description: String = ""
    var shortcut: String? = null
}

fun <T> trieMenu(block: TrieMenuBuilder<T>.() -> Unit): TrieMenuSelector<T> {
    return TrieMenuBuilder<T>().apply(block).build()
}

// Example usage in Nexus
fun createNexusCommandMenu() = trieMenu<() -> Unit> {
    item("refactor", { println("Refactoring...") }) {
        description = "Refactoring operations"
    }
    
    item("refactor.rename", { println("Rename...") }) {
        description = "Rename symbol"
        shortcut = "rr"
    }
    
    item("refactor.extract", { println("Extract...") }) {
        description = "Extract method/variable"
        shortcut = "re"
    }
    
    item("search", { println("Searching...") }) {
        description = "Search operations"
        shortcut = "s"
    }
    
    item("search.files", { println("Search files...") }) {
        description = "Search in files"
        shortcut = "sf"
    }
    
    item("build", { println("Building...") }) {
        description = "Build project"
        shortcut = "b"
    }
    
    item("test", { println("Testing...") }) {
        description = "Run tests"
        shortcut = "t"
    }
}

// Integration with Nexus attention
fun TrieMenuSelector<AttentionFragment>.asAttentionSelector(): (String) -> AttentionFragment? {
    return { input ->
        when (val result = select(input)) {
            is SelectionResult.Selected -> result.item.value
            else -> null
        }
    }
}