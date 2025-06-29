package rtsgame.compat

import kotlin.system.getTimeMillis

actual fun currentTimeMillis(): Long = getTimeMillis()

actual fun formatFloat(value: Float, precision: Int): String = value.toString()

// Native doesn't have JvmInline, use empty annotation  
actual annotation class PlatformInline()