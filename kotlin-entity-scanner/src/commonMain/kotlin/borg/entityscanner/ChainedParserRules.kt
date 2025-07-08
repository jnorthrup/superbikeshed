
package borg.entityscanner

import borg.trikeshed.lib.*

/**
 * Forward and Backward Chaining Kotlin Parser Rules
 * 
 * Implements aggressive rule-based parsing with bidirectional chaining
 * for maximum parsing accuracy and context awareness.
 */

// ==== RULE SYSTEM TYPES ====

typealias RuleId = String

typealias RuleWeight = Double // 0.0 to 1.0

value class ChainDirection(val direction: UByte) {
    companion object {
        const val FORWARD: UByte = 1u
        const val BACKWARD: UByte = 2u
        const val BIDIRECTIONAL: UByte = 3u
    }
}

typealias RuleActivation = Double // Current activation level

typealias ContextWindow = Int // Lookahead/lookbehind window

// Core rule types
typealias ParsingRule = Join<RuleId, Join<RuleWeight, (ParseContext) -> RuleActivation>>
typealias RuleCondition = (ParseContext, Int) -> Boolean
typealias RuleAction = (ParseContext, Int) -> ParseContext
typealias ChainedRule = Join<ParsingRule, Join<ChainDirection, ContextWindow>>

// Rule collections
typealias ForwardRuleSeries = Indexed<ChainedRule>
typealias BackwardRuleSeries = Indexed<ChainedRule>
typealias RuleChain = Join<ForwardRuleSeries, BackwardRuleSeries>

// ==== KOTLIN SYNTAX RULES ====

/**
 * Forward Chaining Rules - Left-to-right parsing with lookahead
 */
object ForwardChainRules {
    
    /**
     * Class Declaration Forward Chain
     */
    fun classDeclarationChain(): ForwardRuleSeries = listOf(
        // Rule 1: Detect class keyword
        createForwardRule(
            id = "class_keyword_detect",
            weight = 0.95,
            window = 5,
            condition = { context, pos -> 
                lookAhead(context, pos, 5).contains("class")
            },
            action = { context, pos ->
                markEntity(context, pos, EntityToken.REGULAR_CLASS)
            }
        ),
        
        // Rule 2: Class name identification
        createForwardRule(
            id = "class_name_capture",
            weight = 0.9,
            window = 3,
            condition = { context, pos ->
                previousRuleActivated(context, "class_keyword_detect") &&
                isIdentifier(context, pos)
            },
            action = { context, pos ->
                captureClassName(context, pos)
            }
        ),
        
        // Rule 3: Generic parameters
        createForwardRule(
            id = "class_generics",
            weight = 0.8,
            window = 10,
            condition = { context, pos ->
                previousRuleActivated(context, "class_name_capture") &&
                lookAhead(context, pos, 2) == "<"
            },
            action = { context, pos ->
                parseGenericParameters(context, pos)
            }
        ),
        
        // Rule 4: Inheritance detection
        createForwardRule(
            id = "class_inheritance",
            weight = 0.85,
            window = 15,
            condition = { context, pos ->
                previousRuleActivated(context, "class_name_capture") &&
                lookAhead(context, pos, 5).contains(":")
            },
            action = { context, pos ->
                parseInheritance(context, pos)
            }
        ),
        
        // Rule 5: Class body start
        createForwardRule(
            id = "class_body_start",
            weight = 0.95,
            window = 3,
            condition = { context, pos ->
                previousRuleActivated(context, "class_name_capture") &&
                lookAhead(context, pos, 1) == "{"
            },
            action = { context, pos ->
                enterClassBody(context, pos)
            }
        )
    ).toSeries()
    
