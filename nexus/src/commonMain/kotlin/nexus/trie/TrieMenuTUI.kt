package nexus.trie

import borg.trikeshed.lib.*

/**
 * TUI widget for Trie menu - supports both plain console and ANSI terminals
 */

// ANSI escape codes
object ANSI {
    const val RESET = "\u001B[0m"
    const val BOLD = "\u001B[1m"
    const val DIM = "\u001B[2m"
    const val UNDERLINE = "\u001B[4m"
    const val REVERSE = "\u001B[7m"
    
    // Colors
    const val BLACK = "\u001B[30m"
    const val RED = "\u001B[31m"
    const val GREEN = "\u001B[32m"
    const val YELLOW = "\u001B[33m"
    const val BLUE = "\u001B[34m"
    const val MAGENTA = "\u001B[35m"
    const val CYAN = "\u001B[36m"
    const val WHITE = "\u001B[37m"
    
    // Background
    const val BG_BLACK = "\u001B[40m"
    const val BG_RED = "\u001B[41m"
    const val BG_GREEN = "\u001B[42m"
    const val BG_YELLOW = "\u001B[43m"
    const val BG_BLUE = "\u001B[44m"
    const val BG_MAGENTA = "\u001B[45m"
    const val BG_CYAN = "\u001B[46m"
    const val BG_WHITE = "\u001B[47m"
    
    // Cursor
    const val CLEAR_LINE = "\u001B[2K"
    const val CURSOR_UP = "\u001B[A"
    const val CURSOR_DOWN = "\u001B[B"
    const val CURSOR_FORWARD = "\u001B[C"
    const val CURSOR_BACK = "\u001B[D"
    const val CURSOR_HOME = "\u001B[H"
    const val CLEAR_SCREEN = "\u001B[2J"
    
    fun cursorTo(row: Int, col: Int) = "\u001B[${row};${col}H"
}

// Terminal capability detection
enum class TerminalMode {
    PLAIN,     // No ANSI support
    ANSI,      // Full ANSI support
    LIMITED    // Basic ANSI (no cursor movement)
}

// Rendering context
data class RenderContext(
    val mode: TerminalMode = TerminalMode.PLAIN,
    val width: Int = 80,
    val height: Int = 24,
    val useUnicode: Boolean = true
)

// Menu renderer interface
interface MenuRenderer<T> {
    fun render(
        menu: TrieMenuSelector<T>,
        input: String,
        context: RenderContext
    ): String
}

// Plain text renderer
class PlainMenuRenderer<T> : MenuRenderer<T> {
    override fun render(
        menu: TrieMenuSelector<T>,
        input: String,
        context: RenderContext
    ): String {
        val options = menu.getOptions(input)
        val sb = StringBuilder()
        
        // Header
        if (input.isNotEmpty()) {
            sb.appendLine("Current: $input")
            sb.appendLine("-".repeat(minOf(40, context.width)))
        }
        
        // Group options by next character
        val grouped = options.groupBy { item ->
            item.key.drop(input.length).firstOrNull() ?: ' '
        }
        
        // Render each group
        grouped.forEach { (nextChar, items) ->
            if (items.size == 1 && items[0].key == input + nextChar) {
                // Terminal option
                val item = items[0]
                sb.append("  $nextChar -> ${item.value}")
                if (item.description.isNotEmpty()) {
                    sb.append(" (${item.description})")
                }
                if (item.shortcut != null) {
                    sb.append(" [${item.shortcut}]")
                }
                sb.appendLine()
            } else {
                // Branch with multiple options
                sb.appendLine("  $nextChar -> (${items.size} options)")
            }
        }
        
        // Prompt
        sb.append("> $input")
        
        return sb.toString()
    }
}

// ANSI color renderer
class AnsiMenuRenderer<T> : MenuRenderer<T> {
    override fun render(
        menu: TrieMenuSelector<T>,
        input: String,
        context: RenderContext
    ): String {
        val options = menu.getOptions(input)
        val sb = StringBuilder()
        
        // Clear screen if supported
        if (context.mode == TerminalMode.ANSI) {
            sb.append(ANSI.CLEAR_SCREEN)
            sb.append(ANSI.CURSOR_HOME)
        }
        
        // Title
        sb.append(ANSI.BOLD)
        sb.append(ANSI.CYAN)
        sb.append("┌─ Trie Menu Navigator ─┐")
        sb.append(ANSI.RESET)
        sb.appendLine()
        
        // Current input
        if (input.isNotEmpty()) {
            sb.append(ANSI.YELLOW)
            sb.append("│ Path: ")
            sb.append(ANSI.BOLD)
            sb.append(input)
            sb.append(ANSI.RESET)
            sb.appendLine()
        }
        
        // Separator
        sb.append(ANSI.DIM)
        sb.append("├" + "─".repeat(23) + "┤")
        sb.append(ANSI.RESET)
        sb.appendLine()
        
        // Group options
        val grouped = options.groupBy { item ->
            item.key.drop(input.length).firstOrNull() ?: ' '
        }
        
        // Render options with colors
        grouped.forEach { (nextChar, items) ->
            sb.append("│ ")
            
            if (items.size == 1 && items[0].key == input + nextChar) {
                // Terminal option - green
                val item = items[0]
                sb.append(ANSI.GREEN)
                sb.append(ANSI.BOLD)
                sb.append(nextChar)
                sb.append(ANSI.RESET)
                sb.append(" → ")
                sb.append(ANSI.WHITE)
                sb.append(item.value)
                sb.append(ANSI.RESET)
                
                if (item.description.isNotEmpty()) {
                    sb.append(ANSI.DIM)
                    sb.append(" ${item.description}")
                    sb.append(ANSI.RESET)
                }
                
                if (item.shortcut != null) {
                    sb.append(" ")
                    sb.append(ANSI.MAGENTA)
                    sb.append("[${item.shortcut}]")
                    sb.append(ANSI.RESET)
                }
            } else {
                // Branch - blue
                sb.append(ANSI.BLUE)
                sb.append(ANSI.BOLD)
                sb.append(nextChar)
                sb.append(ANSI.RESET)
                sb.append(" → ")
                sb.append(ANSI.DIM)
                sb.append("(${items.size} options)")
                sb.append(ANSI.RESET)
            }
            
            sb.appendLine()
        }
        
        // Footer
        sb.append(ANSI.DIM)
        sb.append("└" + "─".repeat(23) + "┘")
        sb.append(ANSI.RESET)
        sb.appendLine()
        
        // Prompt with cursor
        sb.append(ANSI.BOLD)
        sb.append(ANSI.YELLOW)
        sb.append("▶ ")
        sb.append(ANSI.RESET)
        sb.append(input)
        sb.append(ANSI.REVERSE)
        sb.append(" ")
        sb.append(ANSI.RESET)
        
        return sb.toString()
    }
}

