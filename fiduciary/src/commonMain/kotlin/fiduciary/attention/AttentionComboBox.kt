package fiduciary.attention

import kotlinx.coroutines.flow.*

/**
 * Attention as a ComboBox - you can either:
 * 1. Drop down to see recent/relevant items
 * 2. Type to query/filter items
 * 
 * This is the core attention UI metaphor for fiduciary
 */
interface AttentionComboBox<T> {
    // Current selection
    val selected: StateFlow<T?>
    
    // Dropdown items (recent, suggested, pinned)
    val dropdownItems: StateFlow<List<T>>
    
    // Query/search
    suspend fun query(text: String): List<T>
    
    // Select an item (records attention)
    suspend fun select(item: T)
    
    // Open dropdown (shows suggestions)
    suspend fun openDropdown()
    
    // Close dropdown
    suspend fun closeDropdown()
}

/**
 * Fiduciary Attention ComboBox implementation
 * Combines recent history, AI suggestions, and search
 */
class FiduciaryAttentionComboBox(
    private val navigator: AttentionNavigator,
    private val maxDropdownItems: Int = 10
) : AttentionComboBox<NavigableNode> {
    
    private val _selected = MutableStateFlow<NavigableNode?>(null)
    override val selected: StateFlow<NavigableNode?> = _selected
    
    private val _dropdownItems = MutableStateFlow<List<NavigableNode>>(emptyList())
    override val dropdownItems: StateFlow<List<NavigableNode>> = _dropdownItems
    
    private val recentItems = mutableListOf<NavigableNode>()
    private val pinnedItems = mutableSetOf<NavigableNode>()
    private val aiSuggestions = mutableListOf<NavigableNode>()
    
    override suspend fun query(text: String): List<NavigableNode> {
        if (text.isBlank()) {
            // Empty query returns dropdown items
            return dropdownItems.value
        }
        
        // Search across all sources
        val results = mutableListOf<NavigableNode>()
        navigator.search(text).take(maxDropdownItems).collect { node ->
            results.add(node)
        }
        
        // Record this as a search attention event
        _selected.value?.let { current ->
            navigator.recordAttention(current, AttentionAction.SEARCH)
        }
        
        return results
    }
    
    override suspend fun select(item: NavigableNode) {
        _selected.value = item
        
        // Record attention
        navigator.recordAttention(item, AttentionAction.SELECT)
        
        // Add to recent items
        recentItems.remove(item) // Remove if exists
        recentItems.add(0, item) // Add to front
        if (recentItems.size > maxDropdownItems) {
            recentItems.removeAt(recentItems.size - 1)
        }
        
        // Update dropdown
        updateDropdownItems()
        
        // Close dropdown after selection
        closeDropdown()
    }
    
    override suspend fun openDropdown() {
        _selected.value?.let { current ->
            navigator.recordAttention(current, AttentionAction.VIEW)
        }
        updateDropdownItems()
    }
    
    override suspend fun closeDropdown() {
        // Could clear dropdown or keep it
    }
    
    private suspend fun updateDropdownItems() {
        val items = mutableListOf<NavigableNode>()
        
        // 1. Pinned items first
        items.addAll(pinnedItems)
        
        // 2. Current context items (siblings, children)
        _selected.value?.let { current ->
            // Add children if it's a container
            if (current.hasChildren) {
                val children = navigator.getChildren(current).take(3)
                items.addAll(children)
            }
            
            // Add siblings
            current.parent?.let { parentId ->
                val parent = NavigableNode(parentId, "", NodeType.DIRECTORY)
                val siblings = navigator.getChildren(parent)
                    .filter { it.id != current.id }
                    .take(2)
                items.addAll(siblings)
            }
        }
        
        // 3. Recent items (excluding already added)
        val existingIds = items.map { it.id }.toSet()
        items.addAll(recentItems.filter { it.id !in existingIds })
        
        // 4. AI suggestions
        items.addAll(aiSuggestions.filter { it.id !in items.map { it.id } })
        
        // Limit to max items
        _dropdownItems.value = items.take(maxDropdownItems)
    }
    
    // Additional features
    fun pin(item: NavigableNode) {
        pinnedItems.add(item)
        updateDropdownItems()
    }
    
    fun unpin(item: NavigableNode) {
        pinnedItems.remove(item)
        updateDropdownItems()
    }
    
    fun clearRecent() {
        recentItems.clear()
        updateDropdownItems()
    }
    
    fun setSuggestions(suggestions: List<NavigableNode>) {
        aiSuggestions.clear()
        aiSuggestions.addAll(suggestions)
        updateDropdownItems()
    }
}

/**
 * Attention ComboBox for specific data types
 */
class TypedAttentionComboBox<T>(
    private val items: List<T>,
    private val searchFn: (T, String) -> Boolean,
    private val displayFn: (T) -> String
) : AttentionComboBox<T> {
    
    private val _selected = MutableStateFlow<T?>(null)
    override val selected: StateFlow<T?> = _selected
    
    private val _dropdownItems = MutableStateFlow<List<T>>(emptyList())
    override val dropdownItems: StateFlow<List<T>> = _dropdownItems
    
    override suspend fun query(text: String): List<T> {
        return if (text.isBlank()) {
            items.take(10)
        } else {
            items.filter { searchFn(it, text) }.take(10)
        }
    }
    
    override suspend fun select(item: T) {
        _selected.value = item
    }
    
    override suspend fun openDropdown() {
        _dropdownItems.value = items.take(10)
    }
    
    override suspend fun closeDropdown() {
        _dropdownItems.value = emptyList()
    }
}

/**
 * Multi-select attention combo box
 */
class MultiAttentionComboBox<T>(
    private val baseComboBox: AttentionComboBox<T>
) : AttentionComboBox<List<T>> {
    
    private val _selected = MutableStateFlow<List<T>>(emptyList())
    override val selected: StateFlow<List<T>> = _selected
    
    override val dropdownItems: StateFlow<List<List<T>>> = 
        baseComboBox.dropdownItems.map { items ->
            items.map { listOf(it) }
        }.stateIn(
            scope = kotlinx.coroutines.GlobalScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )
    
    override suspend fun query(text: String): List<List<T>> {
        return baseComboBox.query(text).map { listOf(it) }
    }
    
    override suspend fun select(item: List<T>) {
        _selected.value = item
    }
    
    override suspend fun openDropdown() {
        baseComboBox.openDropdown()
    }
    
    override suspend fun closeDropdown() {
        baseComboBox.closeDropdown()
    }
    
    // Multi-select specific
    fun toggle(item: T) {
        val current = _selected.value.toMutableList()
        if (item in current) {
            current.remove(item)
        } else {
            current.add(item)
        }
        _selected.value = current
    }
}

/**
 * Factory for creating attention combo boxes
 */
object AttentionComboBoxFactory {
    fun createForFiduciary(): FiduciaryAttentionComboBox {
        val navigator = NavigatorFactory.createFiduciaryNavigator()
        return FiduciaryAttentionComboBox(navigator)
    }
    
    fun createForDivineIndexes(): AttentionComboBox<NavigableNode> {
        val navigator = FileSystemNavigator("/data/divine-indexes")
        return FiduciaryAttentionComboBox(navigator)
    }
    
    fun createForIDETelemetry(): AttentionComboBox<String> {
        val ides = listOf("IntelliJ", "VS Code", "Eclipse", "Vim", "Emacs")
        return TypedAttentionComboBox(
            items = ides,
            searchFn = { ide, query -> ide.contains(query, ignoreCase = true) },
            displayFn = { it }
        )
    }
}