package borg.trikeshed.platform

actual fun <T> runBlocking(block: suspend () -> T): T {
    throw UnsupportedOperationException("runBlocking is not supported on wasmJs target")
}