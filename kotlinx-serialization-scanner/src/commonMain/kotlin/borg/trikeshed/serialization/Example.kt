package borg.trikeshed.serialization

import kotlinx.serialization.*

/**
 * Example usage of the kotlinx-serialization-scanner module
 */

@Serializable
data class User(
    val id: Int,
    val name: String,
    val email: String,
    val isActive: Boolean,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class Organization(
    val name: String,
    val users: List<User>,
    val settings: Settings
)

@Serializable 
data class Settings(
    val theme: String,
    val notifications: Boolean,
    val maxUsers: Int?
)

/**
 * Demonstrates basic usage of bitmap JSON parsing
 */
fun basicExample() {
    val json = """{
        "id": 1,
        "name": "John Doe",
        "email": "john@example.com",
        "isActive": true,
        "metadata": {
            "department": "Engineering",
            "level": "Senior"
        }
    }"""
    
    // Parse using bitmap scanning
    val user = json.decodeBitmapJson<User>()
    println("Parsed user: ${user.name} (${user.email})")
}

/**
 * Demonstrates performance comparison
 */
fun performanceExample() {
    val largeJson = buildString {
        append("""{"name":"TechCorp","users":[""")
        repeat(1000) { i ->
            if (i > 0) append(",")
            append("""{
                "id": $i,
                "name": "User $i",
                "email": "user$i@example.com",
                "isActive": ${i % 2 == 0},
                "metadata": {"role": "employee", "team": "team${i % 10}"}
            }""")
        }
        append("""],"settings":{"theme":"dark","notifications":true,"maxUsers":null}}""")
    }
    
    println("Benchmarking large JSON parsing...")
    val result = BitmapJsonBenchmark.benchmark<Organization>(largeJson, 100)
    println(result)
}

/**
 * Demonstrates streaming array processing
 */
fun streamingExample() {
    val jsonArray = """[
        {"id":1,"name":"Alice","email":"alice@example.com","isActive":true},
        {"id":2,"name":"Bob","email":"bob@example.com","isActive":false},
        {"id":3,"name":"Charlie","email":"charlie@example.com","isActive":true}
    ]"""
    
    val stream = BitmapJsonStream()
    println("Processing users from stream:")
    
    stream.parseArrayStream<User>(jsonArray).forEach { user ->
        println("- ${user.name}: ${if (user.isActive) "Active" else "Inactive"}")
    }
}

/**
 * Demonstrates custom configuration
 */
fun configurationExample() {
    val jsonWithUnknownFields = """{
        "id": 1,
        "name": "John Doe",
        "email": "john@example.com",
        "isActive": true,
        "unknownField": "ignored",
        "metadata": {}
    }"""
    
    val lenientJson = BitmapJson.create {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    val user = jsonWithUnknownFields.decodeBitmapJson<User>(lenientJson)
    println("Parsed with unknown fields ignored: ${user.name}")
}

/**
 * Demonstrates validation
 */
fun validationExample() {
    val validJson = """{"name":"test","value":123}"""
    val invalidJson = """{"name":"test","value":123""" // Missing closing brace
    
    println("Valid JSON: ${BitmapJsonValidator.isValidJson(validJson)}")
    println("Invalid JSON: ${BitmapJsonValidator.isValidJson(invalidJson)}")
    
    val errors = BitmapJsonValidator.getValidationErrors(invalidJson)
    if (errors.isNotEmpty()) {
        println("Validation errors:")
        errors.forEach { println("  - $it") }
    }
}

/**
 * Run all examples
 */
fun runExamples() {
    println("=== kotlinx-serialization-scanner Examples ===\n")
    
    try {
        println("1. Basic Example:")
        basicExample()
        println()
        
        println("2. Performance Example:")
        performanceExample()
        println()
        
        println("3. Streaming Example:")
        streamingExample()
        println()
        
        println("4. Configuration Example:")
        configurationExample()
        println()
        
        println("5. Validation Example:")
        validationExample()
        println()
        
    } catch (e: Exception) {
        println("Example failed: ${e.message}")
        e.printStackTrace()
    }
}