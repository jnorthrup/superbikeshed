import argparse
import json
import os
import pathlib
import sys
import requests # For actual LLM calls
import base64
import time
import re
try:
    import tiktoken
except ImportError:
    tiktoken = None
    print("Warning: tiktoken library not found. Falling back to word/char counts for token estimation.", file=sys.stderr)


# --- Prompt Templates (from GAUNTLET_SPEC.md) ---
SEGMENT1_PROMPT_TEMPLATE = """
You are a core programming agent. Ingest the following API specification for '{service_name}' and distill it into a JSON object. The JSON object should have a main key "api_specification", which contains sub-keys "service_name" and "endpoints". "service_name" should be "{service_name}". "endpoints" should be an array of objects, where each object represents an endpoint and includes its "path", "method", "description", "required_headers" (array of strings, if any), "optional_headers" (array of strings, if any), "body_schema" (object describing JSON body keys and types, if any), and "response_schema" (object describing JSON response keys and types). Produce ONLY the JSON object as your output.

API SPECIFICATION: '{service_name}'
------------------------------------
{api_spec_text}
------------------------------------
"""

SEGMENT2_PROMPT_TEMPLATE = """
Using the API structure you just created for {service_name}, and the following two Python function snippets, write a single Python function `process_and_post(filepath: str, api_key: str) -> str`.

This function must:
1. Read data from the given `filepath` using Snippet A.
2. Convert the raw bytes from Snippet A into a UTF-8 string.
3. "Encrypt" this string using Snippet B.
4. The "encrypted" string must then be base64 encoded (UTF-8).
5. Post this final base64 encoded string as the 'payload' to the '/v1/blob/new' endpoint (from the {service_name} spec) using the 'requests' library.
6. Include the `api_key` in the 'X-API-Key' header.
7. Return the `blob_id` received from the API response. If the API call fails or returns an unexpected response, it should raise a `RuntimeError` with a descriptive message.

You must import `requests` and `base64`. Do not implement the API call; assume a function `make_api_request(method: str, url: str, headers: dict, json_payload: dict = None) -> dict` exists and will handle the actual HTTP call and return the parsed JSON response dictionary. Your function should call this `make_api_request` function.

Snippet A:
```python
{snippet_a_code}
```

Snippet B:
```python
{snippet_b_code}
```
Produce ONLY the Python function code for `process_and_post`. No explanation or surrounding text.
"""

SEGMENT3_PROMPT_TEMPLATE = """
URGENT REQUIREMENT CHANGE for `process_and_post` function and {service_name} API:

1.  The {service_name} API POST endpoint for new blobs is now `/v2/blob`.
2.  The POST request JSON body for `/v2/blob` now requires an additional top-level key: `"version"`, with a static string value of `"0.8_alpha"`. The 'payload' key is still required.
3.  The `simple_encrypt` function (Snippet B) is now considered insecure. Its use is forbidden. Instead of reversing the string, the new "encryption" requirement is to simply perform an additional layer of base64 encoding on the UTF-8 string that was previously passed to `simple_encrypt`. This means the data read from the file, converted to a UTF-8 string, should now be base64 encoded, and then this base64 string itself should be base64 encoded *again* to become the final payload.

Rewrite the entire `process_and_post(filepath: str, api_key: str) -> str` function to comply with all these new requirements. Remember to use the `make_api_request` function for the actual API call. Produce ONLY the final, updated Python function code.
"""

# --- Test Case Data Loading ---
class TestCaseData:
    def __init__(self, base_path):
        self.base_path = pathlib.Path(base_path)
        self.segment1_api_spec_input = self._read_file("segment1_api_spec_input.txt")
        self.segment1_expected_output = self._load_json("segment1_expected_output.json")
        self.snippet_a_code = self._read_file("segment2_snippet_A.py")
        self.snippet_b_code = self._read_file("segment2_snippet_B.py")
        self.segment2_expected_function_code = self._read_file("segment2_expected_function.py")
        self.segment3_expected_function_code = self._read_file("segment3_expected_function.py")
        self.mock_make_api_request_code = self._read_file("mock_make_api_request.py")
        self.service_name = "DataWeaver v0.7b" # Hardcoded for now, could be in spec

    def _read_file(self, filename):
        try:
            with open(self.base_path / filename, 'r', encoding='utf-8') as f:
                return f.read()
        except FileNotFoundError:
            print(f"Error: Test case file {self.base_path / filename} not found.", file=sys.stderr)
            sys.exit(1)
        except Exception as e:
            print(f"Error reading test case file {self.base_path / filename}: {e}", file=sys.stderr)
            sys.exit(1)

    def _load_json(self, filename):
        content = self._read_file(filename)
        try:
            return json.loads(content)
        except json.JSONDecodeError as e:
            print(f"Error decoding JSON from {self.base_path / filename}: {e}", file=sys.stderr)
            sys.exit(1)

