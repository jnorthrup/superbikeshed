#!/usr/bin/env k2script

// Example demonstrating markdown code block processing in k2script

import java.io.File

/**
 * This script demonstrates how k2script can process markdown files
 * containing code blocks and extract Kotlin code for execution.
 */

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: k2script markdown_code_block_example.kts <markdown_file>")
        return
    }
    
    val markdownFile = File(args[0])
    if (!markdownFile.exists()) {
        println("Error: File ${args[0]} does not exist")
        return
    }
    
    val processor = MarkdownCodeBlockProcessor()
    val kotlinCode = processor.extractKotlinCode(markdownFile)
    
    println("Extracted Kotlin code blocks:")
    println("=" * 50)
    kotlinCode.forEachIndexed { index, code ->
        println("Block ${index + 1}:")
        println(code)
        println("-" * 30)
    }
}

class MarkdownCodeBlockProcessor {
    
    /**
     * Extracts Kotlin code blocks from a markdown file
     */
    fun extractKotlinCode(file: File): List<String> {
        val lines = file.readLines()
        val kotlinBlocks = mutableListOf<String>()
        var inKotlinBlock = false
        var currentBlock = StringBuilder()
        
        for (line in lines) {
            when {
                // Start of Kotlin code block
                line.trim() == "```kotlin" -> {
                    inKotlinBlock = true
                    currentBlock.clear()
                }
                
                // End of any code block
                line.trim() == "```" -> {
                    if (inKotlinBlock) {
                        kotlinBlocks.add(currentBlock.toString().trim())
                        inKotlinBlock = false
                    }
                }
                
                // Inside Kotlin code block
                inKotlinBlock -> {
                    currentBlock.append(line).append("\n")
                }
            }
        }
        
        return kotlinBlocks
    }
    
    /**
     * Processes a markdown file and executes Kotlin code blocks
     */
    fun processAndExecute(file: File) {
        val kotlinCode = extractKotlinCode(file)
        
        println("Found ${kotlinCode.size} Kotlin code blocks")
        
        kotlinCode.forEachIndexed { index, code ->
            println("\nExecuting block ${index + 1}:")
            println("-" * 30)
            
            try {
                // Here you would execute the Kotlin code
                // For now, we'll just print it
                println(code)
                println("✓ Block ${index + 1} processed successfully")
            } catch (e: Exception) {
                println("✗ Error in block ${index + 1}: ${e.message}")
            }
        }
    }
}

// Example usage with inline markdown processing
fun processInlineMarkdown() {
    val markdownContent = """
# Example Markdown with Kotlin Code

This is a markdown file containing Kotlin code blocks.

## Kotlin Function Example

```kotlin
fun calculateSum(numbers: List<Int>): Int {
    return numbers.sum()
}

val result = calculateSum(listOf(1, 2, 3, 4, 5))
println("Sum: $result")
```

## Another Kotlin Block

```kotlin
data class Person(val name: String, val age: Int)

val people = listOf(
    Person("Alice", 25),
    Person("Bob", 30)
)

people.forEach { person ->
    println("${person.name} is ${person.age} years old")
}
```

## Non-Kotlin Block (should be ignored)

```bash
echo "This is bash code"
```

## Generic Code Block (should be ignored)

```
println("This is generic code")
```
""".trimIndent()
    
    // Process the markdown content
    val lines = markdownContent.lines()
    val processor = MarkdownCodeBlockProcessor()
    
    // Create a temporary file for processing
    val tempFile = File.createTempFile("markdown_test", ".md")
    tempFile.writeText(markdownContent)
    
    try {
        processor.processAndExecute(tempFile)
    } finally {
        tempFile.delete()
    }
}

// Run the inline example if no arguments provided
if (args.isEmpty()) {
    println("Running inline markdown example...")
    processInlineMarkdown()
} 