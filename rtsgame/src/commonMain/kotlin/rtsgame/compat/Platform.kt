import kotlin.math.*
package rtsgame.compat
import kotlinx.datetime.*
import kotlin.time.*

expect fun Clock.System.now().toEpochMilliseconds(): Long
expect fun platformMain()

object Platform {
    fun getCurrentTime(): Long = Clock.System.now().toEpochMilliseconds()
}