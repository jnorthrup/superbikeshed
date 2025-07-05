# Ideal IntelliJ API Features for LLM Integration

## 1. PSI (Program Structure Interface) Access
- **Query**: Find symbols, classes, methods by pattern
- **Change**: Modify AST nodes directly
```
GET /api/psi/find?type=class&pattern=*Series
POST /api/psi/rename {"symbol": "Series", "newName": "Indexed"}
```

## 2. Structural Search & Replace
- **Query**: Find code patterns
- **Change**: Replace patterns across codebase
```
POST /api/ssr/search {"template": "$Instance$.$Method$()"}
POST /api/ssr/replace {"search": "j(", "replace": "join("}
```

## 3. Refactoring API
- **Batch operations**: Multiple refactorings atomically
- **Preview**: See changes before applying
```
POST /api/refactor/batch [
  {"type": "rename", "from": "Series", "to": "Indexed"},
  {"type": "extractMethod", "file": "x.kt", "range": [10,20]}
]
```

## 4. Code Analysis
- **Inspections**: Run specific inspections
- **Quick fixes**: Apply fixes programmatically
```
GET /api/inspect?inspection=UnusedImport
POST /api/fix {"problem": "UnresolvedReference", "symbol": "ByteBuffer"}
```

## 5. Navigation Graph
- **Dependencies**: Who calls/uses what
- **Impact**: What changes affect what
```
GET /api/graph/usages?symbol=MetaSeries
GET /api/graph/impact?file=CoreTypes.kt
```

## 6. Live Code Model
- **WebSocket**: Real-time AST updates
- **Streaming**: Continuous compilation state
```
WS /api/live/ast
WS /api/live/errors
```

## Priority for v2superbikeshed:
1. Rename all Series → Indexed
2. Fix unresolved references  
3. Apply missing imports
4. Remove @kotlin.internal annotations
5. Update deprecated APIs