    /**
     * Function Declaration Forward Chain
     */
    fun functionDeclarationChain(): ForwardRuleSeries = listOf(
        // Rule 1: Function keyword detection
        createForwardRule(
            id = "fun_keyword_detect",
            weight = 0.95,
            window = 5,
            condition = { context, pos ->
                lookAhead(context, pos, 3) == "fun"
            },
            action = { context, pos ->
                markEntity(context, pos, EntityToken.SUSPEND_FUNCTION)
            }
        ),
        
        // Rule 2: Modifier detection (suspend, inline, etc.)
        createForwardRule(
            id = "function_modifiers",
            weight = 0.8,
            window = 8,
            condition = { context, pos ->
                val modifiers = setOf("suspend", "inline", "infix", "operator")
                lookAhead(context, pos, 8).split(" ").any { it in modifiers }
            },
            action = { context, pos ->
                captureFunctionModifiers(context, pos)
            }
        ),
        
        // Rule 3: Function name capture
        createForwardRule(
            id = "fun_name_capture",
            weight = 0.9,
            window = 5,
            condition = { context, pos ->
                previousRuleActivated(context, "fun_keyword_detect") &&
                isIdentifier(context, pos)
            },
            action = { context, pos ->
                captureFunctionName(context, pos)
            }
        ),
        
        // Rule 4: Parameter list detection
        createForwardRule(
            id = "fun_parameters",
            weight = 0.85,
            window = 20,
            condition = { context, pos ->
                previousRuleActivated(context, "fun_name_capture") &&
                lookAhead(context, pos, 1) == "("
            },
            action = { context, pos ->
                parseParameterList(context, pos)
            }
        ),
        
        // Rule 5: Return type detection
        createForwardRule(
            id = "fun_return_type",
            weight = 0.75,
            window = 10,
            condition = { context, pos ->
                previousRuleActivated(context, "fun_parameters") &&
                lookAhead(context, pos, 5).contains(":")
            },
            action = { context, pos ->
                parseReturnType(context, pos)
            }
        )
    ).toSeries()
    
    /**
     * Property Declaration Forward Chain
     */
    fun propertyDeclarationChain(): ForwardRuleSeries = listOf(
        // Rule 1: Property keyword (val/var)
        createForwardRule(
            id = "property_keyword",
            weight = 0.95,
            window = 3,
            condition = { context, pos ->
                val keyword = lookAhead(context, pos, 3)
                keyword == "val" || keyword == "var"
            },
            action = { context, pos ->
                markEntity(context, pos, EntityToken.PROPERTY)
            }
        ),
        
        // Rule 2: Property name
        createForwardRule(
            id = "property_name",
            weight = 0.9,
            window = 5,
            condition = { context, pos ->
                previousRuleActivated(context, "property_keyword") &&
                isIdentifier(context, pos)
            },
            action = { context, pos ->
                capturePropertyName(context, pos)
            }
        ),
        
        // Rule 3: Type annotation
        createForwardRule(
            id = "property_type",
            weight = 0.8,
            window = 8,
            condition = { context, pos ->
                previousRuleActivated(context, "property_name") &&
                lookAhead(context, pos, 1) == ":"
            },
            action = { context, pos ->
                parsePropertyType(context, pos)
            }
        ),
        
        // Rule 4: Property initializer
        createForwardRule(
            id = "property_initializer",
            weight = 0.7,
            window = 15,
            condition = { context, pos ->
                previousRuleActivated(context, "property_name") &&
                lookAhead(context, pos, 3).contains("=")
            },
            action = { context, pos ->
                parsePropertyInitializer(context, pos)
            }
        ),
        
        // Rule 5: Property accessors (get/set)
        createForwardRule(
            id = "property_accessors",
            weight = 0.85,
            window = 20,
            condition = { context, pos ->
                previousRuleActivated(context, "property_name") &&
                (lookAhead(context, pos, 10).contains("get()") || 
                 lookAhead(context, pos, 10).contains("set("))
            },
            action = { context, pos ->
                parsePropertyAccessors(context, pos)
            }
        )
    ).toSeries()
}

/**
 * Backward Chaining Rules - Right-to-left parsing with lookbehind
 */
object BackwardChainRules {
    
    /**
     * Context Validation Backward Chain
     */
    fun contextValidationChain(): BackwardRuleSeries = listOf(
        // Rule 1: Validate class context
        createBackwardRule(
            id = "validate_class_context",
            weight = 0.9,
            window = 10,
            condition = { context, pos ->
                lookBehind(context, pos, 10).contains("class") &&
                currentEntity(context, pos) == EntityToken.REGULAR_CLASS
            },
            action = { context, pos ->
                validateClassContext(context, pos)
            }
        ),
        
        // Rule 2: Validate function scope
        createBackwardRule(
            id = "validate_function_scope",
            weight = 0.85,
            window = 15,
            condition = { context, pos ->
                isInFunctionBody(context, pos) &&
                hasMatchingBraces(context, pos)
            },
            action = { context, pos ->
                validateFunctionScope(context, pos)
            }
        ),
        
        // Rule 3: Validate property context
        createBackwardRule(
            id = "validate_property_context",
            weight = 0.8,
            window = 8,
            condition = { context, pos ->
                currentEntity(context, pos) == EntityToken.PROPERTY &&
                hasValidPropertyDeclaration(context, pos)
            },
            action = { context, pos ->
                validatePropertyContext(context, pos)
            }
        ),
        
        // Rule 4: Validate import statements
        createBackwardRule(
            id = "validate_imports",
            weight = 0.95,
            window = 20,
            condition = { context, pos ->
                lookBehind(context, pos, 6) == "import" &&
                isValidImportPath(context, pos)
            },
            action = { context, pos ->
                validateImportStatement(context, pos)
            }
        ),
        
        // Rule 5: Validate annotation usage
        createBackwardRule(
            id = "validate_annotations",
            weight = 0.9,
            window = 12,
            condition = { context, pos ->
                lookBehind(context, pos, 1) == "@" &&
                isValidAnnotation(context, pos)
            },
            action = { context, pos ->
                validateAnnotationUsage(context, pos)
            }
        )
    ).toSeries()
    
