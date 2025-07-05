#!/bin/bash
# Map IntelliJ Built-in Web Server API

BASE="http://localhost:63342"
OUTPUT="intellij-api-map.json"

echo "=== Mapping IntelliJ API Graph ==="

# Initialize JSON output
echo "{" > $OUTPUT
echo '  "timestamp": "'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'",' >> $OUTPUT
echo '  "base_url": "'$BASE'",' >> $OUTPUT

# Get about info
echo '  "about": ' >> $OUTPUT
curl -s "$BASE/api/about" >> $OUTPUT
echo ',' >> $OUTPUT

# Test endpoints and record results
echo '  "endpoints": {' >> $OUTPUT

# Known working endpoint
echo '    "/api/about": {"status": 200, "description": "IDE information"},' >> $OUTPUT

# Test various endpoints
endpoints=(
    "/api/file"
    "/api/project" 
    "/api/modules"
    "/api/vfs"
    "/api/editor"
    "/api/actions"
    "/api/run"
    "/api/debug"
    "/api/refactor"
    "/api/inspect"
    "/api/complete"
    "/api/find"
    "/api/navigate"
    "/api/git"
    "/api/maven"
    "/api/gradle"
)

for endpoint in "${endpoints[@]}"; do
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE$endpoint")
    echo '    "'$endpoint'": {"status": '$STATUS'},' >> $OUTPUT
done

# Remove trailing comma and close JSON
sed -i '' '$ s/,$//' $OUTPUT
echo '  }' >> $OUTPUT
echo '}' >> $OUTPUT

# Display results
echo "API map saved to: $OUTPUT"
cat $OUTPUT | jq '.'

# Create Python script for LLM integration
cat > intellij-llm-client.py << 'EOF'
#!/usr/bin/env python3
"""IntelliJ API Client for LLM Integration"""

import requests
import json

class IntelliJAPI:
    def __init__(self, base_url="http://localhost:63342"):
        self.base_url = base_url
        self.session = requests.Session()
    
    def about(self):
        """Get IDE information"""
        return self.session.get(f"{self.base_url}/api/about").json()
    
    def open_file(self, file_path, line=None, column=None):
        """Attempt to open file in IDE"""
        params = {"file": file_path}
        if line:
            params["line"] = line
        if column:
            params["column"] = column
        
        resp = self.session.get(f"{self.base_url}/api/file", params=params)
        return resp.status_code == 200

    def enumerate_api(self):
        """Enumerate available API endpoints"""
        about = self.about()
        endpoints = {}
        
        # Test common endpoints
        test_paths = [
            "/api/file", "/api/project", "/api/modules",
            "/api/vfs", "/api/editor", "/api/actions"
        ]
        
        for path in test_paths:
            try:
                resp = self.session.head(f"{self.base_url}{path}")
                endpoints[path] = {
                    "status": resp.status_code,
                    "headers": dict(resp.headers)
                }
            except:
                endpoints[path] = {"status": "error"}
        
        return {
            "about": about,
            "endpoints": endpoints
        }

if __name__ == "__main__":
    api = IntelliJAPI()
    print(json.dumps(api.enumerate_api(), indent=2))
EOF

chmod +x intellij-llm-client.py
echo -e "\nCreated intellij-llm-client.py for LLM integration"