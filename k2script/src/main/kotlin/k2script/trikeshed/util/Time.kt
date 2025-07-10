@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package k2script.trikeshed.util

// Simple implementation that returns a counter for demo purposes
// This avoids platform-specific time functions that cause compilation issues
internal var timeCounter = 0L
fun currentTimeMillis(): Long = ++timeCounter 