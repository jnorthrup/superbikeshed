package borg.trikeshed.zlib.internal

/**
 * Base exception for Zlib operations.
 */
open class ZlibException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Exception thrown when compressed data is corrupted or malformed.
 */
class DataFormatException(message: String, cause: Throwable? = null) : ZlibException(message, cause)

/**
 * Exception thrown when an unsupported compression method or feature is encountered.
 */
class UnsupportedFeatureException(message: String, cause: Throwable? = null) : ZlibException(message, cause)

/**
 * Exception thrown when there is an issue with the input or output stream.
 */
class StreamException(message: String, cause: Throwable? = null) : ZlibException(message, cause)
