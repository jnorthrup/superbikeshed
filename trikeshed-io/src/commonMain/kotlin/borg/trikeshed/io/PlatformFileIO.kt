package borg.trikeshed.io

import borg.trikeshed.lib.*

expect interface PlatformFileIO {
    suspend fun readFile(path: String): Join<Int, (Int) -> Byte>?
    suspend fun writeFile(path: String, content: Join<Int, (Int) -> Byte>): Boolean
}

expect class PlatformFileIOImpl() : PlatformFileIO
