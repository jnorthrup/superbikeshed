package nexus

actual suspend fun runInteractivePlatform() {
    println("Interactive mode is not fully supported on native platforms")
    println("Limited functionality available - no LLM integration")
    println("Please use JVM platform for full interactive features")
}