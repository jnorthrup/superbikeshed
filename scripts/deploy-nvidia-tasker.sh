#!/usr/bin/env bash
set -euo pipefail

# NVIDIA Tasker Deployment Script
# Builds shadow jar and deploys to PREFIX like a proper Unix tool

PREFIX="${PREFIX:-$HOME/.local}"
BIN_DIR="$PREFIX/bin"
LIB_DIR="$PREFIX/lib"
SHARE_DIR="$PREFIX/share/nvidia-tasker"

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR_NAME="nvidia-tasker-all.jar"
SCRIPT_NAME="nvidia-tasker"

echo "=== NVIDIA Tasker Deployment ==="
echo "PREFIX: $PREFIX"
echo "Project: $PROJECT_ROOT"
echo

# Create directories
mkdir -p "$BIN_DIR" "$LIB_DIR" "$SHARE_DIR"

# Build shadow jar (fat jar with all dependencies)
echo "Building shadow jar..."
cd "$PROJECT_ROOT"

# Try to build the executable jar we defined
if ./gradlew :nexus:executableJar --console=plain --no-daemon 2>/dev/null; then
    BUILT_JAR="nexus/build/libs/nexus-executable.jar"
elif ./gradlew :nexus:shadowJar --console=plain --no-daemon 2>/dev/null; then
    BUILT_JAR="nexus/build/libs/nexus-all.jar"
else
    echo "ERROR: Failed to build shadow jar. Trying manual assembly..."
    
    # Manual jar assembly as fallback
    TEMP_DIR=$(mktemp -d)
    
    # Find all dependency JARs
    echo "Collecting dependencies..."
    ./gradlew :nexus:dependencies --configuration runtimeClasspath | \
        grep -E '\.jar$' | \
        sed 's/.*-> //' | \
        sort -u > "$TEMP_DIR/deps.txt"
    
    # Extract nexus classes
    if [ -f "nexus/build/libs/nexus-jvm.jar" ]; then
        cd "$TEMP_DIR"
        jar xf "$PROJECT_ROOT/nexus/build/libs/nexus-jvm.jar"
        
        # Extract dependencies
        while read -r jar; do
            if [ -f "$jar" ]; then
                echo "Extracting: $(basename "$jar")"
                jar xf "$jar"
            fi
        done < deps.txt
        
        # Create shadow jar
        jar cfe "$PROJECT_ROOT/$JAR_NAME" nexus.launcher.NvidiaTaskerLauncher .
        cd "$PROJECT_ROOT"
        BUILT_JAR="$JAR_NAME"
    else
        echo "ERROR: No compiled classes found. Run './gradlew :nexus:build' first"
        exit 1
    fi
fi

if [ ! -f "$BUILT_JAR" ]; then
    echo "ERROR: Shadow jar not found at $BUILT_JAR"
    exit 1
fi

# Deploy jar to lib directory
echo "Deploying jar..."
cp "$BUILT_JAR" "$LIB_DIR/$JAR_NAME"

# Create launcher script
echo "Creating launcher script..."
cat > "$BIN_DIR/$SCRIPT_NAME" << 'EOF'
#!/usr/bin/env bash

# NVIDIA Tasker Launcher
# Auto-generated deployment script

# Determine installation directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$(dirname "$SCRIPT_DIR")/lib"
JAR_PATH="$LIB_DIR/nvidia-tasker-all.jar"

# Check if jar exists
if [ ! -f "$JAR_PATH" ]; then
    echo "ERROR: NVIDIA Tasker jar not found at $JAR_PATH"
    echo "Run: deploy-nvidia-tasker.sh"
    exit 1
fi

# Java runtime detection
if command -v java >/dev/null 2>&1; then
    JAVA_CMD="java"
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
else
    echo "ERROR: Java runtime not found"
    echo "Please install Java 17+ or set JAVA_HOME"
    exit 1
fi

# Memory settings
JAVA_OPTS="${JAVA_OPTS:--Xmx2g -Xms512m}"

# Launch with all arguments passed through
exec "$JAVA_CMD" $JAVA_OPTS -jar "$JAR_PATH" "$@"
EOF

# Make launcher executable
chmod +x "$BIN_DIR/$SCRIPT_NAME"

# Create uninstall script
cat > "$SHARE_DIR/uninstall.sh" << EOF
#!/usr/bin/env bash
echo "Uninstalling NVIDIA Tasker..."
rm -f "$BIN_DIR/$SCRIPT_NAME"
rm -f "$LIB_DIR/$JAR_NAME"
rm -rf "$SHARE_DIR"
echo "NVIDIA Tasker uninstalled from $PREFIX"
EOF
chmod +x "$SHARE_DIR/uninstall.sh"

# Installation summary
echo
echo "=== Installation Complete ==="
echo "Launcher: $BIN_DIR/$SCRIPT_NAME"
echo "Jar: $LIB_DIR/$JAR_NAME"
echo "Uninstall: $SHARE_DIR/uninstall.sh"
echo

# Test installation
if [ -x "$BIN_DIR/$SCRIPT_NAME" ]; then
    echo "Testing installation..."
    if "$BIN_DIR/$SCRIPT_NAME" --help >/dev/null 2>&1; then
        echo "✓ Installation successful!"
        echo
        echo "Usage examples:"
        echo "  $SCRIPT_NAME --task 'Fix compilation errors'"
        echo "  $SCRIPT_NAME --task 'Optimize performance' --rawdog"
        echo "  $SCRIPT_NAME --task 'Add new feature' --quiet"
    else
        echo "✗ Installation failed - launcher script error"
        exit 1
    fi
else
    echo "✗ Installation failed - launcher not executable"
    exit 1
fi

# PATH warning
if ! echo "$PATH" | grep -q "$BIN_DIR"; then
    echo
    echo "WARNING: $BIN_DIR is not in your PATH"
    echo "Add this to your shell profile:"
    echo "  export PATH=\"$BIN_DIR:\$PATH\""
fi

echo
echo "=== Deployment Summary ==="
echo "Prefix: $PREFIX"
echo "Binary: $BIN_DIR/$SCRIPT_NAME"
echo "Library: $LIB_DIR/$JAR_NAME"
echo "Size: $(du -h "$LIB_DIR/$JAR_NAME" | cut -f1)"
EOF