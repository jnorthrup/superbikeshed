package borg.trikeshed.num

// Wrapper for native JavaScript BigInt
@JsName("BigInt")
private external fun NativeJsBigInt(value: Any): dynamic

// Helper to check if a value is a JS BigInt
private fun isJsBigInt(value: dynamic): Boolean = js("typeof value === 'bigint'")

actual class BigInt actual constructor() {
    private var nativeValue: dynamic // Stores the native JS BigInt

    // Private constructor for internal use
    private constructor(value: Any) : this() {
        if (isJsBigInt(value)) {
            this.nativeValue = value
        } else {
            this.nativeValue = NativeJsBigInt(value.toString())
        }
    }

    actual companion object {
        actual fun parseString(value: String, radix: Int): BigInt {
            // JS BigInt doesn't directly support radix in constructor like Java.
            // A common workaround is to use a prefix for hex/oct/bin or parse manually for other radices.
            // For simplicity, this basic version will assume base 10 or rely on JS BigInt's own string parsing.
            // More robust radix parsing would require a custom loop for non-standard radices.
            if (radix != 10) {
                // Basic support for hex, octal, binary if string starts with 0x, 0o, 0b
                if (value.startsWith("0x", true) && radix == 16) return BigInt(NativeJsBigInt(value))
                if (value.startsWith("0o", true) && radix == 8) return BigInt(NativeJsBigInt(value))
                if (value.startsWith("0b", true) && radix == 2) return BigInt(NativeJsBigInt(value))
                // Fallback or throw for other radices - this is a simplification
                console.warn("BigInt.parseString for JS currently has limited radix support beyond base 10 or 0x/0o/0b prefixes. Attempting base 10.")
            }
            return BigInt(NativeJsBigInt(value))
        }
        actual fun fromLong(value: Long): BigInt = BigInt(NativeJsBigInt(value.toString()))
        actual fun fromInt(value: Int): BigInt = BigInt(NativeJsBigInt(value.toString()))
        actual val ZERO: BigInt by lazy { BigInt(NativeJsBigInt("0")) }
        actual val ONE: BigInt by lazy { BigInt(NativeJsBigInt("1")) }
        actual val TEN: BigInt by lazy { BigInt(NativeJsBigInt("10")) }
    }

    actual operator fun plus(other: BigInt): BigInt = BigInt(this.nativeValue + other.nativeValue)
    actual operator fun minus(other: BigInt): BigInt = BigInt(this.nativeValue - other.nativeValue)
    actual operator fun times(other: BigInt): BigInt = BigInt(this.nativeValue * other.nativeValue)
    actual operator fun div(other: BigInt): BigInt = BigInt(this.nativeValue / other.nativeValue) // Integer division
    actual operator fun rem(other: BigInt): BigInt = BigInt(this.nativeValue % other.nativeValue)

    actual fun pow(exponent: Int): BigInt {
        if (exponent < 0) throw IllegalArgumentException("Negative exponent not supported in JS BigInt pow")
        var result = ONE.nativeValue
        var base = this.nativeValue
        var exp = NativeJsBigInt(exponent.toString())
        val two = NativeJsBigInt("2")
        while (exp > NativeJsBigInt("0")) {
            if (exp % two == NativeJsBigInt("1")) result *= base
            base *= base
            exp /= two
        }
        return BigInt(result)
        // Note: More direct way is base ** NativeJsBigInt(exponent.toString()) in modern JS
        // return BigInt(this.nativeValue ** NativeJsBigInt(exponent.toString()))
    }
    actual fun abs(): BigInt = if (this.nativeValue < NativeJsBigInt("0")) BigInt(this.nativeValue * NativeJsBigInt("-1")) else this
    actual operator fun unaryMinus(): BigInt = BigInt(this.nativeValue * NativeJsBigInt("-1"))

    actual operator fun compareTo(other: BigInt): Int {
        return when {
            this.nativeValue < other.nativeValue -> -1
            this.nativeValue > other.nativeValue -> 1
            else -> 0
        }
    }

    actual override fun toString(): String = this.nativeValue.toString()
    actual fun toString(radix: Int): String = this.nativeValue.toString(radix) // JS BigInt toString supports radix

    actual fun toLong(): Long = this.nativeValue.toString().toLong() // Can lose precision for very large numbers
    actual fun toInt(): Int = this.nativeValue.toString().toInt()     // Can lose precision
    actual fun toDouble(): Double = this.nativeValue.toString().toDouble() // Can lose precision

    override fun hashCode(): Int = nativeValue.toString().hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BigInt) return false
        return this.nativeValue == other.nativeValue
    }
}
