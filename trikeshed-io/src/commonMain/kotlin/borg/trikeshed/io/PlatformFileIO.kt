package borg.trikeshed.io

/**
 * PlatformFileIO stub for trikeshed-ccek compatibility
 */
interface PlatformFileIO {
    suspend fun readFile(path: String): ByteArray
    suspend fun writeFile(path: String, data: ByteArray)
    suspend fun deleteFile(path: String): Boolean
    suspend fun exists(path: String): Boolean
}