// Adaptive TUI widget
class TrieMenuTUI<T>(
    private val menu: TrieMenuSelector<T>,
    private val context: RenderContext = detectTerminalCapabilities()
) {
    private val plainRenderer = PlainMenuRenderer<T>()
    private val ansiRenderer = AnsiMenuRenderer<T>()
    private var currentInput = StringBuilder()
    
    private val renderer: MenuRenderer<T> = when (context.mode) {
        TerminalMode.ANSI, TerminalMode.LIMITED -> ansiRenderer
        TerminalMode.PLAIN -> plainRenderer
    }
    
    fun render(): String {
        return renderer.render(menu, currentInput.toString(), context)
    }
    
    fun handleInput(input: String): SelectionResult<T> {
        for (char in input) {
            when (char) {
                '\b', '\u007F' -> { // Backspace or DEL
                    if (currentInput.isNotEmpty()) {
                        currentInput.deleteCharAt(currentInput.length - 1)
                    }
                }
                '\u001B' -> { // ESC - clear
                    currentInput.clear()
                }
                '\n', '\r' -> { // Enter
                    val result = menu.select(currentInput.toString())
                    if (result is SelectionResult.Selected) {
                        currentInput.clear()
                        return result
                    }
                }
                else -> {
                    if (char.isLetterOrDigit() || char == '.') {
                        currentInput.append(char)
                    }
                }
            }
        }
        
        return menu.select(currentInput.toString())
    }
    
    fun reset() {
        currentInput.clear()
    }
    
    fun getCurrentInput(): String = currentInput.toString()
    
    // Box drawing helpers
    fun renderBox(
        title: String,
        content: List<String>,
        width: Int = 40
    ): String {
        val sb = StringBuilder()
        
        if (context.useUnicode && context.mode != TerminalMode.PLAIN) {
            // Unicode box drawing
            sb.appendLine("┌─${title.padEnd(width - 4, '─')}─┐")
            content.forEach { line ->
                sb.append("│ ")
                sb.append(line.padEnd(width - 4))
                sb.appendLine(" │")
            }
            sb.append("└${"─".repeat(width - 2)}┘")
        } else {
            // ASCII box drawing
            sb.appendLine("+-${title.padEnd(width - 4, '-')}-+")
            content.forEach { line ->
                sb.append("| ")
                sb.append(line.padEnd(width - 4))
                sb.appendLine(" |")
            }
            sb.append("+${"-".repeat(width - 2)}+")
        }
        
        return sb.toString()
    }
}

// Terminal capability detection
fun detectTerminalCapabilities(): RenderContext {
    val term = System.getenv("TERM") ?: ""
    val colorterm = System.getenv("COLORTERM") ?: ""
    
    val mode = when {
        term.contains("dumb") -> TerminalMode.PLAIN
        colorterm.contains("truecolor") || term.contains("256color") -> TerminalMode.ANSI
        term.contains("xterm") || term.contains("screen") -> TerminalMode.ANSI
        term.contains("color") -> TerminalMode.LIMITED
        else -> TerminalMode.PLAIN
    }
    
    val width = System.getenv("COLUMNS")?.toIntOrNull() ?: 80
    val height = System.getenv("LINES")?.toIntOrNull() ?: 24
    
    // Check for UTF-8 support
    val lang = System.getenv("LANG") ?: ""
    val useUnicode = lang.contains("UTF-8", ignoreCase = true) ||
                     lang.contains("UTF8", ignoreCase = true)
    
    return RenderContext(
        mode = mode,
        width = width,
        height = height,
        useUnicode = useUnicode
    )
}

// Interactive session
fun <T> runInteractiveMenu(
    menu: TrieMenuSelector<T>,
    onSelect: (MenuItem<T>) -> Unit
) {
    val tui = TrieMenuTUI(menu)
    
    while (true) {
        println(tui.render())
        
        val input = readlnOrNull() ?: break
        
        when (val result = tui.handleInput(input)) {
            is SelectionResult.Selected -> {
                onSelect(result.item)
                if (input == "quit" || input == "exit") break
            }
            is SelectionResult.Partial -> {
                // Continue showing partial matches
            }
            is SelectionResult.NoMatch -> {
                println("No match found")
                Thread.sleep(1000)
                tui.reset()
            }
        }
    }
}