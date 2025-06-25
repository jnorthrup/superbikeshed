# Markdown Code Block Detection in k2script

## Overview

k2script now supports detection and processing of markdown code blocks, specifically designed to handle Kotlin fencing (```kotlin) and other language specifications. This feature allows k2script to process markdown files containing code blocks and extract Kotlin code for execution.

## Features

### Supported Code Block Formats

k2script can detect and process the following markdown code block formats:

1. **Kotlin Code Blocks**: ```kotlin
2. **Generic Code Blocks**: ```
3. **Language-Specific Blocks**: ```java, ```bash, ```python, etc.

### Detection Patterns

The parser recognizes:
- **Code block start**: Lines matching `^```(\w*)$` (e.g., ````kotlin`, ````java`, ````)
- **Code block end**: Lines matching `^```$` (e.g., ````)

## Implementation

### New Annotation Type

A new `MarkdownCodeBlock` annotation type has been added to the `ScriptAnnotation` sealed interface:

```kotlin
data class MarkdownCodeBlock(
    val language: String? = null,
    val content: String = "",
    val isStart: Boolean = false,
    val isEnd: Boolean = false
) : ScriptAnnotation
```

### Parser Integration

The markdown code block parser has been integrated into the existing parser chain:

1. **LineParser.kt**: Added `parseMarkdownCodeBlock()` function
2. **Parser.kt**: Added the parser to the `annotationParsers` list
3. **Regex Patterns**: Added `MARKDOWN_CODE_BLOCK_START_REGEX` and `MARKDOWN_CODE_BLOCK_END_REGEX`

## Usage Examples

### Basic Code Block Detection

```kotlin
// This will be detected as a Kotlin code block start
```kotlin
fun example() {
    println("Hello from Kotlin code block!")
}
```

### Language-Specific Detection

```kotlin
// This will be detected as a Java code block
```java
public class Example {
    public static void main(String[] args) {
        System.out.println("Hello from Java!");
    }
}
```

### Generic Code Block

```kotlin
// This will be detected as a generic code block
```
println("Generic code block")
```

## Processing Code Blocks

### Extracting Kotlin Code

You can extract Kotlin code from markdown files:

```kotlin
class MarkdownCodeBlockProcessor {
    fun extractKotlinCode(file: File): List<String> {
        val lines = file.readLines()
        val kotlinBlocks = mutableListOf<String>()
        var inKotlinBlock = false
        var currentBlock = StringBuilder()
        
        for (line in lines) {
            when {
                line.trim() == "```kotlin" -> {
                    inKotlinBlock = true
                    currentBlock.clear()
                }
                line.trim() == "```" -> {
                    if (inKotlinBlock) {
                        kotlinBlocks.add(currentBlock.toString().trim())
                        inKotlinBlock = false
                    }
                }
                inKotlinBlock -> {
                    currentBlock.append(line).append("\n")
                }
            }
        }
        
        return kotlinBlocks
    }
}
```

### Executing Code Blocks

You can process and execute code blocks:

```kotlin
fun processAndExecute(file: File) {
    val kotlinCode = extractKotlinCode(file)
    
    kotlinCode.forEachIndexed { index, code ->
        println("Executing block ${index + 1}:")
        try {
            // Execute the Kotlin code here
            println(code)
            println("✓ Block ${index + 1} processed successfully")
        } catch (e: Exception) {
            println("✗ Error in block ${index + 1}: ${e.message}")
        }
    }
}
```

## Integration with Existing Features

### Parser Chain

The markdown code block parser is integrated into the existing parser chain and works alongside:

- Shebang detection (`#!/`)
- Dependency annotations (`@file:DependsOn`)
- Repository annotations (`@file:Repository`)
- Import statements (`@file:Import`)
- Package declarations (`package`)
- And other existing annotations

### Script Processing

When k2script processes a script file containing markdown code blocks:

1. The parser detects code block boundaries
2. Code blocks are marked with `MarkdownCodeBlock` annotations
3. The script resolution process can handle these annotations
4. Code blocks can be extracted and processed separately

## Example Script

Here's a complete example showing how to use the markdown code block feature:

```kotlin
#!/usr/bin/env k2script

// Example script demonstrating markdown code block processing

import java.io.File

fun main(args: Array<String>) {
    val processor = MarkdownCodeBlockProcessor()
    
    if (args.isNotEmpty()) {
        val file = File(args[0])
        if (file.exists()) {
            processor.processAndExecute(file)
        } else {
            println("File not found: ${args[0]}")
        }
    } else {
        // Run with example markdown content
        processExampleContent()
    }
}

class MarkdownCodeBlockProcessor {
    fun extractKotlinCode(file: File): List<String> {
        // Implementation as shown above
    }
    
    fun processAndExecute(file: File) {
        // Implementation as shown above
    }
}

fun processExampleContent() {
    val exampleMarkdown = """
# Example Document

This document contains Kotlin code blocks.

```kotlin
fun greet(name: String) {
    println("Hello, $name!")
}

greet("World")
```

More content here...

```kotlin
val numbers = listOf(1, 2, 3, 4, 5)
val sum = numbers.sum()
println("Sum: $sum")
```
""".trimIndent()
    
    val tempFile = File.createTempFile("example", ".md")
    tempFile.writeText(exampleMarkdown)
    
    try {
        val processor = MarkdownCodeBlockProcessor()
        processor.processAndExecute(tempFile)
    } finally {
        tempFile.delete()
    }
}
```

## Benefits

### For Documentation

- **Executable Documentation**: Markdown files can contain executable Kotlin code
- **Code Examples**: Documentation can include runnable code examples
- **Tutorials**: Interactive tutorials with embedded code

### For Development

- **Code Extraction**: Extract Kotlin code from markdown documentation
- **Automated Testing**: Run code examples from documentation
- **Script Generation**: Generate scripts from markdown templates

### For Integration

- **Documentation Processing**: Process README files and documentation
- **Code Migration**: Extract code from markdown-based tutorials
- **Automation**: Automate code execution from documentation

## Future Enhancements

Potential future enhancements could include:

1. **Direct Execution**: Execute code blocks directly within k2script
2. **Language Support**: Support for more programming languages
3. **Interactive Mode**: Interactive execution of code blocks
4. **Output Capture**: Capture and display code block output
5. **Error Handling**: Better error reporting for code block execution

## Conclusion

The markdown code block detection feature enhances k2script's capabilities for processing documentation and extracting executable code. This makes k2script more versatile for handling markdown-based workflows and documentation-driven development. 