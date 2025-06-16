package rtsgame.compat

// Use JS Date API for WASM
external object Date {
    fun now(): Double
}

actual fun currentTimeMillis(): Long = Date.now().toLong()

actual fun formatFloat(value: Float, precision: Int): String = value.toString()

// WASM doesn't have JvmInline, use empty annotation
actual annotation class PlatformInline()