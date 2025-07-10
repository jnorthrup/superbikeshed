buildscript {
    repositories {
        mavenCentral()
        google()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/kotlin/kotlin-mpp") }
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.21")
    }
}

// Versions are managed in gradle.properties

plugins { 
    kotlin("multiplatform")
    id("com.github.ben-manes.versions") version "0.51.0"
}

// Apply build policy plugin
apply<buildtools.BuildPolicyPlugin>()

group = "borg.trikeshed"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
}




