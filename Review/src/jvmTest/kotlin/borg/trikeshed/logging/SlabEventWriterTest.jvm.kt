package borg.trikeshed.logging

import borg.trikeshed.io.slabs.ActualMemorySlabManagerService // JVM actual
import borg.trikeshed.io.slabs.MemorySlabManagerService

actual fun getTestMemorySlabManagerService(defaultSlabSize: Int): MemorySlabManagerService {
    return ActualMemorySlabManagerService(defaultSlabSize = defaultSlabSize)
}
