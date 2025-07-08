package rtsgame

/**
 * Common main entry point for RTS Game
 * Platform-specific implementations will provide the actual function
 */
expect fun platformMain()

/**
 * Main function for JVM target
 */
fun main() {
    platformMain()
}