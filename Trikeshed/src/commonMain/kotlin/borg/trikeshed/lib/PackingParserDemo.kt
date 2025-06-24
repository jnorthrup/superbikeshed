package borg.trikeshed.lib

import borg.trikeshed.parse.BashScanner
import borg.trikeshed.parse.Token
import borg.trikeshed.parse.TokenType

/**
 * Parser Demo with Register Packing and Cutting Lines Visualization
 */

data class PackingStep(
    val original: Join<Token, Token>,
    val packed: Join<*, *>, 
    val strategy: String,
    val spaceSaved: Int,
    val cuttingLine: String
)

data class CuttingLineViz(
    val line: String,
    val efficiency: Double,
    val strategy: String
)

object PackingParserDemo {
    
    fun demonstratePacking(input: String): String {
        val scanner = BashScanner(input)
        val tokens = mutableListOf<Token>()
        
        // Tokenize input
        while (true) {
            val token = scanner.scan()
            tokens.add(token)
            if (token.type == TokenType.EOF) break
        }
        
        if (tokens.size < 2) {
            return "Need at least 2 tokens for packing demo"
        }
        
        val result = StringBuilder()
        result.appendLine("=== TrikeShed Register Packing Parser Demo ===")
        result.appendLine("Input: \"$input\"")
        result.appendLine()
        
        // Show tokenization
        result.appendLine("Tokens:")
        tokens.forEachIndexed { i, token ->
            if (token.type != TokenType.EOF) {
                result.appendLine("  [$i] ${token.type}: '${token.literal}'")
            }
        }
        result.appendLine()
        
        // Demonstrate packing on token pairs
        val packingSteps = mutableListOf<PackingStep>()
        var totalSpaceSaved = 0
        
        for (i in 0 until tokens.size - 2) { // Skip EOF
            val tokenA = tokens[i]
            val tokenB = tokens[i + 1]
            
            // Create Join
            val original = tokenA j tokenB
            
            // Apply packing
            val packed = tokenA jj tokenB
            
            // Analyze packing result
            val step = analyzePackingStep(original, packed, i)
            packingSteps.add(step)
            totalSpaceSaved += step.spaceSaved
        }
        
        // Display packing results with cutting lines
        result.appendLine("Packing Analysis:")
        result.appendLine(generateCuttingLineHeader())
        
        packingSteps.forEachIndexed { i, step ->
            result.appendLine(formatPackingStep(i, step))
            result.appendLine(step.cuttingLine)
        }
        
        result.appendLine(generateCuttingLineFooter())
        result.appendLine()
        
        // Summary
        result.appendLine("Summary:")
        result.appendLine("  Token pairs processed: ${packingSteps.size}")
        result.appendLine("  Total space saved: ${totalSpaceSaved} bytes")
        result.appendLine("  Average efficiency: ${if (packingSteps.isNotEmpty()) totalSpaceSaved / packingSteps.size else 0}%")
        
        return result.toString()
    }
    
    private fun analyzePackingStep(original: Join<Token, Token>, packed: Join<*, *>, index: Int): PackingStep {
        // Estimate space savings (simplified)
        val originalSize = estimateTokenSize(original.a) + estimateTokenSize(original.b)
        val packedSize = when (packed) {
            is DiagonalPacked -> 8  // Single Long
            is PrefixedPacked -> 9  // Long + Byte  
            else -> originalSize // No packing applied
        }
        
        val spaceSaved = maxOf(0, originalSize - packedSize)
        val strategy = when (packed) {
            is DiagonalPacked -> "DIAGONAL"
            is PrefixedPacked -> "PREFIXED" 
            else -> "NONE"
        }
        
        val efficiency = if (originalSize > 0) (spaceSaved * 100) / originalSize else 0
        val cuttingLine = generateCuttingLine(strategy, efficiency)
        
        return PackingStep(original, packed, strategy, spaceSaved, cuttingLine)
    }
    
    private fun estimateTokenSize(token: Token): Int {
        return when (token.type) {
            TokenType.LITERAL -> token.literal.length + 4  // String overhead
            TokenType.LBRACE, TokenType.RBRACE -> 1
            TokenType.COMMA, TokenType.DOT -> 1
            TokenType.SEQUENCE -> token.literal.length + 4
            TokenType.ERROR -> 8
            TokenType.EOF -> 0
        }
    }
    
    private fun generateCuttingLineHeader(): String {
        return "┌─────────────────────────────────────────────────────────────────────┐"
    }
    
    private fun generateCuttingLineFooter(): String {
        return "└─────────────────────────────────────────────────────────────────────┘"
    }
    
    private fun generateCuttingLine(strategy: String, efficiency: Int): String {
        val width = 65
        val filled = (width * efficiency) / 100
        val char = when (strategy) {
            "DIAGONAL" -> "═"
            "PREFIXED" -> "━" 
            "NONE" -> "░"
            else -> "─"
        }
        
        val line = char.repeat(filled) + "░".repeat(width - filled)
        return "│ $line │ $strategy ${efficiency}%"
    }
    
    private fun formatPackingStep(index: Int, step: PackingStep): String {
        val tokenA = step.original.a
        val tokenB = step.original.b
        return "│ Pair $index: ${tokenA.type}('${tokenA.literal}') + ${tokenB.type}('${tokenB.literal}') → ${step.strategy}"
    }
}

/**
 * Register fastlane for primitive token packing
 */
object RegisterFastlane {
    fun tryTokenPack(a: Token, b: Token): PackedResult<*, *>? {
        return when {
            // Pack two small literals
            a.type == TokenType.LITERAL && b.type == TokenType.LITERAL && 
            a.literal.length <= 4 && b.literal.length <= 4 -> {
                val packed = packTwoStrings(a.literal, b.literal)
                DiagonalPacked(packed)
            }
            
            // Pack brace pairs
            a.type == TokenType.LBRACE && b.type == TokenType.RBRACE -> {
                DiagonalPacked(0x7B7DL) // ASCII { }
            }
            
            // Pack sequence with number
            a.type == TokenType.SEQUENCE && b.type == TokenType.LITERAL && b.literal.toIntOrNull() != null -> {
                val seqHash = a.literal.hashCode().toLong()
                val num = b.literal.toInt().toLong()
                DiagonalPacked((seqHash shl 32) or num)
            }
            
            else -> null
        }
    }
    
    private fun packTwoStrings(a: String, b: String): Long {
        val aBytes = a.encodeToByteArray().take(4)
        val bBytes = b.encodeToByteArray().take(4)
        
        var result = 0L
        aBytes.forEachIndexed { i, byte -> 
            result = result or ((byte.toLong() and 0xFF) shl (56 - i * 8))
        }
        bBytes.forEachIndexed { i, byte ->
            result = result or ((byte.toLong() and 0xFF) shl (24 - i * 8)) 
        }
        
        return result
    }
}