#!/bin/bash
set -e

echo "Creating K2Script standalone executable..."

# Ensure build directory exists
mkdir -p k2script/build/dist

# Get K2Script JAR location
K2SCRIPT_JAR="k2script/build/dist/k2script.jar"

# Create wrapper script that includes classpath
cat > k2script/build/dist/k2script << 'EOF'
#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Find Kotlin installation
if [ -z "$KOTLIN_HOME" ]; then
    # Try common locations
    if [ -d "/usr/local/kotlin" ]; then
        KOTLIN_HOME="/usr/local/kotlin"
    elif [ -d "$HOME/.sdkman/candidates/kotlin/current" ]; then
        KOTLIN_HOME="$HOME/.sdkman/candidates/kotlin/current"
    else
        echo "Error: KOTLIN_HOME not set and Kotlin installation not found"
        echo "Please install Kotlin or set KOTLIN_HOME"
        exit 1
    fi
fi

# Build classpath
CP="$DIR/k2script.jar"
if [ -d "$KOTLIN_HOME/lib" ]; then
    CP="$CP:$KOTLIN_HOME/lib/kotlin-stdlib.jar"
    CP="$CP:$KOTLIN_HOME/lib/kotlin-script-runtime.jar"
fi

# Run K2Script
java -cp "$CP" k2script.jvm.SimpleJvmEngineKt "$@"
EOF

chmod +x k2script/build/dist/k2script

echo "Executable created: k2script/build/dist/k2script"
echo ""
echo "To run K2Script:"
echo "  ./k2script/build/dist/k2script <script.kts> [args...]"
echo ""
echo "Or add to PATH:"
echo "  export PATH=\$PATH:$(pwd)/k2script/build/dist"