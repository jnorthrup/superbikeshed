package borg.trikeshed.io

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively

actual val homedirGet: String
    get() = System.getProperty("user.home")

actual fun mktemp(): String {
    val tempFile = File.createTempFile("temp", null)
    return tempFile.absolutePath
}

actual fun rm(path: String): Boolean {
    val file = File(path)
    return if (file.isDirectory) {
        file.deleteRecursively()
    } else {
        file.deleteIfExists()
    }
}

actual fun mkdir(path: String): Boolean {
    return try {
        Files.createDirectories(Paths.get(path))
        true
    } catch (e: Exception) {
        false
    }
}