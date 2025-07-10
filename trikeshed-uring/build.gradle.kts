plugins {
    kotlin("multiplatform")
}

group = "borg.trikeshed"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    
    // Native targets with library export
    macosX64 {
        binaries {
            // Export as static library
            staticLib {
                baseName = "uring_darwin"
                export(project(":trikeshed-lib"))
                export(project(":trikeshed-ccek"))
            }
            
            // Export as dynamic library
            sharedLib {
                baseName = "uring_darwin"
                export(project(":trikeshed-lib"))
                export(project(":trikeshed-ccek"))
            }
            
            // Export C headers
            compilations["main"].cinterops {
                val liburing by creating {
                    defFile = file("src/nativeInterop/cinterop/liburing.def")
                    includeDirs.headerFilterOnly("/usr/include", "/usr/local/include")
                }
            }
        }
    }
    
    macosArm64 {
        binaries {
            // Export as static library
            staticLib {
                baseName = "uring_darwin"
                export(project(":trikeshed-lib"))
                export(project(":trikeshed-ccek"))
            }
            
            // Export as dynamic library  
            sharedLib {
                baseName = "uring_darwin"
                export(project(":trikeshed-lib"))
                export(project(":trikeshed-ccek"))
            }
            
            // Export C headers
            compilations["main"].cinterops {
                val liburing by creating {
                    defFile = file("src/nativeInterop/cinterop/liburing.def")
                    includeDirs.headerFilterOnly("/usr/include", "/usr/local/include")
                }
            }
        }
    }
    
    linuxX64 {
        binaries {
            staticLib {
                baseName = "uring_real"
            }
            sharedLib {
                baseName = "uring_real"
            }
        }
    }
    
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":trikeshed-lib"))
                implementation(project(":trikeshed-ccek"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }
        
        val darwinMain by creating {
            dependsOn(commonMain.get())
        }
        
        val macosX64Main by getting {
            dependsOn(darwinMain)
        }
        
        val macosArm64Main by getting {
            dependsOn(darwinMain)
        }
        
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
    }
}