#!/usr/bin/env k2script

// Test script to verify markdown code block detection in k2script

println("Testing markdown code block detection...")

// This should be detected as a markdown code block start with kotlin language
```kotlin
fun testFunction() {
    println("This is Kotlin code inside a markdown block")
    val numbers = listOf(1, 2, 3, 4, 5)
    val sum = numbers.sum()
    println("Sum: $sum")
}
```

// This should be detected as a generic markdown code block
```
println("This is generic code")
```

// This should be detected as a bash code block
```bash
#!/bin/bash
echo "This is bash code"
ls -la
```

// This should be detected as a java code block
```java
public class Test {
    public static void main(String[] args) {
        System.out.println("This is Java code");
    }
}
```

println("Markdown code block detection test completed!") 