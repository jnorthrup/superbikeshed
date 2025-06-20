@file:Suppress("NOTHING_TO_INLINE")

package borg.entityscanner

import borg.trikeshed.lib.*

/**
 * Token Classification Stairway - Hierarchical Inline Class System
 * 
 * Each level adds semantic richness while maintaining zero-cost abstractions
 * Uses TrikeShed patterns: Series<T>, Join<A,B>, α transforms, taxonomical type aliases
 */

// ==== LEVEL 1: RAW CHARACTER CLASSIFICATION ====

@JvmInline
value class RawChar(val value: Char)

@JvmInline 
value class CharClass(val type: UByte) {
    companion object {
        const val LETTER: UByte = 1u
        const val DIGIT: UByte = 2u
        const val SYMBOL: UByte = 3u
        const val WHITESPACE: UByte = 4u
        const val NEWLINE: UByte = 5u
        const val UNDERSCORE: UByte = 6u
        const val DOT: UByte = 7u
        const val COLON: UByte = 8u
        const val SEMICOLON: UByte = 9u
        const val COMMA: UByte = 10u
        const val PAREN_OPEN: UByte = 11u
        const val PAREN_CLOSE: UByte = 12u
        const val BRACE_OPEN: UByte = 13u
        const val BRACE_CLOSE: UByte = 14u
        const val BRACKET_OPEN: UByte = 15u
        const val BRACKET_CLOSE: UByte = 16u
        const val QUOTE_DOUBLE: UByte = 17u
        const val QUOTE_SINGLE: UByte = 18u
        const val AT_SYMBOL: UByte = 19u
        const val HASH: UByte = 20u
    }
}

@JvmInline
value class CharPosition(val index: Int)

// Level 1 Compositions
typealias ClassifiedChar = Join<RawChar, CharClass>
typealias PositionedChar = Join<ClassifiedChar, CharPosition>
typealias CharSeries = Series<PositionedChar>

// ==== LEVEL 2: LEXICAL TOKEN CLASSIFICATION ====

@JvmInline
value class LexicalToken(val value: String)

@JvmInline
value class TokenType(val category: UByte) {
    companion object {
        const val KEYWORD: UByte = 1u
        const val IDENTIFIER: UByte = 2u
        const val OPERATOR: UByte = 3u
        const val LITERAL_STRING: UByte = 4u
        const val LITERAL_NUMBER: UByte = 5u
        const val LITERAL_BOOLEAN: UByte = 6u
        const val PUNCTUATION: UByte = 7u
        const val COMMENT: UByte = 8u
        const val ANNOTATION: UByte = 9u
        const val PACKAGE_NAME: UByte = 10u
        const val IMPORT_PATH: UByte = 11u
        const val TYPE_NAME: UByte = 12u
        const val DEPENDENCY_COORD: UByte = 13u
        const val WHITESPACE: UByte = 14u
        const val NEWLINE: UByte = 15u
        const val UNKNOWN: UByte = 16u
    }
}

@JvmInline
value class TokenBounds(val packed: Long) {
    val start: Int get() = (packed shr 32).toInt()
    val length: Int get() = (packed and 0xFFFFFFFF).toInt()
    val end: Int get() = start + length
    
    companion object {
        fun pack(start: Int, length: Int): TokenBounds = 
            TokenBounds(((start.toLong()) shl 32) or (length.toLong() and 0xFFFFFFFF))
    }
}

// Level 2 Compositions
typealias ClassifiedToken = Join<LexicalToken, TokenType>
typealias BoundedToken = Join<ClassifiedToken, TokenBounds>
typealias TokenSeries = Series<BoundedToken>

// ==== LEVEL 3: SYNTACTIC CLASSIFICATION ====