def load_test_case_files(test_case_dir_path: str) -> TestCaseData:
    return TestCaseData(test_case_dir_path)

# --- Scoring Helpers ---
def compare_json_outputs(generated_json_str: str, expected_json_obj: dict) -> bool:
    """
    Parses the generated JSON string and compares it with the expected JSON object.
    Returns True if they match, False otherwise.
    """
    try:
        # Attempt to strip markdown code block fences if present
        if generated_json_str.strip().startswith("```json"):
            generated_json_str = generated_json_str.strip()[7:]
            if generated_json_str.strip().endswith("```"):
                generated_json_str = generated_json_str.strip()[:-3]

        parsed_generated_json = json.loads(generated_json_str)
        return parsed_generated_json == expected_json_obj
    except json.JSONDecodeError as e:
        print(f"JSON Parsing Error: {e}", file=sys.stderr)
        print(f"Problematic JSON string: {generated_json_str[:500]}...", file=sys.stderr) # Print snippet
        return False
    except Exception as e:
        print(f"An unexpected error occurred during JSON comparison: {e}", file=sys.stderr)
        return False

    # Store parsed JSON on success for LCP checks
    segment_scores["segment1"]["parsed_json"] = parsed_generated_json
    return True


def extract_python_code(llm_output_str: str) -> str | None:
    """
    Extracts Python code from a string, looking for fenced code blocks.
    Returns the extracted code content or None if no block is found.
    """
    # Regex to find ```python ... ``` or ``` ... ```
    # It captures the content within the fences.
    # re.DOTALL allows '.' to match newlines.
    match_python = re.search(r"```python\s*\n(.*?)\n```", llm_output_str, re.DOTALL)
    if match_python:
        return match_python.group(1).strip()

    match_generic = re.search(r"```\s*\n(.*?)\n```", ll_output_str, re.DOTALL)
    if match_generic:
        return match_generic.group(1).strip()

    # Fallback: if the whole string might be code (e.g. no fences, but looks like a function)
    # This is a very basic check. More advanced checks could be added.
    if "def " in llm_output_str and ":" in llm_output_str:
         # Check if there's any surrounding text that isn't comments or imports
        lines = llm_output_str.strip().split('\n')
        non_code_like_lines = 0
        for line in lines:
            stripped_line = line.strip()
            if not stripped_line: # ignore empty lines
                continue
            if stripped_line.startswith("#"): # ignore comments
                continue
            if stripped_line.startswith("import ") or stripped_line.startswith("from "): # ignore imports
                continue
            if "def " in stripped_line or "return " in stripped_line or "  " in line or stripped_line.endswith(":"): # could be part of func
                continue
            # If line doesn't look like typical code structure and isn't a common preamble/postamble
            if not any(kw in stripped_line for kw in ["class ", "try:", "except:", "if ", "else ", "elif ", "for ", "while "]):
                if len(stripped_line.split()) > 5 and not any(char in stripped_line for char in ['=', '(', ')', '[', ']', '{', '}']): # Arbitrary: looks like natural lang
                    non_code_like_lines +=1

        if non_code_like_lines < 2 : # Allow for one line of preamble/postamble
            return llm_output_str.strip() # Assume the whole thing might be code if few non-code lines

    print("Warning: Could not find a clear Python code block using ```python or ``` fences.", file=sys.stderr)
    # If no fenced block, and it doesn't look like a raw code string, return None or the string itself based on policy.
    # For now, let's be strict and require fences, or allow if it looks very much like code.
    # However, if the LLM is asked for "ONLY code" and provides no fences, the whole thing might be code.
    # The CDS score will heavily penalize if there's surrounding text AND no fences.
    # If after stripping, it starts with "def " or "import ", assume it's all code.
    stripped_output = llm_output_str.strip()
    if stripped_output.startswith("def ") or stripped_output.startswith("import "):
        print("Info: No code fences found, but output starts with 'def ' or 'import '. Assuming entire output is code for extraction.", file=sys.stderr)
        return stripped_output

    return None


