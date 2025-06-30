package nexus.flow

import borg.trikeshed.lib.*

/**
 * Type-safe prompt templating system for Nexus
 * 
 * Design principles:
 * - Compile-time safety for prompt variables
 * - Composable prompt fragments
 * - Context-aware prompt generation
 * - Template inheritance and specialization
 */

// Core prompt types
sealed interface PromptElement {
    fun render(context: PromptContext): String
}

data class TextElement(val text: String) : PromptElement {
    override fun render(context: PromptContext) = text
}

data class VariableElement<T>(
    val name: String,
    val type: VariableType<T>,
    val formatter: (T) -> String = { it.toString() }
) : PromptElement {
    override fun render(context: PromptContext): String {
        val value = context.get(name, type) 
            ?: throw IllegalArgumentException("Missing required variable: $name")
        return formatter(value)
    }
}

data class ConditionalElement(
    val condition: (PromptContext) -> Boolean,
    val ifTrue: PromptElement,
    val ifFalse: PromptElement? = null
) : PromptElement {
    override fun render(context: PromptContext): String = 
        if (condition(context)) ifTrue.render(context)
        else ifFalse?.render(context) ?: ""
}

data class LoopElement<T>(
    val items: (PromptContext) -> Indexed<T>,
    val template: PromptTemplate<T>,
    val separator: String = "\n"
) : PromptElement {
    override fun render(context: PromptContext): String {
        val itemList = items(context)
        return itemList.mapIndexed { index, item ->
            val itemContext = context.withLocal("item" to item, "index" to index)
            template.render(itemContext)
        }.joinToString(separator)
    }
}

data class IncludeElement(
    val template: PromptTemplate<*>,
    val contextTransform: (PromptContext) -> PromptContext = { it }
) : PromptElement {
    override fun render(context: PromptContext): String = 
        template.render(contextTransform(context))
}

// Variable type system
sealed interface VariableType<T> {
    fun parse(value: Any?): T?
    fun validate(value: Any?): Boolean
}

object StringType : VariableType<String> {
    override fun parse(value: Any?) = value?.toString()
    override fun validate(value: Any?) = value is String
}

object IntType : VariableType<Int> {
    override fun parse(value: Any?) = when (value) {
        is Int -> value
        is String -> value.toIntOrNull()
        else -> null
    }
    override fun validate(value: Any?) = parse(value) != null
}

object BooleanType : VariableType<Boolean> {
    override fun parse(value: Any?) = when (value) {
        is Boolean -> value
        is String -> value.toBooleanStrictOrNull()
        else -> null
    }
    override fun validate(value: Any?) = parse(value) != null
}

data class ListType<T>(val elementType: VariableType<T>) : VariableType<Indexed<T>> {
    override fun parse(value: Any?): Indexed<T>? = when (value) {
        is List<*> -> value.mapNotNull { elementType.parse(it) }.toTypedArray().toSeries()
        is Array<*> -> value.mapNotNull { elementType.parse(it) }.toTypedArray().toSeries()
        else -> null
    }
    override fun validate(value: Any?) = parse(value) != null
}

data class MapType<K, V>(
    val keyType: VariableType<K>,
    val valueType: VariableType<V>
) : VariableType<Map<K, V>> {
    override fun parse(value: Any?): Map<K, V>? = when (value) {
        is Map<*, *> -> {
            value.entries.mapNotNull { (k, v) ->
                val key = keyType.parse(k)
                val value = valueType.parse(v)
                if (key != null && value != null) key to value else null
            }.toMap()
        }
        else -> null
    }
    override fun validate(value: Any?) = parse(value) != null
}

// Context for variable resolution
class PromptContext(
    private val globals: Map<String, Any?> = emptyMap(),
    private val locals: Map<String, Any?> = emptyMap()
) {
    fun <T> get(name: String, type: VariableType<T>): T? {
        val value = locals[name] ?: globals[name]
        return type.parse(value)
    }
    
    fun has(name: String): Boolean = name in locals || name in globals
    
    fun withLocal(vararg pairs: Pair<String, Any?>): PromptContext =
        PromptContext(globals, locals + pairs)
    
    fun withGlobal(vararg pairs: Pair<String, Any?>): PromptContext =
        PromptContext(globals + pairs, locals)
    
    fun merge(other: PromptContext): PromptContext =
        PromptContext(globals + other.globals, locals + other.locals)
}

// Template class
class PromptTemplate<T>(
    private val elements: Indexed<PromptElement>
) {
    fun render(context: PromptContext): String =
        elements.joinToString("") { it.render(context) }
    
    operator fun plus(other: PromptTemplate<T>): PromptTemplate<T> =
        PromptTemplate((elements.toList() + other.elements.toList()).toTypedArray().toSeries())
    
    fun specialize(transform: PromptTemplateBuilder<T>.() -> Unit): PromptTemplate<T> {
        val builder = PromptTemplateBuilder<T>()
        builder.elements.addAll(elements.toList())
        builder.transform()
        return builder.build()
    }
}

// Template builder DSL
class PromptTemplateBuilder<T> {
    internal val elements = mutableListOf<PromptElement>()
    
    fun text(content: String) {
        elements.add(TextElement(content))
    }
    
    fun <V> variable(
        name: String,
        type: VariableType<V>,
        formatter: (V) -> String = { it.toString() }
    ) {
        elements.add(VariableElement(name, type, formatter))
    }
    