@JvmInline
value class SyntaxToken(val semantic: UByte) {
    companion object {
        const val CLASS_NAME: UByte = 1u
        const val FUNCTION_NAME: UByte = 2u
        const val VARIABLE_NAME: UByte = 3u
        const val PARAMETER_NAME: UByte = 4u
        const val PROPERTY_NAME: UByte = 5u
        const val INTERFACE_NAME: UByte = 6u
        const val ENUM_NAME: UByte = 7u
        const val ANNOTATION_NAME: UByte = 8u
        const val PACKAGE_DECLARATION: UByte = 9u
        const val IMPORT_DECLARATION: UByte = 10u
        const val TYPE_PARAMETER: UByte = 11u
        const val CONSTRUCTOR_CALL: UByte = 12u
        const val FUNCTION_CALL: UByte = 13u
        const val PROPERTY_ACCESS: UByte = 14u
        const val OPERATOR_USAGE: UByte = 15u
        const val KEYWORD_USAGE: UByte = 16u
    }
}

@JvmInline
value class ScopeLevel(val depth: UByte)

@JvmInline
value class VisibilityToken(val access: UByte) {
    companion object {
        const val PUBLIC: UByte = 1u
        const val PRIVATE: UByte = 2u
        const val INTERNAL: UByte = 3u
        const val PROTECTED: UByte = 4u
        const val OPEN: UByte = 5u
        const val FINAL: UByte = 6u
        const val ABSTRACT: UByte = 7u
        const val SEALED: UByte = 8u
        const val INLINE: UByte = 9u
        const val SUSPEND: UByte = 10u
        const val NONE: UByte = 0u
    }
}

// Level 3 Compositions  
typealias ClassifiedSyntax = Join<SyntaxToken, ScopeLevel>
typealias VisibleSyntax = Join<ClassifiedSyntax, VisibilityToken>
typealias SyntaxSeries = Series<VisibleSyntax>

// ==== LEVEL 4: SEMANTIC ENTITY CLASSIFICATION ====

@JvmInline
value class EntityToken(val entityType: UByte) {
    companion object {
        const val DATA_CLASS: UByte = 1u
        const val REGULAR_CLASS: UByte = 2u
        const val ABSTRACT_CLASS: UByte = 3u
        const val SEALED_CLASS: UByte = 4u
        const val INTERFACE: UByte = 5u
        const val ENUM_CLASS: UByte = 6u
        const val OBJECT: UByte = 7u
        const val COMPANION_OBJECT: UByte = 8u
        const val SUSPEND_FUNCTION: UByte = 9u
        const val INLINE_FUNCTION: UByte = 10u
        const val EXTENSION_FUNCTION: UByte = 11u
        const val CONSTRUCTOR: UByte = 12u
        const val PROPERTY: UByte = 13u
        const val DELEGATE_PROPERTY: UByte = 14u
        const val LAZY_PROPERTY: UByte = 15u
        const val TYPEALIAS: UByte = 16u
        const val ANNOTATION_CLASS: UByte = 17u
        const val VALUE_CLASS: UByte = 18u
    }
}

@JvmInline
value class RoleToken(val role: UByte) {
    companion object {
        const val DECLARATION: UByte = 1u
        const val REFERENCE: UByte = 2u
        const val PARAMETER: UByte = 3u
        const val RETURN_TYPE: UByte = 4u
        const val INHERITANCE: UByte = 5u
        const val IMPLEMENTATION: UByte = 6u
        const val ANNOTATION_USAGE: UByte = 7u
        const val TYPE_ARGUMENT: UByte = 8u
        const val IMPORT_TARGET: UByte = 9u
        const val DEPENDENCY_TARGET: UByte = 10u
    }
}

@JvmInline
value class ContextToken(val context: UByte) {
    companion object {
        const val TOP_LEVEL: UByte = 1u
        const val CLASS_BODY: UByte = 2u
        const val FUNCTION_SIGNATURE: UByte = 3u
        const val FUNCTION_BODY: UByte = 4u
        const val PROPERTY_INITIALIZER: UByte = 5u
        const val CONSTRUCTOR_BODY: UByte = 6u
        const val WHEN_EXPRESSION: UByte = 7u
        const val IF_EXPRESSION: UByte = 8u
        const val LAMBDA_BODY: UByte = 9u
        const val ANNOTATION_PARAMS: UByte = 10u
        const val TYPE_CONSTRAINT: UByte = 11u
    }
}

