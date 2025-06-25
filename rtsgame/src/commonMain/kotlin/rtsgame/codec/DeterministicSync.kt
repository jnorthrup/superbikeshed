package rtsgame.codec

import borg.trikeshed.lib.*
import kotlinx.serialization.*
import kotlin.math.*

/**
 * Deterministic synchronization primitives for RTS
 * Ensures bit-perfect compatibility with JS simulation
 */

/**
 * Fixed-point arithmetic for deterministic cross-platform math
 * JS uses double precision floats, we must match exactly
 */
@JvmInline
value class FixedPoint(val raw: Long) {
    companion object {
        const val PRECISION = 10000L // 4 decimal places
        
        fun fromDouble(d: Double): FixedPoint = FixedPoint((d * PRECISION).toLong())
        fun fromInt(i: Int): FixedPoint = FixedPoint(i * PRECISION)
    }
    
    fun toDouble(): Double = raw.toDouble() / PRECISION
    
    operator fun plus(other: FixedPoint): FixedPoint = FixedPoint(raw + other.raw)
    operator fun minus(other: FixedPoint): FixedPoint = FixedPoint(raw - other.raw)
    operator fun times(other: FixedPoint): FixedPoint = FixedPoint((raw * other.raw) / PRECISION)
    operator fun div(other: FixedPoint): FixedPoint = FixedPoint((raw * PRECISION) / other.raw)
}

/**
 * Deterministic random number generator matching JS Math.random() behavior
 */
class DeterministicRandom(private var seed: Long) {
    // Linear congruential generator matching JS behavior
    fun nextDouble(): Double {
        seed = (seed * 1664525L + 1013904223L) and 0xFFFFFFFFL
        return (seed and 0x7FFFFF).toDouble() / 0x800000
    }
    
    fun nextInt(bound: Int): Int = (nextDouble() * bound).toInt()
    
    fun saveState(): Long = seed
    fun restoreState(state: Long) { seed = state }
}

/**
 * Frame-locked execution context
 */
@Serializable
data class FrameContext(
    val frameNumber: Long,
    val deltaTime: Double,
    val randomSeed: Long,
    val inputHash: Int
)

/**
 * Deterministic collection iteration
 * JS iterates in insertion order, we must match
 */
class DeterministicMap<K, V> {
    private val map = LinkedHashMap<K, V>()
    private val insertionOrder = mutableListOf<K>()
    
    operator fun set(key: K, value: V) {
        if (key !in map) {
            insertionOrder.add(key)
        }
        map[key] = value
    }
    
    operator fun get(key: K): V? = map[key]
    
    fun remove(key: K) {
        map.remove(key)
        insertionOrder.remove(key)
    }
    
    fun forEach(action: (K, V) -> GameUnit) {
        // Iterate in insertion order to match JS
        for (key in insertionOrder) {
            map[key]?.let { value ->
                action(key, value)
            }
        }
    }
    
    fun toSeries(): Indexed<Pair<K, V>> {
        val pairs = insertionOrder.mapNotNull { key ->
            map[key]?.let { value -> key to value }
        }
        return pairs.size j { i: Int -> pairs[i] }
    }
}

/**
 * Synchronization checkpoint for validating determinism
 */
@Serializable
data class SyncCheckpoint(
    val frameNumber: Long,
    val entityCount: Int,
    val resourceChecksum: Int,
    val positionChecksum: Int,
    val randomState: Long
) {
    companion object {
        /**
         * Calculate position checksum matching JS implementation
         */
        fun calculatePositionChecksum(positions: Indexed<Pair<Double, Double>>): Int {
            var checksum = 0
            for (i in 0 until positions.a) {
                val (x, y) = positions[i]
                // Match JS number to int conversion
                checksum = checksum xor (x * 1000).toInt()
                checksum = checksum xor (y * 1000).toInt()
                checksum = (checksum shl 1) or (checksum ushr 31) // Rotate left
            }
            return checksum
        }
        
        /**
         * Calculate resource checksum
         */
        fun calculateResourceChecksum(resources: Map<String, TeamResourcesExtended>): Int {
            var checksum = 0
            resources.forEach { (team, res) ->
                checksum = checksum xor res.mass
                checksum = checksum xor res.energy
                checksum = checksum xor res.computronium
                checksum = checksum xor team.hashCode()
            }
            return checksum
        }
    }
}

/**
 * Replay frame for deterministic playback
 */
@Serializable
data class ReplayFrame(
    val frameContext: FrameContext,
    val requests: List<RTSRequest>,
    val checkpoint: SyncCheckpoint
)

/**
 * Full replay data structure
 */
@Serializable
data class ReplayData(
    val version: String = "1.0",
    val seed: Long,
    val frames: List<ReplayFrame>,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Sync validator for ensuring KMP/JS parity
 */
object SyncValidator {
    private const val POSITION_TOLERANCE = 0.0001
    
    fun validateCheckpoints(kmp: SyncCheckpoint, reference: SyncCheckpoint): SyncValidationResult {
        val errors = mutableListOf<String>()
        
        if (kmp.frameNumber != reference.frameNumber) {
            errors.add("Frame mismatch: KMP=${kmp.frameNumber}, REF=${reference.frameNumber}")
        }
        
        if (kmp.entityCount != reference.entityCount) {
            errors.add("Entity count mismatch: KMP=${kmp.entityCount}, REF=${reference.entityCount}")
        }
        
        if (kmp.resourceChecksum != reference.resourceChecksum) {
            errors.add("Resource checksum mismatch: KMP=${kmp.resourceChecksum}, REF=${reference.resourceChecksum}")
        }
        
        if (kmp.positionChecksum != reference.positionChecksum) {
            errors.add("Position checksum mismatch: KMP=${kmp.positionChecksum}, REF=${reference.positionChecksum}")
        }
        
        if (kmp.randomState != reference.randomState) {
            errors.add("Random state mismatch: KMP=${kmp.randomState}, REF=${reference.randomState}")
        }
        
        return SyncValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            frame = kmp.frameNumber
        )
    }
}

@Serializable
data class SyncValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val frame: Long
)