package borg.trikeshed.num

expect enum class RoundingMode {
    UP,          // Rounding mode to round away from zero.
    DOWN,        // Rounding mode to round towards zero.
    CEILING,     // Rounding mode to round towards positive infinity.
    FLOOR,       // Rounding mode to round towards negative infinity.
    HALF_UP,     // Rounding mode to round towards "nearest neighbor" unless both neighbors are equidistant, in which case round up.
    HALF_DOWN,   // Rounding mode to round towards "nearest neighbor" unless both neighbors are equidistant, in which case round down.
    HALF_EVEN,   // Rounding mode to round towards the "nearest neighbor" unless both neighbors are equidistant, in which case, round towards the even neighbor.
    // UNNECESSARY // Removed as it implies an error if rounding is needed.
}
