#!/bin/bash
# Direct IntelliJ API calls for error analysis

API="http://localhost:63342/api"
PROJECT="${1:-/Users/jim/work/v2superbikeshed}"

# Get compilation errors
echo "=== Getting Errors ==="
curl -s "$API/problems?project=$PROJECT&severity=ERROR" | jq '.'

# Example: Apply quick fix
# curl -X POST "$API/quickfix" \
#   -H "Content-Type: application/json" \
#   -d '{"file":"path/to/file.kt","line":42,"fixId":"AddImport"}'

# Example: Auto-import
# curl -X POST "$API/import" \
#   -H "Content-Type: application/json" \
#   -d '{"file":"path/to/file.kt","symbol":"ByteBuffer"}'

# Example: Batch rename
# curl -X POST "$API/refactor" \
#   -H "Content-Type: application/json" \
#   -d '{"type":"rename","file":"file.kt","offset":100,"params":{"newName":"Indexed"}}'