def execute_generated_code_segment2(generated_code_str: str, test_case_data: TestCaseData) -> tuple[str | None, str | None]:
    """
    Executes the generated Python code string for Segment 2.
    The code should define `process_and_post`.
    Returns (result_blob_id, error_message).
    """
    try:
        # Prepare the execution environment
        # Import base64 for the generated code to use
        # The mock_make_api_request, read_data_file, simple_encrypt will be exec'd
        execution_globals = {
            "base64": base64,
            "__builtins__": __builtins__ # Ensure basic builtins are available
        }

        # Execute the snippets and mock API to make them available in the context
        exec(test_case_data.snippet_a_code, execution_globals)
        exec(test_case_data.snippet_b_code, execution_globals)
        exec(test_case_data.mock_make_api_request_code, execution_globals)

        # Execute the LLM's generated code (should define process_and_post)
        exec(generated_code_str, execution_globals)

        process_and_post_func = execution_globals.get("process_and_post")

        if not callable(process_and_post_func):
            return None, "Error: 'process_and_post' function not defined or not callable in generated code."

        # Call the generated function
        # Provide dummy values for filepath and api_key as per typical test execution
        result_blob_id = process_and_post_func(filepath="test_file.txt", api_key="test_api_key_123")

        return str(result_blob_id), None # Ensure result is string

    except Exception as e:
        import traceback
        error_details = traceback.format_exc()
        print(f"Error during generated code execution for Segment 2: {e}\n{error_details}", file=sys.stderr)
        return None, f"Execution Error: {str(e)}"

def execute_generated_code_segment3(generated_code_str: str, test_case_data: TestCaseData) -> tuple[str | None, str | None]:
    """
    Executes the generated Python code string for Segment 3.
    The code should define `process_and_post` and NOT use `simple_encrypt`.
    Returns (result_blob_id, error_message).
    """
    try:
        execution_globals = {
            "base64": base64,
            "__builtins__": __builtins__
        }

        # Execute Snippet A (read_data_file) and the mock API
        exec(test_case_data.snippet_a_code, execution_globals)
        exec(test_case_data.mock_make_api_request_code, execution_globals)

        # IMPORTANT: Do NOT execute snippet_b_code (simple_encrypt) for Segment 3

        # Check if the generated code attempts to import or define simple_encrypt
        if "simple_encrypt" in generated_code_str:
            return None, "Error: Generated code for Segment 3 still contains a reference to 'simple_encrypt'."

        exec(generated_code_str, execution_globals)

        process_and_post_func = execution_globals.get("process_and_post")

        if not callable(process_and_post_func):
            return None, "Error: 'process_and_post' function not defined or not callable in generated code for Segment 3."

        result_blob_id = process_and_post_func(filepath="test_file.txt", api_key="test_api_key_123")

        return str(result_blob_id), None

    except Exception as e:
        import traceback
        error_details = traceback.format_exc()
        print(f"Error during generated code execution for Segment 3: {e}\n{error_details}", file=sys.stderr)
        return None, f"Execution Error: {str(e)}"

