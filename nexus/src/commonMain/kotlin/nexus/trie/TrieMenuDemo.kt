package nexus.trie

import borg.trikeshed.lib.*

/**
 * Demo of Trie-based menu selection for Nexus
 */

// Example: IntelliJ-style action menu
fun createIntelliJActionMenu() = trieMenu<String> {
    // Refactoring actions
    item("refactor", "refactor-menu") {
        description = "Refactoring menu"
    }
    item("refactor.rename", "rename-symbol") {
        description = "Rename symbol (Shift+F6)"
        shortcut = "rn"
    }
    item("refactor.extract.method", "extract-method") {
        description = "Extract Method"
        shortcut = "rem"
    }
    item("refactor.extract.variable", "extract-variable") {
        description = "Extract Variable"
        shortcut = "rev"
    }
    item("refactor.inline", "inline") {
        description = "Inline"
        shortcut = "ri"
    }
    
    // Navigation
    item("navigate", "navigate-menu") {
        description = "Navigation menu"
    }
    item("navigate.class", "go-to-class") {
        description = "Go to Class"
        shortcut = "nc"
    }
    item("navigate.file", "go-to-file") {
        description = "Go to File"
        shortcut = "nf"
    }
    item("navigate.symbol", "go-to-symbol") {
        description = "Go to Symbol"
        shortcut = "ns"
    }
    
    // Code actions
    item("code", "code-menu") {
        description = "Code menu"
    }
    item("code.generate", "generate") {
        description = "Generate code"
        shortcut = "cg"
    }
    item("code.optimize.imports", "optimize-imports") {
        description = "Optimize Imports"
        shortcut = "coi"
    }
    item("code.reformat", "reformat-code") {
        description = "Reformat Code"
        shortcut = "cf"
    }
    
    // Build actions
    item("build", "build-project") {
        description = "Build Project"
        shortcut = "b"
    }
    item("build.clean", "clean-build") {
        description = "Clean and Build"
        shortcut = "bc"
    }
    
    // Git actions
    item("git", "git-menu") {
        description = "Git menu"
    }
    item("git.commit", "git-commit") {
        description = "Commit"
        shortcut = "gc"
    }
    item("git.pull", "git-pull") {
        description = "Pull"
        shortcut = "gp"
    }
    item("git.push", "git-push") {
        description = "Push"
        shortcut = "gP"
    }
}

// Example: Series to Indexed migration menu
fun createMigrationMenu() = trieMenu<() -> Unit> {
    item("migrate", { println("Starting migration...") }) {
        description = "Migration operations"
    }
    
    item("migrate.series", { println("Migrating Series types...") }) {
        description = "Migrate all Series to Indexed"
        shortcut = "ms"
    }
    
    item("migrate.series.byte", { 
        println("ByteSeries → ByteIndexed")
    }) {
        description = "ByteSeries to ByteIndexed"
        shortcut = "msb"
    }
    
    item("migrate.series.char", {
        println("CharSeries → CharIndexed")
    }) {
        description = "CharSeries to CharIndexed"
        shortcut = "msc"
    }
    
    item("migrate.series.int", {
        println("IntSeries → IntIndexed")
    }) {
        description = "IntSeries to IntIndexed"
        shortcut = "msi"
    }
    
    item("analyze", { println("Analyzing codebase...") }) {
        description = "Analysis operations"
    }
    
    item("analyze.usages", { println("Finding usages...") }) {
        description = "Find all Series usages"
        shortcut = "au"
    }
    
    item("analyze.impact", { println("Impact analysis...") }) {
        description = "Analyze migration impact"
        shortcut = "ai"
    }
}

// Demo function showing usage
fun demoTrieMenu() {
    val menu = createIntelliJActionMenu()
    val interactive = InteractiveTrieMenu(menu)
    
    println("=== Trie Menu Demo ===")
    println(interactive.render())
    
    // Simulate user input
    val inputs = listOf('r', 'e', 'f')
    
    inputs.forEach { char ->
        println("\nUser types: '$char'")
        val result = interactive.processChar(char)
        
        when (result) {
            is SelectionResult.Selected -> {
                println("✓ Selected: ${result.item.value} - ${result.item.description}")
                interactive.reset()
            }
            is SelectionResult.Partial -> {
                println("Partial match: ${result.options.size} options")
                println(interactive.render())
            }
            is SelectionResult.NoMatch -> {
                println("✗ No match for: ${result.input}")
                interactive.reset()
            }
        }
    }
}

// Integration example with Nexus
class TrieMenuAttention(
    private val menu: TrieMenuSelector<AttentionFragment>
) : AttentionFragment {
    
    fun handleInput(input: String): AttentionFragment? {
        return when (val result = menu.select(input)) {
            is SelectionResult.Selected -> result.item.value
            is SelectionResult.Partial -> {
                // Return menu display as attention
                AttentionFragment.Info(
                    "Options: ${result.options.joinToString { it.key }}"
                )
            }
            is SelectionResult.NoMatch -> {
                AttentionFragment.Error("No match for: $input")
            }
        }
    }
}

// Create Nexus action menu
fun createNexusActionMenu() = trieMenu<AttentionFragment> {
    item("intellij", AttentionFragment.Action("Connect to IntelliJ")) {
        description = "IntelliJ integration"
        shortcut = "i"
    }
    
    item("intellij.errors", AttentionFragment.Action("Fetch compilation errors")) {
        description = "Get IntelliJ errors"
        shortcut = "ie"
    }
    
    item("intellij.refactor", AttentionFragment.Action("Refactor via IntelliJ")) {
        description = "IntelliJ refactoring"
        shortcut = "ir"
    }
    
    item("build", AttentionFragment.Action("Build project")) {
        description = "Gradle build"
        shortcut = "b"
    }
    
    item("test", AttentionFragment.Action("Run tests")) {
        description = "Run test suite"
        shortcut = "t"
    }
    
    item("migrate", AttentionFragment.Action("Start migration")) {
        description = "Series → Indexed migration"
        shortcut = "m"
    }
}