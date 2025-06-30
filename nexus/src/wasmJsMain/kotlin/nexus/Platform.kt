package nexus

actual suspend fun runInteractivePlatform() {
    println("Interactive mode is not supported on WASM-JS platform")
    println("Please use JVM platform for interactive features")
}