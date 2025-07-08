@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package borg.trikeshed.cursor

import borg.trikeshed.lib.*

/**
 * Cursor types for navigating TrikeShed data structures
 * Stub implementation for k2script
 */
interface Cursor<T> {
    fun current(): T?
    fun next(): Boolean
    fun previous(): Boolean
    fun reset()
}

class IndexedCursor<T>(internal val data: Indexed<T>) : Cursor<T> {
    internal var position = 0
    
    override fun current(): T? = if (position < data.size) data[position] else null
    
    override fun next(): Boolean {
        if (position < data.size - 1) {
            position++
            return true
        }
        return false
    }
    
    override fun previous(): Boolean {
        if (position > 0) {
            position--
            return true
        }
        return false
    }
    
    override fun reset() {
        position = 0
    }
}