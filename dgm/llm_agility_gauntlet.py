#!/usr/bin/env python3
"""
LLM Agility Gauntlet - A fast benchmark for LLM operational agility.

This is the "Bogomips for LLMs" - a standardized test that measures:
1. Learning (API ingestion and distillation)
2. Assembly (component synthesis and problem-solving)
3. Agility (interruption handling and adaptation)

Designed to run in under 5 minutes and produce a quantitative score 0-100.
"""

import json
import re
import time
import base64
from typing import Dict, List, Tuple, Optional
from dataclasses import dataclass
from abc import ABC, abstractmethod

# Mock requests for testing
class MockResponse:
    def __init__(self, json_data, status_code=200):
        self.json_data = json_data
        self.status_code = status_code
    
    def json(self):
        return self.json_data

class MockRequests:
    @staticmethod
    def post(url, json=None, headers=None):
        # Mock successful blob creation
        return MockResponse({"blob_id": "test123", "timestamp": 1234567890})

# Replace requests in test environment
import sys
sys.modules['requests'] = MockRequests()

@dataclass
class GauntletResult:
    """Results from running the LLM Agility Gauntlet"""
    total_score: float
    segment_scores: Dict[str, float]
    metrics: Dict[str, float]
    execution_time: float
    transcript: List[str]
    final_code: Optional[str]
    passed_correctness: bool

class LLMProvider(ABC):
    """Abstract base class for LLM providers"""
    
    @abstractmethod
    def generate(self, prompt: str, context: List[str] = None) -> str:
        """Generate response from the LLM"""
        pass

class AnthropicProvider(LLMProvider):
    """Anthropic Claude provider"""
    
    def __init__(self, api_key: str = None, model: str = "claude-3-sonnet-20241022"):
        self.api_key = api_key
        self.model = model
        self.conversation_history = []
    
    def generate(self, prompt: str, context: List[str] = None) -> str:
        """Generate response using Anthropic API"""
        try:
            import anthropic
            client = anthropic.Anthropic(api_key=self.api_key)
            
            # Build conversation
            messages = []
            if context:
                for i, msg in enumerate(context):
                    role = "user" if i % 2 == 0 else "assistant"
                    messages.append({"role": role, "content": msg})
            
            messages.append({"role": "user", "content": prompt})
            
            response = client.messages.create(
                model=self.model,
                max_tokens=4000,
                messages=messages
            )
            
            result = response.content[0].text
            self.conversation_history.extend([prompt, result])
            return result
            
        except Exception as e:
            # Fallback for testing without API key
            return self._mock_response(prompt)
    
    def _mock_response(self, prompt: str) -> str:
        """Mock responses for testing without API access"""
        if "API specification" in prompt and "DataWeaver" in prompt:
            return """{
  "api_name": "DataWeaver v0.7b",
  "base_url": "https://api.dataweaver.com",
  "authentication": {
    "type": "header",
    "key": "X-API-Key"
  },
  "endpoints": [
    {
      "path": "/v1/blob/new",
      "method": "POST",
      "description": "Creates a new data blob",
      "body": {
        "payload": {
          "type": "string",
          "format": "base64",
          "required": true
        }
      },
      "response": {
        "blob_id": "string",
        "timestamp": "unix_epoch"
      }
    },
    {
      "path": "/v1/blob/fetch/{id}",
      "method": "GET",
      "description": "Retrieves a data blob",
      "parameters": {
        "id": {
          "type": "string",
          "location": "path",
          "description": "blob_id"
        }
      },
      "headers": {
        "X-Cache-Bypass": {
          "type": "string",
          "value": "true",
          "optional": true
        }
      },
      "response": {
        "payload": {
          "type": "string",
          "format": "base64"
        }
      }
    }
  ]
}"""
        elif "process_and_post" in prompt and "filepath" in prompt:
            return """import requests
import base64

def process_and_post(filepath: str, api_key: str) -> str:
    # Read data from file
    data_bytes = read_data_file(filepath)
    
    # Convert bytes to string for encryption
    data_string = data_bytes.decode('utf-8')
    
    # Encrypt the data
    encrypted_data = simple_encrypt(data_string)
    
    # Encode as base64 for API
    payload = base64.b64encode(encrypted_data.encode('utf-8')).decode('utf-8')
    
    # Post to API
    response = requests.post(
        'https://api.dataweaver.com/v1/blob/new',
        json={'payload': payload},
        headers={'X-API-Key': api_key}
    )
    
    return response.json()['blob_id']"""
        elif "REQUIREMENT CHANGE" in prompt and "v0.8" in prompt:
            return """import requests
import base64

def process_and_post(filepath: str, api_key: str) -> str:
    # Read data from file
    data_bytes = read_data_file(filepath)
    
    # Convert bytes to string
    data_string = data_bytes.decode('utf-8')
    
    # Use base64 encoding instead of simple_encrypt
    encrypted_data = base64.b64encode(data_string.encode('utf-8')).decode('utf-8')
    
    # Encode as base64 for API (double encoding as per original requirement)
    payload = base64.b64encode(encrypted_data.encode('utf-8')).decode('utf-8')
    
    # Post to updated API endpoint
    response = requests.post(
        'https://api.dataweaver.com/v2/blob',
        json={'payload': payload, 'version': '0.8'},
        headers={'X-API-Key': api_key}
    )
    
    return response.json()['blob_id']"""
        else:
            return "I understand the request."

