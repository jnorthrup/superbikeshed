@file:OptIn(kotlin.ExperimentalStdlibApi::class, kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

import java.io.File
import kotlin.random.Random

/**
 * JVM implementation of home directory functions
 */
actual val homedirGet: String = System.getProperty("user.home")

actual fun mktemp(): String {
    val tempDir = System.getProperty("java.io.tmpdir")
    return File(tempDir, "tmp_${Random.nextInt(1000000)}").absolutePath
}

actual fun rm(path: String): Boolean {
    return File(path).delete()
}

actual fun mkdir(path: String): Boolean {
    return File(path).mkdirs()
}