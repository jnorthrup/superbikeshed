package borg.trikeshed.io

/**
 * Interface for resources that can be opened and closed
 */
interface Usable {
    fun open()
    fun close()
} 