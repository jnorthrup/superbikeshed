// TEMPORARILY DISABLED - FIXING COMPILATION ERRORS
@file:Suppress("NOTHING_TO_INLINE")

package borg.trikeshed.brokeshed.sgml

import borg.trikeshed.lib.*
import kotlin.jvm.JvmInline

/**
 * Enumerated Parameterized Dispatch - Enum-driven parameterized functions
 * 
 * Shows how to use enums to drive parameterized dispatch while maintaining
 * j packing benefits and type safety
 */

// === ENUM-DRIVEN PARAMETERIZATION ===

/**
 * XPath Operation Types - Enum for parameterized dispatch
 */
enum class XPathOperation(val symbol: String, val precedence: Int) {
    // Navigation operations
    CHILD("/", 100),
    DESCENDANT("//", 90),
    PARENT("..", 80),
    ANCESTOR("ancestor::", 70),
    
    // Selection operations
    ATTRIBUTE("@", 60),
    TEXT("text()", 50),
    COMMENT("comment()", 40),
    
    // Predicate operations
    EQUALS("=", 30),
    NOT_EQUALS("!=", 29),
    LESS_THAN("<", 28),
    GREATER_THAN(">", 27),
    
    // Function operations
    COUNT("count()", 20),
    SUM("sum()", 19),
    AVG("avg()", 18),
    
    // Special operations
    WILDCARD("*", 0),
    CURRENT(".", 45);
    
    companion object {
        fun fromSymbol(symbol: String): XPathOperation? = 
            values().find { it.symbol == symbol }
    }
}

/**
 * XPath Parameter Types - Enum for type-safe parameterization
 */
enum class XPathParameterType(val typeName: String, val defaultValue: Any?) {
    STRING("string", ""),
    NUMBER("number", 0),
    BOOLEAN("boolean", false),
    NODE("node", null),
    NODE_SET("node-set", emptyList<Any>()),
    FUNCTION("function", null);
    
    companion object {
        fun fromTypeName(typeName: String): XPathParameterType? = 
            values().find { it.typeName == typeName }
    }
}

// === PARAMETERIZED DISPATCH SYSTEM ===

/**
 * XPath Parameter - Inline class for type-safe parameterization
 */
@JvmInline
value class XPathParameter<T>(val data: Join<T, XPathParameterType>)

/**
 * XPath Operation Context - MetaSeries for parameterized operations
 */
typealias XPathOperationContext = Join<XPathOperation, XPathParameterIndexed>

/**
 * XPath Parameter Series - MetaSeries for parameter linking
 */
typealias XPathParameterIndexed = Indexed<XPathParameter<*>>

/**
 * XPath Operation Result - Parameterized result type
 */
typealias XPathOperationResult<T> = Join<XPathParameterType, T>

// === ENUMERATED PARAMETERIZED DISPATCH ===

/**
 * Enumerated Parameterized Dispatcher - Uses enums to drive parameterized functions
 */
object EnumeratedParameterizedDispatcher {
    
    /**
     * Parameterized dispatch using enum-driven selection
     */
    fun <T> dispatch(
        operation: XPathOperation,
        parameters: XPathParameterIndexed
    ): XPathOperationResult<T> {
        return when (operation) {
            // Navigation operations - parameterized by node type
            XPathOperation.CHILD -> dispatchChild<T>(parameters)
            XPathOperation.DESCENDANT -> dispatchDescendant<T>(parameters)
            XPathOperation.PARENT -> dispatchParent<T>(parameters)
            XPathOperation.ANCESTOR -> dispatchAncestor<T>(parameters)
            
            // Selection operations - parameterized by attribute/text type
            XPathOperation.ATTRIBUTE -> dispatchAttribute<T>(parameters)
            XPathOperation.TEXT -> dispatchText<T>(parameters)
            XPathOperation.COMMENT -> dispatchComment<T>(parameters)
            
            // Predicate operations - parameterized by comparison type
            XPathOperation.EQUALS -> dispatchEquals<T>(parameters)
            XPathOperation.NOT_EQUALS -> dispatchNotEquals<T>(parameters)
            XPathOperation.LESS_THAN -> dispatchLessThan<T>(parameters)
            XPathOperation.GREATER_THAN -> dispatchGreaterThan<T>(parameters)
            
            // Function operations - parameterized by aggregation type
            XPathOperation.COUNT -> dispatchCount<T>(parameters)
            XPathOperation.SUM -> dispatchSum<T>(parameters)
            XPathOperation.AVG -> dispatchAvg<T>(parameters)
            
            // Special operations - parameterized by context
            XPathOperation.WILDCARD -> dispatchWildcard<T>(parameters)
            XPathOperation.CURRENT -> dispatchCurrent<T>(parameters)
        }
    }
    
    /**
     * Type-safe parameter extraction with enum validation
     */
    inline fun <reified T> extractParameter(
        parameters: XPathParameterIndexed,
        expectedType: XPathParameterType,
        index: Int = 0
    ): T? {
        return if (index < parameters.size) {
            val param = parameters[index]
            if (param.data.b == expectedType && param.data.a is T) {
                param.data.a as T
            } else {
                null
            }
        } else {
            null
        }
    }
    
