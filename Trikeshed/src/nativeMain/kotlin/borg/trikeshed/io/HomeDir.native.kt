package borg.trikeshed.io

import kotlinx.cinterop.*
import platform.posix.*

actual val homedirGet: String = getenv("HOME")?.toKString() ?: "/tmp"

actual fun mktemp(): String {
    // Use POSIX mkdtemp to create a temporary directory
    val template = "/tmp/trikeshed_XXXXXX"
    val templateBytes = template.encodeToByteArray()
    
    return memScoped {
        val cTemplate = allocArray<ByteVar>(templateBytes.size + 1)
        templateBytes.forEachIndexed { index, byte ->
            cTemplate[index] = byte
        }
        cTemplate[templateBytes.size] = 0
        
        val result = mkdtemp(cTemplate)
        result?.toKString() ?: "/tmp/fallback_${kotlin.random.Random.nextInt()}"
    }
}

actual fun rm(path: String): Boolean {
    return remove(path) == 0
}

actual fun mkdir(path: String): Boolean {
    // Create directory with rwxr-xr-x permissions (755)
    return mkdir(path, 0x1EDu) == 0
}