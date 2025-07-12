package borg.trikeshed.io

import borg.trikeshed.lib.Join

actual class PlatformFileIOImpl : PlatformFileIO {
    override suspend fun readFile(path: String): Join<Int, (Int) -> Byte>? {
        TODO("Not yet implemented for macosArm64")
    }

    override suspend fun writeFile(path: String, content: Join<Int, (Int) -> Byte>): Boolean {
        TODO("Not yet implemented for macosArm64")
    }

    override suspend fun deleteFile(path: String): Boolean {
        TODO("Not yet implemented for macosArm64")
    }

    override suspend fun exists(path: String): Boolean {
        TODO("Not yet implemented for macosArm64")
    }

    override suspend fun asyncReadFile(path: String): ByteArray? {
        TODO("Not yet implemented for macosArm64")
    }

    override suspend fun asyncWriteFile(path: String, content: ByteArray): Boolean {
        TODO("Not yet implemented for macosArm64")
    }
}

actual fun getPlatformFileIO(): PlatformFileIO = PlatformFileIOImpl()