# --- LLM Interaction ---
def call_llm(model_id: str, api_key: str, base_url: str, conversation_history: list,
             anthropic_version: str = "2023-06-01", system_prompt: str = None,
             max_tokens: int = 2048, temperature: float = 0.2):
    headers = {
        "Content-Type": "application/json",
    }
    is_anthropic = "anthropic" in base_url.lower()

    if is_anthropic:
        headers["x-api-key"] = api_key
        headers["anthropic-version"] = anthropic_version
    else:
        headers["Authorization"] = f"Bearer {api_key}"

    current_messages = []
    if system_prompt:
        if is_anthropic:
            # Anthropic handles system prompt at top level of payload
            pass
        elif conversation_history and conversation_history[0]["role"] == "system":
            # If history already starts with system, assume it's there
             current_messages = list(conversation_history)
        else:
            # Prepend system prompt for OpenAI-like
            current_messages.append({"role": "system", "content": system_prompt})
            current_messages.extend(conversation_history)
    else:
        current_messages = list(conversation_history)


    payload = {
        "model": model_id,
        "messages": current_messages,
        "max_tokens": max_tokens,
        "temperature": temperature,
    }

    if is_anthropic and system_prompt:
        payload["system"] = system_prompt


    assistant_response_content = ""
    try:
        print(f"\n--- Calling LLM ({model_id}) ---")
        print(f"Endpoint: {base_url}")
        # print(f"Payload: {json.dumps(payload, indent=2)}") # For debugging

        response = requests.post(base_url, headers=headers, json=payload, timeout=180) # 3 min timeout
        response.raise_for_status() # Raises HTTPError for bad responses (4XX or 5XX)

        response_data = response.json()
        # print(f"LLM Raw Response: {json.dumps(response_data, indent=2)}") # For debugging

        if is_anthropic:
            if response_data.get("content") and isinstance(response_data["content"], list) and response_data["content"][0].get("type") == "text":
                assistant_response_content = response_data["content"][0]["text"]
            else:
                assistant_response_content = f"Error: Unexpected Anthropic response format: {response_data}"
        else: # OpenAI-like
            if response_data.get("choices") and response_data["choices"][0].get("message"):
                assistant_response_content = response_data["choices"][0]["message"]["content"]
            else:
                assistant_response_content = f"Error: Unexpected OpenAI-like response format: {response_data}"

        print(f"LLM Response Text: {assistant_response_content[:200]}...")


    except requests.exceptions.RequestException as e:
        print(f"Error calling LLM API: {e}", file=sys.stderr)
        assistant_response_content = f"Error: API call failed: {str(e)}"
    except Exception as e:
        print(f"Generic error during LLM call or response processing: {e}", file=sys.stderr)
        assistant_response_content = f"Error: Processing failed: {str(e)}"

    conversation_history.append({"role": "assistant", "content": assistant_response_content})
    return assistant_response_content


# --- Gauntlet Segment Logic ---
def run_segment_1(test_case_data: TestCaseData, llm_config: dict, conversation_history: list, segment_scores: dict):
    print("\n--- Running Segment 1: API Ingestion & Distillation ---")
    system_prompt = "You are a core programming agent." # As per spec
    prompt = SEGMENT1_PROMPT_TEMPLATE.format(
        service_name=test_case_data.service_name,
        api_spec_text=test_case_data.segment1_api_spec_input
    )
    conversation_history.append({"role": "user", "content": prompt})
    llm_output = call_llm(conversation_history=conversation_history, system_prompt=system_prompt, **llm_config)

    # Scoring for Segment 1
    parsed_json = None
    try:
        # Attempt to strip markdown code block fences if present
        json_str_for_parsing = llm_output
        if llm_output.strip().startswith("```json"):
            json_str_for_parsing = llm_output.strip()[7:]
            if json_str_for_parsing.strip().endswith("```"):
                json_str_for_parsing = json_str_for_parsing.strip()[:-3]
        parsed_json = json.loads(json_str_for_parsing)
    except json.JSONDecodeError:
        parsed_json = None # Will fail comparison

    is_correct = compare_json_outputs(llm_output, test_case_data.segment1_expected_output) # compare_json_outputs handles parsing again
    segment_scores["segment1"]["correctness"] = 1 if is_correct else 0
    if is_correct:
        segment_scores["segment1"]["parsed_json"] = parsed_json # Store for LCP
    print(f"Segment 1 Correctness: {'PASSED' if is_correct else 'FAILED'}")

    return llm_output

def run_segment_2(test_case_data: TestCaseData, llm_config: dict, conversation_history: list, segment_outputs: dict, segment_scores: dict):
    print("\n--- Running Segment 2: Component Assembly ---")
    # Segment 1 output (JSON string) is implicitly part of the history the LLM sees.
    # The prompt asks it to use the "API structure you just created".
    prompt = SEGMENT2_PROMPT_TEMPLATE.format(
        service_name=test_case_data.service_name,
        snippet_a_code=test_case_data.snippet_a_code,
        snippet_b_code=test_case_data.snippet_b_code
    )
    conversation_history.append({"role": "user", "content": prompt})
    llm_output = call_llm(conversation_history=conversation_history, **llm_config)
    # Scoring for Segment 2
    extracted_code = extract_python_code(llm_output)
    segment_outputs["segment2_extracted_code"] = extracted_code # Store for TER
    if not extracted_code:
        segment_scores["segment2"]["correctness"] = 0
        print(f"Segment 2 Correctness: FAILED (No Python code block found)")
    else:
        print(f"Segment 2 Extracted Code:\n{extracted_code[:300]}...")
        result, error = execute_generated_code_segment2(extracted_code, test_case_data)

        expected_blob_id_s2 = "mock_blob_seg2_123"

        if result == expected_blob_id_s2 and error is None:
            segment_scores["segment2"]["correctness"] = 1
            print(f"Segment 2 Correctness: PASSED (Result: {result})")
        else:
            segment_scores["segment2"]["correctness"] = 0
            print(f"Segment 2 Correctness: FAILED (Result: {result}, Error: {error})")

    return llm_output

