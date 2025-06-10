package borg.trikeshed.nio

/**
 * Custom exception for NIO-related errors within the TrikeShed framework.
 * This common exception can be used by expect/actual implementations.
 */
class NioException(message: String, cause: Throwable? = null) : Exception(message, cause)
