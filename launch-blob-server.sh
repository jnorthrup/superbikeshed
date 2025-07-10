#!/bin/bash
# EMERGENCY BLOB SERVER LAUNCHER
# "Jump first, build parachute on the way down"

echo "🚀 EMERGENCY BLOB SERVER LAUNCH SEQUENCE"
echo "======================================="
echo ""
echo "🪂 Building the airplane while falling..."
echo ""

# Create emergency directories
mkdir -p data logs

# Just run it - figure out the rest later
echo "⚡ Compiling and launching in one shot..."

# Try to compile and run
if command -v kotlin &> /dev/null; then
    echo "🏃 Running with Kotlin compiler..."
    kotlin platform-launcher/src/main/kotlin/borg/trikeshed/launcher/LaunchFiduciary.kt
elif [ -f "./gradlew" ]; then
    echo "🏃 Running with Gradle..."
    ./gradlew :platform-launcher:run --args="emergency" 2>&1 | tee logs/emergency-$(date +%s).log
else
    echo "🔨 Attempting raw Java compilation..."
    # Last resort - try to compile with javac
    find . -name "*.kt" -type f | head -20 > files.txt
    kotlinc @files.txt -d emergency-build.jar
    java -cp emergency-build.jar borg.trikeshed.launcher.LaunchFiduciary
fi

# If we get here, we crashed
echo ""
echo "💥 Blob server has landed (crashed)"
echo "📋 Check logs/ directory for details"