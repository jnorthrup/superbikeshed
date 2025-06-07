package borg.trikeshed.logging

import borg.trikeshed.nio.slabs.ActualMemorySlabManagerService // Native actual
import borg.trikeshed.nio.slabs.MemorySlabManagerService

actual fun getTestMemorySlabManagerService(defaultSlabSize: Int): MemorySlabManagerService {
    return ActualMemorySlabManagerService(defaultSlabSize = defaultSlabSize)
}