    /**
     * Type Resolution Backward Chain
     */
    fun typeResolutionChain(): BackwardRuleSeries = listOf(
        // Rule 1: Resolve generic types
        createBackwardRule(
            id = "resolve_generics",
            weight = 0.85,
            window = 25,
            condition = { context, pos ->
                hasGenericParameters(context, pos) &&
                canResolveTypeParameters(context, pos)
            },
            action = { context, pos ->
                resolveGenericTypes(context, pos)
            }
        ),
        
        // Rule 2: Resolve inheritance hierarchy
        createBackwardRule(
            id = "resolve_inheritance",
            weight = 0.9,
            window = 30,
            condition = { context, pos ->
                hasInheritanceClause(context, pos) &&
                canResolveParentTypes(context, pos)
            },
            action = { context, pos ->
                resolveInheritanceHierarchy(context, pos)
            }
        ),
        
        // Rule 3: Resolve function return types
        createBackwardRule(
            id = "resolve_return_types",
            weight = 0.8,
            window = 15,
            condition = { context, pos ->
                isInFunctionDeclaration(context, pos) &&
                hasExplicitReturnType(context, pos)
            },
            action = { context, pos ->
                resolveFunctionReturnType(context, pos)
            }
        ),
        
        // Rule 4: Resolve variable types
        createBackwardRule(
            id = "resolve_variable_types",
            weight = 0.75,
            window = 10,
            condition = { context, pos ->
                isVariableDeclaration(context, pos) &&
                (hasExplicitType(context, pos) || canInferType(context, pos))
            },
            action = { context, pos ->
                resolveVariableType(context, pos)
            }
        ),
        
        // Rule 5: Resolve lambda types
        createBackwardRule(
            id = "resolve_lambda_types",
            weight = 0.7,
            window = 20,
            condition = { context, pos ->
                isLambdaExpression(context, pos) &&
                hasLambdaContext(context, pos)
            },
            action = { context, pos ->
                resolveLambdaType(context, pos)
            }
        )
    ).toSeries()
}

/**
 * Bidirectional Chaining Engine
 */
object ChainedParserEngine {
    
    /**
     * Execute forward chaining rules
     */
    fun executeForwardChain(
        context: ParseContext,
        rules: ForwardRuleSeries,
        startPos: Int = 0
    ): ParseContext {
        var currentContext = context
        var pos = startPos
        
        while (pos < context.sourceLength) {
            // Apply all forward rules at current position
            rules.play.forEach { rule ->
                val (ruleData, chainData) = rule
                val (ruleId, weightAndEvaluator) = ruleData
                val (weight, evaluator) = weightAndEvaluator
                val (direction, window) = chainData
                
                if ((direction.direction and ChainDirection.FORWARD) != 0u.toUByte()) {
                    val activation = evaluator(currentContext)
                    if (activation.activation > 0.5) {
                        currentContext = applyRule(currentContext, ruleId j weight, pos)
                    }
                }
            }
            pos++
        }
        
        return currentContext
    }
    
    /**
     * Execute backward chaining rules
     */
    fun executeBackwardChain(
        context: ParseContext,
        rules: BackwardRuleSeries,
        startPos: Int? = null
    ): ParseContext {
        var currentContext = context
        var pos = startPos ?: (context.sourceLength - 1)
        
        while (pos >= 0) {
            // Apply all backward rules at current position
            rules.play.forEach { rule ->
                val (ruleData, chainData) = rule
                val (ruleId, weightAndEvaluator) = ruleData
                val (weight, evaluator) = weightAndEvaluator
                val (direction, window) = chainData
                
                if ((direction.direction and ChainDirection.BACKWARD) != 0u.toUByte()) {
                    val activation = evaluator(currentContext)
                    if (activation.activation > 0.5) {
                        currentContext = applyRule(currentContext, ruleId j weight, pos)
                    }
                }
            }
            pos--
        }
        
        return currentContext
    }
    
