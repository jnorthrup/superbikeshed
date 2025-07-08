
package borg.trikeshed.wireproto

// === MANUAL JOIN OVERLOADS (KSP would generate these) ===

/**
 * Optimized j overloads for primitive combinations that fit in 64-bit register
 */

// Int + Boolean (32 + 1 = 33 bits) ✓
inline infix fun Int.j(b: Boolean): RegisterJoin<Int, Boolean> {
    val bitsL = PInt.pack(this)
    val bitsR = PBoolean.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 32))
}

// Boolean + Int
inline infix fun Boolean.j(b: Int): RegisterJoin<Boolean, Int> {
    val bitsL = PBoolean.pack(this)
    val bitsR = PInt.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 1))
}

// Int + Byte (32 + 8 = 40 bits) ✓
inline infix fun Int.j(b: Byte): RegisterJoin<Int, Byte> {
    val bitsL = PInt.pack(this)
    val bitsR = PByte.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 32))
}

// Short + Short (16 + 16 = 32 bits) ✓
inline infix fun Short.j(b: Short): RegisterJoin<Short, Short> {
    val bitsL = PShort.pack(this)
    val bitsR = PShort.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 16))
}

// Byte + Byte (8 + 8 = 16 bits) ✓
inline infix fun Byte.j(b: Byte): RegisterJoin<Byte, Byte> {
    val bitsL = PByte.pack(this)
    val bitsR = PByte.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 8))
}

// Float + Float (32 + 32 = 64 bits) ✓
inline infix fun Float.j(b: Float): RegisterJoin<Float, Float> {
    val bitsL = PFloat.pack(this)
    val bitsR = PFloat.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 32))
}

// Boolean + Boolean (1 + 1 = 2 bits) ✓
inline infix fun Boolean.j(b: Boolean): RegisterJoin<Boolean, Boolean> {
    val bitsL = PBoolean.pack(this)
    val bitsR = PBoolean.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 1))
}

// Byte + Boolean (8 + 1 = 9 bits) ✓
inline infix fun Byte.j(b: Boolean): RegisterJoin<Byte, Boolean> {
    val bitsL = PByte.pack(this)
    val bitsR = PBoolean.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 8))
}

// Boolean + Byte (1 + 8 = 9 bits) ✓
inline infix fun Boolean.j(b: Byte): RegisterJoin<Boolean, Byte> {
    val bitsL = PBoolean.pack(this)
    val bitsR = PByte.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 1))
}

// Short + Byte (16 + 8 = 24 bits) ✓
inline infix fun Short.j(b: Byte): RegisterJoin<Short, Byte> {
    val bitsL = PShort.pack(this)
    val bitsR = PByte.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 16))
}

// Byte + Short (8 + 16 = 24 bits) ✓
inline infix fun Byte.j(b: Short): RegisterJoin<Byte, Short> {
    val bitsL = PByte.pack(this)
    val bitsR = PShort.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 8))
}

// Int + Short (32 + 16 = 48 bits) ✓
inline infix fun Int.j(b: Short): RegisterJoin<Int, Short> {
    val bitsL = PInt.pack(this)
    val bitsR = PShort.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 32))
}

// Short + Int (16 + 32 = 48 bits) ✓
inline infix fun Short.j(b: Int): RegisterJoin<Short, Int> {
    val bitsL = PShort.pack(this)
    val bitsR = PInt.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 16))
}