@file:Suppress("NOTHING_TO_INLINE")

package core

import borg.trikeshed.lib.*
// import evolution.T_2D // Removed - using TensorCursor instead
import kotlin.jvm.*

/**
 * Metaprogramming Advancement for Tensor-Core
 * 
 * Addresses the DRY violations and metaprogramming opportunities identified in the analysis,
 * specifically for tensor-core module advancement while preserving performance.
 */

// ═══════════════════════════════════════════════════════════════════════════════════════
// PRIMITIVE SPECIALIZATION CODE GENERATION
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * Primitive type metadata for code generation
 * Replaces manual repetition of primitive specializations
 */
enum class PrimitiveType(
    val kotlinType: String,
    val defaultValue: String,
    val arrayType: String,
    val simdWidth: Int,  // For SIMD vectorization
    val bitSize: Int
) {
    BYTE("Byte", "0.toByte()", "ByteArray", 64, 8),
    SHORT("Short", "0.toShort()", "ShortArray", 32, 16), 
    INT("Int", "0", "IntArray", 16, 32),
    LONG("Long", "0L", "LongArray", 8, 64),
    FLOAT("Float", "0.0f", "FloatArray", 16, 32),
    DOUBLE("Double", "0.0", "DoubleArray", 8, 64),
    
    // Unsigned types
    UBYTE("UByte", "0u.toUByte()", "UByteArray", 64, 8),
    USHORT("UShort", "0u.toUShort()", "UShortArray", 32, 16),
    UINT("UInt", "0u", "UIntArray", 16, 32),
    ULONG("ULong", "0uL", "ULongArray", 8, 64);
    
    val isUnsigned: Boolean get() = name.startsWith("U")
    val isFpType: Boolean get() = this in setOf(FLOAT, DOUBLE)
}

/**
 * Template-based code generation for primitive specializations
 * Eliminates DRY violations while maintaining zero-cost performance
 */
object PrimitiveCodeGen {
    
    /**
     * Generate Series<T> extensions for all primitive types
     * Replaces manual repetition in Series.kt
     */
    fun generateSeriesExtensions(): String = buildString {
        PrimitiveType.values().forEach { type ->
            appendLine("""
                fun ${type.arrayType}.toSeries(): Series<${type.kotlinType}> = size j ::get
                
                fun Series<${type.kotlinType}>.toArray(): ${type.arrayType} = ${type.arrayType}(size, ::get)
                
                infix fun <C, B : (${type.kotlinType}) -> C> ${type.arrayType}.α(m: B): Series<C> = 
                    this.size j { m(this[it]) }
            """.trimIndent())
            appendLine()
        }
    }
    
    /**
     * Generate AlignedArray specializations for SIMD operations
     */
    fun generateAlignedArrays(): String = buildString {
        PrimitiveType.values().forEach { type ->
            appendLine("""
                @JvmInline
                value class Aligned${type.kotlinType}Array(val data: ${type.arrayType}) {
                    companion object {
                        const val SIMD_WIDTH = ${type.simdWidth}
                        const val BIT_SIZE = ${type.bitSize}
                    }
                    
                    inline val size: Int get() = data.size
                    inline operator fun get(i: Int): ${type.kotlinType} = data[i]
                    inline operator fun set(i: Int, value: ${type.kotlinType}) { data[i] = value }
                    
                    // SIMD-friendly batch operations
                    inline fun vectorizedTransform(
                        crossinline operation: (${type.kotlinType}) -> ${type.kotlinType}
                    ): Aligned${type.kotlinType}Array {
                        val result = ${type.arrayType}(size)
                        var i = 0
                        
                        // Vectorizable loop
                        while (i < size - SIMD_WIDTH + 1) {
                            for (j in 0 until SIMD_WIDTH) {
                                result[i + j] = operation(data[i + j])
                            }
                            i += SIMD_WIDTH
                        }
                        
                        // Handle remainder
                        while (i < size) {
                            result[i] = operation(data[i])
                            i++
                        }
                        
                        return Aligned${type.kotlinType}Array(result)
                    }
                }
            """.trimIndent())
            appendLine()
        }
    }
    
