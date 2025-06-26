package borg.trikeshed.io
@file:OptIn(ExperimentalUnsignedTypes::class)


/**
 * A common interface for objects that can be opened and closed.
 * This is intended to be used with a `use` extension function.
 */
interface Usable {
    fun open()
    fun close()
} 