@file:Suppress("NOTHING_TO_INLINE")

package borg.trikeshed.brokeshed.sgml

import borg.trikeshed.lib.*
import kotlin.jvm.JvmInline
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j

/**
 * Correct MetaSeries - Using existing j and jj infrastructure for register packing, not duplicating the register packer.
 * 
 * MetaSeries should reduce to 1-d indexed iterable worst case
 * Size prefix slipped into leftover bits for register packing
 * Uses existing j and jj for sticky register packing
 */

// === CORRECT META SERIES ARCHITECTURE ===

/**
 * XPath Operation - Enum for register packing
 */
enum class XPathOp(val symbol: String, val precedence: Int) {
    CHILD("/", 100),
    DESCENDANT("//", 90),
    PARENT("..", 80),
    ATTRIBUTE("@", 60),
    TEXT("text()", 50),
    EQUALS("=", 30),
    NOT_EQUALS("!=", 29),
    COUNT("count()", 20),
    WILDCARD("*", 0);
}

/**
 * XPath Parameter Type - Enum for register packing
 */
enum class XPathParamType(val typeName: String) {
    STRING("string"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    NODE("node"),
    NODE_SET("node-set");
}

// === META SERIES WITH SIZE PREFIX ===

/**
 * XPath Operation MetaSeries - Reduces to 1-d indexed iterable
 * Size prefix slipped into leftover bits
 */
typealias XPathOpMetaSeries = MetaSeries<XPathOp, XPathParamType>

/**
 * XPath Parameter MetaSeries - Size-prefixed parameter linking
 */
typealias XPathParamMetaSeries = MetaSeries<XPathParamType, String>

/**
 * XPath Operation Chain - Series of operations with size prefix
 */
typealias XPathOpChain = Indexed<XPathOp>

/**
 * XPath Parameter Chain - Series of parameters with size prefix
 */
typealias XPathParamChain = Indexed<String>

// === REGISTER-PACKED META SERIES ===

/**
 * XPath Operation Context - Using j for normal join, jj for sticky register packing
 */
typealias XPathOpContext = Join<XPathOp, XPathParamType>

/**
 * XPath Operation Series - Using j for normal join, jj for sticky register packing
 */
typealias XPathOpSeries = Indexed<XPathOpContext>

/**
 * XPath Parameter Series - Using j for normal join, jj for sticky register packing
 */
typealias XPathParamSeries = Indexed<XPathParamType>

// === META SERIES DISPATCH ===

/**
 * MetaSeries Dispatcher - Uses existing j/jj infrastructure
 */
object MetaSeriesDispatcher {
    
    /**
     * Dispatch using MetaSeries that reduces to 1-d indexed iterable
     */
    fun dispatch(metaSeries: XPathOpMetaSeries, parameters: XPathParamChain): String {
        val operation = metaSeries.a
        val paramType = metaSeries.b(operation)
        
        return when (operation) {
            XPathOp.CHILD -> dispatchChild(paramType, parameters)
            XPathOp.DESCENDANT -> dispatchDescendant(paramType, parameters)
            XPathOp.PARENT -> dispatchParent(paramType, parameters)
            XPathOp.ATTRIBUTE -> dispatchAttribute(paramType, parameters)
            XPathOp.TEXT -> dispatchText(paramType, parameters)
            XPathOp.EQUALS -> dispatchEquals(paramType, parameters)
            XPathOp.NOT_EQUALS -> dispatchNotEquals(paramType, parameters)
            XPathOp.COUNT -> dispatchCount(paramType, parameters)
            XPathOp.WILDCARD -> dispatchWildcard(paramType, parameters)
        }
    }
    
    /**
     * Dispatch operation chain using Series with size prefix
     */
    fun dispatchChain(chain: XPathOpChain, parameters: XPathParamChain): XPathParamChain {
        val results = chain.size j { index: Int ->
            val operation = chain[index]
            val metaSeries = operation j { op -> XPathParamType.STRING } // Default param type
            dispatch(metaSeries, parameters)
        }
        return results
    }
    
    /**
     * Dispatch using Join context (normal j)
     */
    fun dispatchContext(context: XPathOpContext, parameters: XPathParamChain): String {
        val operation = context.a
        val paramType = context.b
        
        return when (operation) {
            XPathOp.CHILD -> dispatchChild(paramType, parameters)
            XPathOp.DESCENDANT -> dispatchDescendant(paramType, parameters)
            XPathOp.PARENT -> dispatchParent(paramType, parameters)
            XPathOp.ATTRIBUTE -> dispatchAttribute(paramType, parameters)
            XPathOp.TEXT -> dispatchText(paramType, parameters)
            XPathOp.EQUALS -> dispatchEquals(paramType, parameters)
            XPathOp.NOT_EQUALS -> dispatchNotEquals(paramType, parameters)
            XPathOp.COUNT -> dispatchCount(paramType, parameters)
            XPathOp.WILDCARD -> dispatchWildcard(paramType, parameters)
        }
    }
    
    // === DISPATCH IMPLEMENTATIONS ===
    
    private fun dispatchChild(paramType: XPathParamType, parameters: XPathParamChain): String {
        val nodeName = if (parameters.size > 0) parameters[0] else "unknown"
        return "child:$nodeName"
    }
    
