package nexus.interactive

/**
 * JVM-specific implementation of input reading
 */
actual fun readInput(): String? = readlnOrNull()