package borg.trikeshed.io

/**
 * WasmJS implementation of IOException
 */
actual class IOException actual constructor(message: String) : Exception(message)