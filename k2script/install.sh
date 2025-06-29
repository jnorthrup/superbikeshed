#!/bin/bash
set -e

echo "🎱 K2Script Installation"
echo "======================="

# Default installation directory
INSTALL_DIR="${K2SCRIPT_HOME:-$HOME/.k2script}"
BIN_DIR="$INSTALL_DIR/bin"
LIB_DIR="$INSTALL_DIR/lib"

echo "Installing K2Script to: $INSTALL_DIR"

# Create installation directories
mkdir -p "$BIN_DIR"
mkdir -p "$LIB_DIR"

# Build K2Script if needed
if [ ! -f "build/dist/k2script.jar" ]; then
    echo "Building K2Script..."
    cd /Users/jim/work/v2superbikeshed
    ./gradlew :k2script:compileKotlinJvm
    
    # Create the JAR manually since gradle tasks are being skipped
    cd k2script/build
    echo -e "Manifest-Version: 1.0\nMain-Class: k2script.jvm.SimpleJvmEngineKt" > MANIFEST.MF
    mkdir -p dist
    jar cvfm dist/k2script.jar MANIFEST.MF -C classes/kotlin/jvm/main . || {
        echo "Error: Failed to create JAR"
        exit 1
    }
    cd ../..
fi

# Copy JAR to lib directory
echo "Installing K2Script JAR..."
cp k2script/build/dist/k2script.jar "$LIB_DIR/"

# Create the k2script executable
echo "Creating k2script executable..."
cat > "$BIN_DIR/k2script" << 'EOF'
#!/bin/bash
# K2Script - The Cue Ball

# Find K2Script home
if [ -z "$K2SCRIPT_HOME" ]; then
    # Resolve script location
    SOURCE="${BASH_SOURCE[0]}"
    while [ -h "$SOURCE" ]; do
        DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
        SOURCE="$(readlink "$SOURCE")"
        [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE"
    done
    K2SCRIPT_HOME="$( cd -P "$( dirname "$SOURCE" )/.." && pwd )"
fi

# Find Java
if [ -z "$JAVA_HOME" ]; then
    JAVA_CMD="java"
else
    JAVA_CMD="$JAVA_HOME/bin/java"
fi

# Find Kotlin libraries if KOTLIN_HOME is set
CLASSPATH="$K2SCRIPT_HOME/lib/k2script.jar"
if [ -n "$KOTLIN_HOME" ]; then
    for jar in "$KOTLIN_HOME"/lib/kotlin-*.jar; do
        if [ -f "$jar" ]; then
            CLASSPATH="$CLASSPATH:$jar"
        fi
    done
fi

# Add any additional JARs from lib directory
for jar in "$K2SCRIPT_HOME"/lib/*.jar; do
    if [ -f "$jar" ] && [ "$jar" != "$K2SCRIPT_HOME/lib/k2script.jar" ]; then
        CLASSPATH="$CLASSPATH:$jar"
    fi
done

# Execute K2Script
exec "$JAVA_CMD" -cp "$CLASSPATH" k2script.jvm.SimpleJvmEngineKt "$@"
EOF

chmod +x "$BIN_DIR/k2script"

# Download minimal Kotlin runtime dependencies if needed
echo "Checking for Kotlin runtime dependencies..."
if [ ! -f "$LIB_DIR/kotlin-stdlib.jar" ]; then
    echo "Downloading Kotlin runtime..."
    curl -L -o "$LIB_DIR/kotlin-stdlib.jar" \
        "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/1.9.0/kotlin-stdlib-1.9.0.jar" 2>/dev/null || {
        echo "Warning: Could not download kotlin-stdlib.jar"
    }
    
    curl -L -o "$LIB_DIR/kotlinx-coroutines-core.jar" \
        "https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.7.3/kotlinx-coroutines-core-jvm-1.7.3.jar" 2>/dev/null || {
        echo "Warning: Could not download kotlinx-coroutines-core.jar"
    }
fi

# Add to PATH instructions
echo ""
echo "✅ K2Script installed successfully!"
echo ""
echo "To add k2script to your PATH, add this line to your shell config:"
echo ""
echo "  export PATH=\"$BIN_DIR:\$PATH\""
echo ""
echo "For bash (~/.bashrc or ~/.bash_profile):"
echo "  echo 'export PATH=\"$BIN_DIR:\$PATH\"' >> ~/.bashrc"
echo ""
echo "For zsh (~/.zshrc):"
echo "  echo 'export PATH=\"$BIN_DIR:\$PATH\"' >> ~/.zshrc"
echo ""
echo "Then reload your shell or run:"
echo "  source ~/.bashrc  # or ~/.zshrc"
echo ""
echo "Test with:"
echo "  k2script --version"