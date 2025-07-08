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
