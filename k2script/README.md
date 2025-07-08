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

For more detailed information, features, configuration, and architecture, please refer to the [documentation](docs/README.md).