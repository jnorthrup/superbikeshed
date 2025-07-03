package borg.trikeshed.parse.markdown

import borg.trikeshed.lib.*
import kotlin.test.*

class MarkdownCodeBlockTest {
    
    @Test
    fun testExtractKotlinCodeBlocks() {
        val markdown = """
            # Test Document
            
            Here's some Kotlin code:
            
            ```kotlin
            fun hello() {
                println("Hello, World!")
            }
            ```
            
            And some Java code:
            
            ```java
            public class Test {
                public static void main(String[] args) {
                    System.out.println("Hello, Java!");
                }
            }
            ```
            
            And more Kotlin:
            
            ```kotlin
            val numbers = listOf(1, 2, 3, 4, 5)
            val sum = numbers.sum()
            println("Sum: $sum")
            ```
        """.trimIndent()
        
        val kotlinBlocks = LightningMarkdown.extractKotlinCodeBlocks(markdown)
        
        assertEquals(2, kotlinBlocks.size)
        
        val firstBlock = kotlinBlocks[0]
        assertEquals("kotlin", firstBlock.language)
        assertTrue(firstBlock.content.contains("fun hello()"))
        
        val secondBlock = kotlinBlocks[1]
        assertEquals("kotlin", secondBlock.language)
        assertTrue(secondBlock.content.contains("val numbers"))
    }
    
    @Test
    fun testExtractAllCodeBlocks() {
        val markdown = """
            ```kotlin
            fun test() = "hello"
            ```
            
            ```java
            public class Test {}
            ```
            
            ```bash
            echo "hello"
            ```
        """.trimIndent()
        
        val allBlocks = LightningMarkdown.extractCodeBlocks(markdown)
        
        assertEquals(3, allBlocks.size)
        assertEquals("kotlin", allBlocks[0].language)
        assertEquals("java", allBlocks[1].language)
        assertEquals("bash", allBlocks[2].language)
    }
    
    @Test
    fun testExtractCodeBlockContent() {
        val markdown = """
            ```kotlin
            fun main() {
                println("Hello from Kotlin!")
            }
            ```
        """.trimIndent()
        
        val content = LightningMarkdown.extractKotlinCodeBlockContent(markdown)
        
        assertEquals(1, content.size)
        assertTrue(content[0].contains("fun main()"))
        assertTrue(content[0].contains("Hello from Kotlin!"))
    }
} 