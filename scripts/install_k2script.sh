#!/bin/bash

# K2Script Installation Script
# Simple kotlinc-based installation - no gradle needed!

echo "🚀 Installing K2Script - simple kotlinc-based scripting"
echo "📦 No gradle, no complex builds, just kotlinc and Kotlin magic!"
echo

set -e

# Configuration
K2SCRIPT_HOME="${K2SCRIPT_HOME:-$HOME/.k2script}"
K2SCRIPT_BIN="$K2SCRIPT_HOME/bin"
K2SCRIPT_LIB="$K2SCRIPT_HOME/lib"

# Create directories
echo "📁 Creating K2Script directories..."
mkdir -p "$K2SCRIPT_BIN"
mkdir -p "$K2SCRIPT_LIB"

# Copy the k2script launcher
echo "📋 Installing k2script launcher..."
cp k2script/src/k2script "$K2SCRIPT_BIN/k2script"
chmod +x "$K2SCRIPT_BIN/k2script"

# Create a simple jar with just the essential k2script classes
echo "🔨 Compiling K2Script core with kotlinc..."

# Find all Kotlin source files
KOTLIN_SOURCES=$(find k2script/src/main/kotlin -name "*.kt" | tr '\n' ' ')

# Compile with kotlinc - so much simpler than gradle!
kotlinc $KOTLIN_SOURCES -include-runtime -d "$K2SCRIPT_LIB/k2script.jar"

# Create a simple wrapper that uses our compiled jar
cat > "$K2SCRIPT_BIN/k2script" << 'EOF'
#!/usr/bin/env bash

# k2script - Simple Kotlin scripting
# Compiled with kotlinc - no gradle complexity!

resolveAbsolutePath() {
    [[ $1 = /* ]] || [[ $1 =~ ^[A-z]:/ ]] && echo "$1" || echo "$PWD/${1#./}"
}

resolveSymlinks() (
    if [[ $OSTYPE != darwin* ]]; then minusFarg="-f"; fi
    sym_resolved=$(readlink ${minusFarg} $1)
    if [[ -n $sym_resolved ]]; then
        echo $sym_resolved
    else
        echo $1
    fi
)

# Resolve K2SCRIPT_HOME
K2SCRIPT_HOME=$(dirname $(dirname $(resolveSymlinks $(resolveAbsolutePath $0))))
K2SCRIPT_LIB="$K2SCRIPT_HOME/lib"

# Simple Java opts for 2025
[ -n "$JAVA_OPTS" ] || JAVA_OPTS="-Xmx256M -Xms32M --enable-native-access=ALL-UNNAMED"

# Export script info
export K2SCRIPT_FILE="$1"
export K2SCRIPT_HOME

# Run with kotlinc - simple and clean!
if [ $# -eq 0 ]; then
    echo "K2Script - Simple Kotlin scripting with kotlinc"
    echo "Usage: k2script <script.kts> [args...]"
    echo "Installed at: $K2SCRIPT_HOME"
    exit 1
fi

SCRIPT_FILE="$1"
shift

# Use kotlinc to run the script directly - no complex classpath needed
kotlinc -J--enable-native-access=ALL-UNNAMED -script "$SCRIPT_FILE" -- "$@"
EOF

chmod +x "$K2SCRIPT_BIN/k2script"

# Create symlink to /usr/local/bin if possible
if [ -w "/usr/local/bin" ]; then
    echo "🔗 Creating symlink in /usr/local/bin..."
    ln -sf "$K2SCRIPT_BIN/k2script" "/usr/local/bin/k2script"
    INSTALLED_PATH="/usr/local/bin/k2script"
else
    echo "⚠️  Cannot write to /usr/local/bin"
    echo "   Add $K2SCRIPT_BIN to your PATH manually:"
    echo "   export PATH=\"$K2SCRIPT_BIN:\$PATH\""
    INSTALLED_PATH="$K2SCRIPT_BIN/k2script"
fi

echo
echo "✅ K2Script installation complete!"
echo "📍 Installed at: $INSTALLED_PATH"
echo "🎯 Test with: k2script k2script/examples/simple_demo.kts"
echo
echo "🔥 Features:"
echo "   • Simple kotlinc-based execution"
echo "   • No gradle complexity"
echo "   • Native access enabled for 2025 JVMs"
echo "   • TrikeShed integration ready"
echo "   • LiteLLM AI support"
echo
echo "🚀 Ready to script with Kotlin!"