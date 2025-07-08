plugins {
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
                implementation(project(":trikeshed-lib"))
                implementation(project(":trikeshed-channel-api"))
                implementation(project(":trikeshed-reactor"))
                implementation(project(":trikeshed-couchdb"))
            }
        }
        commonTest {
            dependencies {
                implementation(project(":trikeshed-lib"))
                implementation(kotlin("test"))
            }
        }
    }
}
