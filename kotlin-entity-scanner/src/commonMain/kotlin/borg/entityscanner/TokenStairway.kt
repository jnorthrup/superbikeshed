
package borg.entityscanner

import borg.trikeshed.lib.*
import borg.trikeshed.lib.Indexed as Indexed
import borg.trikeshed.lib.ByteIndexed
import borg.trikeshed.lib.CharIndexed

/**
 * Token Classification Stairway - Hierarchical Inline Class System
 * 
 * Each level adds semantic richness while maintaining zero-cost abstractions
 * Uses TrikeShed patterns: Indexed<T>, Join<A,B>, α transforms, taxonomical type aliases
 */

// ==== LEVEL 1: RAW CHARACTER CLASSIFICATION ====

value class RawChar(val value: Char)

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

value class CharPosition(val index: Int)

// Level 1 Compositions
typealias ClassifiedChar = Join<RawChar, CharClass>
typealias PositionedChar = Join<ClassifiedChar, CharPosition>
typealias CharIndexed = borg.trikeshed.lib.CharIndexed<PositionedChar>
 
// ==== LEVEL 2: LEXICAL TOKEN CLASSIFICATION ====

value class LexicalToken(val value: String)

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

/**
 * Canonical TrikeShed token stream: functional, lazily-accessed sequence of tokens.
 */
typealias TokenIndexed = Indexed<BoundedToken>

/**
 * For small, materialized token sets (register-packing, hot paths).
 */
typealias TokenArray = Array<BoundedToken>

// ==== LEVEL 3: SYNTACTIC CLASSIFICATION ====

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
        const val CONSTANT_NAME: UByte = 17u
        const val QUALIFIED_NAME_PART: UByte = 18u
        const val IMPORT_KEYWORD: UByte = 19u
        const val PACKAGE_KEYWORD: UByte = 20u
        const val BRACE_OPEN: UByte = 21u
        const val BRACE_CLOSE: UByte = 22u
        const val PAREN_OPEN: UByte = 23u
        const val PAREN_CLOSE: UByte = 24u
        // Specific keywords if needed, e.g. for visibility checks
        const val CLASS_KEYWORD: UByte = 25u
        const val INTERFACE_KEYWORD: UByte = 26u
        const val FUN_KEYWORD: UByte = 27u
        const val VAL_KEYWORD: UByte = 28u
        const val VAR_KEYWORD: UByte = 29u
        const val DATA_KEYWORD: UByte = 30u
        // Visibility Keywords
        const val PUBLIC_KEYWORD: UByte = 31u
        const val PRIVATE_KEYWORD: UByte = 32u
        const val INTERNAL_KEYWORD: UByte = 33u
        const val PROTECTED_KEYWORD: UByte = 34u
        const val ABSTRACT_KEYWORD: UByte = 35u
        const val FINAL_KEYWORD: UByte = 36u
        const val OPEN_KEYWORD: UByte = 37u
        const val SUSPEND_KEYWORD: UByte = 38u
        const val INLINE_KEYWORD: UByte = 39u
        // Add more as identified
    }
}

value class ScopeLevel(val depth: UByte)

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
typealias SyntaxIndexed = borg.trikeshed.lib.ByteIndexed<VisibleSyntax>

// ==== LEVEL 4: SEMANTIC ENTITY CLASSIFICATION ====

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
        const val UNKNOWN_ENTITY: UByte = 19u
    }
}

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
        const val UNKNOWN_ROLE: UByte = 11u
    }
}

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
        const val PACKAGE_CONTEXT: UByte = 12u
        const val IMPORT_CONTEXT: UByte = 13u
        const val UNKNOWN_CONTEXT: UByte = 14u
    }
}

// Level 4 Compositions
typealias ClassifiedEntity = Join<EntityToken, RoleToken>
typealias ContextualEntity = Join<ClassifiedEntity, ContextToken>
typealias EntityIndexed = borg.trikeshed.lib.ByteIndexed<ContextualEntity>

// ==== LEVEL 5: GRAPH NODE CLASSIFICATION ====

