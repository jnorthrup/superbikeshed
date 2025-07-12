package borg.trikeshed.fiduciary

import borg.trikeshed.lib.*
import kotlinx.serialization.*

/**
 * Core Wave CRDT Operations based on Apache Wave mathematical specification.
 * 
 * Operation<T> := {ω | ω: T → T ∪ {OperationException}}
 * ReversibleOperation<T> := {ω ∈ Operation<T> | ∃ ω⁻¹: ω⁻¹(ω(t)) = t}
 * OperationSink<T> := {Σ | Σ: Ω → (T → T), intent_preserving(Σ)}
 */

sealed interface WaveOp<T> {
    fun apply(target: T): Either<T, OperationException>
    
    fun compose(other: WaveOp<T>): WaveOp<T> = object : WaveOp<T> {
        override fun apply(target: T): Either<T, OperationException> = 
            when (val first = this@WaveOp.apply(target)) {
                is Either.Left -> other.apply(first.value)
                is Either.Right -> Either.right(first.value)
            }
    }
}

sealed interface ReversibleWaveOp<T> : WaveOp<T> {
    val inverse: ReversibleWaveOp<T>
    
    fun verifyReversible(target: T): Boolean {
        val forward = apply(target)
        return when (forward) {
            is Either.Left -> {
                val backward = inverse.apply(forward.value)
                when (backward) {
                    is Either.Left -> backward.value == target
                    is Either.Right -> false
                }
            }
            is Either.Right -> false
        }
    }
}

interface WaveOperationSink<T> {
    fun transform(op: WaveOp<T>): (T) -> T
    fun preservesIntent(op: WaveOp<T>): Boolean
    
    fun compose(other: WaveOperationSink<T>): WaveOperationSink<T> = 
        object : WaveOperationSink<T> {
            override fun transform(op: WaveOp<T>): (T) -> T = 
                { t -> other.transform(op)(this@WaveOperationSink.transform(op)(t)) }
            override fun preservesIntent(op: WaveOp<T>): Boolean = 
                this@WaveOperationSink.preservesIntent(op) && other.preservesIntent(op)
        }
}

data class OperationException(
    val message: String,
    val cause: Throwable? = null
) : Exception(message, cause)

/**
 * Wave document operations following the mathematical specification.
 * These map to TrikeShed's Indexed<T> for efficient columnar operations.
 */
sealed class WaveDocumentOp : ReversibleWaveOp<WaveDocumentState> {
    
    data class Retain(val count: Int) : WaveDocumentOp() {
        override fun apply(target: WaveDocumentState): Either<WaveDocumentState, OperationException> =
            if (count < 0 || count > target.content.length) {
                Either.right(OperationException("Invalid retain count: $count"))
            } else {
                Either.left(target.copy(cursor = target.cursor + count))
            }
        
        override val inverse: ReversibleWaveOp<WaveDocumentState> = Retain(-count)
    }
    
    data class Insert(val text: String, val position: Int) : WaveDocumentOp() {
        override fun apply(target: WaveDocumentState): Either<WaveDocumentState, OperationException> =
            if (position < 0 || position > target.content.length) {
                Either.right(OperationException("Invalid insert position: $position"))
            } else {
                val newContent = target.content.substring(0, position) + 
                                text + 
                                target.content.substring(position)
                Either.left(target.copy(content = newContent))
            }
        
        override val inverse: ReversibleWaveOp<WaveDocumentState> = 
            Delete(text, position)
    }
    
    data class Delete(val text: String, val position: Int) : WaveDocumentOp() {
        override fun apply(target: WaveDocumentState): Either<WaveDocumentState, OperationException> =
            if (position < 0 || position + text.length > target.content.length) {
                Either.right(OperationException("Invalid delete range: $position to ${position + text.length}"))
            } else if (target.content.substring(position, position + text.length) != text) {
                Either.right(OperationException("Delete text mismatch"))
            } else {
                val newContent = target.content.substring(0, position) + 
                                target.content.substring(position + text.length)
                Either.left(target.copy(content = newContent))
            }
        
        override val inverse: ReversibleWaveOp<WaveDocumentState> = 
            Insert(text, position)
    }
    
    data class ElementStart(val type: String, val attributes: Map<String, String>) : WaveDocumentOp() {
        override fun apply(target: WaveDocumentState): Either<WaveDocumentState, OperationException> {
            val element = WaveElement(type, attributes)
            return Either.left(target.copy(
                elements = target.elements + element,
                cursor = target.cursor + 1
            ))
        }
        
