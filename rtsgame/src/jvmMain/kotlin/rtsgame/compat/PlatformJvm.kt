package rtsgame.compat

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun platformMain() {
    println("RTS Game JVM Platform")
}

actual fun formatFloat(value: Float, precision: Int): String = "%.${precision}f".format(value)

actual typealias PlatformInline = JvmInline