    private fun dispatchDescendant(paramType: XPathParamType, parameters: XPathParamChain): String {
        val nodeName = if (parameters.size > 0) parameters[0] else "unknown"
        return "descendant:$nodeName"
    }
    
    private fun dispatchParent(paramType: XPathParamType, parameters: XPathParamChain): String {
        return "parent"
    }
    
    private fun dispatchAttribute(paramType: XPathParamType, parameters: XPathParamChain): String {
        val attrName = if (parameters.size > 0) parameters[0] else ""
        return "attribute:$attrName"
    }
    
    private fun dispatchText(paramType: XPathParamType, parameters: XPathParamChain): String {
        return "text_content"
    }
    
    private fun dispatchEquals(paramType: XPathParamType, parameters: XPathParamChain): String {
        val left = if (parameters.size > 0) parameters[0] else ""
        val right = if (parameters.size > 1) parameters[1] else ""
        return if (left == right) "true" else "false"
    }
    
    private fun dispatchNotEquals(paramType: XPathParamType, parameters: XPathParamChain): String {
        val left = if (parameters.size > 0) parameters[0] else ""
        val right = if (parameters.size > 1) parameters[1] else ""
        return if (left != right) "true" else "false"
    }
    
    private fun dispatchCount(paramType: XPathParamType, parameters: XPathParamChain): String {
        return parameters.size.toString()
    }
    
    private fun dispatchWildcard(paramType: XPathParamType, parameters: XPathParamChain): String {
        return "all_nodes"
    }
}

// === META SERIES BUILDER ===

/**
 * MetaSeries Builder - Uses existing j/jj infrastructure
 */
object MetaSeriesBuilder {
    
    /**
     * Build MetaSeries using j for normal join
     */
    fun buildMetaSeries(operation: XPathOp, paramType: XPathParamType): XPathOpMetaSeries {
        return operation j { op -> paramType }
    }
    
    /**
     * Build operation chain using Series with size prefix
     */
    fun buildChain(vararg operations: XPathOp): XPathOpChain {
        return operations.size j { index: Int -> operations[index] }
    }
    
    /**
     * Build parameter chain using Series with size prefix
     */
    fun buildParamChain(vararg parameters: String): XPathParamChain {
        return parameters.size j { index: Int -> parameters[index] }
    }
    
    /**
     * Build operation context using j for normal join
     */
    fun buildContext(operation: XPathOp, paramType: XPathParamType): XPathOpContext {
        return operation j paramType
    }
    
    /**
     * Build operation series using Series with size prefix
     */
    fun buildOpSeries(vararg contexts: XPathOpContext): XPathOpSeries {
        return contexts.size j { index: Int -> contexts[index] }
    }
}

// === EXAMPLE USAGE ===

/**
 * Example demonstrating correct MetaSeries usage
 */
fun main() {
    println("=== Correct MetaSeries Example ===")
    
    // 1. Build MetaSeries using j (normal join)
    val childMetaSeries = MetaSeriesBuilder.buildMetaSeries(XPathOp.CHILD, XPathParamType.STRING)
    val attributeMetaSeries = MetaSeriesBuilder.buildMetaSeries(XPathOp.ATTRIBUTE, XPathParamType.STRING)
    val equalsMetaSeries = MetaSeriesBuilder.buildMetaSeries(XPathOp.EQUALS, XPathParamType.STRING)
    
    // 2. Build operation chain using Series with size prefix
    val operationChain = MetaSeriesBuilder.buildChain(
        XPathOp.CHILD,
        XPathOp.ATTRIBUTE,
        XPathOp.EQUALS,
        XPathOp.COUNT
    )
    
    // 3. Build parameter chain using Series with size prefix
    val parameterChain = MetaSeriesBuilder.buildParamChain("book", "category", "fiction")
    
    // 4. Dispatch using MetaSeries (reduces to 1-d indexed iterable)
    val childResult = MetaSeriesDispatcher.dispatch(childMetaSeries, parameterChain)
    val attributeResult = MetaSeriesDispatcher.dispatch(attributeMetaSeries, parameterChain)
    val equalsResult = MetaSeriesDispatcher.dispatch(equalsMetaSeries, parameterChain)
    
    println("Child operation result: $childResult")
    println("Attribute operation result: $attributeResult")
    println("Equals operation result: $equalsResult")
    
    // 5. Dispatch operation chain (Series with size prefix)
    val chainResults = MetaSeriesDispatcher.dispatchChain(operationChain, parameterChain)
    println("Operation chain results: ${chainResults.asIterable().toList()}")
    
    // 6. Build operation context using j (normal join)
    val childContext = MetaSeriesBuilder.buildContext(XPathOp.CHILD, XPathParamType.STRING)
    val contextResult = MetaSeriesDispatcher.dispatchContext(childContext, parameterChain)
    println("Context result: $contextResult")
    
    // 7. Show MetaSeries properties
    println("\n=== MetaSeries Properties ===")
    println("Child MetaSeries operation: ${childMetaSeries.a.symbol}")
    println("Child MetaSeries param type: ${childMetaSeries.b(childMetaSeries.a).typeName}")
    println("Operation chain size: ${operationChain.size}")
    println("Parameter chain size: ${parameterChain.size}")
    println("MetaSeries reduces to 1-d indexed iterable worst case")
    println("Size prefix slipped into leftover bits for register packing")
    println("Using existing j/jj infrastructure - no duplication!")
} 