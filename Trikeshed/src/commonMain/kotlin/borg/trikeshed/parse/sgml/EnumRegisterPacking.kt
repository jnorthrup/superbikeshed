@file:Suppress("NOTHING_TO_INLINE")

package borg.trikeshed.parse.sgml

import borg.trikeshed.lib.*
import kotlin.jvm.JvmInline

/**
 * Enum Register Packing - Cheap enum packing into registers
 * 
 * Shows how enums in joins can be made very cheap to pack into registers
 * Avoids inefficient 'to' function and uses direct register packing
 */

// === ENUM REGISTER PACKING ===

/**
 * XPath Operation - Enum designed for register packing
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
    
    companion object {
        fun fromSymbol(symbol: String): XPathOp? = 
            values().find { it.symbol == symbol }
    }
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
    
    companion object {
        fun fromTypeName(typeName: String): XPathParamType? = 
            values().find { it.typeName == typeName }
    }
}

// === REGISTER-PACKED ENUM JOINS ===

/**
 * XPath Operation Context - Register-packed enum join
 * Uses enum ordinal for efficient register packing
 */
@JvmInline
value class XPathOpContext(val packed: Int) {
    val operation: XPathOp get() = XPathOp.values()[packed shr 16]
    val paramType: XPathParamType get() = XPathParamType.values()[(packed shr 8) and 0xFF]
    val paramIndex: Int get() = packed and 0xFF
    
    companion object {
        fun pack(op: XPathOp, paramType: XPathParamType, paramIndex: Int): XPathOpContext {
            require(paramIndex in 0..255) { "Parameter index must fit in 8 bits" }
            val packed = (op.ordinal shl 16) or (paramType.ordinal shl 8) or paramIndex
            return XPathOpContext(packed)
        }
        
        fun pack(op: XPathOp): XPathOpContext = pack(op, XPathParamType.STRING, 0)
    }
}

/**
 * XPath Operation Chain - Register-packed enum series
 * Uses bit-packed enums for efficient register storage
 */
@JvmInline
value class XPathOpChain(val packed: Long) {
    val op1: XPathOp get() = XPathOp.values()[(packed shr 48).toInt() and 0xFF]
    val op2: XPathOp get() = XPathOp.values()[(packed shr 40).toInt() and 0xFF]
    val op3: XPathOp get() = XPathOp.values()[(packed shr 32).toInt() and 0xFF]
    val op4: XPathOp get() = XPathOp.values()[(packed shr 24).toInt() and 0xFF]
    val op5: XPathOp get() = XPathOp.values()[(packed shr 16).toInt() and 0xFF]
    val op6: XPathOp get() = XPathOp.values()[(packed shr 8).toInt() and 0xFF]
    val op7: XPathOp get() = XPathOp.values()[packed.toInt() and 0xFF]
    
    companion object {
        fun pack(vararg ops: XPathOp): XPathOpChain {
            require(ops.size <= 7) { "Can pack at most 7 operations" }
            var packed: Long = 0
            ops.forEachIndexed { index, op ->
                packed = packed or (op.ordinal.toLong() shl (48 - index * 8))
            }
            return XPathOpChain(packed)
        }
    }
}

// === REGISTER-PACKED META SERIES ===

/**
 * XPath Operation MetaSeries - Register-packed enum linking
 * Uses enum ordinals for efficient MetaSeries construction
 */
typealias XPathOpMetaSeries = Join<XPathOpContext, XPathOpChain>

/**
 * XPath Parameter MetaSeries - Register-packed parameter linking
 * Uses enum ordinals for efficient parameter storage
 */
typealias XPathParamMetaSeries = Join<XPathParamType, Indexed<String>>

// === REGISTER-PACKED DISPATCH ===

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