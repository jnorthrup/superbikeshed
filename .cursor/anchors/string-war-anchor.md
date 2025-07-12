# String War Anchor - The Most Critical Battle

## The String Problem in Kotlin JVM

### Why Strings Are Dangerous
- **String pointers cause innumerable stalls** in common Kotlin JVM code
- **String concatenation** creates temporary objects in hot paths
- **String comparisons** in loops trigger garbage collection pressure
- **String allocations** in speculative contexts kill performance

### The War Zones

#### 1. Speculative Loops (HIGHEST PRIORITY)
```kotlin
// ❌ DEADLY - String in speculative loop
for (item in items) {
    val key = "dynamic_key_${item.id}" // String allocation in hot path
    processItem(key)
}

// ✅ SURVIVAL - String outside speculative loop
val key = "static_key"
for (item in items) {
    processItem(key) // No String allocation in loop
}

// ✅ BETTER - Use constants or enums
enum class KeyType { USER, SYSTEM, TEMP }
for (item in items) {
    processItem(KeyType.USER) // No String allocation
}
```

#### 2. String Concatenation in Hot Paths
```kotlin
// ❌ DEADLY - String concatenation in hot path
fun processData(data: List<Data>) {
    for (item in data) {
        val message = "Processing: ${item.name} at ${item.timestamp}" // String allocation
        log(message)
    }
}

// ✅ SURVIVAL - Use StringBuilder or structured logging
fun processData(data: List<Data>) {
    val sb = StringBuilder()
    for (item in data) {
        sb.clear()
        sb.append("Processing: ").append(item.name).append(" at ").append(item.timestamp)
        log(sb.toString())
    }
}

// ✅ BETTER - Structured logging
fun processData(data: List<Data>) {
    for (item in data) {
        log(LogEvent.PROCESSING, item.name, item.timestamp) // No String allocation
    }
}
```

#### 3. String Comparisons in Performance-Critical Code
```kotlin
// ❌ DEADLY - String comparison in hot path
fun findItem(items: List<Item>, targetName: String): Item? {
    for (item in items) {
        if (item.name == targetName) { // String comparison
            return item
        }
    }
    return null
}

// ✅ SURVIVAL - Use identity comparison or hashing
fun findItem(items: List<Item>, targetId: Int): Item? {
    for (item in items) {
        if (item.id == targetId) { // Primitive comparison
            return item
        }
    }
    return null
}
```

## String Usage Rules

### ALLOWED String Usage
- **Keys in maps/dictionaries** (fine for keys)
- **Configuration values** (static, not in loops)
- **Error messages** (outside hot paths)
- **User input/output** (boundary contexts)

### FORBIDDEN String Usage
- **Inside speculative loops** (HIGHEST PRIORITY)
- **In performance-critical paths**
- **For internal identifiers** (use enums/constants)
- **In data processing pipelines**

## Alternative Patterns

### For Internal Identifiers
```kotlin
// ❌ WRONG
val type = "user"
val status = "active"

// ✅ RIGHT
enum class UserType { USER, ADMIN, GUEST }
enum class Status { ACTIVE, INACTIVE, PENDING }
```

### For Dynamic Keys
```kotlin
// ❌ WRONG
val key = "item_${id}_${timestamp}"

// ✅ RIGHT
data class ItemKey(val id: Int, val timestamp: Long)
val key = ItemKey(id, timestamp)
```

### For Logging
```kotlin
// ❌ WRONG
log("Processing item ${item.id} with status ${item.status}")

// ✅ RIGHT
log(LogEvent.PROCESSING, item.id, item.status)
```

## Detection Patterns
- **Look for String literals in loops**
- **Check for String concatenation with `+` or `${}`**
- **Identify String comparisons in hot paths**
- **Find String allocations in performance-critical code**

## The Battle Cry
**"Strings in speculative loops are the enemy of performance!"** 