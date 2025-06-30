package borg.trikeshed.num

// For JS, the enum itself is enough as the expect enum defines the members.
// The actual implementations of BigDecimal will interpret these.
actual enum class RoundingMode {
    UP,
    DOWN,
    CEILING,
    FLOOR,
    HALF_UP,
    HALF_DOWN,
    HALF_EVEN;
}
