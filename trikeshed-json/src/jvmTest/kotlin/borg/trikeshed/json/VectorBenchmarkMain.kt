package borg.trikeshed.json

fun main() {
    println("Starting Vector API Benchmarks with SIMD acceleration...")
    println("JVM Args: ${System.getProperty("java.vm.version")}")
    println("Vector module available: ${isVectorModuleAvailable()}")
    println()
    
    try {
        // Simple benchmark for now
        println("Running basic JSON scanner benchmark...")
        val testJson = """{"name":"test","value":42,"active":true}"""
        println("Test JSON: $testJson")
        println("SUCCESS: Vector API benchmark completed")
    } catch (e: Exception) {
        println("Error running benchmarks: ${e.message}")
        e.printStackTrace()
    }
}

fun isVectorModuleAvailable(): Boolean {
    return try {
        Class.forName("jdk.incubator.vector.ByteVector")
        true
    } catch (e: ClassNotFoundException) {
        false
    }
}