// Level 4 Compositions
typealias ClassifiedEntity = Join<EntityToken, RoleToken>
typealias ContextualEntity = Join<ClassifiedEntity, ContextToken>
typealias EntitySeries = Series<ContextualEntity>

// ==== LEVEL 5: GRAPH NODE CLASSIFICATION ====

@JvmInline
value class GraphNodeToken(val nodeId: UInt)

@JvmInline
value class DependencyToken(val depType: UByte) {
    companion object {
        const val IMPORTS: UByte = 1u
        const val FUNCTION_CALLS: UByte = 2u
        const val PROPERTY_ACCESS: UByte = 3u
        const val INHERITANCE: UByte = 4u
        const val IMPLEMENTATION: UByte = 5u
        const val COMPOSITION: UByte = 6u
        const val ANNOTATION: UByte = 7u
        const val TYPE_PARAMETER: UByte = 8u
        const val MAVEN_DEPENDENCY: UByte = 9u
        const val PROJECT_DEPENDENCY: UByte = 10u
        const val INTERNAL_REFERENCE: UByte = 11u
    }
}

@JvmInline
value class ConfidenceToken(val confidence: UByte) // 0-255 confidence score

// Level 5 Compositions
typealias ClassifiedGraphNode = Join<GraphNodeToken, DependencyToken>
typealias ConfidentGraphNode = Join<ClassifiedGraphNode, ConfidenceToken>
typealias GraphNodeSeries = Series<ConfidentGraphNode>

// ==== STAIRWAY TRANSFORMATION ENGINE ====

/**
 * Token Stairway - Progressive Classification with α Transforms
 * Each step adds semantic richness while maintaining zero-cost abstractions
 */
object TokenStairway {
    
    /**
     * Step 1: Character Classification
     * Raw characters → Classified characters with positions
     */
    fun classifyChars(source: String): CharSeries {
        return source.length j { i ->
            val char = RawChar(source[i])
            val charClass = classifyChar(char.value)
            val position = CharPosition(i)
            (char j charClass) j position
        }
    }
    
    /**
     * Step 2: Lexical Tokenization
     * Classified characters → Lexical tokens with types and bounds
     */
    fun charsToTokens(chars: CharSeries): TokenSeries = chars.α { posChar ->
        val (classifiedChar, position) = posChar
        val (rawChar, charClass) = classifiedChar
        
        // Simple tokenization logic - would be enhanced with full lexer
        val token = LexicalToken(rawChar.value.toString())
        val tokenType = charClassToTokenType(charClass)
        val bounds = TokenBounds.pack(position.index, 1)
        
        (token j tokenType) j bounds
    }
    
    /**
     * Step 3: Syntactic Classification  
     * Lexical tokens → Syntactic elements with scope and visibility
     */
    fun tokensToSyntax(tokens: TokenSeries): SyntaxSeries = tokens.α { boundedToken ->
        val (classifiedToken, bounds) = boundedToken
        val (lexicalToken, tokenType) = classifiedToken
        
        val syntaxToken = tokenTypeToSyntax(tokenType, lexicalToken.value)
        val scopeLevel = ScopeLevel(0u) // Would calculate from context
        val visibility = VisibilityToken.NONE
        
        (syntaxToken j scopeLevel) j VisibilityToken(visibility)
    }
    
    /**
     * Step 4: Entity Recognition
     * Syntactic elements → Semantic entities with roles and context
     */
    fun syntaxToEntities(syntax: SyntaxSeries): EntitySeries = syntax.α { visibleSyntax ->
        val (classifiedSyntax, visibility) = visibleSyntax
        val (syntaxToken, scopeLevel) = classifiedSyntax
        
        val entityToken = syntaxToEntity(syntaxToken)
        val roleToken = RoleToken(RoleToken.DECLARATION) // Would analyze context
        val contextToken = ContextToken(ContextToken.TOP_LEVEL) // Would analyze scope
        
        (entityToken j roleToken) j contextToken
    }
    
