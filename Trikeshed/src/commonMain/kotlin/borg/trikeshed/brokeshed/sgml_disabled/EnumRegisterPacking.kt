@file:Suppress("NOTHING_TO_INLINE")

package borg.trikeshed.brokeshed.sgml

import borg.trikeshed.lib.*
import kotlin.jvm.JvmInline

/**
 * Enum Register Packing - Cheap enum packing into registers
 * 
 * Shows how enums in joins can be made very cheap to pack into registers
 * Avoids inefficient 'to' function and uses direct register packing
 */

// === REGISTER-PACKED DISPATCH ===

// NOTE: Register packing logic is deprecated. Use canonical Indexed-based types from CorrectMetaSeries.kt.
// The following code is commented out due to removal of value class logic:
/*
/**
 * Register-Packed Enum Dispatcher - Uses register-packed enums for dispatch
 */
object RegisterPackedDispatcher {
    
    /**
     * Dispatch using register-packed enum context
     */
    fun dispatch(context: XPathOpContext, parameters: Indexed<String>): String {
        return when (context.operation) {
            XPathOp.CHILD -> dispatchChild(context, parameters)
            XPathOp.DESCENDANT -> dispatchDescendant(context, parameters)
            XPathOp.PARENT -> dispatchParent(context, parameters)
            XPathOp.ATTRIBUTE -> dispatchAttribute(context, parameters)
            XPathOp.TEXT -> dispatchText(context, parameters)
            XPathOp.EQUALS -> dispatchEquals(context, parameters)
            XPathOp.NOT_EQUALS -> dispatchNotEquals(context, parameters)
            XPathOp.COUNT -> dispatchCount(context, parameters)
            XPathOp.WILDCARD -> dispatchWildcard(context, parameters)
        }
    }
    
    /**
     * Dispatch operation chain using register-packed enums
     */
    fun dispatchChain(chain: XPathOpChain, parameters: Indexed<String>): Indexed<String> {
        val results = 7 j { index: Int ->
            when (index) {
                0 -> if (chain.op1 != XPathOp.WILDCARD) dispatch(XPathOpContext.pack(chain.op1), parameters) else ""
                1 -> if (chain.op2 != XPathOp.WILDCARD) dispatch(XPathOpContext.pack(chain.op2), parameters) else ""
                2 -> if (chain.op3 != XPathOp.WILDCARD) dispatch(XPathOpContext.pack(chain.op3), parameters) else ""
                3 -> if (chain.op4 != XPathOp.WILDCARD) dispatch(XPathOpContext.pack(chain.op4), parameters) else ""
                4 -> if (chain.op5 != XPathOp.WILDCARD) dispatch(XPathOpContext.pack(chain.op5), parameters) else ""
                5 -> if (chain.op6 != XPathOp.WILDCARD) dispatch(XPathOpContext.pack(chain.op6), parameters) else ""
                6 -> if (chain.op7 != XPathOp.WILDCARD) dispatch(XPathOpContext.pack(chain.op7), parameters) else ""
                else -> ""
            }
        }
        return results.play.filter { it.isNotEmpty() }.toSeries()
    }
    
    // === REGISTER-PACKED DISPATCH IMPLEMENTATIONS ===
    
    internal fun dispatchChild(context: XPathOpContext, parameters: Indexed<String>): String {
        val paramIndex = context.paramIndex
        val nodeName = if (paramIndex < parameters.size) parameters[paramIndex] else "unknown"
        return "child:$nodeName"
    }
    
    internal fun dispatchDescendant(context: XPathOpContext, parameters: Indexed<String>): String {
        val paramIndex = context.paramIndex
        val nodeName = if (paramIndex < parameters.size) parameters[paramIndex] else "unknown"
        return "descendant:$nodeName"
    }
    
    internal fun dispatchParent(context: XPathOpContext, parameters: Indexed<String>): String {
        return "parent"
    }
    
    internal fun dispatchAttribute(context: XPathOpContext, parameters: Indexed<String>): String {
        val paramIndex = context.paramIndex
        val attrName = if (paramIndex < parameters.size) parameters[paramIndex] else ""
        return "attribute:$attrName"
    }
    
    internal fun dispatchText(context: XPathOpContext, parameters: Indexed<String>): String {
        return "text_content"
    }
    
    internal fun dispatchEquals(context: XPathOpContext, parameters: Indexed<String>): String {
        val left = if (context.paramIndex < parameters.size) parameters[context.paramIndex] else ""
        val right = if (context.paramIndex + 1 < parameters.size) parameters[context.paramIndex + 1] else ""
        return if (left == right) "true" else "false"
    }
    
