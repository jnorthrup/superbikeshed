
package borg.trikeshed.io

import kotlinx.cinterop.ExperimentalForeignApi

/**
 * Native implementation of IOException
 */
actual class IOException actual constructor(message: String) : Exception(message)