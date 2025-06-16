actual class CommonSelector {
    suspend fun select(): Int = 0  // Placeholder: to be implemented with actual JVM selector logic
    suspend fun wakeup() {}  // Placeholder: wakeup implementation for JVM
    suspend fun selectedKeys(): Set<SelectionKey> = emptySet()  // Placeholder: requires actual SelectionKey definition
    suspend fun close() {}  // Placeholder: close implementation for JVM
}