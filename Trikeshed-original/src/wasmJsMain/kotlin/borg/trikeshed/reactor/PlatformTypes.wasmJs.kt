package borg.trikeshed.reactor

actual class Selector {
    actual fun select(): Int = 0

    actual fun selectedKeys(): MutableSet<SelectionKey> = mutableSetOf()

    actual fun close() {}
}