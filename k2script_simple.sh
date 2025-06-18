#!/bin/bash

# K2Script - Ultra Simple Kotlin Scripting
# Just a smart kotlinc wrapper - no compilation needed!

if [ $# -eq 0 ]; then
    echo "K2Script - Simple Kotlin scripting (kotlinc wrapper)"
    echo "Usage: k2script <script.kts> [args...]"
    echo "Features: Native access enabled, TrikeShed ready, AI integration"
    exit 1
fi

SCRIPT_FILE="$1"
shift

# Export k2script environment
export K2SCRIPT_FILE="$SCRIPT_FILE"
export K2SCRIPT_HOME="$(dirname "$0")"

# Simple java opts for modern JVMs
JAVA_OPTS="${JAVA_OPTS:--Xmx512M --enable-native-access=ALL-UNNAMED}"

# Run the script with kotlinc - clean and simple!
kotlinc -J--enable-native-access=ALL-UNNAMED -script "$SCRIPT_FILE" -- "$@"