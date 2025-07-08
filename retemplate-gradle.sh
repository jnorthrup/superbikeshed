#!/bin/bash

# Retemplate all child gradle files based on trikeshed-lib pattern

TEMPLATE='@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    kotlin("multiplatform")
}

group = "GROUP_PLACEHOLDER"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    wasmJs {
        nodejs()
    }
    
    // Native targets based on host OS
    val hostOs = System.getProperty("os.name")
    when {
        hostOs == "Mac OS X" -> {
            macosX64()
            macosArm64()
        }
        hostOs == "Linux" -> {
            linuxX64()
            linuxArm64()
        }
    }
    
    sourceSets {
        commonMain {
            dependencies {
                DEPENDENCIES_PLACEHOLDER
            }
        }
        
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
'

# Function to extract group from existing file
get_group() {
    grep -E "^group\s*=" "$1" | sed 's/group\s*=\s*//g' | tr -d '"'
}

# Function to extract dependencies from existing file
get_dependencies() {
    local file=$1
    awk '/commonMain \{/,/\}/ { if (/dependencies \{/) flag=1; if (flag && /implementation/) print "                " $0; if (/\}/ && flag) flag=0 }' "$file" | grep -v "^$"
}

# Process each module
for module in trikeshed-reactor trikeshed-net trikeshed-common trikeshed-io kotlinx-serialization-wireproto kotlin-entity-scanner moneyfan; do
    gradle_file="./$module/build.gradle.kts"
    
    if [ -f "$gradle_file" ]; then
        echo "Processing $module..."
        
        # Get existing group
        group=$(get_group "$gradle_file")
        if [ -z "$group" ]; then
            group="borg.trikeshed"
        fi
        
        # Get existing dependencies
        deps=$(get_dependencies "$gradle_file")
        
        # Create new file
        echo "$TEMPLATE" | sed "s/GROUP_PLACEHOLDER/$group/g" > "${gradle_file}.new"
        
        # Insert dependencies
        if [ -n "$deps" ]; then
            sed -i "/DEPENDENCIES_PLACEHOLDER/r /dev/stdin" "${gradle_file}.new" <<< "$deps"
        fi
        sed -i "/DEPENDENCIES_PLACEHOLDER/d" "${gradle_file}.new"
        
        # Check if needs serialization plugin
        if grep -q "serialization" "$gradle_file"; then
            sed -i '/kotlin("multiplatform")/a\    kotlin("plugin.serialization")' "${gradle_file}.new"
        fi
        
        # Replace old file
        mv "${gradle_file}.new" "$gradle_file"
        
        echo "✓ $module retemplated"
    fi
done

echo "All gradle files retemplated!"