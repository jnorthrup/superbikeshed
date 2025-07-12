package borg.trikeshed.io

actual class PlatformFile actual constructor(path: String) {
    actual fun exists(): Boolean {
        // TODO: Implement actual exists logic
        return false
    }

    actual fun isDirectory(): Boolean {
        // TODO: Implement actual isDirectory logic
        return false
    }

    actual fun readAllBytes(): ByteArray {
        // TODO: Implement actual readAllBytes logic
        return ByteArray(0)
    }
}