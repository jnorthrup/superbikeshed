import kotlin.random.AbstractPlatformRandom
import kotlin.random.Random
import kotlin.random.nextLong
class DeterministicRNG(private var seed: Long) : Random() {

    override fun nextBits(bits: Int): Int {
        seed = seed xor (seed ushr 20) * 0x5BDCCD15L
        seed = seed xor (seed ushr 12) * 0xACAACC1BL
        seed = seed xor (seed ushr 7) * 0x9E17DC31L
        seed = seed xor (seed ushr 15)
        return (seed ushr (32 - bits)) and ((1L shl bits) - 1L).toInt()
    }

    override fun nextLong(): Long {
        return (nextBits(32).toLong() shl 32) + nextBits(32)
    }
}

    override fun nextLong(): Long {
        return (nextBits(32).toLong() shl 32) + nextBits(32)
    }
}
