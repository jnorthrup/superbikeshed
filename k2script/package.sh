#!/bin/bash
# Quick packaging script for k2script

# Build the project
./gradlew :k2script:compileKotlinJvm

# Create output directory
mkdir -p k2script/build/dist

# Create the JAR manually
cd k2script/build
jar cvfm dist/k2script.jar - -C classes/kotlin/jvm/main . <<EOF
Manifest-Version: 1.0
Main-Class: k2script.jvm.K2ScriptStandaloneKt
EOF

# Create executable script
cat > dist/k2script <<'EOF'
#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
java -jar "$DIR/k2script.jar" "$@"
EOF

chmod +x dist/k2script

echo "K2Script packaged in: k2script/build/dist/"
echo "Run with: k2script/build/dist/k2script <script.kts>"