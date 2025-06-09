#!/usr/bin/env kotlin

// K2script Standalone Jupyter-style Notebook
// No daemon, no server - just a self-contained interactive notebook

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

println("=== K2script Standalone Jupyter Notebook ===")
println()

// Generate a unique notebook session ID
val sessionId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
val notebookDir = File(System.getProperty("java.io.tmpdir"), "k2script-notebook-$sessionId")
notebookDir.mkdirs()

println("📓 Creating standalone notebook session: $sessionId")
println("📁 Notebook directory: ${notebookDir.absolutePath}")

// Cell execution tracker
data class NotebookCell(
    val id: String,
    val type: String, // "code" or "markdown"
    val source: String,
    var output: String = "",
    var executionCount: Int = 0,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

class StandaloneNotebook {
    private val cells = mutableListOf<NotebookCell>()
    private var executionCounter = 0
    
    fun addMarkdownCell(content: String): NotebookCell {
        val cell = NotebookCell(
            id = "cell-${cells.size + 1}",
            type = "markdown",
            source = content
        )
        cells.add(cell)
        return cell
    }
    
    fun addCodeCell(code: String): NotebookCell {
        val cell = NotebookCell(
            id = "cell-${cells.size + 1}",
            type = "code", 
            source = code
        )
        cells.add(cell)
        return cell
    }
    
    fun executeCodeCell(cell: NotebookCell): String {
        if (cell.type != "code") return "Not a code cell"
        
        executionCounter++
        cell.executionCount = executionCounter
        
        println("🔄 Executing cell ${cell.id} [${cell.executionCount}]")
        
        return try {
            // Create a temporary Kotlin script file
            val tempScript = File(notebookDir, "cell_${cell.id}.kts")
            tempScript.writeText(cell.source)
            
            // Execute the script and capture output
            val process = ProcessBuilder("kotlin", tempScript.absolutePath)
                .directory(notebookDir)
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                cell.output = output.trim()
                println("✅ Success!")
            } else {
                cell.output = "Error: $output"
                println("❌ Error!")
            }
            
            tempScript.delete() // Clean up
            cell.output
            
        } catch (e: Exception) {
            val errorMsg = "Execution failed: ${e.message}"
            cell.output = errorMsg
            println("❌ Exception: ${e.message}")
            errorMsg
        }
    }
    
    fun exportToHTML(): String {
        val html = StringBuilder()
        
        html.append("""
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>K2script Standalone Notebook - $sessionId</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            line-height: 1.6;
            max-width: 1000px;
            margin: 0 auto;
            padding: 20px;
            background: #fafafa;
        }
        .notebook-header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 20px;
            border-radius: 8px;
            margin-bottom: 20px;
        }
        .cell {
            background: white;
            border: 1px solid #ddd;
            border-radius: 6px;
            margin-bottom: 15px;
            overflow: hidden;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        .cell-header {
            background: #f8f9fa;
            padding: 8px 15px;
            font-size: 12px;
            color: #666;
            border-bottom: 1px solid #eee;
        }
        .cell-content {
            padding: 15px;
        }
        .code-cell .cell-content {
            font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
            background: #f8f8f8;
            border-left: 4px solid #007acc;
        }
        .markdown-cell .cell-content {
            border-left: 4px solid #28a745;
        }
        .cell-output {
            background: #f1f3f4;
            padding: 10px 15px;
            border-top: 1px solid #eee;
            font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
            font-size: 13px;
            white-space: pre-wrap;
        }
        .execution-count {
            color: #007acc;
            font-weight: bold;
        }
        .timestamp {
            color: #888;
            font-size: 11px;
        }
        pre {
            margin: 0;
            white-space: pre-wrap;
            word-wrap: break-word;
        }
        h1, h2, h3 { color: #333; }
        .stats {
            background: #e3f2fd;
            padding: 10px;
            border-radius: 4px;
            margin: 10px 0;
        }
    </style>
</head>
<body>
    <div class="notebook-header">
        <h1>📓 K2script Standalone Notebook</h1>
        <p>Session: $sessionId | Generated: ${LocalDateTime.now()}</p>
        <div class="stats">
            <strong>Cells:</strong> ${cells.size} | 
            <strong>Code Cells:</strong> ${cells.count { it.type == "code" }} |
            <strong>Executed:</strong> ${cells.count { it.executionCount > 0 }}
        </div>
    </div>
""".trimIndent())
        
        cells.forEach { cell ->
            html.append("""
    <div class="cell ${cell.type}-cell">
        <div class="cell-header">
            <span class="execution-count">[${if (cell.executionCount > 0) cell.executionCount else " "}]</span>
            ${cell.type.uppercase()} Cell: ${cell.id}
            <span class="timestamp">${cell.timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"))}</span>
        </div>
        <div class="cell-content">
            <pre>${cell.source}</pre>
        </div>
""".trimIndent())
            
            if (cell.type == "code" && cell.output.isNotEmpty()) {
                html.append("""
        <div class="cell-output">${cell.output}</div>
""".trimIndent())
            }
            
            html.append("    </div>\n")
        }
        
        html.append("""
</body>
</html>
""".trimIndent())
        
        return html.toString()
    }
    
    fun getCells() = cells.toList()
}

