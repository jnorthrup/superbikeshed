package borg.trikeshed.nio.spi

/**
 * Simple demo runner for the SPI system
 */
fun main() {
    try {
        SimpleNioDemo.demonstrateSpiSystem()
        println("\n✅ SPI system demonstration completed successfully!")
    } catch (e: Exception) {
        println("\n❌ SPI system demonstration failed: ${e.message}")
        e.printStackTrace()
    }
} 