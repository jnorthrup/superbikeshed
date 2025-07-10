@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

@file:OptIn(ExperimentalForeignApi::class)

package borg.trikeshed.io

import kotlinx.cinterop.*
import platform.posix.*
import kotlin.random.Random

/**
 * Native implementation of home directory functions
 */
actual val homedirGet: String = getenv("HOME")?.toKString() ?: "/tmp"

actual fun mktemp(): String {
    val tempDir = getenv("TMPDIR")?.toKString() ?: "/tmp"
    return "$tempDir/tmp_${Random.nextInt(1000000)}"
}

actual fun rm(path: String): Boolean {
    return remove(path) == 0
}

actual fun mkdir(path: String): Boolean {
    return mkdir(path, S_IRWXU.toUShort()) == 0
}