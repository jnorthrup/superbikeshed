package borg.trikeshed.nio

actual interface HasSize : HasDescriptor {
    actual val size: Long
}