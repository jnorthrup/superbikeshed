#!/bin/bash
# Fix all broken build.gradle.kts files with a minimal template

TEMPLATE='plugins {
    kotlin("multiplatform")
}

group = "borg.trikeshed"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    
    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}'

for file in $(find . -name "build.gradle.kts" -type f -not -path "./build.gradle.kts"); do
    echo "Fixing $file"
    echo "$TEMPLATE" > "$file"
done
