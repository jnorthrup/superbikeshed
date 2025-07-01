package borg.trikeshed.io

import borg.trikeshed.lib.Indexed

expect interface PlatformFileIO {
    suspend fun readFile(path: String): Indexed<Byte>?
    suspend fun writeFile(path: String, content: Indexed<Byte>): Boolean
}