    // === ENUM-DRIVEN DISPATCH IMPLEMENTATIONS ===
    
    internal fun <T> dispatchChild(parameters: XPathParameterIndexed): XPathOperationResult<T> {
        val nodeName = extractParameter<String>(parameters, XPathParameterType.STRING)
        @Suppress("UNCHECKED_CAST")
        return (XPathParameterType.NODE j (nodeName ?: "unknown")) as XPathOperationResult<T>
    }
    
    internal fun <T> dispatchDescendant(parameters: XPathParameterIndexed): XPathOperationResult<T> {
        val nodeName = extractParameter<String>(parameters, XPathParameterType.STRING)
        @Suppress("UNCHECKED_CAST")
        return (XPathParameterType.NODE_SET j (listOf(nodeName ?: "unknown"))) as XPathOperationResult<T>
    }
    
    internal fun <T> dispatchParent(parameters: XPathParameterIndexed): XPathOperationResult<T> {
        @Suppress("UNCHECKED_CAST")
        return (XPathParameterType.NODE j "parent") as XPathOperationResult<T>
    }
    
    internal fun <T> dispatchAncestor(parameters: XPathParameterIndexed): XPathOperationResult<T> {
        val level = extractParameter<Int>(parameters, XPathParameterType.NUMBER, 0) ?: 1
        @Suppress("UNCHECKED_CAST")
        return (XPathParameterType.NODE j "ancestor_level_$level") as XPathOperationResult<T>
    }
    
    internal fun <T> dispatchAttribute(parameters: XPathParameterIndexed): XPathOperationResult<T> {
        val attrName = extractParameter<String>(parameters, XPathParameterType.STRING)
        @Suppress("UNCHECKED_CAST")
        return (XPathParameterType.STRING j (attrName ?: "")) as XPathOperationResult<T>
    }
    
    internal fun <T> dispatchText(parameters: XPathParameterIndexed): XPathOperationResult<T> {
        @Suppress("UNCHECKED_CAST")
        return (XPathParameterType.STRING j "text_content") as XPathOperationResult<T>
    }
    
    internal fun <T> dispatchComment(parameters: XPathParameterIndexed): XPathOperationResult<T> {
        @Suppress("UNCHECKED_CAST")
        return (XPathParameterType.STRING j "comment_content") as XPathOperationResult<T>
    }
    
    internal fun <T> dispatchEquals(parameters: XPathParameterIndexed): XPathOperationResult<T> {
        val left = extractParameter<Any>(parameters, XPathParameterType.STRING, 0)
        val right = extractParameter<Any>(parameters, XPathParameterType.STRING, 1)
        @Suppress("UNCHECKED_CAST")
        return (XPathParameterType.BOOLEAN j (left == right)) as XPathOperationResult<T>
    }
    
    internal fun <T> dispatchNotEquals(parameters: XPathParameterIndexed): XPathOperationResult<T> {
        val left = extractParameter<Any>(parameters, XPathParameterType.STRING, 0)
        val right = extractParameter<Any>(parameters, XPathParameterType.STRING, 1)
        @Suppress("UNCHECKED_CAST")
        return (XPathParameterType.BOOLEAN j (left != right)) as XPathOperationResult<T>
    }
    
    internal fun <T> dispatchLessThan(parameters: XPathParameterIndexed): XPathOperationResult<T> {
        val left = extractParameter<Number>(parameters, XPathParameterType.NUMBER, 0)?.toDouble() ?: 0.0
        val right = extractParameter<Number>(parameters, XPathParameterType.NUMBER, 1)?.toDouble() ?: 0.0
        @Suppress("UNCHECKED_CAST")
        return (XPathParameterType.BOOLEAN j (left < right)) as XPathOperationResult<T>
    }
    
    internal fun <T> dispatchGreaterThan(parameters: XPathParameterIndexed): XPathOperationResult<T> {
        val left = extractParameter<Number>(parameters, XPathParameterType.NUMBER, 0)?.toDouble() ?: 0.0
        val right = extractParameter<Number>(parameters, XPathParameterType.NUMBER, 1)?.toDouble() ?: 0.0
        @Suppress("UNCHECKED_CAST")
        return (XPathParameterType.BOOLEAN j (left > right)) as XPathOperationResult<T>
    }
    
    internal fun <T> dispatchCount(parameters: XPathParameterIndexed): XPathOperationResult<T> {
        val nodeSet = extractParameter<List<*>>(parameters, XPathParameterType.NODE_SET)
        @Suppress("UNCHECKED_CAST")
        return (XPathParameterType.NUMBER j (nodeSet?.size ?: 0)) as XPathOperationResult<T>
    }
    