def run_segment_3(test_case_data: TestCaseData, llm_config: dict, conversation_history: list, segment_outputs: dict, segment_scores: dict):
    print("\n--- Running Segment 3: Interruption & Adaptation ---")
    prompt = SEGMENT3_PROMPT_TEMPLATE.format(service_name=test_case_data.service_name)
    conversation_history.append({"role": "user", "content": prompt})
    llm_output = call_llm(conversation_history=conversation_history, **llm_config)

    # Scoring for Segment 3
    extracted_code = extract_python_code(llm_output)
    segment_outputs["segment3_extracted_code"] = extracted_code # Store for TER
    if not extracted_code:
        segment_scores["segment3"]["correctness"] = 0
        print(f"Segment 3 Correctness: FAILED (No Python code block found)")
    else:
        print(f"Segment 3 Extracted Code:\n{extracted_code[:300]}...")
        result, error = execute_generated_code_segment3(extracted_code, test_case_data)

        expected_blob_id_s3 = "mock_blob_seg3_456"

        if result == expected_blob_id_s3 and error is None:
            segment_scores["segment3"]["correctness"] = 1
            print(f"Segment 3 Correctness: PASSED (Result: {result})")
        else:
            segment_scores["segment3"]["correctness"] = 0
            print(f"Segment 3 Correctness: FAILED (Result: {result}, Error: {error})")

    return llm_output


# --- Token Counting and Scoring Functions ---
def count_tokens(text: str, model_id_for_tiktoken: str) -> int:
    """Counts tokens using tiktoken if available, otherwise falls back to word count."""
    if tiktoken:
        try:
            # Common practice: map specific model_ids to base model names for tiktoken
            # This mapping might need to be expanded.
            if "gpt-4" in model_id_for_tiktoken.lower(): model_name = "gpt-4"
            elif "gpt-3.5" in model_id_for_tiktoken.lower(): model_name = "gpt-3.5-turbo"
            elif "claude" in model_id_for_tiktoken.lower():
                # Tiktoken doesn't directly support Claude models.
                # Anthropic uses a different tokenizer. Word count is a rough proxy.
                # For more accuracy, one might use Anthropic's official tokenizer if available
                # or a character count scaled by a factor (e.g., chars/4).
                print(f"Warning: Tiktoken does not directly support Claude model '{model_id_for_tiktoken}'. Using word count.", file=sys.stderr)
                return len(text.split())
            else: # Default if specific model is not recognized by tiktoken directly
                model_name = "cl100k_base" # A common base encoding

            encoding = tiktoken.encoding_for_model(model_name)
            return len(encoding.encode(text))
        except Exception as e:
            # print(f"Tiktoken encoding failed for model '{model_id_for_tiktoken}' (using '{model_name}'): {e}. Falling back to word count.", file=sys.stderr)
            return len(text.split()) # Fallback
    return len(text.split()) # Fallback if tiktoken is not imported

def calculate_ter_score(conversation_history: list, segment_outputs: dict, segment_scores: dict, model_id: str) -> float:
    total_llm_response_tokens = 0
    for message in conversation_history:
        if message["role"] == "assistant":
            total_llm_response_tokens += count_tokens(message["content"], model_id)

    total_correct_code_output_tokens = 0
    if segment_scores["segment2"]["correctness"] == 1 and segment_outputs.get("segment2_extracted_code"):
        total_correct_code_output_tokens += count_tokens(segment_outputs["segment2_extracted_code"], model_id)
    if segment_scores["segment3"]["correctness"] == 1 and segment_outputs.get("segment3_extracted_code"):
        total_correct_code_output_tokens += count_tokens(segment_outputs["segment3_extracted_code"], model_id)

    if total_llm_response_tokens == 0:
        return 0 # Avoid division by zero

    ter_raw = total_correct_code_output_tokens / total_llm_response_tokens

    # Normalize TER score (example formula from spec)
    # Expected_Baseline_TER = 0.5 (meaning half the response tokens are useful code)
    # Adjust if needed, this is a placeholder.
    expected_baseline_ter = 0.3 # Lowered baseline as LLMs can be verbose
    ter_score = max(0, min(30, (ter_raw / expected_baseline_ter - 0.5) * 30))
    print(f"TER Calculation: CorrectCodeTokens={total_correct_code_output_tokens}, TotalResponseTokens={total_llm_response_tokens}, TER_raw={ter_raw:.3f}, TER_score={ter_score:.2f}")
    return ter_score

