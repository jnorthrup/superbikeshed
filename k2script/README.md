# k2script

Kotlin script execution engine with dependency management and AI integration.

## Installation

```bash
./install.sh
```

## Usage

### Basic Script Execution
```bash
k2script script.kts
```

### With Dependencies
```bash
k2script -d "com.example:library:1.0" script.kts
```

### AI Features
```bash
k2script --ai "create a script to find large files"
k2script --ai "explain this code" < script.kts
```

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