// Create the standalone notebook
val notebook = StandaloneNotebook()

// Add notebook cells demonstrating various features
println()
println("📝 Building notebook content...")

// Title cell
notebook.addMarkdownCell("""
# K2script Standalone Notebook Demo

This is a self-contained Jupyter-style notebook generated and executed by k2script.
No servers, no daemons - just pure Kotlin scripting power!

## Features:
- ✅ Code execution with output capture
- ✅ Markdown cells for documentation  
- ✅ Session management
- ✅ HTML export
- ✅ No external dependencies
""")

// Basic computation
val cell1 = notebook.addCodeCell("""
val x = 42
val y = 58
val result = x + y
println("Basic computation: " + x + " + " + y + " = " + result)
println("Result: " + result)
""")

// Data processing
val cell2 = notebook.addCodeCell("""
val numbers = (1..10).toList()
val squares = numbers.map { it * it }
val sum = squares.sum()
println("Numbers: " + numbers)
println("Squares: " + squares) 
println("Sum of squares: " + sum)
""")

// System information
val cell3 = notebook.addCodeCell("""
val runtime = Runtime.getRuntime()
val javaVersion = System.getProperty("java.version")
val osName = System.getProperty("os.name")
val processors = runtime.availableProcessors()
val maxMemory = runtime.maxMemory() / 1024 / 1024

println("System Info:")
println("Java: " + javaVersion)
println("OS: " + osName)
println("Processors: " + processors)
println("Max Memory: " + maxMemory + "MB")
""")

// Mathematical computation
val cell4 = notebook.addCodeCell("""
import kotlin.math.*

fun fibonacci(n: Int): Long = when {
    n <= 1 -> n.toLong()
    else -> {
        var a = 0L
        var b = 1L
        repeat(n - 1) {
            val temp = a + b
            a = b
            b = temp
        }
        b
    }
}

val fibNumbers = (1..15).map { fibonacci(it) }
println("Fibonacci sequence (1-15):")
println(fibNumbers.joinToString(", "))
""")

// Execute all code cells
println()
println("🚀 Executing notebook cells...")
notebook.getCells().filter { it.type == "code" }.forEach { cell ->
    notebook.executeCodeCell(cell)
    println()
}

// Export to HTML
println("📄 Exporting notebook to HTML...")
val htmlContent = notebook.exportToHTML()
val htmlFile = File(notebookDir, "notebook.html")
htmlFile.writeText(htmlContent)

// Also create a summary file
val summaryFile = File(notebookDir, "session-summary.txt")
summaryFile.writeText("""
K2script Standalone Notebook Session Summary
==========================================

Session ID: $sessionId
Generated: ${LocalDateTime.now()}
Directory: ${notebookDir.absolutePath}

Cells Executed: ${notebook.getCells().count { it.executionCount > 0 }}
Total Cells: ${notebook.getCells().size}

Files Generated:
- notebook.html (Interactive notebook view)
- session-summary.txt (This file)
- *.kts (Temporary execution files, cleaned up)

To view the notebook:
open ${htmlFile.absolutePath}
""")

println("✅ Notebook generated successfully!")
println()
println("📓 Notebook files:")
println("   HTML: ${htmlFile.absolutePath}")
println("   Summary: ${summaryFile.absolutePath}")
println()
println("🌐 Opening notebook in browser...")

// Open the notebook
val openCommand = when {
    System.getProperty("os.name").contains("Mac") -> "open"
    System.getProperty("os.name").contains("Windows") -> "start"
    else -> "xdg-open"
}

try {
    ProcessBuilder(openCommand, htmlFile.absolutePath).start()
    println("✅ Notebook opened in browser!")
} catch (e: Exception) {
    println("❌ Could not auto-open browser. Please open manually:")
    println("   ${htmlFile.absolutePath}")
}

println()
println("=== Standalone Jupyter Notebook Complete! ===")
println("📊 Session Stats:")
println("   Total cells: ${notebook.getCells().size}")
println("   Code cells executed: ${notebook.getCells().count { it.type == "code" && it.executionCount > 0 }}")
println("   Session directory: ${notebookDir.absolutePath}")
println()
println("🎯 K2script has created a fully functional notebook without any daemon!")