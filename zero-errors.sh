#!/bin/bash

echo "=== Moving Toward Zero Errors ==="

# Fix withJava() deprecation
echo "1. Fixing withJava() deprecation..."
find . -name "build.gradle.kts" -type f -exec grep -l "withJava()" {} \; | while read file; do
    echo "   Fixing: $file"
    sed -i.bak 's/withJava()/\/\/ withJava() deprecated in Kotlin 2.2.0/' "$file"
done

# Check for successful core modules
echo -e "\n2. Testing core modules..."
./gradlew :trikeshed-lib:build :trikeshed-common:build :trikeshed-json:build --console=plain --no-daemon

# List all warnings
echo -e "\n3. Collecting warnings..."
./gradlew build --console=plain --no-daemon 2>&1 | grep "^w:" | sort | uniq -c | sort -nr > warnings.txt
echo "Warnings saved to warnings.txt"

# List all errors
echo -e "\n4. Collecting errors..."
./gradlew build --console=plain --no-daemon 2>&1 | grep "^e:" | sort | uniq -c | sort -nr > errors.txt
echo "Errors saved to errors.txt"

echo -e "\nTop 10 warnings:"
head -10 warnings.txt

echo -e "\nTop 10 errors:"
head -10 errors.txt