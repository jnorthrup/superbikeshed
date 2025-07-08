#!/bin/bash
# IntelliJ API Enumeration - Map the API graph

API="http://localhost:63342/api"
PROJECT="${1:-/Users/jim/work/v2superbikeshed}"

echo "=== IntelliJ API Enumeration ==="
echo "Base URL: $API"
echo "Project: $PROJECT"
echo

# Test connection
echo "1. Testing API Status..."
curl -s -w "\nHTTP Status: %{http_code}\n" "$API/status" || echo "API not responding"
echo

# Enumerate endpoints
echo "2. Enumerating API Endpoints..."

# Project info
echo "=== Project Info ==="
curl -s "$API/project?path=$PROJECT" | jq '.' 2>/dev/null || echo "No project endpoint"
echo

# Problems/Errors
echo "=== Compilation Problems ==="
curl -s "$API/problems?project=$PROJECT" | jq '.' 2>/dev/null || echo "No problems endpoint"
echo

# File structure
echo "=== File Structure ==="
curl -s "$API/files?project=$PROJECT" | jq '.' 2>/dev/null || echo "No files endpoint"
echo

# Available refactorings
echo "=== Available Refactorings ==="
curl -s "$API/refactorings" | jq '.' 2>/dev/null || echo "No refactorings endpoint"
echo

# Inspections
echo "=== Available Inspections ==="
curl -s "$API/inspections" | jq '.' 2>/dev/null || echo "No inspections endpoint"
echo

# Try common REST patterns
echo "3. Testing Common REST Patterns..."
for endpoint in "" "v1" "v2" "help" "docs" "swagger" "openapi" "api-docs"; do
    echo -n "Testing /$endpoint: "
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API/$endpoint")
    echo "HTTP $STATUS"
done
echo

# Try IntelliJ-specific endpoints
echo "4. Testing IntelliJ-specific Endpoints..."
for endpoint in "project" "module" "file" "psi" "editor" "vfs" "codeInsight" "actions" "intentions"; do
    echo -n "Testing /$endpoint: "
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API/$endpoint")
    echo "HTTP $STATUS"
done
echo

# Try to get available actions
echo "5. Available Actions..."
curl -s "$API/actions" | jq '.' 2>/dev/null || echo "No actions endpoint"
echo

# Try to list available tools/services
echo "6. Available Services..."
curl -s "$API/services" | jq '.' 2>/dev/null || echo "No services endpoint"
echo

# Generate report
echo "=== API Enumeration Summary ==="
echo "Timestamp: $(date)"
echo "API Base: $API"
echo "Project: $PROJECT"