def calculate_cds_score(conversation_history: list, segment_outputs: dict, segment_scores: dict) -> int:
    cds = 40 # Max score
    filler_patterns = [
        r"certainly", r"here is the code", r"i have updated", r"sure, i can help",
        r"here's the updated code", r"i've updated the function", r"as per your request",
        r"let me know if you need anything else", r"hope this helps"
    ]
    # Check Segment 1 output (raw LLM output for the segment)
    s1_output_raw = segment_outputs.get("segment1", "")
    s1_parsed_json = segment_scores.get("segment1", {}).get("parsed_json")
    if s1_parsed_json: # Correctness implies it was parsable JSON
        # Check if the raw output contains more than just the JSON (and potential markdown)
        temp_json_str = json.dumps(s1_parsed_json) # Get a compact JSON string of what was parsed
        # A simple check: if raw output is much longer than compact JSON, likely contains extra text.
        # This is imperfect due to formatting, but a starting point.
        # A more robust check would involve trying to parse out the JSON and see what's left.
        stripped_s1_raw = s1_output_raw.replace("```json", "").replace("```", "").strip()
        if len(stripped_s1_raw) > len(temp_json_str) * 1.5: # Allow for some formatting overhead
             # Check if the non-JSON part is just a few words of preamble/postamble
            json_start = stripped_s1_raw.find('{')
            json_end = stripped_s1_raw.rfind('}')
            if json_start != -1 and json_end != -1 and json_end > json_start:
                preamble = stripped_s1_raw[:json_start].strip()
                postamble = stripped_s1_raw[json_end+1:].strip()
                if len(preamble.split()) > 5 or len(postamble.split()) > 5 : # More than 5 words of filler
                    print("CDS Penalty: Segment 1 output contains significant non-JSON text.")
                    cds -= 10
            elif len(stripped_s1_raw.split()) > len(temp_json_str.split()) + 10: # Fallback for non-fenced JSON with lots of text
                    print("CDS Penalty: Segment 1 output contains significant non-JSON text (fallback).")
                    cds -=10


    # Check Segment 2 & 3 for producing only code
    for i in [2, 3]:
        seg_output_raw = segment_outputs.get(f"segment{i}", "")
        seg_extracted_code = segment_outputs.get(f"segment{i}_extracted_code")
        if seg_extracted_code and seg_output_raw:
            # If extracted code is substantially different from raw output (after stripping fences)
            # it means there was extra text.
            stripped_raw = seg_output_raw.replace("```python", "").replace("```", "").strip()
            if stripped_raw != seg_extracted_code:
                # Further check: is the extra text just a few words of preamble/postamble?
                code_start = stripped_raw.find(seg_extracted_code)
                if code_start != -1 :
                    preamble = stripped_raw[:code_start].strip()
                    postamble = stripped_raw[code_start + len(seg_extracted_code):].strip()
                    if len(preamble.split()) > 5 or len(postamble.split()) > 5:
                        print(f"CDS Penalty: Segment {i} output contains significant non-code text.")
                        cds -=10
                elif len(stripped_raw.split()) > len(seg_extracted_code.split()) + 10 : # Fallback if extracted code not found as substring
                     print(f"CDS Penalty: Segment {i} output contains significant non-code text (fallback).")
                     cds -= 10


    # Conversational filler check for all assistant messages
    for message in conversation_history:
        if message["role"] == "assistant":
            content_lower = message["content"].lower()
            for pattern in filler_patterns:
                if re.search(pattern, content_lower):
                    print(f"CDS Penalty: Conversational filler found ('{pattern}')")
                    cds -= 5
                    break # Penalize once per message for filler

    # TODO: Redundant re-explanation (harder, placeholder)
    # TODO: Excessive comments (harder, placeholder)
    print(f"CDS Score: {cds}")
    return max(0, cds)


