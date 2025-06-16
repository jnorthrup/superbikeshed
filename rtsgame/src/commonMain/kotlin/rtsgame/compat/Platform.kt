package rtsgame.compat

/**
 * Platform compatibility layer for multiplatform code
 */

expect fun currentTimeMillis(): Long
expect fun formatFloat(value: Float, precision: Int): String

// Multiplatform-compatible annotation
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
expect annotation class PlatformInline()