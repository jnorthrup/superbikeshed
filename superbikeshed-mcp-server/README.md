# Trikeshed MCP Three-Server Architecture

A Model Context Protocol (MCP) server implementation using exclusively trikeshed components, providing three specialized servers for different aspects of code analysis and manipulation.

## Architecture Overview

The system consists of three independent MCP servers, each handling specific functionality:

### 1. PSI Server (Port 3001)
Handles Program Structure Interface operations:
- **find_symbols**: Locate symbols by pattern and type
- **rename_symbols**: Rename symbols across the codebase
- **get_ast**: Retrieve Abstract Syntax Tree for files
- **modify_ast**: Modify AST nodes directly

### 2. Analysis Server (Port 3002)
Handles code analysis and inspections:
- **run_inspections**: Execute specific code inspections
- **apply_fixes**: Apply automatic fixes for detected problems
- **get_problems**: Retrieve current problems in the codebase
- **analyze_dependencies**: Analyze symbol dependencies and usage

### 3. Refactor Server (Port 3003)
Handles refactoring operations:
- **batch_refactor**: Execute multiple refactoring operations atomically
- **preview_changes**: Generate previews of refactoring changes
- **structural_search**: Perform structural search and replace
- **structural_replace**: Execute structural replacements

## Trikeshed Components Used

The implementation leverages the following trikeshed modules:
- `trikeshed-common`: Common utilities and interfaces
- `trikeshed-json`: JSON serialization and parsing
- `trikeshed-io`: Input/output operations
- `trikeshed-net`: Network communication
- `trikeshed-ipc`: Inter-process communication
- `trikeshed-services`: Service registry and management
- `trikeshed-reactor`: Event-driven processing

## Usage

### Starting the Servers

```bash
./gradlew run
```

This will start all three servers:
- PSI Server: `localhost:3001`
- Analysis Server: `localhost:3002`
- Refactor Server: `localhost:3003`

### Example Requests

#### PSI Server - Find Symbols
```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "find_symbols",
  "params": {
    "pattern": "*Series",
    "type": "class"
  }
}
```

#### Analysis Server - Run Inspections
```json
{
  "jsonrpc": "2.0",
  "id": "2",
  "method": "run_inspections",
  "params": {
    "inspection": "UnusedImport",
    "filePath": "src/main/kotlin/CoreTypes.kt"
  }
}
```

#### Refactor Server - Batch Refactor
```json
{
  "jsonrpc": "2.0",
  "id": "3",
  "method": "batch_refactor",
  "params": {
    "operations": "rename:Series->Indexed,extract:calculateIndex"
  }
}
```

## Implementation Details

### Three-Server Pattern
Each server follows the "three" pattern:
- Three core capabilities per server
- Three-step request processing (validate, process, respond)
- Three levels of error handling (validation, processing, system)

### MCP Protocol Integration
The servers implement the MCP protocol with:
- JSON-RPC 2.0 compliance
- Standard error codes and messages
- Request/response correlation via IDs

### Trikeshed Integration
All networking, serialization, and service management uses trikeshed components:
- `Reactor` for event-driven processing
- `NetworkManager` for TCP communication
- `ServiceRegistry` for service discovery
- `JsonSerializer` for protocol serialization

## Development

### Building
```bash
./gradlew build
```

### Testing
```bash
./gradlew test
```

### Adding New Capabilities
1. Extend the appropriate three-server class
2. Add new method handlers
3. Update the capabilities list
4. Add corresponding MCP protocol methods

## Configuration

The servers can be configured via environment variables:
- `PSI_SERVER_PORT`: PSI server port (default: 3001)
- `ANALYSIS_SERVER_PORT`: Analysis server port (default: 3002)
- `REFACTOR_SERVER_PORT`: Refactor server port (default: 3003)
- `LOG_LEVEL`: Logging level (default: INFO) 