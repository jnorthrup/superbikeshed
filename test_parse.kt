// Simple test script to verify parse functionality
import borg.trikeshed.lib.*
import borg.trikeshed.parse.*
import borg.trikeshed.parse.json.*

fun main() {
    println("Testing TrikeShed Parse Package...")

    // Test JSON scanning
    try {
        val jsonText = """{"name":"TrikeShed","version":1.0,"active":true}"""
        val scanResult = jsonText.scanJson()
        println("✓ JSON scanning works: ${scanResult.isSuccess}")

        if (scanResult.isSuccess) {
            val tokens = scanResult.getOrThrow()
            println("  Found ${tokens.size} tokens")
        }
    } catch (e: Exception) {
        println("✗ JSON scanning failed: ${e.message}")
    }

    // Test JSON operations
    try {
        val structural = Json.index("""{"test": "value"}""")
        println("✓ JSON indexing works")
        println("  Bounds: ${structural.component1().component1()} to ${structural.component1().component2()}")
        println("  Comma indices: ${structural.component2().size}")
    } catch (e: Exception) {
        println("✗ JSON indexing failed: ${e.message}")
    }

    // Test bash brace scanning
    try {
        val tokens = BashBrace.of("file{1,2,3}.txt").scanTokens()
        println("✓ Bash brace scanning works: ${tokens.size} tokens")
    } catch (e: Exception) {
        println("✗ Bash brace scanning failed: ${e.message}")
    }

    // Test markdown parsing
    try {
        val markdown = "```kotlin\nfun test() = \"hello\"\n```"
        val bitmap = LightningMarkdown.parseToBitmap(markdown)
        println("✓ Markdown parsing works: ${bitmap.size} bitmap elements")
    } catch (e: Exception) {
        println("✗ Markdown parsing failed: ${e.message}")
    }

    println("Parse package testing complete!")
}