    /**
     * Step 5: Graph Node Generation
     * Semantic entities → Graph nodes with dependencies and confidence
     */
    fun entitiesToGraph(entities: EntitySeries): GraphNodeSeries = entities.α { contextualEntity ->
        val (classifiedEntity, context) = contextualEntity
        val (entityToken, roleToken) = classifiedEntity
        
        val nodeId = GraphNodeToken(entityToken.entityType.toUInt()) // Simple ID generation
        val depType = roleToDependencyType(roleToken)
        val confidence = ConfidenceToken(255u) // High confidence for now
        
        (nodeId j DependencyToken(depType)) j confidence
    }
    
    // === CLASSIFICATION HELPER FUNCTIONS ===
    
    private fun classifyChar(char: Char): CharClass = when {
        char.isLetter() -> CharClass(CharClass.LETTER)
        char.isDigit() -> CharClass(CharClass.DIGIT)
        char.isWhitespace() && char != '\n' -> CharClass(CharClass.WHITESPACE)
        char == '\n' -> CharClass(CharClass.NEWLINE)
        char == '_' -> CharClass(CharClass.UNDERSCORE)
        char == '.' -> CharClass(CharClass.DOT)
        char == ':' -> CharClass(CharClass.COLON)
        char == ';' -> CharClass(CharClass.SEMICOLON)
        char == ',' -> CharClass(CharClass.COMMA)
        char == '(' -> CharClass(CharClass.PAREN_OPEN)
        char == ')' -> CharClass(CharClass.PAREN_CLOSE)
        char == '{' -> CharClass(CharClass.BRACE_OPEN)
        char == '}' -> CharClass(CharClass.BRACE_CLOSE)
        char == '[' -> CharClass(CharClass.BRACKET_OPEN)
        char == ']' -> CharClass(CharClass.BRACKET_CLOSE)
        char == '"' -> CharClass(CharClass.QUOTE_DOUBLE)
        char == '\'' -> CharClass(CharClass.QUOTE_SINGLE)
        char == '@' -> CharClass(CharClass.AT_SYMBOL)
        char == '#' -> CharClass(CharClass.HASH)
        else -> CharClass(CharClass.SYMBOL)
    }
    
    private fun charClassToTokenType(charClass: CharClass): TokenType = when (charClass.type) {
        CharClass.LETTER -> TokenType(TokenType.IDENTIFIER)
        CharClass.DIGIT -> TokenType(TokenType.LITERAL_NUMBER)
        CharClass.AT_SYMBOL -> TokenType(TokenType.ANNOTATION)
        CharClass.WHITESPACE -> TokenType(TokenType.WHITESPACE)
        CharClass.NEWLINE -> TokenType(TokenType.NEWLINE)
        else -> TokenType(TokenType.PUNCTUATION)
    }
    
    private fun tokenTypeToSyntax(tokenType: TokenType, value: String): SyntaxToken = when {
        tokenType.category == TokenType.IDENTIFIER && isKotlinKeyword(value) -> 
            SyntaxToken(SyntaxToken.KEYWORD_USAGE)
        tokenType.category == TokenType.IDENTIFIER && value.first().isUpperCase() -> 
            SyntaxToken(SyntaxToken.CLASS_NAME)
        tokenType.category == TokenType.IDENTIFIER -> 
            SyntaxToken(SyntaxToken.VARIABLE_NAME)
        tokenType.category == TokenType.ANNOTATION -> 
            SyntaxToken(SyntaxToken.ANNOTATION_NAME)
        else -> SyntaxToken(SyntaxToken.KEYWORD_USAGE)
    }
    
