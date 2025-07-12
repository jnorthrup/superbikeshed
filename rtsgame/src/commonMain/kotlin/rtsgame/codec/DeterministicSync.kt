import kotlin.math.*
package rtsgame.codec
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.lib.*
import kotlin.math.*
import rtsgame.core.TeamResourcesExtended

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

class DeterministicRandom(internal var seed: Long) {
    fun nextDouble(): Double {
        seed = (seed * 1664525L + 1013904223L) and 0xFFFFFFFFL
        return (seed and 0x7FFFFF).toDouble() / 0x800000
    }
    
    fun nextInt(bound: Int): Int = (nextDouble() * bound).toInt()
    
    fun saveState(): Long = seed
    fun restoreState(state: Long) { seed = state }
}

data class FrameContext(
    val frameNumber: Long,
    val deltaTime: Double,
    val randomSeed: Long,
    val inputHash: Int
)

class DeterministicMap<K, V> {
    internal val map = LinkedHashMap<K, V>()
    internal val insertionOrder = mutableListOf<K>()
    
    operator fun set(key: K, value: V) {
        if (key !in map) insertionOrder.add(key)
        map[key] = value
    }
    
    operator fun get(key: K): V? = map[key]
    
    fun remove(key: K) {
        map.remove(key)
        insertionOrder.remove(key)
    }
    
    fun forEach(action: (K, V) -> Unit) {
        for (key in insertionOrder) {
            map[key]?.let { value -> action(key, value) }
        }
    }
    
    fun toIndexed(): Indexed<Pair<K, V>> = \1 j { \2: Int -> insertionOrder[i] to map[insertionOrder[i]]!! }
}

data class SyncCheckpoint(
    val frameNumber: Long,
    val entityCount: Int,
    val resourceChecksum: Int,
    val positionChecksum: Int,
    val randomState: Long
) {
    companion object {
        fun calculatePositionChecksum(positions: Indexed<Pair<Double, Double>>): Int {
            var checksum = 0
            for (i in 0 until positions.size) {
                val (x, y) = positions[i]
                checksum = checksum xor (x * 1000).toInt()
                checksum = checksum xor (y * 1000).toInt()
                checksum = (checksum shl 1) or (checksum ushr 31)
            }
            return checksum
        }
        
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

data class SyncValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val frame: Long
)

object SyncValidator {
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

data class ReplayFrame(
    val frameContext: FrameContext,
    val requests: List<RTSRequest>,
    val checkpoint: SyncCheckpoint
)

data class ReplayData(
    val version: String = "1.0",
    val seed: Long,
    val frames: List<ReplayFrame>,
    val metadata: Map<String, String> = emptyMap()
)