        override val inverse: ReversibleWaveOp<WaveDocumentState> = 
            ElementEnd(type)
    }
    
    data class ElementEnd(val type: String) : WaveDocumentOp() {
        override fun apply(target: WaveDocumentState): Either<WaveDocumentState, OperationException> {
            val lastElement = target.elements.lastOrNull()
            return if (lastElement?.type == type) {
                Either.left(target.copy(
                    elements = target.elements.dropLast(1),
                    cursor = target.cursor + 1
                ))
            } else {
                Either.right(OperationException("Element type mismatch: expected $type"))
            }
        }
        
        override val inverse: ReversibleWaveOp<WaveDocumentState> = 
            ElementStart(type, emptyMap())
    }
    
    data class AnnotationBoundary(
        val changes: Map<String, String?>,
        val position: Int
    ) : WaveDocumentOp() {
        override fun apply(target: WaveDocumentState): Either<WaveDocumentState, OperationException> =
            if (position < 0 || position > target.content.length) {
                Either.right(OperationException("Invalid annotation position: $position"))
            } else {
                val newAnnotations = target.annotations.toMutableMap()
                changes.forEach { (key, value) ->
                    if (value != null) {
                        newAnnotations[key] = value
                    } else {
                        newAnnotations.remove(key)
                    }
                }
                Either.left(target.copy(annotations = newAnnotations))
            }
        
        override val inverse: ReversibleWaveOp<WaveDocumentState> = 
            AnnotationBoundary(
                changes.mapValues { (key, value) -> 
                    if (value != null) null else target.annotations[key]
                },
                position
            )
    }
}

/**
 * Wave document state using TrikeShed's Indexed types
 */
data class WaveDocumentState(
    val content: String,
    val cursor: Int = 0,
    val elements: List<WaveElement> = emptyList(),
    val annotations: Map<String, String> = emptyMap(),
    val operationHistory: Indexed<WaveDocumentOp> = 0 j { throw IndexOutOfBoundsException() }
) {
    fun toIndexed(): Indexed<Char> = content.length j { i: Int -> content[i] }
    
    fun withHistory(ops: Indexed<WaveDocumentOp>): WaveDocumentState = 
        copy(operationHistory = ops)
}

data class WaveElement(
    val type: String,
    val attributes: Map<String, String>
)

/**
 * Operational Transformation following mathematical specification.
 * Convergence Property: ∀ω₁,ω₂ ∈ Ω, t ∈ T: ω₁'(ω₂(t)) = ω₂'(ω₁(t))
 */
interface OperationalTransform<T> {
    fun transform(op1: WaveOp<T>, op2: WaveOp<T>): Join<WaveOp<T>, WaveOp<T>>
    
    fun inclusionTransform(op1: WaveOp<T>, op2: WaveOp<T>): Join<WaveOp<T>, WaveOp<T>> = 
        transform(op1, op2)
    
    fun exclusionTransform(op1: WaveOp<T>, op2: WaveOp<T>): WaveOp<T>
}

/**
 * Wave CRDT convergence implementation
 */
class WaveCRDTConvergence : OperationalTransform<WaveDocumentState> {
    
    override fun transform(
        op1: WaveOp<WaveDocumentState>, 
        op2: WaveOp<WaveDocumentState>
    ): Join<WaveOp<WaveDocumentState>, WaveOp<WaveDocumentState>> {
        return when (op1) {
            is WaveDocumentOp.Insert -> when (op2) {
                is WaveDocumentOp.Insert -> transformInsertInsert(op1, op2)
                is WaveDocumentOp.Delete -> transformInsertDelete(op1, op2)
                else -> op1 j op2
            }
            is WaveDocumentOp.Delete -> when (op2) {
                is WaveDocumentOp.Insert -> transformDeleteInsert(op1, op2)
                is WaveDocumentOp.Delete -> transformDeleteDelete(op1, op2)
                else -> op1 j op2
            }
            else -> op1 j op2
        }
    }
    
    override fun exclusionTransform(
        op1: WaveOp<WaveDocumentState>, 
        op2: WaveOp<WaveDocumentState>
    ): WaveOp<WaveDocumentState> {
        val transformed = transform(op1, op2)
        return transformed.component1()
    }
    
