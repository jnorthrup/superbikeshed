package borg.trikeshed.lib

/**
 * CZero - Zero/Non-Zero utility extensions
 * 
 * Provides convenient extension properties for checking zero/non-zero values
 * across different numeric types. This eliminates boilerplate code like
 * `value == 0` or `value != 0` throughout the codebase.
 */

object CZero {
    // === INT EXTENSIONS ===
    val Int.z: Boolean get() = this == 0
    val Int.nz: Boolean get() = this != 0
    
    // === LONG EXTENSIONS ===
    val Long.z: Boolean get() = this == 0L
    val Long.nz: Boolean get() = this != 0L
    
    // === UINT EXTENSIONS ===
    val UInt.z: Boolean get() = this == 0u
    val UInt.nz: Boolean get() = this != 0u
    
    // === ULONG EXTENSIONS ===
    val ULong.z: Boolean get() = this == 0uL
    val ULong.nz: Boolean get() = this != 0uL
    
    // === SHORT EXTENSIONS ===
    val Short.z: Boolean get() = this == 0.toShort()
    val Short.nz: Boolean get() = this != 0.toShort()
    
    // === USHORT EXTENSIONS ===
    val UShort.z: Boolean get() = this == 0u.toUShort()
    val UShort.nz: Boolean get() = this != 0u.toUShort()
    
    // === BYTE EXTENSIONS ===
    val Byte.z: Boolean get() = this == 0.toByte()
    val Byte.nz: Boolean get() = this != 0.toByte()
    
    // === UBYTE EXTENSIONS ===
    val UByte.z: Boolean get() = this == 0u.toUByte()
    val UByte.nz: Boolean get() = this != 0u.toUByte()
    
    // === FLOAT EXTENSIONS ===
    val Float.z: Boolean get() = this == 0.0f
    val Float.nz: Boolean get() = this != 0.0f
    
    // === DOUBLE EXTENSIONS ===
    val Double.z: Boolean get() = this == 0.0
    val Double.nz: Boolean get() = this != 0.0
    
    // === NULLABLE EXTENSIONS ===
    val Int?.z: Boolean get() = this == null || this == 0
    val Int?.nz: Boolean get() = this != null && this != 0
    
    val Long?.z: Boolean get() = this == null || this == 0L
    val Long?.nz: Boolean get() = this != null && this != 0L
    
    val UInt?.z: Boolean get() = this == null || this == 0u
    val UInt?.nz: Boolean get() = this != null && this != 0u
    
    val ULong?.z: Boolean get() = this == null || this == 0uL
    val ULong?.nz: Boolean get() = this != null && this != 0uL
} 