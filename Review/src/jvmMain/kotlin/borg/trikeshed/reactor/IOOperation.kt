package borg.trikeshed.reactor

actual class IOOperation private constructor(actual val value: Int) {
    actual companion object {
        actual val READ = IOOperation(1)
        actual val WRITE = IOOperation(4) 
        actual val ACCEPT = IOOperation(16)
        actual val CONNECT = IOOperation(8)
    }
}
