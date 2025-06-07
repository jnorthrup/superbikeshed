package borg.trikeshed.io

actual interface HasSize : HasDescriptor {
    actual val size: Long
        get() = 0L // Placeholder
}
