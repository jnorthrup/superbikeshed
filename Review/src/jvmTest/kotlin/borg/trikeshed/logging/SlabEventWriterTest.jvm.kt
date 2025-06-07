package borg.trikeshed.logging

import borg.trikeshed.nio.slabs.ActualMemorySlabManagerService // JVM actual
import borg.trikeshed.nio.slabs.MemorySlabManagerService

actual fun getTestMemorySlabManagerService(defaultSlabSize: Int): MemorySlabManagerService {
    return ActualMemorySlabManagerService(defaultSlabSize = defaultSlabSize)
}
