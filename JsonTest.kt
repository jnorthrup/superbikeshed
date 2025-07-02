import borg.trikeshed.parse.json.Json

fun main() {
    // Test valid JSON
    val validJson = """{"name": "test", "value": 42, "active": true}"""
    println("Valid JSON: ${Json.parse(validJson)}")
    
    val parseResult = Json.parseWithPosition(validJson)
    println("Parse result: $parseResult")
    
    // Test invalid JSON
    val invalidJson = """{"name": "test", "value":}"""
    println("Invalid JSON: ${Json.parse(invalidJson)}")
    
    // Test array
    val arrayJson = """[1, 2, "hello", true, null]"""
    println("Array JSON: ${Json.parse(arrayJson)}")
    
    // Test empty objects/arrays
    println("Empty object: ${Json.parse("{}")}")
    println("Empty array: ${Json.parse("[]")}")
}