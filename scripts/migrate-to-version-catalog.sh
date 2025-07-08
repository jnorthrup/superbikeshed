#!/bin/bash
# Migrate to Gradle Version Catalog (recommended approach)

echo "=== Migrating to Gradle Version Catalog ==="

# Check if libs.versions.toml exists
VERSIONS_FILE="gradle/libs.versions.toml"
if [ -f "$VERSIONS_FILE" ]; then
    echo "Found existing $VERSIONS_FILE - will update it"
else
    echo "Creating new $VERSIONS_FILE"
    mkdir -p gradle
fi

# Collect all unique versions from the project
echo "Scanning project for version declarations..."

# Create comprehensive version catalog
cat > "$VERSIONS_FILE" << 'EOF'
[versions]
# Kotlin
kotlin = "2.2.0"
kotlinx-coroutines = "1.10.2"
kotlinx-serialization = "1.9.0"
kotlinx-datetime = "0.7.0-0.6.x-compat"

# Apache Commons
commons-cli = "1.9.0"
commons-codec = "1.18.0"
commons-io = "2.19.0"
commons-lang3 = "3.17.0"

# Ktor
ktor = "2.3.8"

# Testing
junit = "4.13.2"

# Build tools
gradle-plugin = "8.14.2"

[libraries]
# Kotlin core
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }

# Kotlinx
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
kotlinx-serialization-core = { module = "org.jetbrains.kotlinx:kotlinx-serialization-core", version.ref = "kotlinx-serialization" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinx-datetime" }

# Apache Commons
commons-cli = { module = "commons-cli:commons-cli", version.ref = "commons-cli" }
commons-codec = { module = "commons-codec:commons-codec", version.ref = "commons-codec" }
commons-io = { module = "commons-io:commons-io", version.ref = "commons-io" }
commons-lang3 = { module = "org.apache.commons:commons-lang3", version.ref = "commons-lang3" }

# Ktor
ktor-server-core = { module = "io.ktor:ktor-server-core", version.ref = "ktor" }
ktor-server-netty = { module = "io.ktor:ktor-server-netty", version.ref = "ktor" }
ktor-server-content-negotiation = { module = "io.ktor:ktor-server-content-negotiation", version.ref = "ktor" }
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }

# Testing
junit = { module = "junit:junit", version.ref = "junit" }

[bundles]
# Common bundles
ktor-server = ["ktor-server-core", "ktor-server-netty", "ktor-server-content-negotiation", "ktor-serialization-kotlinx-json"]
ktor-client = ["ktor-client-core", "ktor-client-cio", "ktor-serialization-kotlinx-json"]

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
EOF

echo "Created version catalog at $VERSIONS_FILE"

# Now update build files to use the catalog
echo "Updating build files to use version catalog..."

# Update root settings.gradle.kts to enable version catalogs
if ! grep -q "enableFeaturePreview.*VERSION_CATALOGS" settings.gradle.kts 2>/dev/null; then
    echo "enableFeaturePreview(\"VERSION_CATALOGS\")" >> settings.gradle.kts
fi

# Function to update build.gradle.kts files
update_build_file() {
    local file=$1
    echo "Updating: $file"
    
    # Create temporary file with updates
    gsed -E '
        # Update plugin declarations (in plugins block)
        s/id\("org\.jetbrains\.kotlin\.multiplatform"\) version "[^"]+"/alias(libs.plugins.kotlin.multiplatform)/g
        s/id\("org\.jetbrains\.kotlin\.jvm"\) version "[^"]+"/alias(libs.plugins.kotlin.jvm)/g
        s/kotlin\("multiplatform"\) version "[^"]+"/alias(libs.plugins.kotlin.multiplatform)/g
        
        # Update dependencies
        s/implementation\("org\.jetbrains\.kotlinx:kotlinx-coroutines-core:[^"]+"\)/implementation(libs.kotlinx.coroutines.core)/g
        s/implementation\("org\.jetbrains\.kotlinx:kotlinx-coroutines-test:[^"]+"\)/testImplementation(libs.kotlinx.coroutines.test)/g
        s/implementation\("org\.jetbrains\.kotlinx:kotlinx-serialization-core:[^"]+"\)/implementation(libs.kotlinx.serialization.core)/g
        s/implementation\("org\.jetbrains\.kotlinx:kotlinx-serialization-json:[^"]+"\)/implementation(libs.kotlinx.serialization.json)/g
        s/implementation\("org\.jetbrains\.kotlinx:kotlinx-datetime:[^"]+"\)/implementation(libs.kotlinx.datetime)/g
        
        # Commons libraries
        s/implementation\("commons-cli:commons-cli:[^"]+"\)/implementation(libs.commons.cli)/g
        s/implementation\("commons-codec:commons-codec:[^"]+"\)/implementation(libs.commons.codec)/g
        s/implementation\("commons-io:commons-io:[^"]+"\)/implementation(libs.commons.io)/g
        s/implementation\("org\.apache\.commons:commons-lang3:[^"]+"\)/implementation(libs.commons.lang3)/g
        
        # Ktor
        s/implementation\("io\.ktor:ktor-server-core:[^"]+"\)/implementation(libs.ktor.server.core)/g
        s/implementation\("io\.ktor:ktor-server-netty:[^"]+"\)/implementation(libs.ktor.server.netty)/g
        s/implementation\("io\.ktor:ktor-client-core:[^"]+"\)/implementation(libs.ktor.client.core)/g
        
        # Testing
        s/testImplementation\("junit:junit:[^"]+"\)/testImplementation(libs.junit)/g
    ' "$file" > "${file}.tmp"
    
    mv "${file}.tmp" "$file"
}

# Process all build files
for BUILD_FILE in $(find . -name "build.gradle.kts" -type f | grep -v ".gradle" | grep -v "build/"); do
    update_build_file "$BUILD_FILE"
done

echo ""
echo "=== Migration Complete ==="
echo "Version catalog created at: $VERSIONS_FILE"
echo "All build files have been updated to use the version catalog"
echo ""
echo "Benefits:"
echo "  - Single source of truth for versions"
echo "  - Type-safe dependency references"
echo "  - IDE auto-completion support"
echo "  - Easy dependency updates"
echo ""
echo "To update a version, edit: $VERSIONS_FILE"
echo "To see available updates: ./gradlew dependencyUpdates"