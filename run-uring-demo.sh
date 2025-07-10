#!/bin/bash

# Run the TrikeShed io_uring demos on macOS

echo "🍺👓 TrikeShed io_uring Beer Goggles Demo"
echo "========================================"
echo ""
echo "Building the project..."

# Build the Darwin target
./gradlew :trikeshed-uring:macosArm64MainClasses || {
    echo "❌ Build failed. Make sure you have Kotlin Native installed."
    exit 1
}

echo ""
echo "✅ Build successful!"
echo ""
echo "Choose a demo to run:"
echo "1. Basic Platform Demo (shows all features)"
echo "2. Interactive Async I/O Demo"
echo "3. File Copy Demo (real-world usage)"
echo ""
read -p "Your choice (1-3): " choice

case $choice in
    1)
        echo "Running Basic Platform Demo..."
        ./gradlew :trikeshed-uring:runDarwinDemo
        ;;
    2)
        echo "Running Interactive Demo..."
        ./gradlew :trikeshed-uring:runDarwinInteractiveDemo
        ;;
    3)
        echo "Running File Copy Demo..."
        ./gradlew :trikeshed-uring:runFileCopyDemo
        ;;
    *)
        echo "Invalid choice"
        exit 1
        ;;
esac