value class GraphNodeToken(val nodeId: UInt)

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

value class ConfidenceToken(val confidence: UByte) // 0-255 confidence score

// Level 5 Compositions
typealias ClassifiedGraphNode = Join<GraphNodeToken, DependencyToken>
typealias ConfidentGraphNode = Join<ClassifiedGraphNode, ConfidenceToken>
typealias GraphNodeIndexed = borg.trikeshed.lib.ByteIndexed<ConfidentGraphNode>

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
    fun classifyChars(source: String): CharIndexed {
        return \1 j { \2: Int ->
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
    fun charsToTokens(chars: CharIndexed): TokenIndexed = chars.α { posChar ->
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
    fun tokensToSyntax(tokens: TokenIndexed): SyntaxIndexed {
        val syntaxList = mutableListOf<VisibleSyntax>()
        var currentScopeDepth: UByte = 0u
        val activeVisibilityModifiers = mutableListOf<VisibilityToken>()

        for (i in 0 until tokens.size) {
            val currentToken = tokens[i]
            val prevToken = if (i > 0) tokens[i - 1] else null
            val nextToken = if (i < tokens.size - 1) tokens[i + 1] else null

            val (classifiedToken, _) = currentToken
            val (lexicalToken, tokenType) = classifiedToken

            // Basic scope handling
            if (lexicalToken.value == "{") currentScopeDepth++

            val syntaxToken = tokenTypeToSyntax(tokenType, lexicalToken.value, prevToken, nextToken)
            val scopeLevel = ScopeLevel(currentScopeDepth)

            var currentVisibility = VisibilityToken(VisibilityToken.NONE)
            if (isVisibilityKeyword(lexicalToken.value)) {
                 activeVisibilityModifiers.add(mapLexicalToVisibility(lexicalToken.value))
            } else if (syntaxToken.semantic == SyntaxToken.CLASS_NAME ||
                       syntaxToken.semantic == SyntaxToken.INTERFACE_NAME ||
                       syntaxToken.semantic == SyntaxToken.FUNCTION_NAME ||
                       syntaxToken.semantic == SyntaxToken.PROPERTY_NAME) {
                // Apply collected modifiers and then clear them for the next declaration
                if (activeVisibilityModifiers.isNotEmpty()) {
                    // Combine multiple modifiers if necessary (e.g. "public open")
                    // For simplicity, just taking the first one found for now, or a combined value.
                    // A more robust system would create a Join<VisibilityToken, VisibilityToken> or a bitmask.
                    currentVisibility = activeVisibilityModifiers.first() // Simplified
                    activeVisibilityModifiers.clear()
                }
            }


            syntaxList.add((syntaxToken j scopeLevel) j currentVisibility)

            if (lexicalToken.value == "}") {
                if (currentScopeDepth > 0u) currentScopeDepth--
            }
        }
        return \1 j { \2: Int -> syntaxList[idx] }
    }

    /**
     * Step 4: Entity Recognition
     * Syntactic elements → Semantic entities with roles and context
     */
    fun syntaxToEntities(syntaxSeries: SyntaxIndexed): EntityIndexed {
        val entityList = mutableListOf<ContextualEntity>()
        var currentPackageContext = false
        var currentImportContext = false

        for (i in 0 until syntaxSeries.size) {
            val currentSyntax = syntaxSeries[i]
            val prevSyntax = if (i > 0) syntaxSeries[i-1] else null
            val nextSyntax = if (i < syntaxSeries.size - 1) syntaxSeries[i+1] else null

            val (classifiedSyntax, visibility) = currentSyntax
            val (syntaxToken, scopeLevel) = classifiedSyntax

            val entityToken = syntaxToEntity(syntaxToken, visibility, prevSyntax, nextSyntax)

            // Basic Role Token Logic
            var roleToken = RoleToken(RoleToken.UNKNOWN_ROLE)
            when (syntaxToken.semantic) {
                SyntaxToken.CLASS_NAME, SyntaxToken.INTERFACE_NAME, SyntaxToken.ENUM_NAME,
                SyntaxToken.FUNCTION_NAME, SyntaxToken.PROPERTY_NAME, SyntaxToken.VARIABLE_NAME -> {
                    // Check if previous token was a declaration keyword (simplified)
                    val prevLexical = prevSyntax?.let { ps ->
                        // This requires access to the original BoundedToken, which is not directly in VisibleSyntax
                        // This part highlights a limitation if we only pass VisibleSyntax
                        // For now, we assume some keywords might be classified as SyntaxToken types
                        ps.get<ClassifiedSyntax>().get<SyntaxToken>()
                    }
                    if (prevLexical?.semantic == SyntaxToken.CLASS_KEYWORD ||
                        prevLexical?.semantic == SyntaxToken.INTERFACE_KEYWORD ||
                        prevLexical?.semantic == SyntaxToken.FUN_KEYWORD ||
                        prevLexical?.semantic == SyntaxToken.VAL_KEYWORD ||
                        prevLexical?.semantic == SyntaxToken.VAR_KEYWORD) {
                        roleToken = RoleToken(RoleToken.DECLARATION)
                    } else {
                         roleToken = RoleToken(RoleToken.REFERENCE) // Default to reference if not clearly a declaration start
                    }
                }
                SyntaxToken.FUNCTION_CALL -> roleToken = RoleToken(RoleToken.REFERENCE)
                SyntaxToken.IMPORT_DECLARATION -> roleToken = RoleToken(RoleToken.IMPORT_TARGET) // Should apply to parts of import
                SyntaxToken.IDENTIFIER -> { // If it's part of an import path
                    if (currentImportContext) roleToken = RoleToken(RoleToken.IMPORT_TARGET)
                }
                else -> roleToken = RoleToken(RoleToken.UNKNOWN_ROLE)
            }
            if (syntaxToken.semantic == SyntaxToken.IMPORT_KEYWORD) currentImportContext = true
            if (syntaxToken.semantic != SyntaxToken.IDENTIFIER && syntaxToken.semantic != SyntaxToken.QUALIFIED_NAME_PART && syntaxToken.semantic != SyntaxToken.OPERATOR_USAGE) {
                 // Reset import context if we see something that's not part of a path
                if (lexicalValueFromSyntax(currentSyntax) != ".") currentImportContext = false
            }


            // Basic Context Token Logic
            var contextToken = ContextToken(ContextToken.UNKNOWN_CONTEXT)
            if (syntaxToken.semantic == SyntaxToken.PACKAGE_KEYWORD) currentPackageContext = true

            if (currentPackageContext) {
                contextToken = ContextToken(ContextToken.PACKAGE_CONTEXT)
                 if (syntaxToken.semantic != SyntaxToken.IDENTIFIER && syntaxToken.semantic != SyntaxToken.QUALIFIED_NAME_PART && syntaxToken.semantic != SyntaxToken.OPERATOR_USAGE) {
                    // Reset package context if we see something that's not part of a path
                     if (lexicalValueFromSyntax(currentSyntax) != ".") currentPackageContext = false
                }
            } else if (currentImportContext) {
                 contextToken = ContextToken(ContextToken.IMPORT_CONTEXT)
            } else if (scopeLevel.depth == 0u.toUByte()) {
                contextToken = ContextToken(ContextToken.TOP_LEVEL)
            } else if (scopeLevel.depth > 0u.toUByte()) {
                // Simplified: any scope > 0 is class body. Needs refinement.
                contextToken = ContextToken(ContextToken.CLASS_BODY)
            }
            // ANNOTATION_PARAMS needs more specific logic, e.g. tracking if inside @Ann(...)

            entityList.add((entityToken j roleToken) j contextToken)
        }
        return \1 j { \2: Int -> entityList[idx] }
    }

    // Helper to attempt to get lexical value from VisibleSyntax (placeholder)
    // This is a conceptual challenge: VisibleSyntax doesn't directly hold the BoundedToken.
    // The transformation chain currently loses direct access to the original lexical value easily.
    // This might require redesigning how context is passed or how tokens are structured.
    // For now, this function won't work correctly without significant changes to data structures or transform process.
    internal fun lexicalValueFromSyntax(vs: VisibleSyntax): String? {
        // This is a placeholder. In a real scenario, you'd need a way to trace back
        // to the LexicalToken or ensure BoundedToken (or its value) is carried forward.
        // One way is to make BoundedToken part of the Join chain up to VisibleSyntax.
        // E.g., typealias VisibleSyntax = Join<Join<ClassifiedSyntax, VisibilityToken>, BoundedToken>
        // For this exercise, we'll assume such access or a workaround.
        return null
    }


    /**
     * Step 5: Graph Node Generation
     * Semantic entities → Graph nodes with dependencies and confidence
     */
    fun entitiesToGraph(entities: EntityIndexed): GraphNodeIndexed = entities.α { contextualEntity ->
        val (classifiedEntity, context) = contextualEntity
        val (entityToken, roleToken) = classifiedEntity
        
        val nodeId = GraphNodeToken(entityToken.entityType.toUInt()) // Simple ID generation
        val depType = roleToDependencyType(roleToken)
        val confidence = ConfidenceToken(255u) // High confidence for now
        
        (nodeId j DependencyToken(depType)) j confidence
    }
    
    // === CLASSIFICATION HELPER FUNCTIONS ===
    
    internal fun classifyChar(char: Char): CharClass = when {
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

    internal fun charClassToTokenType(charClass: CharClass): TokenType = when (charClass.type) {
        CharClass.LETTER -> TokenType(TokenType.IDENTIFIER)
        CharClass.DIGIT -> TokenType(TokenType.LITERAL_NUMBER)
        CharClass.AT_SYMBOL -> TokenType(TokenType.ANNOTATION) // Assumes @ is lexed as part of annotation marker
        CharClass.WHITESPACE -> TokenType(TokenType.WHITESPACE)
        CharClass.NEWLINE -> TokenType(TokenType.NEWLINE)
        CharClass.DOT -> TokenType(TokenType.OPERATOR) // Or specific DOT_PUNCTUATION
        CharClass.PAREN_OPEN, CharClass.PAREN_CLOSE,
        CharClass.BRACE_OPEN, CharClass.BRACE_CLOSE -> TokenType(TokenType.PUNCTUATION)
        else -> TokenType(TokenType.PUNCTUATION) // Default for other symbols
    }

    internal fun tokenTypeToSyntax(
        tokenType: TokenType,
        value: String,
        prevToken: BoundedToken?,
        nextToken: BoundedToken?
    ): SyntaxToken {
        val prevLexical = prevToken?.get<ClassifiedToken>()?.get<LexicalToken>()?.value
        val nextLexical = nextToken?.get<ClassifiedToken>()?.get<LexicalToken>()?.value

        return when (tokenType.category) {
            TokenType.IDENTIFIER -> {
                if (isKotlinKeyword(value)) { // Check if identifier is actually a keyword missed by lexer
                    return mapKeywordToSyntax(value)
                }
                when (prevLexical) {
                    "class" -> SyntaxToken(SyntaxToken.CLASS_NAME)
                    "interface" -> SyntaxToken(SyntaxToken.INTERFACE_NAME)
                    "fun" -> SyntaxToken(SyntaxToken.FUNCTION_NAME)
                    "val" -> SyntaxToken(SyntaxToken.PROPERTY_NAME)
                    "var" -> SyntaxToken(SyntaxToken.PROPERTY_NAME) // or VARIABLE_NAME if distinction is needed
                    "@" -> SyntaxToken(SyntaxToken.ANNOTATION_NAME) // If @ is separate
                    else -> {
                        if (nextLexical == "(" && prevLexical !in setOf("if", "while", "for", "when")) {
                            SyntaxToken(SyntaxToken.FUNCTION_CALL)
                        } else if (nextLexical == ".") {
                            SyntaxToken(SyntaxToken.QUALIFIED_NAME_PART) // part of qualified name or start of property access
                        } else if (value.all { it.isUpperCase() || it == '_' || it.isDigit() } && value.contains('_')) {
                            SyntaxToken(SyntaxToken.CONSTANT_NAME)
                        } else if (value.firstOrNull()?.isUpperCase() == true && prevLexical != "." && prevLexical != "import") {
                            // Heuristic: Capitalized identifier might be a class name (e.g. type annotation, constructor call)
                            // This needs more context to differentiate (e.g. Type vs ConstructorCall)
                            SyntaxToken(SyntaxToken.CLASS_NAME) // Could be TYPE_REFERENCE or CONSTRUCTOR_CALL
                        }
                        else {
                            SyntaxToken(SyntaxToken.VARIABLE_NAME) // Default
                        }
                    }
                }
            }
            TokenType.ANNOTATION -> { // e.g. @file:
                if (value.contains("EntryPoint")) SyntaxToken(SyntaxToken.ANNOTATION_NAME) // Specific check
                else SyntaxToken(SyntaxToken.ANNOTATION_NAME)
            }
            TokenType.KEYWORD -> mapKeywordToSyntax(value)
            TokenType.PUNCTUATION -> when(value) {
                "{" -> SyntaxToken(SyntaxToken.BRACE_OPEN)
                "}" -> SyntaxToken(SyntaxToken.BRACE_CLOSE)
                "(" -> SyntaxToken(SyntaxToken.PAREN_OPEN)
                ")" -> SyntaxToken(SyntaxToken.PAREN_CLOSE)
                else -> SyntaxToken(SyntaxToken.OPERATOR_USAGE) // Or some GENERIC_PUNCTUATION
            }
            TokenType.OPERATOR -> SyntaxToken(SyntaxToken.OPERATOR_USAGE)
            // Handle other token types if necessary
            else -> SyntaxToken(SyntaxToken.KEYWORD_USAGE) // Fallback, consider UNKNOWN_SYNTAX
        }
    }
    
    internal fun mapKeywordToSyntax(value: String): SyntaxToken {
        return when(value) {
            "package" -> SyntaxToken(SyntaxToken.PACKAGE_KEYWORD)
            "import" -> SyntaxToken(SyntaxToken.IMPORT_KEYWORD)
            "class" -> SyntaxToken(SyntaxToken.CLASS_KEYWORD)
            "interface" -> SyntaxToken(SyntaxToken.INTERFACE_KEYWORD)
            "fun" -> SyntaxToken(SyntaxToken.FUN_KEYWORD)
            "val" -> SyntaxToken(SyntaxToken.VAL_KEYWORD)
            "var" -> SyntaxToken(SyntaxToken.VAR_KEYWORD)
            "data" -> SyntaxToken(SyntaxToken.DATA_KEYWORD)
            "public" -> SyntaxToken(SyntaxToken.PUBLIC_KEYWORD)
            "private" -> SyntaxToken(SyntaxToken.PRIVATE_KEYWORD)
            "internal" -> SyntaxToken(SyntaxToken.INTERNAL_KEYWORD)
            "protected" -> SyntaxToken(SyntaxToken.PROTECTED_KEYWORD)
            "abstract" -> SyntaxToken(SyntaxToken.ABSTRACT_KEYWORD)
            "final" -> SyntaxToken(SyntaxToken.FINAL_KEYWORD)
            "open" -> SyntaxToken(SyntaxToken.OPEN_KEYWORD)
            "suspend" -> SyntaxToken(SyntaxToken.SUSPEND_KEYWORD)
            "inline" -> SyntaxToken(SyntaxToken.INLINE_KEYWORD)
            // Add all other keywords from isKotlinKeyword
            else -> SyntaxToken(SyntaxToken.KEYWORD_USAGE)
        }
    }
    
    internal fun isVisibilityKeyword(value: String): Boolean {
        return value in setOf("public", "private", "internal", "protected", "abstract", "final", "open", "suspend", "inline")
    }

    internal fun mapLexicalToVisibility(value: String): VisibilityToken {
        return when(value) {
            "public" -> VisibilityToken(VisibilityToken.PUBLIC)
            "private" -> VisibilityToken(VisibilityToken.PRIVATE)
            "internal" -> VisibilityToken(VisibilityToken.INTERNAL)
            "protected" -> VisibilityToken(VisibilityToken.PROTECTED)
            "abstract" -> VisibilityToken(VisibilityToken.ABSTRACT)
            "final" -> VisibilityToken(VisibilityToken.FINAL)
            "open" -> VisibilityToken(VisibilityToken.OPEN)
            "suspend" -> VisibilityToken(VisibilityToken.SUSPEND)
            "inline" -> VisibilityToken(VisibilityToken.INLINE)
            else -> VisibilityToken(VisibilityToken.NONE)
        }
    }


    internal fun syntaxToEntity(
        syntaxToken: SyntaxToken,
        visibility: VisibilityToken,
        prevSyntax: VisibleSyntax?,
        nextSyntax: VisibleSyntax?
    ): EntityToken {
        // Basic logic, needs refinement with more context
        val prevLexicalSyntax = prevSyntax?.get<ClassifiedSyntax>()?.get<SyntaxToken>()

        return when (syntaxToken.semantic) {
            SyntaxToken.CLASS_NAME -> {
                if (prevLexicalSyntax?.semantic == SyntaxToken.DATA_KEYWORD) EntityToken(EntityToken.DATA_CLASS)
                else if (visibility.access == VisibilityToken.ABSTRACT) EntityToken(EntityToken.ABSTRACT_CLASS)
                else EntityToken(EntityToken.REGULAR_CLASS)
            }
            SyntaxToken.INTERFACE_NAME -> EntityToken(EntityToken.INTERFACE)
            SyntaxToken.FUNCTION_NAME -> {
                if (visibility.access == VisibilityToken.SUSPEND) EntityToken(EntityToken.SUSPEND_FUNCTION)
                // else if (visibility includes INLINE) EntityToken(EntityToken.INLINE_FUNCTION) // Needs combined visibility
                else EntityToken(EntityToken.EXTENSION_FUNCTION) // Default, needs refinement
            }
            SyntaxToken.PROPERTY_NAME -> EntityToken(EntityToken.PROPERTY)
            SyntaxToken.VARIABLE_NAME -> EntityToken(EntityToken.PROPERTY) // Or a more general variable if not a class member
            SyntaxToken.ANNOTATION_NAME -> EntityToken(EntityToken.ANNOTATION_CLASS) // Or ANNOTATION_USAGE if it's an instance
            // Add more mappings
            else -> EntityToken(EntityToken.UNKNOWN_ENTITY)
        }
    }

    internal fun roleToDependencyType(roleToken: RoleToken): UByte = when (roleToken.role) {
        RoleToken.DECLARATION -> DependencyToken.INTERNAL_REFERENCE
        RoleToken.REFERENCE -> DependencyToken.FUNCTION_CALLS // Or PROPERTY_ACCESS, etc.
        RoleToken.INHERITANCE -> DependencyToken.INHERITANCE
        RoleToken.IMPLEMENTATION -> DependencyToken.IMPLEMENTATION
        RoleToken.IMPORT_TARGET -> DependencyToken.IMPORTS
        RoleToken.ANNOTATION_USAGE -> DependencyToken.ANNOTATION
        RoleToken.TYPE_ARGUMENT -> DependencyToken.TYPE_PARAMETER // Or generic type usage
        RoleToken.PARAMETER -> DependencyToken.INTERNAL_REFERENCE // Parameter usage
        else -> DependencyToken.INTERNAL_REFERENCE
    }
    
    // More comprehensive keyword list
    internal val kotlinKeywords = setOf(
        // Hard keywords
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
        "interface", "is", "null", "object", "package", "return", "super", "this", "throw",
        "true", "try", "typealias", "typeof", "val", "var", "when", "while",
        // Soft keywords
        "by", "catch", "constructor", "delegate", "dynamic", "field", "file", "finally",
        "get", "import", "init", "param", "property", "receiver", "set", "setparam", "where",
        // Modifier keywords
        "abstract", "actual", "annotation", "companion", "const", "crossinline", "data",
        "enum", "expect", "external", "final", "infix", "inline", "inner", "internal",
        "lateinit", "noinline", "open", "operator", "out", "override", "private",
        "protected", "public", "reified", "sealed", "suspend", "tailrec", "value", "vararg"
    )

    internal fun isKotlinKeyword(value: String): Boolean = value in kotlinKeywords

}

// ==== CONVENIENCE EXTENSIONS ====

/**
 * Extension functions for easy stairway traversal
 */
fun String.scanToGraph(): GraphNodeIndexed {
    val chars = TokenStairway.classifyChars(this)
    val tokens = TokenStairway.charsToTokens(chars)
    val syntax = TokenStairway.tokensToSyntax(tokens)
    val entities = TokenStairway.syntaxToEntities(syntax)
    return TokenStairway.entitiesToGraph(entities)
}

fun String.scanToEntities(): EntityIndexed {
    val chars = TokenStairway.classifyChars(this)
    val tokens = TokenStairway.charsToTokens(chars)
    val syntax = TokenStairway.tokensToSyntax(tokens)
    return TokenStairway.syntaxToEntities(syntax)
}

fun CharIndexed.extractTokens(): TokenIndexed = TokenStairway.charsToTokens(this)
fun TokenIndexed.extractSyntax(): SyntaxIndexed = TokenStairway.tokensToSyntax(this)
fun SyntaxIndexed.extractEntities(): EntityIndexed = TokenStairway.syntaxToEntities(this)
fun EntityIndexed.extractGraph(): GraphNodeIndexed = TokenStairway.entitiesToGraph(this)

/**
 * Materialization functions using play operator - gateway to stdlib
 */
fun GraphNodeIndexed.materializeGraphNodes(): List<ConfidentGraphNode> = this.play.toList()
fun EntityIndexed.materializeEntities(): List<ContextualEntity> = this.play.toList()
fun TokenIndexed.materializeTokens(): List<BoundedToken> = this.play.toList()

/**
 * Example usage demonstrating the complete stairway
 */
object TokenStairwayExample {
    fun demonstrateStairway() {
        val kotlinCode = """
            @kotlin.jvm.JvmInline
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

/**
 * Enum for ergonomic, pattern-matchable token types.
 */
enum class TokenTypeEnum {
    KEYWORD,
    IDENTIFIER,
    OPERATOR,
    LITERAL_STRING,
    LITERAL_NUMBER,
    LITERAL_BOOLEAN,
    PUNCTUATION,
    COMMENT,
    ANNOTATION,
    PACKAGE_NAME,
    IMPORT_PATH,
    TYPE_NAME,
    DEPENDENCY_COORD,
    WHITESPACE,
    NEWLINE,
    UNKNOWN
}

/**
 * Map TokenType value class to TokenTypeEnum for ergonomic use.
 */
fun TokenType.toEnum(): TokenTypeEnum = when (this.category) {
    1u -> TokenTypeEnum.KEYWORD
    2u -> TokenTypeEnum.IDENTIFIER
    3u -> TokenTypeEnum.OPERATOR
    4u -> TokenTypeEnum.LITERAL_STRING
    5u -> TokenTypeEnum.LITERAL_NUMBER
    6u -> TokenTypeEnum.LITERAL_BOOLEAN
    7u -> TokenTypeEnum.PUNCTUATION
    8u -> TokenTypeEnum.COMMENT
    9u -> TokenTypeEnum.ANNOTATION
    10u -> TokenTypeEnum.PACKAGE_NAME
    11u -> TokenTypeEnum.IMPORT_PATH
    12u -> TokenTypeEnum.TYPE_NAME
    13u -> TokenTypeEnum.DEPENDENCY_COORD
    14u -> TokenTypeEnum.WHITESPACE
    15u -> TokenTypeEnum.NEWLINE
    16u -> TokenTypeEnum.UNKNOWN
    else -> TokenTypeEnum.UNKNOWN
}