    fun conditional(
        condition: (PromptContext) -> Boolean,
        ifTrue: PromptTemplateBuilder<T>.() -> Unit,
        ifFalse: (PromptTemplateBuilder<T>.() -> Unit)? = null
    ) {
        val trueBuilder = PromptTemplateBuilder<T>().apply(ifTrue)
        val falseBuilder = ifFalse?.let { PromptTemplateBuilder<T>().apply(it) }
        
        elements.add(ConditionalElement(
            condition,
            CompoundElement(trueBuilder.elements.toTypedArray().toSeries()),
            falseBuilder?.let { CompoundElement(it.elements.toTypedArray().toSeries()) }
        ))
    }
    
    fun <I> loop(
        items: (PromptContext) -> Indexed<I>,
        separator: String = "\n",
        template: PromptTemplateBuilder<I>.() -> Unit
    ) {
        val itemTemplate = PromptTemplateBuilder<I>().apply(template).build()
        elements.add(LoopElement(items, itemTemplate, separator))
    }
    
    fun include(
        template: PromptTemplate<*>,
        contextTransform: (PromptContext) -> PromptContext = { it }
    ) {
        elements.add(IncludeElement(template, contextTransform))
    }
    
    fun build(): PromptTemplate<T> = PromptTemplate(elements.toTypedArray().toSeries())
}

// Compound element for grouping
data class CompoundElement(
    val children: Indexed<PromptElement>
) : PromptElement {
    override fun render(context: PromptContext): String =
        children.joinToString("") { it.render(context) }
}

// DSL entry point
fun <T> promptTemplate(block: PromptTemplateBuilder<T>.() -> Unit): PromptTemplate<T> =
    PromptTemplateBuilder<T>().apply(block).build()

// Predefined template fragments
object TemplateFragments {
    val systemPrompt = promptTemplate<Unit> {
        text("You are ")
        variable("assistant_name", StringType)
        text(", ")
        variable("assistant_description", StringType)
        text(".")
        
        conditional(
            condition = { it.has("capabilities") },
            ifTrue = {
                text("\n\nYour capabilities include:\n")
                loop<String>(
                    items = { it.get("capabilities", ListType(StringType)) ?: emptyArray<String>().toSeries() },
                    separator = "\n"
                ) {
                    text("- ")
                    variable("item", StringType)
                }
            }
        )
    }
    
    val toolDescription = promptTemplate<ToolCapability> {
        text("Tool: ")
        variable("name", StringType) { tool -> tool }
        text("\nDescription: ")
        variable("description", StringType)
        
        conditional(
            condition = { ctx -> 
                val tool = ctx.get("tool", StringType)
                tool != null && ctx.has("${tool}_parameters")
            },
            ifTrue = {
                text("\nParameters:\n")
                loop<Parameter>(
                    items = { ctx ->
                        val tool = ctx.get("tool", StringType) ?: ""
                        ctx.get("${tool}_parameters", ListType(StringType)) 
                            ?: emptyArray<Parameter>().toSeries()
                    }
                ) {
                    text("  - ")
                    variable("name", StringType)
                    text(" (")
                    variable("type", StringType) { it.toString() }
                    text("): ")
                    variable("description", StringType)
                    conditional(
                        condition = { it.get("required", BooleanType) == false },
                        ifTrue = { text(" [optional]") }
                    )
                }
            }
        )
    }
    
    val conversationHistory = promptTemplate<Unit> {
        loop<ConversationEntry>(
            items = { it.get("history", ListType(StringType)) ?: emptyArray<ConversationEntry>().toSeries() }
        ) {
            variable("role", StringType) { it.uppercase() }
            text(": ")
            variable("content", StringType)
        }
    }
    
    val errorResponse = promptTemplate<Unit> {
        text("I encountered an error: ")
        variable("error_type", StringType)
        text(" - ")
        variable("error_message", StringType)
        
        conditional(
            condition = { it.has("suggestions") },
            ifTrue = {
                text("\n\nSuggestions:\n")
                loop<String>(
                    items = { it.get("suggestions", ListType(StringType)) ?: emptyArray<String>().toSeries() }
                ) {
                    text("- ")
                    variable("item", StringType)
                }
            }
        )
    }
}

// Example specialized templates
object SpecializedTemplates {
    val codeGenerationPrompt = TemplateFragments.systemPrompt.specialize {
        text("\n\nYou are specialized in code generation.")
        text("\nProgramming language: ")
        variable("language", StringType)
        text("\nFramework: ")
        variable("framework", StringType) { it }
        
        conditional(
            condition = { it.has("style_guide") },
            ifTrue = {
                text("\n\nFollow this style guide:\n")
                variable("style_guide", StringType)
            }
        )
    }
    
    val analysisPrompt = promptTemplate<Unit> {
        include(TemplateFragments.systemPrompt)
        text("\n\nAnalysis Task:\n")
        variable("task_description", StringType)
        
        conditional(
            condition = { it.has("data_sources") },
            ifTrue = {
                text("\n\nData Sources:\n")
                loop<String>(
                    items = { it.get("data_sources", ListType(StringType)) ?: emptyArray<String>().toSeries() }
                ) {
                    text("- ")
                    variable("item", StringType)
                }
            }
        )
        
        text("\n\nProvide your analysis with the following structure:\n")
        text("1. Summary\n")
        text("2. Key Findings\n")
        text("3. Recommendations\n")
    }
}

// Conversation entry for history tracking
data class ConversationEntry(
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

// Template validation
class TemplateValidator {
    fun validate(template: PromptTemplate<*>, requiredVars: Set<String>): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Check for required variables
        // Note: This would require traversing the template structure
        // For now, we'll return a simple result
        
        return if (errors.isEmpty()) {
            ValidationResult.Success(warnings)
        } else {
            ValidationResult.Failure(errors, warnings)
        }
    }
}

sealed interface ValidationResult {
    data class Success(val warnings: List<String>) : ValidationResult
    data class Failure(val errors: List<String>, val warnings: List<String>) : ValidationResult
}