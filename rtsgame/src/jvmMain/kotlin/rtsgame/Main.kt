package rtsgame

import kotlinx.coroutines.runBlocking

/**
 * JVM entry point for RTS game
 */

fun main(args: Array<String>) = runBlocking {
    launchRTSGame(args)
}