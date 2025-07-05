#!/bin/bash
# IntelliJ Real API Enumeration - Try different paths

BASE="http://localhost:63342"
PROJECT="${1:-/Users/jim/work/v2superbikeshed}"

echo "=== IntelliJ API Discovery ==="
echo "Base URL: $BASE"
echo

# Try root
echo "1. Testing root..."
curl -s -I "$BASE" | head -5
echo

# Try without /api
echo "2. Testing direct endpoints..."
for endpoint in "" "status" "project" "file" "rest" "api" "_api" "codeWithMe"; do
    echo -n "Testing $BASE/$endpoint: "
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/$endpoint")
    echo "HTTP $STATUS"
done
echo

# Try IntelliJ built-in server paths
echo "3. Testing IntelliJ built-in paths..."
for path in "file/$PROJECT" "file" "static" "_intellij" "built-in-server"; do
    echo -n "Testing $BASE/$path: "
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/$path")
    echo "HTTP $STATUS"
done
echo

# Try to get a file directly
echo "4. Testing file access..."
curl -s "$BASE/file/$PROJECT/README.md" | head -10
echo

# Check headers for clues
echo "5. Response headers from root..."
curl -s -I "$BASE" | grep -E "(Server|X-|Allow|API)"
echo

# Try POST to see if it reveals anything
echo "6. Testing POST methods..."
curl -X POST -s -I "$BASE" | head -5
echo

# Try OPTIONS to see allowed methods
echo "7. Testing OPTIONS..."
curl -X OPTIONS -s -I "$BASE" 2>&1 | head -10