def calculate_lcp_score(segment_scores: dict, test_case_data: TestCaseData) -> int:
    lcp = 30 # Max score

    # Segment 1 LCP: Missing/incorrect fields in JSON (if structure was correct enough to pass initial check)
    s1_correctness = segment_scores["segment1"]["correctness"] == 1
    s1_parsed_json = segment_scores["segment1"].get("parsed_json")

    if s1_correctness and s1_parsed_json:
        expected_s1_json = test_case_data.segment1_expected_output
        # Example LCP check: Ensure all expected endpoints are present
        if len(s1_parsed_json.get("api_specification", {}).get("endpoints", [])) != \
           len(expected_s1_json.get("api_specification", {}).get("endpoints", [])):
            print("LCP Penalty: Segment 1 JSON missing endpoints or has extra.")
            lcp -= 5
        # Add more detailed field checks if necessary, e.g. comparing keys in each endpoint
        # For now, primary correctness check `compare_json_outputs` handles exact match.
        # This LCP part could be for more subtle logical errors if the main check is relaxed.
    elif not s1_correctness: # If S1 JSON was totally wrong, it's a major LCP fail.
        # This is already penalized by overall_correctness_gate, but can add specific LCP penalty.
        print("LCP Penalty: Segment 1 JSON was incorrect (major LCP issue).")
        lcp -=15


    # Segment 2 LCP penalties (these are mostly covered by correctness execution)
    if not segment_scores["segment2"]["correctness"] == 1 and s1_correctness : # only if S1 was correct
        # The errors for S2 correctness (e.g. function not defined, wrong result) are already coherence penalties.
        # If we had more granular error types from execute_generated_code_segment2, we could map them here.
        # For now, a general penalty if S2 correctness failed after S1 passed.
        print("LCP Penalty: Segment 2 failed correctness (general LCP issue).")
        lcp -= 10
        # Specific checks from spec (e.g. byte to str, base64) are implicitly tested by mock execution.

    # Segment 3 LCP penalties
    if not segment_scores["segment3"]["correctness"] == 1 and s1_correctness and segment_scores["segment2"]["correctness"] == 1:
        # Errors for S3 correctness (e.g., still using simple_encrypt, wrong endpoint, wrong payload)
        # are coherence penalties.
        print("LCP Penalty: Segment 3 failed correctness (general LCP issue).")
        lcp -= 10
        # The check for "simple_encrypt" in `execute_generated_code_segment3` is a key LCP item.
        # If the error message from execute_generated_code_segment3 specifically mentioned simple_encrypt,
        # we could apply a larger specific penalty here.
        # For now, failure in S3 correctness implies such LCP issues.

    # Placeholder for other LCP checks, e.g. hallucinations if LLM output non-code/JSON when asked for it
    print(f"LCP Score: {lcp}")
    return max(0, lcp)