    internal fun <T> dispatchSum(parameters: XPathParameterIndexed): XPathOperationResult<T> {
        val numbers = extractParameter<List<Number>>(parameters, XPathParameterType.NODE_SET)
        val sum = numbers?.sumOf { it.toDouble() } ?: 0.0
        @Suppress("UNCHECKED_CAST")
        return (XPathParameterType.NUMBER j sum) as XPathOperationResult<T>
    }
    
    internal fun <T> dispatchAvg(parameters: XPathParameterIndexed): XPathOperationResult<T> {
        val numbers = extractParameter<List<Number>>(parameters, XPathParameterType.NODE_SET)
        val avg = if (numbers?.isNotEmpty() == true) {
            numbers.sumOf { it.toDouble() } / numbers.size
        } else {
            0.0
        }
        @Suppress("UNCHECKED_CAST")
        return (XPathParameterType.NUMBER j avg) as XPathOperationResult<T>
    }
    
    internal fun <T> dispatchWildcard(parameters: XPathParameterIndexed): XPathOperationResult<T> {
        @Suppress("UNCHECKED_CAST")
        return (XPathParameterType.NODE_SET j listOf("all_nodes")) as XPathOperationResult<T>
    }
    
    internal fun <T> dispatchCurrent(parameters: XPathParameterIndexed): XPathOperationResult<T> {
        @Suppress("UNCHECKED_CAST")
        return (XPathParameterType.NODE j "current_context") as XPathOperationResult<T>
    }
}

// === ENUMERATED PARAMETERIZED BUILDER ===

/**
 * Enumerated Parameterized Builder - Uses enums to build parameterized operations
 */
object EnumeratedParameterizedBuilder {
    
    /**
     * Build parameterized operation using enum-driven construction
     */
    fun buildOperation(
        operation: XPathOperation,
        vararg parameters: Pair<XPathParameterType, Any>
    ): XPathOperationContext {
        val parameterSeries = parameters.size j { index ->
            val (type, value) = parameters[index]
            XPathParameter(value j type)
        }
        return operation j parameterSeries
    }
    
    /**
     * Build operation from string using enum lookup
     */
    fun buildOperationFromString(
        operationSymbol: String,
        vararg parameters: Pair<XPathParameterType, Any>
    ): XPathOperationContext? {
        val operation = XPathOperation.fromSymbol(operationSymbol)
        return operation?.let { buildOperation(it, *parameters) }
    }
    
    /**
     * Build operation chain using enum precedence
     */
    fun buildOperationChain(
        operations: XPathOperationIndexed
    ): XPathOperationIndexed {
        // Sort by enum precedence for proper evaluation order
        return operations.play
            .sortedBy { it.operation.precedence }
            .toIndexed()
    }
}

// === META SERIES FOR ENUMERATED PARAMETERIZATION ===

/**
 * XPath Operation Series - MetaSeries for operation linking
 */
typealias XPathOperationIndexed = Indexed<XPathOperationContext>

/**
 * XPath Operation Chain - MetaSeries for operation composition
 */
typealias XPathOperationChain = Join<XPathOperationIndexed, XPathParameterIndexed>

// === EXAMPLE USAGE ===

/**
 * Example demonstrating enumerated parameterized dispatch
 */
fun main() {
    println("=== Enumerated Parameterized Dispatch Example ===")
    
    // 1. Build parameterized operations using enums
    val childOp = EnumeratedParameterizedBuilder.buildOperation(
        XPathOperation.CHILD,
        XPathParameterType.STRING to "book"
    )
    
    val attributeOp = EnumeratedParameterizedBuilder.buildOperation(
        XPathOperation.ATTRIBUTE,
        XPathParameterType.STRING to "category"
    )
    
    val equalsOp = EnumeratedParameterizedBuilder.buildOperation(
        XPathOperation.EQUALS,
        XPathParameterType.STRING to "fiction",
        XPathParameterType.STRING to "fiction"
    )
    
    // 2. Dispatch operations using enum-driven parameterization
    val childResult = EnumeratedParameterizedDispatcher.dispatch<Any>(childOp.a, childOp.b)
    val attributeResult = EnumeratedParameterizedDispatcher.dispatch<Any>(attributeOp.a, attributeOp.b)
    val equalsResult = EnumeratedParameterizedDispatcher.dispatch<Any>(equalsOp.a, equalsOp.b)
    
    println("Child operation result: $childResult")
    println("Attribute operation result: $attributeResult")
    println("Equals operation result: $equalsResult")
    
    // 3. Build operation chain using enum precedence
    val operations = 3 j { index ->
        when (index) {
            0 -> childOp
            1 -> attributeOp
            2 -> equalsOp
            else -> childOp
        }
    }
    
    val operationChain = EnumeratedParameterizedBuilder.buildOperationChain(operations)
    println("Operation chain (sorted by precedence): ${operationChain.play.map { it.a.symbol }}")
    
    // 4. String-based operation building
    val stringOp = EnumeratedParameterizedBuilder.buildOperationFromString(
        "/",
        XPathParameterType.STRING to "bookstore"
    )
    println("String-based operation: $stringOp")
} 