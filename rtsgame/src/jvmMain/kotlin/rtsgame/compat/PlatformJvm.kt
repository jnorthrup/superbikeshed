package rtsgame.compat

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun formatFloat(value: Float, precision: Int): String = "%.${precision}f".format(value)

actual typealias PlatformInline = JvmInline