    internal fun dispatchNotEquals(context: XPathOpContext, parameters: Indexed<String>): String {
        val left = if (context.paramIndex < parameters.size) parameters[context.paramIndex] else ""
        val right = if (context.paramIndex + 1 < parameters.size) parameters[context.paramIndex + 1] else ""
        return if (left != right) "true" else "false"
    }
    
    internal fun dispatchCount(context: XPathOpContext, parameters: Indexed<String>): String {
        return parameters.size.toString()
    }
    
    internal fun dispatchWildcard(context: XPathOpContext, parameters: Indexed<String>): String {
        return "all_nodes"
    }
}

// === REGISTER-PACKED BUILDER ===

/**
 * Register-Packed Builder - Uses register-packed enums for construction
 */
object RegisterPackedBuilder {
    
    /**
     * Build register-packed operation context
     */
    fun buildContext(op: XPathOp, paramType: XPathParamType = XPathParamType.STRING, paramIndex: Int = 0): XPathOpContext {
        return XPathOpContext.pack(op, paramType, paramIndex)
    }
    
    /**
     * Build register-packed operation chain
     */
    fun buildChain(vararg ops: XPathOp): XPathOpChain {
        return XPathOpChain.pack(*ops)
    }
    
    /**
     * Build MetaSeries using register-packed enums
     */
    fun buildMetaSeries(context: XPathOpContext, chain: XPathOpChain): XPathOpMetaSeries {
        return context j chain
    }
    
    /**
     * Build parameter MetaSeries using register-packed enums
     */
    fun buildParamMetaSeries(paramType: XPathParamType, parameters: Indexed<String>): XPathParamMetaSeries {
        return paramType j parameters
    }
}

// === EXAMPLE USAGE ===

// NOTE: Register packing logic is deprecated. Use canonical Indexed-based types from CorrectMetaSeries.kt.
// The following code is commented out due to removal of value class logic:
/*
/**
 * Example demonstrating register-packed enum dispatch
 */
fun main() {
    println("=== Register-Packed Enum Dispatch Example ===")
    
    // 1. Build register-packed contexts (no 'to' function!)
    val childContext = RegisterPackedBuilder.buildContext(XPathOp.CHILD, XPathParamType.STRING, 0)
    val attributeContext = RegisterPackedBuilder.buildContext(XPathOp.ATTRIBUTE, XPathParamType.STRING, 1)
    val equalsContext = RegisterPackedBuilder.buildContext(XPathOp.EQUALS, XPathParamType.STRING, 2)
    
    // 2. Build register-packed operation chain
    val operationChain = RegisterPackedBuilder.buildChain(
        XPathOp.CHILD,
        XPathOp.ATTRIBUTE,
        XPathOp.EQUALS,
        XPathOp.COUNT
    )
    
    // 3. Build parameters (register-packed)
    val parameters = 3 j { index ->
        when (index) {
            0 -> "book"
            1 -> "category"
            2 -> "fiction"
            else -> ""
        }
    }
    
    // 4. Dispatch using register-packed enums
    val childResult = RegisterPackedDispatcher.dispatch(childContext, parameters)
    val attributeResult = RegisterPackedDispatcher.dispatch(attributeContext, parameters)
    val equalsResult = RegisterPackedDispatcher.dispatch(equalsContext, parameters)
    
    println("Child operation result: $childResult")
    println("Attribute operation result: $attributeResult")
    println("Equals operation result: $equalsResult")
    
    // 5. Dispatch operation chain
    val chainResults = RegisterPackedDispatcher.dispatchChain(operationChain, parameters)
    println("Operation chain results: ${chainResults.play}")
    
    // 6. Build MetaSeries using register-packed enums
    val metaSeries = RegisterPackedBuilder.buildMetaSeries(childContext, operationChain)
    val paramMetaSeries = RegisterPackedBuilder.buildParamMetaSeries(XPathParamType.STRING, parameters)
    
    println("MetaSeries context: ${metaSeries.a.operation.symbol}")
    println("MetaSeries chain: ${metaSeries.b.op1.symbol} -> ${metaSeries.b.op2.symbol}")
    println("Parameter MetaSeries: ${paramMetaSeries.a.typeName} with ${paramMetaSeries.b.size} parameters")
    
    // 7. Show register packing efficiency
    println("\n=== Register Packing Efficiency ===")
    println("XPathOpContext size: ${XPathOpContext::class.java.simpleName} packs into 32 bits")
    println("XPathOpChain size: ${XPathOpChain::class.java.simpleName} packs into 64 bits")
    println("No 'to' function overhead - direct register packing!")
}
*/ 