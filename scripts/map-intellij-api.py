#!/usr/bin/env python3
"""Map IntelliJ API endpoints and create graph"""

import requests
import json
from datetime import datetime

BASE_URL = "http://localhost:63342"

def test_endpoint(session, path):
    """Test an endpoint and return status info"""
    try:
        resp = session.head(f"{BASE_URL}{path}", timeout=2)
        return {
            "status": resp.status_code,
            "headers": dict(resp.headers) if resp.headers else {}
        }
    except requests.exceptions.RequestException as e:
        return {"status": "error", "error": str(e)}

def map_api():
    """Map the IntelliJ API"""
    session = requests.Session()
    
    # Get about info
    try:
        about = session.get(f"{BASE_URL}/api/about").json()
    except:
        about = {"error": "Could not fetch about info"}
    
    # Test endpoints
    endpoints_to_test = [
        "/api/about",
        "/api/file",
        "/api/file/",
        "/api/project",
        "/api/projects",
        "/api/modules",
        "/api/module",
        "/api/vfs",
        "/api/editor",
        "/api/editors",
        "/api/actions",
        "/api/action",
        "/api/run",
        "/api/debug",
        "/api/refactor",
        "/api/refactoring",
        "/api/inspect",
        "/api/inspection",
        "/api/complete",
        "/api/completion",
        "/api/find",
        "/api/search",
        "/api/navigate",
        "/api/navigation",
        "/api/git",
        "/api/vcs",
        "/api/maven",
        "/api/gradle",
        "/api/build",
        "/api/compile",
        "/api/problems",
        "/api/diagnostics",
        "/api/quickfix",
        "/api/intentions",
        "/api/codeInsight",
        "/api/psi",
        "/api/ast",
        "/api/structure",
        "/api/symbols",
        "/api/references",
        "/api/usages",
        "/api/highlight",
        "/api/folding",
        "/api/format",
        "/api/settings",
        "/api/config",
        "/api/plugins",
        "/api/extensions"
    ]
    
    endpoints = {}
    for path in endpoints_to_test:
        print(f"Testing {path}...")
        endpoints[path] = test_endpoint(session, path)
    
    # Build result
    result = {
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "base_url": BASE_URL,
        "about": about,
        "endpoints": endpoints,
        "working_endpoints": [
            path for path, info in endpoints.items() 
            if info.get("status") == 200
        ]
    }
    
    return result

def main():
    """Main function"""
    print("=== Mapping IntelliJ API ===")
    api_map = map_api()
    
    # Save to file
    with open("intellij-api-graph.json", "w") as f:
        json.dump(api_map, f, indent=2)
    
    # Print summary
    print(f"\nIDE: {api_map['about'].get('name', 'Unknown')}")
    print(f"Working endpoints: {len(api_map['working_endpoints'])}")
    for endpoint in api_map['working_endpoints']:
        print(f"  ✓ {endpoint}")
    
    # Create LLM integration example
    print("\n=== LLM Integration Example ===")
    print("""
# Use with LLM:
import requests

# Get IDE info
resp = requests.get("http://localhost:63342/api/about")
print(resp.json())

# Open file (if supported)
# requests.get("http://localhost:63342/api/file?file=/path/to/file.kt&line=10")
""")

if __name__ == "__main__":
    main()