    internal fun transformInsertInsert(
        op1: WaveDocumentOp.Insert, 
        op2: WaveDocumentOp.Insert
    ): Join<WaveOp<WaveDocumentState>, WaveOp<WaveDocumentState>> {
        return when {
            op1.position < op2.position -> op1 j op2.copy(position = op2.position + op1.text.length)
            op1.position > op2.position -> op1.copy(position = op1.position + op2.text.length) j op2
            else -> op1 j op2.copy(position = op2.position + op1.text.length)
        }
    }
    
    internal fun transformInsertDelete(
        op1: WaveDocumentOp.Insert, 
        op2: WaveDocumentOp.Delete
    ): Join<WaveOp<WaveDocumentState>, WaveOp<WaveDocumentState>> {
        return when {
            op1.position <= op2.position -> op1 j op2.copy(position = op2.position + op1.text.length)
            op1.position >= op2.position + op2.text.length -> 
                op1.copy(position = op1.position - op2.text.length) j op2
            else -> op1.copy(position = op2.position) j op2
        }
    }
    
    internal fun transformDeleteInsert(
        op1: WaveDocumentOp.Delete, 
        op2: WaveDocumentOp.Insert
    ): Join<WaveOp<WaveDocumentState>, WaveOp<WaveDocumentState>> {
        return when {
            op1.position + op1.text.length <= op2.position -> 
                op1 j op2.copy(position = op2.position - op1.text.length)
            op1.position >= op2.position -> 
                op1.copy(position = op1.position + op2.text.length) j op2
            else -> op1 j op2
        }
    }
    
    internal fun transformDeleteDelete(
        op1: WaveDocumentOp.Delete, 
        op2: WaveDocumentOp.Delete
    ): Join<WaveOp<WaveDocumentState>, WaveOp<WaveDocumentState>> {
        return when {
            op1.position + op1.text.length <= op2.position -> 
                op1 j op2.copy(position = op2.position - op1.text.length)
            op2.position + op2.text.length <= op1.position -> 
                op1.copy(position = op1.position - op2.text.length) j op2
            else -> {
                val overlap = findOverlap(op1, op2)
                if (overlap != null) {
                    overlap.component1() j overlap.component2()
                } else {
                    op1 j op2
                }
            }
        }
    }
    
    internal fun findOverlap(
        op1: WaveDocumentOp.Delete, 
        op2: WaveDocumentOp.Delete
    ): Join<WaveDocumentOp.Delete, WaveDocumentOp.Delete>? {
        val start1 = op1.position
        val end1 = op1.position + op1.text.length
        val start2 = op2.position
        val end2 = op2.position + op2.text.length
        
        return if (start1 < end2 && start2 < end1) {
            val overlapStart = maxOf(start1, start2)
            val overlapEnd = minOf(end1, end2)
            val overlapLength = overlapEnd - overlapStart
            
            when {
                start1 < start2 -> {
                    val newOp1 = op1.copy(text = op1.text.substring(0, start2 - start1))
                    val newOp2 = op2.copy(
                        position = start1 + newOp1.text.length,
                        text = op2.text.substring(overlapLength)
                    )
                    newOp1 j newOp2
                }
                else -> {
                    val newOp2 = op2.copy(text = op2.text.substring(0, start1 - start2))
                    val newOp1 = op1.copy(
                        position = start2 + newOp2.text.length,
                        text = op1.text.substring(overlapLength)
                    )
                    newOp1 j newOp2
                }
            }
        } else null
    }
}

/**
 * Void Sink - Identity element for sink composition
 */
object VoidSink : WaveOperationSink<WaveDocumentState> {
    override fun transform(op: WaveOp<WaveDocumentState>): (WaveDocumentState) -> WaveDocumentState = 
        { state -> state }
    
    override fun preservesIntent(op: WaveOp<WaveDocumentState>): Boolean = false
}

/**
 * Intent-preserving sink that ensures operation effect equivalence
 */
class IntentPreservingSink : WaveOperationSink<WaveDocumentState> {
    internal val convergence = WaveCRDTConvergence()
    
    override fun transform(op: WaveOp<WaveDocumentState>): (WaveDocumentState) -> WaveDocumentState = 
        { state ->
            when (val result = op.apply(state)) {
                is Either.Left -> result.value
                is Either.Right -> state
            }
        }
    
    override fun preservesIntent(op: WaveOp<WaveDocumentState>): Boolean = true
}