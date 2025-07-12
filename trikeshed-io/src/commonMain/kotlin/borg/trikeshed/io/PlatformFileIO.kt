package borg.trikeshed.io

import borg.trikeshed.lib.*

/**
 * PlatformFileIO stub for trikeshed-ccek compatibility
 */
expect interface PlatformFileIO {
    suspend fun readFile(path: String): Join<Int, (Int) -> Byte>?
    suspend fun writeFile(path: String, content: Join<Int, (Int) -> Byte>): Boolean
    suspend fun deleteFile(path: String): Boolean
    suspend fun exists(path: String): Boolean
    suspend fun asyncReadFile(path: String): ByteArray?
    suspend fun asyncWriteFile(path: String, content: ByteArray): Boolean
}

expect fun getPlatformFileIO(): PlatformFileIO