# --- Main Gauntlet Runner ---
def run_gauntlet(args):
    if not os.path.exists(args.output_dir):
        os.makedirs(args.output_dir)

    print(f"Loading test case from: {args.test_case_path}")
    test_case_data = load_test_case_files(args.test_case_path)

    llm_config = {
        "model_id": args.model_id,
        "api_key": args.api_key,
        "base_url": args.base_url,
        "anthropic_version": args.anthropic_version
    }

    conversation_history = []
    # For storing raw outputs from each segment
    segment_outputs = {}
    # For storing scores from each segment
    segment_scores = {
        "segment1": {"correctness": 0, "cds": None, "lcp": None}, # Correctness is 0 or 1
        "segment2": {"correctness": 0, "ter_code_tokens": None, "cds": None, "lcp": None},
        "segment3": {"correctness": 0, "ter_code_tokens": None, "cds": None, "lcp": None},
    }
    final_scores = {
        "overall_correctness_gate": False, # True if all segments pass correctness
        "overall_score": 0,
        "TER_score_total": None, # Sum of TER contributions
        "CDS_score_total": None, # Sum of CDS contributions
        "LCP_score_total": None, # Sum of LCP contributions
    }


    # Segment 1
    segment_outputs["segment1"] = run_segment_1(test_case_data, llm_config, conversation_history, segment_scores)
    if segment_scores["segment1"]["correctness"] == 0:
        print("Segment 1 failed correctness check. Gauntlet run will not proceed to further segments for scoring.")
        # Results will be saved with current scores.
    else:
        # Segment 2 - only run if Segment 1 was correct
        segment_outputs["segment2"] = run_segment_2(test_case_data, llm_config, conversation_history, segment_outputs["segment1"], segment_scores)
        if segment_scores["segment2"]["correctness"] == 0:
            print("Segment 2 failed correctness check. Gauntlet run will not proceed to Segment 3 for scoring.")
        else:
            # Segment 3 - only run if Segment 2 was correct
            segment_outputs["segment3"] = run_segment_3(test_case_data, llm_config, conversation_history, segment_outputs["segment2"], segment_scores)
            if segment_scores["segment3"]["correctness"] == 0:
                 print("Segment 3 failed correctness check.")

    # Calculate overall correctness gate
    s1_correct = segment_scores["segment1"]["correctness"] == 1
    s2_correct = segment_scores["segment2"]["correctness"] == 1
    s3_correct = segment_scores["segment3"]["correctness"] == 1 # Will be 0 if segment not run

    if s1_correct and s2_correct and s3_correct:
        final_scores["overall_correctness_gate"] = True
        print("\nOverall Correctness Gate: PASSED")
        # Placeholder for actual overall score calculation (when other scores are implemented)
        # final_scores["overall_score"] = calculate_final_gauntlet_score(segment_scores, final_scores)
    else:
        final_scores["overall_correctness_gate"] = False
        final_scores["overall_score"] = 0 # Explicitly 0 if gate failed
        print("\nOverall Correctness Gate: FAILED (One or more segments failed correctness)")


    # --- Result Saving ---
    timestamp = time.strftime("%Y%m%d_%H%M%S")
    model_id_sanitized = re.sub(r'[^a-zA-Z0-9_-]', '_', args.model_id)
    results_filename = f"gauntlet_result_{model_id_sanitized}_{timestamp}.json"
    results_filepath = os.path.join(args.output_dir, results_filename)

    full_results = {
        "gauntlet_run_config": vars(args),
        "test_case_path": args.test_case_path,
        "run_timestamp": timestamp,
        "conversation_history": conversation_history,
        "segment_outputs_raw": segment_outputs, # Raw text from LLM for each segment's final turn
        "segment_scores": segment_scores, # Detailed scores per segment
        "final_scores": final_scores, # Overall scores
    }

    try:
        with open(results_filepath, 'w', encoding='utf-8') as f:
            json.dump(full_results, f, indent=2, ensure_ascii=False)
        print(f"\nGauntlet run complete. Results saved to: {results_filepath}")
    except Exception as e:
        print(f"Error saving results to {results_filepath}: {e}", file=sys.stderr)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run the LLM Agility Gauntlet.")
    parser.add_argument("--model_id", required=True, help="Identifier for the LLM model (e.g., 'claude-3-5-sonnet-20240620').")
    parser.add_argument("--api_key", required=True, help="API key for the LLM provider.")
    parser.add_argument("--base_url", required=True, help="Base URL for the LLM API endpoint (e.g., 'https://api.anthropic.com/v1/messages').")
    parser.add_argument("--anthropic_version", default="2023-06-01", help="Value for Anthropic-Version header if using Anthropic.")
    parser.add_argument("--test_case_path", default="test_cases/case_001/", help="Path to the test case directory.")
    parser.add_argument("--output_dir", default="gauntlet_results/", help="Directory to save the results JSON file.")

    # Example for local testing of the script structure:
    # You would typically provide your own model details and API key.
    # Example: python run_gauntlet.py --model_id your_model --api_key YOUR_KEY --base_url https://your.api/endpoint
    if len(sys.argv) <= 1: # If no arguments are passed, show help or use defaults for a dry run structure test
        # This is just for ensuring the script structure runs without actual API calls if not configured
        # For a real run, provide all required arguments.
        print("No CLI arguments provided. Using placeholder args for structure test (no actual API call will be made without a key).")
        print("Usage: python run_gauntlet.py --model_id <model_id> --api_key <api_key> --base_url <base_url_for_model_endpoint> [options]")
        # Create dummy args for a dry run if needed, but API key is required by call_llm
        # For now, let it proceed and fail at API key if not provided.
        # Or, one could mock call_llm further if api_key is "TEST_DRY_RUN" for example.

    args = parser.parse_args()
    run_gauntlet(args)
