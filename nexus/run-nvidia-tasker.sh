#!/bin/bash
# Run nvidia-tasker with proper serialization plugin

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Find kotlinx-serialization-core jar
SERIALIZATION_JAR=$(find ~/.m2/repository/org/jetbrains/kotlinx/kotlinx-serialization-core -name "*.jar" | grep -v sources | grep -v javadoc | head -1)

if [ -z "$SERIALIZATION_JAR" ]; then
    echo "Downloading kotlinx-serialization..."
    mkdir -p /tmp/kotlin-libs
    cd /tmp/kotlin-libs
    curl -LO https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-core-jvm/1.6.0/kotlinx-serialization-core-jvm-1.6.0.jar
    curl -LO https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json-jvm/1.6.0/kotlinx-serialization-json-jvm-1.6.0.jar
    SERIALIZATION_JAR="/tmp/kotlin-libs/kotlinx-serialization-core-jvm-1.6.0.jar:/tmp/kotlin-libs/kotlinx-serialization-json-jvm-1.6.0.jar"
fi

# Run with serialization plugin
exec kotlin \
    -Xplugin=/usr/local/lib/kotlin/lib/kotlinx-serialization-compiler-plugin.jar \
    -cp "$SERIALIZATION_JAR" \
    "$SCRIPT_DIR/nvidia-tasker.main.kts" "$@"