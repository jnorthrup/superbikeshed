package evolution

actual class ActualDecryptionFailedException actual constructor(
    message: String,
    cause: Throwable?
) : Exception(message, cause) {
    actual constructor(message: String) : this(message, null)
}
