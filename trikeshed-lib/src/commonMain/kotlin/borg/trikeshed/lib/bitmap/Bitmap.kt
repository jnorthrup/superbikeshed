package borg.trikeshed.lib.bitmap

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Bitmap(val width: Int, val height: Int) {
    private val bits = BooleanArray(width * height)
    private val mutex = Mutex()

    suspend fun set(x: Int, y: Int) {
        mutex.withLock {
            bits[y * width + x] = true
        }
    }

    suspend fun get(x: Int, y: Int): Boolean {
        return mutex.withLock {
            bits[y * width + x]
        }
    }
}
