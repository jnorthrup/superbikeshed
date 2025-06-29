@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package borg.trikeshed.reactor

import kotlinx.datetime.Clock

/**
 * Native platform-specific implementations
 * All time references must be inlined to KMP Clock class calls
 */

// Platform-specific extensions can be added here as needed