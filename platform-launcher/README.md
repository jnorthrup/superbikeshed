# Platform Launcher

The Platform Launcher is a versatile application designed for dynamically loading and executing code across different runtime environments, including JVM, WebAssembly (WASM), and native contexts. It provides a unified interface to manage and run modules, with a focus on flexibility and performance.

## Features

-   **Dynamic JVM Management**:
    -   Can create or attach to a JVM instance at runtime using JNI (via JNA).
    -   Supports dynamic compilation and execution of Java source files and JARs.
-   **WebAssembly (WASM) Execution**:
    -   Integrates with GraalVM's Polyglot API to load and run WASM modules.
    -   Provides a fallback mechanism (currently non-functional, logs errors) if GraalVM is not available.
    -   Supports basic WASM imports for console, filesystem, and process interactions.
-   **Native Launcher Entry Point (`NativeLauncher.kt`)**:
    -   A primary executable component, which can be compiled into a GraalVM native image for fast startup.
    -   Parses command-line arguments for various operations.
    -   Manages the lifecycle of the `PlatformLauncher`.
-   **Platform Abstraction (`PlatformLauncher.kt`)**:
    -   Core component responsible for interacting with different runtime environments (JVM, WASM).
-   **Daemon Mode**:
    -   The launcher can run as a daemon, controllable via an HTTP API implemented with Ktor.
    -   Key API Endpoints:
        -   `GET /`: Basic status.
        -   `POST /execute`: Executes a target file/class (e.g., WASM, Java).
            -   Request body: `{ "target": "path/to/file", "args": ["arg1"], "type": "wasm|java|jar|class" }`
        -   `POST /load/wasm`: Loads a WASM module.
            -   Request body: `{ "name": "moduleName", "path": "path/to/module.wasm", "type": "wasm" }`
        -   `GET /status`: Returns daemon status.
        -   `POST /shutdown`: Initiates daemon shutdown.
-   **Experimental `io_uring` Support (Linux-specific)**:
    -   Provides asynchronous file I/O operations (`asyncReadFileUring`, `asyncWriteFileUring`) on Linux using `io_uring`.
    -   This is implemented via JNA, calling into a native shared library (`libtrikeshed_native_uring.so`) which must be available on the system.
    -   The native library itself would use `liburing` for the asynchronous operations.
-   **Benchmarking**: Includes a basic benchmark mode for measuring JVM startup and WASM loading times.

## Command-Line Usage

The `NativeLauncher` is the typical entry point.

**General Syntax:**
`./platform-launcher [command] [options] [target]`

**Commands:**
-   `--daemon, -d`: Start in daemon mode.
    -   `--port <port>`: Specify daemon port (default: 8080).
-   `--execute, -e <file_or_class>`: Execute a file or class.
    -   Target can be `.wasm`, `.java`, `.jar`, or a fully qualified Java class name.
    -   Additional arguments for the target program can follow.
-   `--compile, -c <file>`: Compile a Java file to a native image (requires GraalVM `native-image` tool).
-   `--benchmark, -b`: Run internal performance benchmarks.
-   `--help, -h`: Show help information.

**Execution Options:**
-   `--jvm-options <opts>`: Comma-separated JVM options (e.g., "-Xmx1g,-Dfoo=bar").
-   `--wasm <module_path>`: (If not using daemon) Load an additional WASM module on startup. (Currently, daemon API is preferred for loading).

**Examples:**
-   Execute a WASM file:
    `./platform-launcher -e example.wasm`
-   Execute a Java source file with arguments:
    `./platform-launcher -e com.example.Main.java arg1 arg2`
-   Run in daemon mode on port 9000:
    `./platform-launcher -d --port 9000`
-   Compile a Java application to a native executable:
    `./platform-launcher -c com.example.MyApp.java`

## Dependencies

-   **Java Development Kit (JDK)**: Required for running as a JVM application and for dynamic Java compilation.
-   **GraalVM (Optional but Recommended)**:
    -   Required for WebAssembly (WASM) execution.
    -   Required for compiling the launcher into a native executable using `native-image`.
-   **`libtrikeshed_native_uring.so` (Linux Only, Optional)**:
    -   A native shared library (expected to be provided by the `superbikeshed-mcp-server/trikeshed` project) that implements `io_uring` operations.
    -   Required for the `asyncReadFileUring` and `asyncWriteFileUring` features on Linux. This library should link against `liburing`.
-   **Ktor Libraries**: For daemon mode (dependencies are managed by the build system).

## Building

The project is built using Gradle. See the main project's `build.gradle.kts` for details.
To build `NativeLauncher` as a GraalVM native image, the GraalVM `native-image` tool and appropriate build configurations are necessary.

## Notes on `io_uring` Integration

The `io_uring` functionality allows for high-performance asynchronous I/O on Linux. The `PlatformLauncher` uses JNA to interface with `libtrikeshed_native_uring.so`. This native library is responsible for:
1.  Initializing an `io_uring` instance.
2.  Submitting I/O requests (e.g., open, read, write, close) to the kernel.
3.  Polling for and retrieving completions.
4.  Passing results (data, status codes) back to the JVM.

The `PlatformLauncher` manages a completion polling thread and uses Kotlin coroutines (`Deferred`) to provide an asynchronous API to its callers. This feature is only active if the native library is found and successfully initialized on a Linux system.
