#!/bin/bash
# Build fat JAR with all dependencies

echo "Building K2Script fat JAR..."

# Clean and compile
./gradlew :k2script:clean :k2script:compileKotlinJvm

# Create temp directory
mkdir -p k2script/build/fatjar
cd k2script/build/fatjar

# Extract all dependencies
echo "Extracting dependencies..."
for jar in $(find ~/.gradle/caches/modules-2/files-2.1 -name "*.jar" | grep -E "(kotlin-stdlib|kotlin-coroutines|commons-cli|commons-io|commons-codec|commons-lang3|semver4j)" | grep -v sources | grep -v javadoc); do
    echo "Extracting: $(basename $jar)"
    jar xf "$jar" 2>/dev/null || true
done

# Copy our classes
echo "Copying K2Script classes..."
cp -r ../classes/kotlin/jvm/main/* .

# Create manifest
echo -e "Manifest-Version: 1.0\nMain-Class: k2script.jvm.SimpleJvmEngineKt" > MANIFEST.MF

# Create fat JAR
echo "Creating fat JAR..."
jar cfm ../dist/k2script-fat.jar MANIFEST.MF .

# Clean up
cd ..
rm -rf fatjar

echo "Fat JAR created: k2script/build/dist/k2script-fat.jar"

# Create executable
cat > dist/k2script << 'EOF'
#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
java -jar "$DIR/k2script-fat.jar" "$@"
EOF

chmod +x dist/k2script

echo "Executable created: k2script/build/dist/k2script"