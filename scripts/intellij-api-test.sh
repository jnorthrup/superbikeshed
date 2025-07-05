#!/bin/bash
# Test IntelliJ Built-in Web Server API

BASE="http://localhost:63342"

echo "=== IntelliJ Built-in Web Server API Test ==="

# Test about endpoint
echo "1. About endpoint:"
curl -s "$BASE/api/about" | jq '.'

# Test file open with absolute path
echo -e "\n2. Open file (absolute path):"
curl -s "$BASE/api/file//Users/jim/work/v2superbikeshed/README.md"

# Test file with line/column
echo -e "\n3. Open file with line/column:"
curl -s "$BASE/api/file//Users/jim/work/v2superbikeshed/build.gradle.kts:10:5"

# Test with query params
echo -e "\n4. Open file with query params:"
curl -s "$BASE/api/file?file=/Users/jim/work/v2superbikeshed/CLAUDE.md&line=50"