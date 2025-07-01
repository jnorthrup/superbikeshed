package rtsgame.compat

expect fun currentTimeMillis(): Long
expect fun platformMain()

object Platform {
    fun getCurrentTime(): Long = currentTimeMillis()
}