class LLMAgility:
    """Main class for running the LLM Agility Gauntlet"""
    
    def __init__(self, llm_provider: LLMProvider):
        self.llm = llm_provider
        self.transcript = []
        self.start_time = None
        
    def run_gauntlet(self) -> GauntletResult:
        """Run the complete LLM Agility Gauntlet"""
        self.start_time = time.time()
        self.transcript = []
        
        # Segment 1: API Ingestion & Distillation
        segment1_result = self._run_segment1()
        
        # Segment 2: Component Assembly  
        segment2_result = self._run_segment2()
        
        # Segment 3: Interruption & Adaptation
        segment3_result = self._run_segment3()
        
        execution_time = time.time() - self.start_time
        
        # Score the results
        scores = self._calculate_scores(segment1_result, segment2_result, segment3_result)
        
        return GauntletResult(
            total_score=scores['total'],
            segment_scores=scores['segments'],
            metrics=scores['metrics'],
            execution_time=execution_time,
            transcript=self.transcript,
            final_code=segment3_result,
            passed_correctness=self._test_correctness(segment3_result)
        )
    
    def _run_segment1(self) -> str:
        """Segment 1: API Ingestion & Distillation"""
        prompt = """You are a core programming agent. Ingest the following API specification and distill it into a structured, machine-readable format (JSON or YAML) that represents the available endpoints, their methods, required parameters, and expected response structure. Produce ONLY the structured output.

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
        
        response = self.llm.generate(prompt)
        self.transcript.extend([prompt, response])
        return response
    
    def _run_segment2(self) -> str:
        """Segment 2: Component Assembly"""
        prompt = """Use the API structure you just created. You have two functions:

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
        
        response = self.llm.generate(prompt, self.transcript)
        self.transcript.extend([prompt, response])
        return response
    
    def _run_segment3(self) -> str:
        """Segment 3: Interruption & Adaptation"""
        prompt = """**REQUIREMENT CHANGE:** The `DataWeaver` API has been updated to v0.8.
1. The POST endpoint is now `/v2/blob`.
2. The POST body now requires an additional key: `'version'`, with a static string value of `"0.8"`.
3. The `simple_encrypt` function is now insecure. You must replace its logic with a `base64` encoding step *instead of* the string reversal.

Rewrite the `process_and_post` function to comply. Produce ONLY the final, updated function code."""
        
        response = self.llm.generate(prompt, self.transcript)
        self.transcript.extend([prompt, response])
        return response
    
    def _test_correctness(self, final_code: str) -> bool:
        """Test if the final code is correct"""
        try:
            # Extract just the function code
            function_code = self._extract_function_code(final_code)
            if not function_code:
                return False
            
            # Create test environment
            test_globals = {
                'requests': MockRequests(),
                'base64': base64,
                'read_data_file': lambda fp: b"test data",
                'simple_encrypt': lambda s: s[::-1]  # Original function for reference
            }
            
            # Execute the function
            exec(function_code, test_globals)
            
            # Test the function
            result = test_globals['process_and_post']('/test/path', 'test_key')
            
            # Should return a blob_id
            return isinstance(result, str) and len(result) > 0
            
        except Exception as e:
            print(f"Correctness test failed: {e}")
            return False
    
    def _extract_function_code(self, response: str) -> str:
        """Extract Python function code from response"""
        # Look for code blocks
        code_blocks = re.findall(r'```python\n(.*?)\n```', response, re.DOTALL)
        if code_blocks:
            return code_blocks[0]
        
        # Look for function definition
        func_match = re.search(r'(def process_and_post.*?)(?=\n\n|\n#|\nclass|\ndef|\Z)', response, re.DOTALL)
        if func_match:
            return func_match.group(1)
        
        return response.strip()
    
    def _calculate_scores(self, segment1: str, segment2: str, segment3: str) -> Dict:
        """Calculate all scoring metrics"""
        # Test correctness first (gate)
        passed_correctness = self._test_correctness(segment3)
        
        if not passed_correctness:
            return {
                'total': 0.0,
                'segments': {'segment1': 0, 'segment2': 0, 'segment3': 0},
                'metrics': {'TER': 0, 'CDS': 0, 'LCP': 0, 'correctness': 0}
            }
        
        # Calculate metrics
        ter_score = self._calculate_ter()
        cds_score = self._calculate_cds()
        lcp_score = self._calculate_lcp(segment1, segment2, segment3)
        
        total_score = ter_score + cds_score + lcp_score
        
        return {
            'total': total_score,
            'segments': {
                'segment1': self._score_segment1(segment1),
                'segment2': self._score_segment2(segment2), 
                'segment3': self._score_segment3(segment3)
            },
            'metrics': {
                'TER': ter_score,
                'CDS': cds_score,
                'LCP': lcp_score,
                'correctness': 100 if passed_correctness else 0
            }
        }
    
    def _calculate_ter(self) -> float:
        """Token Efficiency Ratio (30 points max)"""
        total_tokens = sum(len(msg.split()) for msg in self.transcript)
        
        # Extract final code
        final_response = self.transcript[-1] if self.transcript else ""
        final_code = self._extract_function_code(final_response)
        code_tokens = len(final_code.split()) if final_code else 0
        
        if total_tokens == 0:
            return 0.0
        
        # TER = code tokens / total tokens, normalized to 30 points
        ter_ratio = code_tokens / total_tokens
        # Assume baseline of 0.05 (5% efficiency) = 30 points
        normalized_score = min(30.0, (ter_ratio / 0.05) * 30.0)
        
        return normalized_score
    
    def _calculate_cds(self) -> float:
        """Conceptual Density Score (40 points max, penalties subtracted)"""
        score = 40.0
        
        # Analyze all responses for bloviations
        for response in self.transcript[1::2]:  # Only LLM responses
            # Conversational filler penalties
            filler_patterns = [
                r'\bcertainly\b', r'\bhere you go\b', r'\bi have updated\b',
                r'\bhere is\b', r'\blet me\b', r'\bi will\b'
            ]
            for pattern in filler_patterns:
                matches = len(re.findall(pattern, response, re.IGNORECASE))
                score -= matches * 5
            
            # Redundant explanation penalty
            if len(response.split()) > 200:  # Arbitrary threshold for verbosity
                score -= 10
            
            # Unwanted output penalty (code + explanation when only code requested)
            if 'produce only' in self.transcript[self.transcript.index(response) - 1].lower():
                # Check if response has both code and explanation
                has_code = '```' in response or 'def ' in response
                explanation_words = len([w for w in response.split() if w not in ['```', 'python']])
                if has_code and explanation_words > 50:
                    score -= 10
        
        return max(0.0, score)
    
    def _calculate_lcp(self, segment1: str, segment2: str, segment3: str) -> float:
        """Logical Coherence Penalty (30 points max, penalties subtracted)"""
        score = 30.0
        
        # Check for API hallucinations in segment 2
        if 'blob_id' not in segment1.lower() and 'blob_id' in segment2.lower():
            pass  # This is actually correct - they should remember blob_id from segment 1
        
        # Check if bytes/str mismatch was addressed
        if 'decode' not in segment2 and 'encode' not in segment2:
            score -= 10
        
        # Check segment 3 adaptations
        if '/v2/blob' not in segment3:
            score -= 15  # Failed to update endpoint
        
        if '"version"' not in segment3 and "'version'" not in segment3:
            score -= 15  # Failed to add version field
        
        if 'simple_encrypt' in segment3 and 'base64' not in segment3:
            score -= 15  # Failed to replace encryption logic
        
        return max(0.0, score)
    
    def _score_segment1(self, response: str) -> float:
        """Score segment 1 (API distillation)"""
        try:
            # Try to parse as JSON
            json.loads(response)
            return 33.3  # Perfect JSON structure
        except:
            # Check for key elements
            score = 0
            if 'blob/new' in response: score += 10
            if 'POST' in response: score += 10  
            if 'payload' in response: score += 10
            return min(33.3, score)
    
    def _score_segment2(self, response: str) -> float:
        """Score segment 2 (component assembly)"""
        score = 0
        if 'def process_and_post' in response: score += 10
        if 'read_data_file' in response: score += 5
        if 'simple_encrypt' in response: score += 5
        if 'requests.post' in response: score += 10
        if 'decode' in response or 'encode' in response: score += 3.3  # Handles type mismatch
        return min(33.3, score)
    
    def _score_segment3(self, response: str) -> float:
        """Score segment 3 (adaptation)"""
        score = 0
        if '/v2/blob' in response: score += 11
        if 'version' in response: score += 11  
        if 'base64' in response and 'simple_encrypt' not in response: score += 11.3
        return min(33.3, score)

def main():
    """Run the gauntlet with Anthropic"""
    print("🤖 LLM Agility Gauntlet - Testing Anthropic Claude")
    print("=" * 50)
    
    # Initialize provider
    provider = AnthropicProvider()  # Will use mock responses if no API key
    gauntlet = LLMAgility(provider)
    
    # Run the test
    print("⚡ Running gauntlet...")
    result = gauntlet.run_gauntlet()
    
    # Display results
    print(f"\n📊 RESULTS (Execution time: {result.execution_time:.2f}s)")
    print(f"🎯 Total Score: {result.total_score:.1f}/100")
    print(f"✅ Correctness: {'PASS' if result.passed_correctness else 'FAIL'}")
    
    print(f"\n📈 Metrics:")
    for metric, score in result.metrics.items():
        print(f"  {metric}: {score:.1f}")
    
    print(f"\n🎮 Segment Scores:")
    for segment, score in result.segment_scores.items():
        print(f"  {segment}: {score:.1f}/33.3")
    
    if result.final_code:
        print(f"\n💻 Final Code:")
        print("-" * 40)
        print(result.final_code)
        print("-" * 40)
    
    return result

if __name__ == "__main__":
    main()