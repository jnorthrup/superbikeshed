#!/usr/bin/env bash
# K2script standalone launcher
# Resolves dependencies from ~/.m2 and runs Kotlin scripts

set -euo pipefail

SCRIPT_FILE="$1"
shift

if [[ ! -f "$SCRIPT_FILE" ]]; then
    echo "Error: Script not found: $SCRIPT_FILE"
    exit 1
fi

# Parse @file:DependsOn annotations
DEPS=$(grep "@file:DependsOn" "$SCRIPT_FILE" | sed -E 's/@file:DependsOn\("([^"]+)"\)/\1/g')

# Build classpath from ~/.m2
CLASSPATH=""
M2_REPO="$HOME/.m2/repository"

for DEP in $DEPS; do
    IFS=':' read -r GROUP ARTIFACT VERSION <<< "$DEP"
    GROUP_PATH=$(echo "$GROUP" | tr '.' '/')
    JAR_PATH="$M2_REPO/$GROUP_PATH/$ARTIFACT/$VERSION/$ARTIFACT-$VERSION.jar"
    
    if [[ -f "$JAR_PATH" ]]; then
        CLASSPATH="$CLASSPATH:$JAR_PATH"
    else
        echo "Warning: Dependency not found: $JAR_PATH"
        echo "Fetching with Maven..."
        mvn dependency:get -DgroupId=$GROUP -DartifactId=$ARTIFACT -Dversion=$VERSION -DremoteRepositories=https://repo.maven.apache.org/maven2 2>/dev/null || true
    fi
done

# Add Kotlin runtime
KOTLIN_LIB="$M2_REPO/org/jetbrains/kotlin/kotlin-stdlib/1.9.0/kotlin-stdlib-1.9.0.jar"
KOTLIN_SCRIPT="$M2_REPO/org/jetbrains/kotlin/kotlin-script-runtime/1.9.0/kotlin-script-runtime-1.9.0.jar"

# Run the script
exec kotlin -classpath "$CLASSPATH:$KOTLIN_LIB:$KOTLIN_SCRIPT" "$SCRIPT_FILE" "$@"