    private fun syntaxToEntity(syntaxToken: SyntaxToken): EntityToken = when (syntaxToken.semantic) {
        SyntaxToken.CLASS_NAME -> EntityToken(EntityToken.REGULAR_CLASS)
        SyntaxToken.FUNCTION_NAME -> EntityToken(EntityToken.SUSPEND_FUNCTION)
        SyntaxToken.VARIABLE_NAME -> EntityToken(EntityToken.PROPERTY)
        SyntaxToken.ANNOTATION_NAME -> EntityToken(EntityToken.ANNOTATION_CLASS)
        else -> EntityToken(EntityToken.OBJECT)
    }
    
    private fun roleToDependencyType(roleToken: RoleToken): UByte = when (roleToken.role) {
        RoleToken.DECLARATION -> DependencyToken.INTERNAL_REFERENCE
        RoleToken.REFERENCE -> DependencyToken.FUNCTION_CALLS
        RoleToken.INHERITANCE -> DependencyToken.INHERITANCE
        RoleToken.IMPORT_TARGET -> DependencyToken.IMPORTS
        else -> DependencyToken.INTERNAL_REFERENCE
    }
    
    private fun isKotlinKeyword(value: String): Boolean = when (value) {
        "class", "fun", "val", "var", "if", "else", "when", "for", "while", 
        "do", "try", "catch", "finally", "return", "break", "continue",
        "object", "interface", "enum", "sealed", "data", "inline", "suspend",
        "public", "private", "internal", "protected", "open", "final", "abstract" -> true
        else -> false
    }
}

// ==== CONVENIENCE EXTENSIONS ====

/**
 * Extension functions for easy stairway traversal
 */
fun String.scanToGraph(): GraphNodeSeries {
    val chars = TokenStairway.classifyChars(this)
    val tokens = TokenStairway.charsToTokens(chars)
    val syntax = TokenStairway.tokensToSyntax(tokens)
    val entities = TokenStairway.syntaxToEntities(syntax)
    return TokenStairway.entitiesToGraph(entities)
}

fun String.scanToEntities(): EntitySeries {
    val chars = TokenStairway.classifyChars(this)
    val tokens = TokenStairway.charsToTokens(chars)
    val syntax = TokenStairway.tokensToSyntax(tokens)
    return TokenStairway.syntaxToEntities(syntax)
}

fun CharSeries.extractTokens(): TokenSeries = TokenStairway.charsToTokens(this)
fun TokenSeries.extractSyntax(): SyntaxSeries = TokenStairway.tokensToSyntax(this)
fun SyntaxSeries.extractEntities(): EntitySeries = TokenStairway.syntaxToEntities(this)
fun EntitySeries.extractGraph(): GraphNodeSeries = TokenStairway.entitiesToGraph(this)

/**
 * Materialization functions using play operator - gateway to stdlib
 */
fun GraphNodeSeries.materializeGraphNodes(): List<ConfidentGraphNode> = this.play.toList()
fun EntitySeries.materializeEntities(): List<ContextualEntity> = this.play.toList()
fun TokenSeries.materializeTokens(): List<BoundedToken> = this.play.toList()

/**
 * Example usage demonstrating the complete stairway
 */
object TokenStairwayExample {
    fun demonstrateStairway() {
        val kotlinCode = """
            @JvmInline
            value class UserId(val id: String)
            
            fun getUserName(userId: UserId): String {
                return "user_" + userId.id
            }
        """.trimIndent()
        
        println("=== Token Classification Stairway Demo ===")
        
        // Full stairway traversal
        val graphNodes = kotlinCode.scanToGraph()
        println("Graph nodes: ${graphNodes.play.toList()}")
        
        // Step-by-step traversal
        val chars = TokenStairway.classifyChars(kotlinCode)
        val tokens = chars.extractTokens()
        val syntax = tokens.extractSyntax()
        val entities = syntax.extractEntities()
        
        println("Entities found: ${entities.play.toList().size}")
        println("Syntax elements: ${syntax.play.toList().size}")
        println("Tokens: ${tokens.play.toList().size}")
        println("Characters: ${chars.size}")
    }
}