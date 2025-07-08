# Hermetic TrikeShed MCP Architecture

## Our Core MCP Composition

The TrikeShed ecosystem maintains a hermetic MCP (Model Context Protocol) composition with these active server keys:

### Primary MCP Servers

1. **`v2superbikeshed-mcp`** - Main IntelliJ plugin MCP server
   - Location: `nexus/intellij-plugin/src/main/kotlin/com/v2superbikeshed/nexus/mcp/`
   - Version: 1.0.0
   - Capabilities: PSI operations, code analysis, refactoring

2. **`standalone-mcp-server`** - Standalone Trikeshed MCP server
   - Location: `superbikeshed-mcp-server/src/main/kotlin/com/superbikeshed/mcp/`
   - Hermetic TrikeShed integration

### Specialized MCP Services

3. **`PSI-Server`** - PSI manipulation server
   - Tools: `find_symbols`, `rename_symbols`, `get_ast`, `modify_ast`

4. **`Analysis-Server`** - Code analysis server
   - Tools: `run_inspections`, `apply_fixes`, `get_problems`, `analyze_dependencies`

5. **`Refactor-Server`** - Refactoring operations server
   - Tools: `batch_refactor`, `preview_changes`, `structural_search`, `structural_replace`

### TrikeShed Protocol Adapters

6. **`couchdb-mcp-server`** - CouchDB database adapter
   - Hermetic database operations within TrikeShed ecosystem

7. **`quic-mcp-server`** - QUIC protocol adapter
   - High-performance networking for TrikeShed components

### Deployment Infrastructure

8. **`gateway`** - Kubernetes gateway server
9. **`tools-server`** - Kubernetes tools server  
10. **`resources-server`** - Kubernetes resources server

## Hermetic Properties

- **Self-contained**: All MCP servers operate within TrikeShed ecosystem
- **Isolated**: No external dependencies beyond TrikeShed core
- **Composable**: Servers can be combined for complex operations
- **Resilient**: Circuit breakers and health monitoring built-in

## Architecture Benefits

- **Zero External Leakage**: All operations contained within TrikeShed
- **Performance**: QUIC and optimized protocols
- **Scalability**: Kubernetes deployment ready
- **Integration**: Deep IntelliJ IDE integration