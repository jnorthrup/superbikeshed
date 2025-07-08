# K2script I/O Architecture: Leveraging TrikeShed-IO for Multiplatform Operations

This document outlines the I/O architecture of `k2script`, emphasizing its reliance on the `trikeshed-io` module for multiplatform file system and network operations. The design follows a Service Provider Interface (SPI) pattern, where `k2script.platform.FileSystemOperations` and `k2script.platform.ProcessExecutor` act as the primary interfaces, delegating their core functionalities to `trikeshed-io`'s `expect`/`actual` implementations.

## Core Principles

1.  **Multiplatform Abstraction**: All I/O operations are exposed through common interfaces defined in `k2script.platform` (e.g., `FileSystemOperations`, `ProcessExecutor`). This allows `k2script`'s core logic to remain platform-agnostic.
2.  **Delegation to `trikeshed-io`**: For file system operations, `k2script.platform.FileSystemOperations` delegates to `borg.trikeshed.io.Files` and `borg.trikeshed.io.PlatformFileIO` (via `PlatformFileIOImpl`). This ensures that the underlying I/O is handled by a dedicated, multiplatform-aware library.
3.  **Service Provider Interface (SPI)**: `trikeshed-io` acts as the SPI for native I/O. `k2script.platform` consumes these services, and different platforms (JVM, Native, JS) provide their concrete `actual` implementations within `trikeshed-io`.
4.  **Coroutine Context Element Key (CCEK)**: Instances of `FileSystemOperations` and `ProcessExecutor` are provided through the `CoroutineContext` using `FileSystemOperationsKey` and `ProcessExecutorKey`. This allows for flexible dependency injection and easy testing with mock implementations.

## Architecture Breakdown

### `k2script.platform` Module

This module defines the common interfaces and CCEK keys that `k2script`'s application logic interacts with.

*   **`PlatformInterfaces.kt`**:
    *   `typealias File = borg.trikeshed.io.PlatformFile`: `k2script` uses `PlatformFile` from `trikeshed-io` as its multiplatform file representation.
    *   `interface FileSystemOperations`: Defines high-level file system operations. It has a `platformFileIO: PlatformFileIO` property to access `trikeshed-io`'s lower-level I/O capabilities.
        *   Methods like `fileExists`, `isDirectory`, `readText`, `writeText` directly delegate to `borg.trikeshed.io.Files`.
        *   Methods like `createTempDir`, `copyFile`, `deleteRecursively`, `getFileSize` are currently implemented in the JVM-specific `JvmFileSystemOperations` using `java.io.File` directly. This is a temporary measure.
    *   `interface ProcessExecutor`: Defines an interface for running external commands.
    *   `FileSystemOperationsKey`, `ProcessExecutorKey`: CCEK keys to inject implementations into the `CoroutineContext`.

### `trikeshed-io` Module

This module provides the core multiplatform I/O primitives.

*   **`borg.trikeshed.io.Files` (expect object)**:
    *   Provides `expect` functions for common file operations like `exists`, `readString`, `write`, etc.
    *   Its `actual` implementations are provided in platform-specific source sets (e.g., `jvmMain`, `nativeMain`, `wasmJsMain`).
*   **`borg.trikeshed.io.PlatformFile` (expect class)**:
    *   The multiplatform representation of a file path.
    *   Its `actual` implementations are provided in platform-specific source sets.
*   **`borg.trikeshed.io.PlatformFileIO` (expect interface)**:
    *   Defines lower-level I/O operations, potentially including file channels, scatter/gather, etc.
    *   `PlatformFileIOImpl` is its `actual` implementation.

### JVM Implementation (`k2script/src/jvmMain/kotlin/k2script/platform/JvmPlatformImplementations.kt`)

This file provides the JVM-specific `actual` implementations for `k2script.platform`'s interfaces.

*   `JvmFileSystemOperations`: Implements `FileSystemOperations`.
    *   Delegates `fileExists`, `isDirectory`, `readText`, `writeText` to `borg.trikeshed.io.Files`.
    *   Implements `createTempDir`, `copyFile`, `deleteRecursively`, `getFileSize` using `java.io.File` directly. These are the functions that should ideally be moved to `trikeshed-io`.
*   `JvmProcessExecutor`: Implements `ProcessExecutor` using `java.lang.ProcessBuilder`.

## Delegation and SPI

The architecture ensures that `k2script`'s logic is decoupled from concrete I/O implementations. `FileSystemOperations` acts as an adapter, translating `k2script`'s I/O needs into calls to `trikeshed-io`'s multiplatform primitives. This allows `trikeshed-io` to evolve its native I/O capabilities (e.g., adding direct NIO `FileChannel` support, scatter/gather) without requiring changes in `k2script`'s core logic.

## Future Extensions: Moving More to `trikeshed-io`

The current implementation of `createTempDir`, `copyFile`, `deleteRecursively`, and `getFileSize` in `JvmFileSystemOperations` is a temporary measure. For a truly robust and consistent multiplatform I/O layer, these functionalities should be moved into `borg.trikeshed.io.Files` or a new `borg.trikeshed.io.FileOperations` `expect` object, with `actual` implementations provided for each platform.

This would involve:

1.  **Extending `borg.trikeshed.io.Files`**: Add `expect` functions for `createTempDir`, `copyFile`, `deleteRecursively`, `getFileSize`.
2.  **Implementing `actual` functions in `trikeshed-io`**: Provide platform-specific implementations for these new `expect` functions within `trikeshed-io`'s `jvmMain`, `nativeMain`, `wasmJsMain`, etc.
3.  **Updating `JvmFileSystemOperations`**: Once `trikeshed-io` provides these, `JvmFileSystemOperations` would then delegate these operations directly to `borg.trikeshed.io.Files`.

This approach ensures that `trikeshed-io` becomes the single source of truth for multiplatform I/O, and `k2script` remains a consumer of these well-defined services.