    /**
     * Execute full bidirectional chain
     */
    fun executeBidirectionalChain(
        context: ParseContext,
        forwardRules: ForwardRuleSeries,
        backwardRules: BackwardRuleSeries,
        iterations: Int = 3
    ): ParseContext {
        var currentContext = context
        
        repeat(iterations) {
            // Forward pass
            currentContext = executeForwardChain(currentContext, forwardRules)
            
            // Backward pass for validation and type resolution
            currentContext = executeBackwardChain(currentContext, backwardRules)
        }
        
        return currentContext
    }
}

// ==== RULE HELPER FUNCTIONS ====

/**
 * Create forward chaining rule
 */
internal fun createForwardRule(
    id: String,
    weight: Double,
    window: Int,
    condition: RuleCondition,
    action: RuleAction
): ChainedRule {
    val ruleId = RuleId(id)
    val ruleWeight = RuleWeight(weight)
    val evaluator: (ParseContext) -> RuleActivation = { context ->
        // Simplified evaluator - would be more complex in real implementation
        RuleActivation(if (condition(context, 0)) weight else 0.0)
    }
    
    val rule = ruleId j (ruleWeight j evaluator)
    val direction = ChainDirection(ChainDirection.FORWARD)
    val contextWindow = ContextWindow(window)
    
    return rule j (direction j contextWindow)
}

/**
 * Create backward chaining rule
 */
internal fun createBackwardRule(
    id: String,
    weight: Double,
    window: Int,
    condition: RuleCondition,
    action: RuleAction
): ChainedRule {
    val ruleId = RuleId(id)
    val ruleWeight = RuleWeight(weight)
    val evaluator: (ParseContext) -> RuleActivation = { context ->
        RuleActivation(if (condition(context, 0)) weight else 0.0)
    }
    
    val rule = ruleId j (ruleWeight j evaluator)
    val direction = ChainDirection(ChainDirection.BACKWARD)
    val contextWindow = ContextWindow(window)
    
    return rule j (direction j contextWindow)
}

// ==== PARSING PREDICATES AND ACTIONS ====

// Lookahead/lookbehind functions
internal fun lookAhead(context: ParseContext, pos: Int, length: Int): String = ""
internal fun lookBehind(context: ParseContext, pos: Int, length: Int): String = ""

// Rule activation checks
internal fun previousRuleActivated(context: ParseContext, ruleId: String): Boolean = false
internal fun currentEntity(context: ParseContext, pos: Int): UByte = 0u

// Context validation functions
internal fun isIdentifier(context: ParseContext, pos: Int): Boolean = false
internal fun isInFunctionBody(context: ParseContext, pos: Int): Boolean = false
internal fun hasMatchingBraces(context: ParseContext, pos: Int): Boolean = false
internal fun hasValidPropertyDeclaration(context: ParseContext, pos: Int): Boolean = false
internal fun isValidImportPath(context: ParseContext, pos: Int): Boolean = false
internal fun isValidAnnotation(context: ParseContext, pos: Int): Boolean = false

// Type resolution functions
internal fun hasGenericParameters(context: ParseContext, pos: Int): Boolean = false
internal fun canResolveTypeParameters(context: ParseContext, pos: Int): Boolean = false
internal fun hasInheritanceClause(context: ParseContext, pos: Int): Boolean = false
internal fun canResolveParentTypes(context: ParseContext, pos: Int): Boolean = false
internal fun isInFunctionDeclaration(context: ParseContext, pos: Int): Boolean = false
internal fun hasExplicitReturnType(context: ParseContext, pos: Int): Boolean = false
internal fun isVariableDeclaration(context: ParseContext, pos: Int): Boolean = false
internal fun hasExplicitType(context: ParseContext, pos: Int): Boolean = false
internal fun canInferType(context: ParseContext, pos: Int): Boolean = false
internal fun isLambdaExpression(context: ParseContext, pos: Int): Boolean = false
internal fun hasLambdaContext(context: ParseContext, pos: Int): Boolean = false

