# k2script: The Backbone of Platform Launching (KMP)

**k2script is a Kotlin Multiplatform (KMP) project and serves as the backbone for the platform launcher, Nexus, and any application or script requiring robust, cross-platform automation, sandboxing, and deployment.**

- k2script provides the core infrastructure for safe, repeatable, and flexible script/app execution across environments.
- Its KMP nature ensures portability and extensibility for JVM, native, and JS targets.
- All higher-level launchers, orchestrators, or workflow tools (like Nexus) build on top of k2script's robust, cross-platform capabilities.
- Any application or script that needs reliable launching, sandboxing, or automation can use k2script as its backbone.

---

## Overview
This directory contains consolidated documentation for the k2script project.
Consolidated on: Tue Jun 24 20:49:36 EDT 2025

## Features

- Kotlin script execution (.kts files)
- Automatic dependency resolution
- Maven/Gradle dependency support
- Template processing
- Code explanation and generation via LLM
- Java interoperability

## Configuration

Configuration files:
- `~/.kscript/kscript.properties`
- Project-specific `.kscript/kscript.properties`

## Architecture

- **K2script.kt**: Main entry point and CLI
- **parser/**: Script annotation parsing
- **ai/llm/**: LLM client integration
- **executor/**: Script execution engine

## Files
- [user_guide](user_guide.md) (    7381 bytes,      224 lines)
- [markdown_code_blocks](markdown_code_blocks.md) (    7075 bytes,      261 lines)
- [notes](notes.md) (    7085 bytes,      209 lines)
