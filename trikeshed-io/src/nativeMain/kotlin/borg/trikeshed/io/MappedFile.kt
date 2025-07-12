package borg.trikeshed.io

import borg.trikeshed.lib.Join

actual class MappedFile actual constructor(path: String, size: Long, readOnly: Boolean) {
    actual fun close() {
        // TODO: Implement actual close logic
        println("MappedFile.close() not implemented for macosArm64")
    }

    actual fun open() {
        // TODO: Implement actual open logic
        println("MappedFile.open() not implemented for macosArm64")
    }

    actual fun isOpen(): Boolean {
        // TODO: Implement actual isOpen logic
        return false
    }

    actual fun size(): Long {
        // TODO: Implement actual size logic
        return 0L
    }

    actual fun get(index: Long): Byte {
        // TODO: Implement actual get logic
        return 0
    }

    actual fun put(index: Long, value: Byte) {
        // TODO: Implement actual put logic
        println("MappedFile.put() not implemented for macosArm64")
    }
}