    /**
     * Generate binary operations for all numeric types
     */
    fun generateBinaryOperations(): String = buildString {
        val numericTypes = PrimitiveType.values().filter { it != PrimitiveType.BYTE && it != PrimitiveType.UBYTE }
        
        numericTypes.forEach { type ->
            appendLine("""
                // Binary operations for ${type.kotlinType}
                inline infix fun Tensor<${type.kotlinType}>.plus(other: Tensor<${type.kotlinType}>): Tensor<${type.kotlinType}> {
                    require(this.shape.contentEquals(other.shape)) { "Shape mismatch" }
                    return Tensor(this.shape) { coords -> this[coords] + other[coords] }
                }
                
                inline infix fun Tensor<${type.kotlinType}>.minus(other: Tensor<${type.kotlinType}>): Tensor<${type.kotlinType}> {
                    require(this.shape.contentEquals(other.shape)) { "Shape mismatch" }
                    return Tensor(this.shape) { coords -> this[coords] - other[coords] }
                }
                
                inline infix fun Tensor<${type.kotlinType}>.times(other: Tensor<${type.kotlinType}>): Tensor<${type.kotlinType}> {
                    require(this.shape.contentEquals(other.shape)) { "Shape mismatch" }
                    return Tensor(this.shape) { coords -> this[coords] * other[coords] }
                }
            """.trimIndent())
            appendLine()
            
            if (type.isFpType) {
                appendLine("""
                inline infix fun Tensor<${type.kotlinType}>.div(other: Tensor<${type.kotlinType}>): Tensor<${type.kotlinType}> {
                    require(this.shape.contentEquals(other.shape)) { "Shape mismatch" }
                    return Tensor(this.shape) { coords -> this[coords] / other[coords] }
                }
                """.trimIndent())
                appendLine()
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════════════
// CONFIGURATION-DRIVEN METAPROGRAMMING
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * Configuration-driven tensor operations
 * Addresses repetitive setup and processing logic
 */
data class TensorPipeline<T>(
    val name: String,
    val inputShape: IntArray,
    val operations: List<TensorOperation<T>>,
    val outputValidation: (Tensor<T>) -> Boolean = { true }
)

/**
 * Generic tensor operation interface
 * Enables declarative pipeline configuration
 */
sealed class TensorOperation<T> {
    data class Transform<T>(val transform: (T) -> T) : TensorOperation<T>()
    data class Reshape<T>(val newShape: IntArray) : TensorOperation<T>()
    data class Slice<T>(val ranges: Array<IntRange>) : TensorOperation<T>()
    data class Reduce<T>(val axis: Int, val reducer: (T, T) -> T, val identity: T) : TensorOperation<T>()
}

/**
 * Pipeline executor with metaprogramming capabilities
 */
object TensorPipelineEngine {
    
    fun <T> executePipeline(input: Tensor<T>, pipeline: TensorPipeline<T>): Tensor<T> {
        var current = input
        
        pipeline.operations.forEach { operation ->
            current = when (operation) {
                is TensorOperation.Transform -> current α operation.transform
                is TensorOperation.Reshape -> reshapeTensor(current, operation.newShape)
                is TensorOperation.Slice -> sliceTensor(current, operation.ranges)
                is TensorOperation.Reduce -> reduceTensor(current, operation.axis, operation.reducer, operation.identity)
            }
        }
        
        require(pipeline.outputValidation(current)) { 
            "Pipeline '${pipeline.name}' output validation failed" 
        }
        
        return current
    }
    
    private fun <T> reshapeTensor(tensor: Tensor<T>, newShape: IntArray): Tensor<T> {
        require(tensor.totalSize == newShape.fold(1, Int::times)) { 
            "Cannot reshape: size mismatch" 
        }
        
        return Tensor(newShape) { coords ->
            val linearIndex = coordsToLinearIndex(coords, newShape)
            val originalCoords = linearIndexToCoords(linearIndex, tensor.shape)
            tensor(originalCoords)
        }
    }
    
    private fun <T> sliceTensor(tensor: Tensor<T>, ranges: Array<IntRange>): Tensor<T> {
        require(ranges.size == tensor.rank) { "Slice dimensions must match tensor rank" }
        
        val newShape = ranges.map { it.last - it.first + 1 }.toIntArray()
        
        return Tensor(newShape) { coords ->
            val originalCoords = coords.mapIndexed { i, coord -> 
                ranges[i].first + coord 
            }.toIntArray()
            tensor(originalCoords)
        }
    }
    
    private fun <T> reduceTensor(
        tensor: Tensor<T>, 
        axis: Int, 
        reducer: (T, T) -> T, 
        identity: T
    ): Tensor<T> {
        val newShape = tensor.shape.filterIndexed { i, _ -> i != axis }.toIntArray()
        
        return Tensor(newShape) { coords ->
            // Insert the axis dimension back for iteration
            val fullCoords = coords.toMutableList()
            fullCoords.add(axis, 0)
            
            var result = identity
            var i = 0
            while (i < tensor.shape[axis]) {
                fullCoords[axis] = i
                result = reducer(result, tensor(fullCoords.toIntArray()))
                i++
            }
            result
        }
    }
    
    private fun coordsToLinearIndex(coords: IntArray, shape: IntArray): Int {
        var index = 0
        var multiplier = 1
        
        for (i in coords.indices.reversed()) {
            index += coords[i] * multiplier
            if (i > 0) multiplier *= shape[i]
        }
        
        return index
    }
    
    private fun linearIndexToCoords(index: Int, shape: IntArray): IntArray {
        val coords = IntArray(shape.size)
        var remaining = index
        
        for (i in shape.indices.reversed()) {
            coords[i] = remaining % shape[i]
            remaining /= shape[i]
        }
        
        return coords
    }
}

// ═══════════════════════════════════════════════════════════════════════════════════════
// DSL METAPROGRAMMING FOR CONTEXT FLOW
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * Annotation-driven DSL generation
 * If the Stairway DSL grows massively, this enables automatic generation
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class TensorContext(
    val name: String,
    val allowedTransitions: Array<String> = [],
    val operations: Array<String> = []
)

/**
 * DSL operation metadata for code generation
 */
data class DSLOperation(
    val name: String,
    val inputType: String,
    val outputType: String,
    val parameters: List<DSLParameter> = emptyList()
)

data class DSLParameter(
    val name: String,
    val type: String,
    val defaultValue: String? = null
)

/**
 * Context flow graph for validation and optimization
 */
class ContextFlowGraph {
    private val contexts = mutableMapOf<String, ContextNode>()
    private val transitions = mutableMapOf<String, MutableList<String>>()
    
    data class ContextNode(
        val name: String,
        val operations: List<DSLOperation>,
        val allowedTransitions: Set<String>
    )
    
    fun addContext(name: String, operations: List<DSLOperation>, transitions: Set<String>) {
        contexts[name] = ContextNode(name, operations, transitions)
        this.transitions[name] = transitions.toMutableList()
    }
    
    fun validateTransition(from: String, to: String): Boolean {
        return transitions[from]?.contains(to) ?: false
    }
    
    fun findOptimalPath(from: String, to: String): List<String>? {
        // Simple BFS for now - could be A* with cost heuristics
        val queue = mutableListOf(listOf(from))
        val visited = mutableSetOf<String>()
        
        while (queue.isNotEmpty()) {
            val path = queue.removeAt(0)
            val current = path.last()
            
            if (current == to) return path
            if (current in visited) continue
            
            visited.add(current)
            
            transitions[current]?.forEach { next ->
                queue.add(path + next)
            }
        }
        
        return null
    }
    
    fun generateContextCode(): String = buildString {
        contexts.values.forEach { context ->
            appendLine("""
                @JvmInline
                value class ${context.name}Context<T>(val tensor: Tensor<T>) {
            """.trimIndent())
            
            context.operations.forEach { op ->
                val params = op.parameters.joinToString(", ") { param ->
                    if (param.defaultValue != null) {
                        "${param.name}: ${param.type} = ${param.defaultValue}"
                    } else {
                        "${param.name}: ${param.type}"
                    }
                }
                
                appendLine("""
                    inline fun ${op.name}($params): ${op.outputType}<T> = 
                        ${op.outputType}(tensor) // Implementation placeholder
                """.trimIndent())
            }
            
            appendLine("}")
            appendLine()
        }
    }
}
