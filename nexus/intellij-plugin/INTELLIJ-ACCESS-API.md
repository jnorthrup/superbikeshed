# IntelliJ Access API Documentation

This document describes the REST and WebSocket APIs exposed by the Nexus IntelliJ Plugin for accessing IDE functionality.

## Base URL

All API endpoints are available at:
```
http://localhost:63343/api/nexus/
```

## Authentication

Currently, no authentication is required as the API is only accessible locally.

## REST API Endpoints

### Project Management

#### List Open Projects
```http
GET /api/intellij/projects
```

Response:
```json
[
  {
    "name": "v2superbikeshed",
    "path": "/Users/jim/work/v2superbikeshed",
    "isOpen": true
  }
]
```

### PSI (Program Structure Interface) Access

#### Find Element by FQN
```http
POST /api/intellij/psi/{projectName}/find-element
Content-Type: application/json

{
  "fqName": "borg.trikeshed.lib.CoreTypes.Indexed"
}
```

Response:
```json
{
  "name": "typealias Indexed<T> = Join<Int, (Int) -> T>",
  "type": "KtTypeAlias",
  "file": "/path/to/CoreTypes.kt",
  "offset": 1234
}
```

#### Get PSI File
```http
GET /api/intellij/psi/{projectName}/file?path=/absolute/path/to/file.kt
```

Response:
```json
{
  "path": "/path/to/file.kt",
  "language": "Kotlin",
  "content": "file content here..."
}
```

### Search and Navigation

#### Find Usages
```http
POST /api/intellij/search/{projectName}/usages
Content-Type: application/json

{
  "elementFqName": "borg.trikeshed.lib.CoreTypes.Series"
}
```

Response:
```json
{
  "elementName": "borg.trikeshed.lib.CoreTypes.Series",
  "usages": [
    {
      "file": "/path/to/ColumnarExtensions.kt",
      "line": 42,
      "column": 15,
      "text": "Series<T>",
      "context": "fun Series<T>.toIndexed(): Indexed<T> = ..."
    }
  ]
}
```

#### Find Symbols by Pattern
```http
GET /api/intellij/search/{projectName}/symbols?pattern=*Series&includeLibraries=false
```

Response:
```json
{
  "symbols": [
    {
      "name": "IntSeries",
      "fqName": "borg.trikeshed.lib.CoreTypes.IntSeries",
      "type": "typealias",
      "file": "/path/to/CoreTypes.kt",
      "line": 123
    }
  ]
}
```

### Refactoring Operations

#### Rename Symbol
```http
POST /api/intellij/refactor/{projectName}/rename
Content-Type: application/json

{
  "elementFqName": "borg.trikeshed.lib.CoreTypes.Series",
  "newName": "Indexed"
}
```

Response:
```json
{
  "success": true,
  "filesChanged": 329,
  "error": null
}
```

#### Move Element
```http
POST /api/intellij/refactor/{projectName}/move
Content-Type: application/json

{
  "elementFqName": "com.example.MyClass",
  "targetPackage": "com.example.newpackage"
}
```

### Code Analysis

#### Get Code Inspections
```http
GET /api/intellij/analysis/{projectName}/inspections?file=/path/to/file.kt
```

Response:
```json
{
  "inspections": [
    {
      "id": "UnresolvedReference",
      "severity": "ERROR",
      "message": "Unresolved reference: Series",
      "file": "/path/to/file.kt",
      "line": 42
    }
  ]
}
```

#### Get Compilation Errors
```http
GET /api/intellij/analysis/{projectName}/compilation-errors
```

### Editor Operations

#### Apply Text Edits
```http
POST /api/intellij/editor/{projectName}/apply-edits
Content-Type: application/json

{
  "filePath": "/path/to/file.kt",
  "edits": [
    {
      "startOffset": 100,
      "endOffset": 106,
      "newText": "Indexed"
    }
  ]
}
```

### Action Execution

#### Execute IntelliJ Action
```http
POST /api/intellij/action/{projectName}/{actionId}
```

Common action IDs:
- `CompileProject` - Compile entire project
- `CompileDirty` - Compile modified files
- `Vcs.RefreshStatuses` - Refresh VCS status
- `ReformatCode` - Reformat current file
- `OptimizeImports` - Optimize imports

