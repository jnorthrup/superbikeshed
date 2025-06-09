#!/usr/bin/env python3
"""
Real Anthropic API test for the LLM Agility Gauntlet
"""

import os
import json
import time
from typing import List

class RealAnthropicProvider:
    """Real Anthropic provider that makes actual API calls"""
    
    def __init__(self, api_key: str, model: str = "claude-3-5-sonnet-20241022"):
        self.api_key = api_key
        self.model = model
        self.conversation_history = []
    
    def generate(self, prompt: str, context: List[str] = None) -> str:
        """Generate response using real Anthropic API"""
        try:
            import anthropic
            
            client = anthropic.Anthropic(api_key=self.api_key)
            
            # Build conversation from context
            messages = []
            if context:
                for i, msg in enumerate(context):
                    role = "user" if i % 2 == 0 else "assistant"
                    messages.append({"role": role, "content": msg})
            
            messages.append({"role": "user", "content": prompt})
            
            print(f"🔄 Making API call to {self.model}...")
            start_time = time.time()
            
            response = client.messages.create(
                model=self.model,
                max_tokens=4000,
                temperature=0.1,  # Low temperature for consistent results
                messages=messages
            )
            
            api_time = time.time() - start_time
            result = response.content[0].text
            
            print(f"⚡ API response in {api_time:.2f}s ({len(result)} chars)")
            
            self.conversation_history.extend([prompt, result])
            return result
            
        except Exception as e:
            print(f"❌ API Error: {e}")
            raise

def run_real_gauntlet():
    """Run gauntlet with real API calls"""
    api_key = os.getenv('ANTHROPIC_API_KEY')
    
    if not api_key:
        print("❌ ANTHROPIC_API_KEY environment variable required")
        print("💡 Get your API key from: https://console.anthropic.com/")
        return None
    
    print("🚀 Running LLM Agility Gauntlet with REAL Anthropic API")
    print("=" * 60)
    
    provider = RealAnthropicProvider(api_key)
    
    # Manual gauntlet run to see real responses
    transcript = []
    start_time = time.time()
    
    # Segment 1: API Ingestion
    print("\n📝 SEGMENT 1: API Ingestion & Distillation")
    print("-" * 40)
    
    segment1_prompt = """You are a core programming agent. Ingest the following API specification and distill it into a structured, machine-readable format (JSON or YAML) that represents the available endpoints, their methods, required parameters, and expected response structure. Produce ONLY the structured output.

**API SPECIFICATION: 'DataWeaver v0.7b'**
```
# DataWeaver is a service for posting and retrieving encrypted data blobs.
# All requests require an 'X-API-Key' header.

## Endpoint: /v1/blob/new
# Method: POST
# Creates a new data blob.
# Body must be a JSON object with a single key, 'payload', containing the base64 encoded data string.
# Returns: JSON with 'blob_id' (string) and 'timestamp' (unix_epoch).

## Endpoint: /v1/blob/fetch/{id}
# Method: GET
# Retrieves a data blob. The 'id' in the URL is the blob_id.
# Returns: JSON with 'payload' (base64 string).
# Special Header: Optionally accepts 'X-Cache-Bypass' with value 'true'.
```"""
    
    segment1_response = provider.generate(segment1_prompt)
    transcript.extend([segment1_prompt, segment1_response])
    
    print("🤖 Claude's Response:")
    print(segment1_response)
    
    # Segment 2: Component Assembly
    print("\n🔧 SEGMENT 2: Component Assembly")
    print("-" * 40)
    
    segment2_prompt = """Use the API structure you just created. You have two functions:

```python
# Snippet A
def read_data_file(filepath: str) -> bytes:
  \"\"\"Reads raw bytes from a file.\"\"\"
  with open(filepath, 'rb') as f:
    return f.read()

# Snippet B
def simple_encrypt(data_string: str) -> str:
  \"\"\"A simple 'encryption' that reverses the string.\"\"\"
  return data_string[::-1]
```

Write a single Python function `process_and_post(filepath: str, api_key: str)` that:
1. Reads data from `filepath` using Snippet A.
2. Encrypts the data using Snippet B.
3. Posts the encrypted data to the `/v1/blob/new` endpoint.
4. Returns the `blob_id`.

Handle any data type mismatches. Import only `requests` and `base64`. Produce ONLY the function code."""
    
    segment2_response = provider.generate(segment2_prompt, transcript)
    transcript.extend([segment2_prompt, segment2_response])
    
    print("🤖 Claude's Response:")
    print(segment2_response)
    
    # Segment 3: Interruption & Adaptation
    print("\n⚡ SEGMENT 3: Interruption & Adaptation")
    print("-" * 40)
    
    segment3_prompt = """**REQUIREMENT CHANGE:** The `DataWeaver` API has been updated to v0.8.
1. The POST endpoint is now `/v2/blob`.
2. The POST body now requires an additional key: `'version'`, with a static string value of `"0.8"`.
3. The `simple_encrypt` function is now insecure. You must replace its logic with a `base64` encoding step *instead of* the string reversal.

Rewrite the `process_and_post` function to comply. Produce ONLY the final, updated function code."""
    
    segment3_response = provider.generate(segment3_prompt, transcript)
    transcript.extend([segment3_prompt, segment3_response])
    
    print("🤖 Claude's Response:")
    print(segment3_response)
    
    total_time = time.time() - start_time
    
    # Analyze results
    print(f"\n📊 ANALYSIS")
    print("=" * 40)
    print(f"⏱️  Total Time: {total_time:.2f}s")
    print(f"💬 Total Transcript Length: {sum(len(msg) for msg in transcript):,} chars")
    
    # Check segment 1 (JSON parsing)
    try:
        parsed = json.loads(segment1_response)
        print("✅ Segment 1: Valid JSON structure")
    except:
        print("❌ Segment 1: Invalid JSON")
    
    # Check segment 2 (function presence)
    if 'def process_and_post' in segment2_response:
        print("✅ Segment 2: Function definition found")
        if 'decode' in segment2_response:
            print("✅ Segment 2: Handles bytes/string conversion")
        else:
            print("⚠️  Segment 2: May not handle type conversion")
    else:
        print("❌ Segment 2: No function definition")
    
    # Check segment 3 (adaptations)
    adaptations = {
        '/v2/blob': '✅ Updated endpoint' if '/v2/blob' in segment3_response else '❌ Endpoint not updated',
        'version': '✅ Added version field' if 'version' in segment3_response else '❌ Version field missing',
        'base64': '✅ Replaced encryption' if 'base64' in segment3_response and 'simple_encrypt' not in segment3_response else '❌ Encryption not replaced'
    }
    
    print("🔄 Segment 3 Adaptations:")
    for check, result in adaptations.items():
        print(f"   {result}")
    
    return {
        'transcript': transcript,
        'execution_time': total_time,
        'responses': {
            'segment1': segment1_response,
            'segment2': segment2_response, 
            'segment3': segment3_response
        }
    }

def main():
    """Main test runner"""
    result = run_real_gauntlet()
    
    if result:
        print(f"\n🎉 Real Anthropic test completed!")
        print(f"📝 Use this data to calibrate the scoring system")
    
    return result

if __name__ == "__main__":
    main()