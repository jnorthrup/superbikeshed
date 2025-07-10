import kotlin.system.measureTimeMillis

fun main() {
    println("Testing JSON libraries...")
    
    val testJson = """{"name": "test", "value": 42, "active": true}"""
    
    // Test TrikeShed Simple
    try {
        val time = measureTimeMillis {
            repeat(1000) {
                testJson.simpleJson().properties()
            }
        }
        println("✓ TrikeShed Simple: ${time}ms for 1000 iterations")
    } catch (e: Exception) {
        println("✗ TrikeShed Simple failed: ${e.message}")
    }
    
    // Test TrikeShed Compact
    try {
        val time = measureTimeMillis {
            repeat(1000) {
                testJson.json().properties()
            }
        }
        println("✓ TrikeShed Compact: ${time}ms for 1000 iterations")
    } catch (e: Exception) {
        println("✗ TrikeShed Compact failed: ${e.message}")
    }
    
    // Test TrikeShed Fast
    try {
        val time = measureTimeMillis {
            repeat(1000) {
                testJson.fastJson().properties()
            }
        }
        println("✓ TrikeShed Fast: ${time}ms for 1000 iterations")
    } catch (e: Exception) {
        println("✗ TrikeShed Fast failed: ${e.message}")
    }
    
    // Test JsonBBCursive
    try {
        val time = measureTimeMillis {
            repeat(1000) {
                borg.trikeshed.ljson.JsonBBCursive.parse(testJson)
            }
        }
        println("✓ JsonBBCursive: ${time}ms for 1000 iterations")
    } catch (e: Exception) {
        println("✗ JsonBBCursive failed: ${e.message}")
    }
    
    println("Test completed!")
} 