## WebSocket Endpoints

### AST Update Stream
```
ws://localhost:63343/api/nexus/ws/{projectName}/ast-updates
```

Receives real-time AST (Abstract Syntax Tree) updates as code changes:

```json
{
  "projectName": "v2superbikeshed",
  "file": "/path/to/CoreTypes.kt",
  "type": "TYPEALIAS_CHANGED",
  "change": "Series renamed to Indexed",
  "timestamp": 1234567890
}
```

### Compilation Error Stream
```
ws://localhost:63343/api/nexus/ws/{projectName}/compilation-errors
```

Streams compilation errors in real-time:

```json
{
  "projectName": "v2superbikeshed",
  "file": "/path/to/file.kt",
  "line": 42,
  "column": 15,
  "message": "Unresolved reference: Series",
  "severity": "ERROR"
}
```

## Example Usage

### Python Client Example
```python
import requests
import websocket
import json

# REST API example
base_url = "http://localhost:63343/api/nexus"

# Find all Series usages
response = requests.post(
    f"{base_url}/api/intellij/search/v2superbikeshed/usages",
    json={"elementFqName": "borg.trikeshed.lib.CoreTypes.Series"}
)
usages = response.json()
print(f"Found {len(usages['usages'])} usages of Series")

# Rename Series to Indexed
response = requests.post(
    f"{base_url}/api/intellij/refactor/v2superbikeshed/rename",
    json={
        "elementFqName": "borg.trikeshed.lib.CoreTypes.Series",
        "newName": "Indexed"
    }
)
result = response.json()
print(f"Renamed successfully: {result['success']}, files changed: {result['filesChanged']}")

# WebSocket example for AST updates
def on_message(ws, message):
    update = json.loads(message)
    print(f"AST Update: {update['type']} in {update['file']}")

ws = websocket.WebSocketApp(
    "ws://localhost:63343/api/nexus/ws/v2superbikeshed/ast-updates",
    on_message=on_message
)
ws.run_forever()
```

### JavaScript/TypeScript Client Example
```typescript
// REST API
const baseUrl = 'http://localhost:63343/api/nexus';

// Find symbols
const response = await fetch(`${baseUrl}/api/intellij/search/v2superbikeshed/symbols?pattern=*Series`);
const { symbols } = await response.json();
console.log(`Found ${symbols.length} Series-related symbols`);

// WebSocket for real-time updates
const ws = new WebSocket('ws://localhost:63343/api/nexus/ws/v2superbikeshed/compilation-errors');

ws.onmessage = (event) => {
  const error = JSON.parse(event.data);
  console.error(`Compilation error: ${error.file}:${error.line} - ${error.message}`);
};
```

### curl Examples
```bash
# List projects
curl http://localhost:63343/api/nexus/api/intellij/projects

# Find element
curl -X POST http://localhost:63343/api/nexus/api/intellij/psi/v2superbikeshed/find-element \
  -H "Content-Type: application/json" \
  -d '{"fqName": "borg.trikeshed.lib.CoreTypes.Indexed"}'

# Execute action
curl -X POST http://localhost:63343/api/nexus/api/intellij/action/v2superbikeshed/CompileProject
```

## Error Handling

All endpoints return appropriate HTTP status codes:
- `200 OK` - Success
- `404 Not Found` - Project or element not found
- `400 Bad Request` - Invalid request format
- `500 Internal Server Error` - IntelliJ operation failed

Error responses include a JSON body:
```json
{
  "error": "Detailed error message",
  "code": "ERROR_CODE"
}
```

## Performance Considerations

1. **PSI Operations**: PSI access is performed in read/write actions as appropriate
2. **Batch Operations**: Use batch endpoints when possible to reduce overhead
3. **WebSocket Streams**: Connect once and maintain connection for real-time updates
4. **Large Files**: File content may be truncated for very large files

## Limitations

1. **Local Only**: API is only accessible from localhost
2. **Single Instance**: Only one IntelliJ instance can run the server at a time
3. **Project Scope**: Operations are limited to open projects
4. **Indexing**: Some operations require project indexing to be complete