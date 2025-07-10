@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@file:OptIn(RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
@file:OptIn(ExperimentalForeignApi::class)

package borg.trikeshed.io

import kotlinx.cinterop.*
import platform.posix.*


/**
 * Native implementation of read lines functions
 */
actual fun readLinesSeq(path: String): Sequence<String> {
    return sequence {
        val file = fopen(path, "r") ?: return@sequence
        try {
            memScoped {
                val buffer = allocArray<ByteVar>(4096)
                while (fgets(buffer, 4096, file) != null) {
                    yield(buffer.toKString().trimEnd('\n'))
                }
            }
        } finally {
            fclose(file)
        }
    }
}

actual fun readLines(path: String): List<String> {
    return readLinesSeq(path).toList()
}