<<<<<<< HEAD
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package borg.trikeshed.reactor

import kotlinx.datetime.Clock

/**
 * Native platform-specific implementations
 * All time references must be inlined to KMP Clock class calls
 */

// Platform-specific extensions can be added here as needed
=======
package borg.trikeshed.reactor

actual class Selector {
    actual fun select(): Int = 0

    actual fun selectedKeys(): MutableSet<SelectionKey> = mutableSetOf()

    actual fun close() {}
}
>>>>>>> origin/feat/core-serialization-impl
