# Platform Launcher

This module serves as the core component for launching and orchestrating various platform-specific tasks, with a strong focus on kernel-level integration and high-performance IPC.

## JVM Inline Issues and WASM Target

As of now, the `wasmJs` target is temporarily excluded from this module's build configuration. This decision is due to known issues related to JVM inline annotations and their impact on WASM compilation, as tracked in Jira. Once these issues are resolved and `jvmInline` annotations can be reliably supported across all targets, the `wasmJs` target will be re-enabled.

## Core Principles

*   **Kernel as the Database**: Leveraging `io_uring` and eBPF for direct, high-performance interaction with the Linux kernel.
*   **IPC Acceleration**: Optimizing inter-process communication channels, with a focus on XDR-like serialization for efficient data exchange.
*   **Sandboxed Tooling**: Utilizing `k2script` as a secure sandbox for mutating tools that interact with local infrastructure.

## Development Environment (Linux Docker for liburing)

To facilitate development and dogfooding of `liburing` and other kernel-level components, a dedicated Linux Docker environment is provided. This environment allows for SSH-based development, enabling direct interaction with the kernel and `io_uring` from a controlled and reproducible setup.

Refer to `platform-launcher/docker/Dockerfile` for details on setting up this development environment.