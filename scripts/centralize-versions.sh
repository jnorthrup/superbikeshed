#!/bin/bash
# Script to centralize all version declarations to root build.gradle.kts

echo "=== Centralizing Version Declarations ==="

# Create version catalog in root build.gradle.kts
ROOT_BUILD="build.gradle.kts"

# Backup root build file
cp $ROOT_BUILD ${ROOT_BUILD}.backup

# Extract unique versions from all child projects
echo "Extracting versions from child projects..."

# Find all unique library versions
VERSIONS=$(find . -name "build.gradle.kts" -type f | \
    xargs grep -h "implementation.*:" 2>/dev/null | \
    grep -E "[0-9]+\.[0-9]+" | \
    sed -E 's/.*"([^:]+):([^:]+):([^"]+)".*/\1:\2:\3/' | \
    sort -u)

# Create versions block
cat > versions.tmp << 'EOF'
// Centralized version declarations
extra["versions"] = mapOf(
    // Kotlin and core libraries
    "kotlin" to "2.2.0",
    "kotlinx-coroutines" to "1.10.2",
    "kotlinx-serialization" to "1.9.0", 
    "kotlinx-datetime" to "0.7.0-0.6.x-compat",
    
    // Apache Commons
    "commons-cli" to "1.9.0",
    "commons-codec" to "1.18.0",
    "commons-io" to "2.19.0",
    "commons-lang3" to "3.17.0",
    
    // Testing
    "junit" to "4.13.2",
    
    // Other libraries
    "ktor" to "2.3.8"
)

// Helper function to get version
fun version(lib: String): String = (extra["versions"] as Map<String, String>)[lib] ?: error("Version not found: $lib")

EOF

# Insert versions block after plugins section in root build.gradle.kts
echo "Adding version catalog to root build.gradle.kts..."
awk '
/^plugins/ { in_plugins = 1 }
in_plugins && /^}/ { 
    in_plugins = 0
    print
    print ""
    while ((getline line < "versions.tmp") > 0) print line
    next
}
{ print }
' $ROOT_BUILD > ${ROOT_BUILD}.new
mv ${ROOT_BUILD}.new $ROOT_BUILD
rm versions.tmp

# Process each child project
echo "Processing child projects..."

for BUILD_FILE in $(find . -name "build.gradle.kts" -type f | grep -E "(trikeshed-|nexus|moneyfan|ta4k|spacegraph|rtsgame)" | grep -v "^./build.gradle.kts"); do
    echo "Processing: $BUILD_FILE"
    
    # Replace versioned dependencies with version references
    gsed -i.bak -E '
        # Kotlin coroutines
        s/("org\.jetbrains\.kotlinx:kotlinx-coroutines-[^:]+):([0-9]+\.[0-9]+\.[0-9]+[^"]*)/\1:\${rootProject.version("kotlinx-coroutines")}/g
        
        # Kotlin serialization
        s/("org\.jetbrains\.kotlinx:kotlinx-serialization-[^:]+):([0-9]+\.[0-9]+\.[0-9]+[^"]*)/\1:\${rootProject.version("kotlinx-serialization")}/g
        
        # Kotlin datetime
        s/("org\.jetbrains\.kotlinx:kotlinx-datetime):([0-9]+\.[0-9]+\.[0-9]+[^"]*)/\1:\${rootProject.version("kotlinx-datetime")}/g
        
        # Commons libraries
        s/("commons-cli:commons-cli):([0-9]+\.[0-9]+\.[0-9]+[^"]*)/\1:\${rootProject.version("commons-cli")}/g
        s/("commons-codec:commons-codec):([0-9]+\.[0-9]+\.[0-9]+[^"]*)/\1:\${rootProject.version("commons-codec")}/g
        s/("commons-io:commons-io):([0-9]+\.[0-9]+\.[0-9]+[^"]*)/\1:\${rootProject.version("commons-io")}/g
        s/("org\.apache\.commons:commons-lang3):([0-9]+\.[0-9]+\.[0-9]+[^"]*)/\1:\${rootProject.version("commons-lang3")}/g
        
        # Ktor
        s/("io\.ktor:[^:]+):([0-9]+\.[0-9]+\.[0-9]+[^"]*)/\1:\${rootProject.version("ktor")}/g
    ' "$BUILD_FILE"
    
    # Clean up backup
    rm -f "${BUILD_FILE}.bak"
done

echo "=== Version Centralization Complete ==="
echo "All versions have been moved to root build.gradle.kts"
echo "Child projects now reference versions using \${rootProject.version(\"library-name\")}"
echo ""
echo "To verify the changes:"
echo "  ./gradlew build --dry-run"