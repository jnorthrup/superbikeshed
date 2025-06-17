package borg.trikeshed.num

actual enum class RoundingMode {
    UP,
    DOWN,
    CEILING,
    FLOOR,
    HALF_UP,
    HALF_DOWN,
    HALF_EVEN;
    // UNNECESSARY was removed from expect, so not needed here.
}

// Helper function to convert to java.math.RoundingMode
internal fun RoundingMode.toJavaRoundingMode(): java.math.RoundingMode {
    return when (this) {
        RoundingMode.UP -> java.math.RoundingMode.UP
        RoundingMode.DOWN -> java.math.RoundingMode.DOWN
        RoundingMode.CEILING -> java.math.RoundingMode.CEILING
        RoundingMode.FLOOR -> java.math.RoundingMode.FLOOR
        RoundingMode.HALF_UP -> java.math.RoundingMode.HALF_UP
        RoundingMode.HALF_DOWN -> java.math.RoundingMode.HALF_DOWN
        RoundingMode.HALF_EVEN -> java.math.RoundingMode.HALF_EVEN
    }
}