// Action functions
internal fun markEntity(context: ParseContext, pos: Int, entityType: UByte): ParseContext = context
internal fun captureClassName(context: ParseContext, pos: Int): ParseContext = context
internal fun parseGenericParameters(context: ParseContext, pos: Int): ParseContext = context
internal fun parseInheritance(context: ParseContext, pos: Int): ParseContext = context
internal fun enterClassBody(context: ParseContext, pos: Int): ParseContext = context
internal fun captureFunctionModifiers(context: ParseContext, pos: Int): ParseContext = context
internal fun captureFunctionName(context: ParseContext, pos: Int): ParseContext = context
internal fun parseParameterList(context: ParseContext, pos: Int): ParseContext = context
internal fun parseReturnType(context: ParseContext, pos: Int): ParseContext = context
internal fun capturePropertyName(context: ParseContext, pos: Int): ParseContext = context
internal fun parsePropertyType(context: ParseContext, pos: Int): ParseContext = context
internal fun parsePropertyInitializer(context: ParseContext, pos: Int): ParseContext = context
internal fun parsePropertyAccessors(context: ParseContext, pos: Int): ParseContext = context
internal fun validateClassContext(context: ParseContext, pos: Int): ParseContext = context
internal fun validateFunctionScope(context: ParseContext, pos: Int): ParseContext = context
internal fun validatePropertyContext(context: ParseContext, pos: Int): ParseContext = context
internal fun validateImportStatement(context: ParseContext, pos: Int): ParseContext = context
internal fun validateAnnotationUsage(context: ParseContext, pos: Int): ParseContext = context
internal fun resolveGenericTypes(context: ParseContext, pos: Int): ParseContext = context
internal fun resolveInheritanceHierarchy(context: ParseContext, pos: Int): ParseContext = context
internal fun resolveFunctionReturnType(context: ParseContext, pos: Int): ParseContext = context
internal fun resolveVariableType(context: ParseContext, pos: Int): ParseContext = context
internal fun resolveLambdaType(context: ParseContext, pos: Int): ParseContext = context
internal fun applyRule(context: ParseContext, rule: Join<RuleId, RuleWeight>, pos: Int): ParseContext = context

// Context extension
internal val ParseContext.sourceLength: Int get() = 0

/**
 * Comprehensive rule chains for aggressive Kotlin parsing
 */
object KotlinChainedParser {
    
    /**
     * Parse with full bidirectional chaining
     */
    fun parseWithChains(source: KotlinSourceCode): Join<GraphNodeSeries, RefinementSeries> {
        val context = createParseContext(source)
        
        // Build rule chains
        val forwardRules = buildForwardRuleChains()
        val backwardRules = buildBackwardRuleChains()
        
        // Execute bidirectional parsing
        val finalContext = ChainedParserEngine.executeBidirectionalChain(
            context = context,
            forwardRules = forwardRules,
            backwardRules = backwardRules,
            iterations = 5
        )
        
        // Convert to graph nodes and refinements
        return convertToGraphNodes(finalContext) j convertToRefinements(finalContext)
    }
    
    internal fun buildForwardRuleChains(): ForwardRuleSeries {
        val allRules = mutableListOf<ChainedRule>()
        allRules.addAll(ForwardChainRules.classDeclarationChain().play)
        allRules.addAll(ForwardChainRules.functionDeclarationChain().play)
        allRules.addAll(ForwardChainRules.propertyDeclarationChain().play)
        // --- Trikeshed additive rules ---
        allRules.addAll(borg.trikeshed.TrikeshedForwardRules.all().play)
        return allRules.toSeries()
    }
    
    internal fun buildBackwardRuleChains(): BackwardRuleSeries {
        val allRules = mutableListOf<ChainedRule>()
        allRules.addAll(BackwardChainRules.contextValidationChain().play)
        allRules.addAll(BackwardChainRules.typeResolutionChain().play)
        // --- Trikeshed additive rules ---
        allRules.addAll(borg.trikeshed.TrikeshedBackwardRules.all().play)
        return allRules.toSeries()
    }
    
    internal fun createParseContext(source: String): ParseContext = 
        ParsePosition(0) j (source j emptySeries<ParseState>())
    
    internal fun convertToGraphNodes(context: ParseContext): GraphNodeSeries = emptySeries()
    internal fun convertToRefinements(context: ParseContext): RefinementSeries = emptySeries()
}

/**
 * Extension for easy chained parsing
 */
fun KotlinSourceCode.parseWithChains(): Join<GraphNodeSeries, RefinementSeries> =
    KotlinChainedParser.parseWithChains(this)