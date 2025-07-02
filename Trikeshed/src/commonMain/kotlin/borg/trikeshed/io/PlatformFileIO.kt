package borg.trikeshed.io

import borg.trikeshed.lib.*

expect interface PlatformFileIO {
    suspend fun readFile(path: String): Join<Int, Function1<Int, Byte>>?
    suspend fun writeFile(path: String, content: Join<Int, Function1<Int, Byte>>): Boolean
}

expect class PlatformFileIOImpl() : PlatformFileIO
