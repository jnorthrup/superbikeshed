# Trikeshed Minimal JSON Serialization (KMP) Project

## Purpose

This project serves as a minimal Kotlin Multiplatform (KMP) example and specification for how core Trikeshed data structures should be serialized to and deserialized from JSON using the `kotlinx.serialization` library. It is designed to be a reference for other components or projects that need to interact with these Trikeshed types in JSON format across multiple platforms.

This project is "non-dependent" in the sense that it only includes the minimal necessary code from Trikeshed and focuses solely on the JSON serialization aspect with `kotlinx.serialization`.

## Core Data Structures

The primary data structures handled in this project (defined in `src/commonMain/kotlin`):

- `org.example.trikeshedminimaljson.DealProxy`: Represents a deal.
- `org.example.trikeshedminimaljson.VendorProxy`: Represents a vendor.
- `org.example.trikeshedminimaljson.CouchTxProxy`: Represents a transaction result.

## JSON Serialization Strategy

Serialization is handled using `kotlinx.serialization.json.Json` (from `org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3`). A pre-configured instance, `AppJson`, is available in `org.example.trikeshedminimaljson.JsonSerialization.kt` (in `commonMain`).
- `prettyPrint` is set to `false` to ensure stable JSON string output for tests.
- `encodeDefaults` is set to `false`.

Custom serializers (`DealProxySerializer`, `VendorProxySerializer`) are used for `DealProxy` and `VendorProxy` respectively, to:
- Map the `id` field in Kotlin to `_id` in JSON.
- Conditionally include `_id` in JSON only if `id` is not empty.
- Inject a `type` field ("deal" or "vendor") during serialization and validate it during deserialization.

`CouchTxProxy` uses the default serialization mechanisms.

## Kotlin Multiplatform Configuration

This project is built using Kotlin Multiplatform (Kotlin version `2.0.0`). It targets:
- JVM (Toolchain version 21)
- JavaScript (IR for browser and Node.js)
- A host-specific Native target (e.g., linuxX64, macosArm64)
- WasmJs support was temporarily removed during troubleshooting but can be re-added.

The core logic, models, and tests are defined in `commonMain` and `commonTest` respectively.

## Building and Testing

This is a standard Kotlin Multiplatform Gradle project.
- Kotlin Version: `2.0.0`
- kotlinx.serialization Version: `1.6.3` (core and json)
- kotlinx.coroutines Version: `1.8.0`
- kotlinx.datetime Version: `0.6.0`

**Note**: The Gradle wrapper (`gradlew`) might need to be generated if not present (`gradle wrapper`). The following commands assume `gradle` is available or `gradlew` has been generated and made executable.

To build the project and run tests on all configured targets (that can run on the current host):
`gradle check` or `./gradlew check`

To run tests specifically on the JVM target:
`gradle jvmTest` or `./gradlew jvmTest`

The unit tests in `src/commonTest/kotlin/org/example/trikeshedminimaljson/JsonSerializationTest.kt` verify the serialization and deserialization logic. All tests pass on the JVM platform.
