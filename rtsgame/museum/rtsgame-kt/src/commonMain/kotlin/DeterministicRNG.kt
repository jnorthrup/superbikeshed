package com.rtsgame.kt

class DeterministicRNG(initialSeed: Long) {
    private var seed: Long = initialSeed

    private val multiplier: Long = 1664525L
    private val increment: Long = 1013904223L
    private val modulus: Long = 4294967296L // 2^32

    init {
        setSeed(initialSeed)
    }

    fun setSeed(newSeed: Long) {
        // In JS, the seed is directly used as the initial state.
        // The LCG formula means the first call to next() will produce a number based on this initial seed.
        // So, we just assign it.
        this.seed = newSeed
    }

    // Generates a random Double between 0.0 (inclusive) and 1.0 (exclusive)
    fun getRandom(): Double {
        seed = (multiplier * seed + increment) % modulus
        // Ensure the result is positive if seed becomes negative due to overflow before modulo
        val positiveSeed = if (seed < 0) seed + modulus else seed
        return (positiveSeed.toDouble() / modulus.toDouble())
    }

    // Generates a random Int between min (inclusive) and max (inclusive)
    // This matches the behavior of the JavaScript version's randomInt(min, max)
    fun getInt(min: Int, max: Int): Int {
        require(min <= max) { "max must be greater than or equal to min" }
        val randomValue = getRandom() // value between 0.0 and <1.0
        // Scale to min to max range (inclusive of max)
        return (min + (randomValue * (max - min + 1)).toInt())
    }

    // Generates a random Float between 0.0f (inclusive) and 1.0f (exclusive)
    fun getFloat(): Float {
        return getRandom().toFloat()
    }

    // Generates a random Float between min (inclusive) and max (exclusive)
    // This matches the behavior of the JavaScript version's randomFloat(min, max)
    fun getFloat(min: Float, max: Float): Float {
        require(min < max) { "max must be greater than min" }
        return (min + getRandom().toFloat() * (max - min))
    }
}
