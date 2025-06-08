import argparse
import json
import os
import pathlib
import sys
import requests # For actual LLM calls later
import base64   # For payload manipulation if directly testing snippets
import time
import re

# --- Constants for Prompts (extracted from GAUNTLET_SPEC.md) ---

SEGMENT_1_PROMPT_TEMPLATE = """
You are a core programming agent. Ingest the following API specification for 'DataWeaver v0.7b' and distill it into a JSON object. The JSON object should have a main key "api_specification", which contains sub-keys "service_name" and "endpoints". "service_name" should be "DataWeaver v0.7b". "endpoints" should be an array of objects, where each object represents an endpoint and includes its "path", "method", "description", "required_headers" (array of strings, if any), "optional_headers" (array of strings, if any), "body_schema" (object describing JSON body keys and types, if any), and "response_schema" (object describing JSON response keys and types). Produce ONLY the JSON object as your output.

API SPECIFICATION: 'DataWeaver v0.7b'
------------------------------------
{api_spec_content}
------------------------------------
"""

SEGMENT_2_PROMPT_TEMPLATE = """
Using the API structure you just created for DataWeaver v0.7b, and the following two Python function snippets, write a single Python function `process_and_post(filepath: str, api_key: str) -> str`.

This function must:
1. Read data from the given `filepath` using Snippet A.
2. Convert the raw bytes from Snippet A into a UTF-8 string.
3. "Encrypt" this string using Snippet B.
4. The "encrypted" string must then be base64 encoded (UTF-8).
5. Post this final base64 encoded string as the 'payload' to the `/v1/blob/new` endpoint using the 'requests' library.
6. Include the `api_key` in the 'X-API-Key' header.
7. Return the `blob_id` received from the API response. If the API call fails or returns an unexpected response, it should raise a `RuntimeError` with a descriptive message.

You must import `requests` and `base64`. Do not implement the API call; assume a function `make_api_request(method: str, url: str, headers: dict, json_payload: dict = None) -> dict` exists and will handle the actual HTTP call and return the parsed JSON response dictionary. Your function should call this `make_api_request` function.

Snippet A:
```python
{snippet_a_content}
```

Snippet B:
```python
{snippet_b_content}
```
Produce ONLY the Python function code for `process_and_post`. No explanation or surrounding text.
"""

SEGMENT_3_PROMPT_TEMPLATE = """
URGENT REQUIREMENT CHANGE for `process_and_post` function and DataWeaver API:

1.  The DataWeaver API POST endpoint for new blobs is now `/v2/blob`.
2.  The POST request JSON body for `/v2/blob` now requires an additional top-level key: `"version"`, with a static string value of `"0.8_alpha"`. The 'payload' key is still required.
3.  The `simple_encrypt` function (Snippet B) is now considered insecure. Its use is forbidden. Instead of reversing the string, the new "encryption" requirement is to simply perform an additional layer of base64 encoding on the UTF-8 string that was previously passed to `simple_encrypt`. This means the data read from the file, converted to a UTF-8 string, should now be base64 encoded, and then this base64 string itself should be base64 encoded *again* to become the final payload.

Rewrite the entire `process_and_post(filepath: str, api_key: str) -> str` function to comply with all these new requirements. Remember to use the `make_api_request` function for the actual API call. Produce ONLY the final, updated Python function code.
"""


class TestCaseData:
    def __init__(self, seg1_spec_input, seg1_expected_json,
                 seg2_snippet_a, seg2_snippet_b,
                 seg2_expected_func, seg3_expected_func):
        self.segment1_api_spec_input = seg1_spec_input
        self.segment1_expected_output_json = seg1_expected_json
        self.segment2_snippet_A_py = seg2_snippet_a
        self.segment2_snippet_B_py = seg2_snippet_b
        self.segment2_expected_function_py = seg2_expected_func
        self.segment3_expected_function_py = seg3_expected_func

def load_test_case_files(test_case_path: pathlib.Path) -> TestCaseData:
    """Loads all necessary files for a given test case."""
    print(f"Loading test case from: {test_case_path}")

    s1_spec_path = test_case_path / "segment1_api_spec_input.txt"
    s1_expected_path = test_case_path / "segment1_expected_output.json"
    s2_snippet_a_path = test_case_path / "segment2_snippet_A.py"
    s2_snippet_b_path = test_case_path / "segment2_snippet_B.py"
    s2_expected_func_path = test_case_path / "segment2_expected_function.py"
    s3_expected_func_path = test_case_path / "segment3_expected_function.py"

    try:
        seg1_spec_input = s1_spec_path.read_text()
        with open(s1_expected_path, 'r') as f:
            seg1_expected_json = json.load(f)
        seg2_snippet_a = s2_snippet_a_path.read_text()
        seg2_snippet_b = s2_snippet_b_path.read_text()
        seg2_expected_func = s2_expected_func_path.read_text()
        seg3_expected_func = s3_expected_func_path.read_text()

        return TestCaseData(
            seg1_spec_input, seg1_expected_json,
            seg2_snippet_a, seg2_snippet_b,
            seg2_expected_func, seg3_expected_func
        )
    except FileNotFoundError as e:
        print(f"Error: Missing file in test case path {test_case_path}: {e.filename}")
        sys.exit(1)
    except json.JSONDecodeError as e:
        print(f"Error: Could not parse JSON from {s1_expected_path}: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"An unexpected error occurred while loading test case files: {e}")
        sys.exit(1)

def call_llm(model_id: str, api_key: str, base_url: str, conversation_history: list, system_prompt: str = None, anthropic_version: str = "2023-06-01") -> str:
    """
    Calls the specified LLM API with the given conversation history.
    Appends the LLM's response to the conversation_history.
    """
    print(f"\n--- Calling LLM ({model_id}) ---")
    # Print snippet of the last user message
    if conversation_history and conversation_history[-1]["role"] == "user":
        print(f"User Prompt (last message snippet): {conversation_history[-1]['content'][:200]}...")
    else:
        print("No user prompt in the last message or history is empty/not ending with user message.")


    if not model_id or not api_key or not base_url:
        raise ValueError("LLM model_id, api_key, and base_url are required.")

    headers = {
        "Content-Type": "application/json",
    }

    # Prepare payload structure based on API type (inferred from base_url)
    is_anthropic = "anthropic" in base_url.lower()

    if is_anthropic:
        headers["x-api-key"] = api_key
        headers["anthropic-version"] = anthropic_version

        # For Anthropic, system prompt is a top-level parameter.
        # Messages should not contain a system role message.
        api_messages = [msg for msg in conversation_history if msg.get("role") != "system"]
        if system_prompt and not any(msg.get("role") == "system" for msg in api_messages): # ensure system prompt isn't duplicated
             # Anthropic expects system prompt as a top-level parameter
             pass # Will be added to the main payload body
        elif not system_prompt and any(msg.get("role") == "system" for msg in conversation_history) : # if system prompt is in history
            # This case implies the system prompt might be the first message in conversation_history.
            # For Anthropic, it's better to extract it.
            # However, the current Gauntlet structure adds user prompts, and system_prompt is passed separately.
            # So, this path should ideally not be hit if system_prompt parameter is used correctly.
            print("Warning: System prompt found in conversation history for Anthropic, but also passed as parameter or not expected.")


        payload = {
            "model": model_id,
            "messages": api_messages,
            "max_tokens": 2048, # Increased for potentially long outputs
            "temperature": 0.2,
        }
        if system_prompt:
            payload["system"] = system_prompt
    else: # OpenAI-like
        headers["Authorization"] = f"Bearer {api_key}"

        api_messages = []
        if system_prompt and not any(msg.get("role") == "system" for msg in conversation_history):
            api_messages.append({"role": "system", "content": system_prompt})
        api_messages.extend(conversation_history)

        payload = {
            "model": model_id,
            "messages": api_messages,
            "max_tokens": 2048,
            "temperature": 0.2,
        }

    print(f"Request URL: {base_url}")
    # print(f"Request Headers: {headers}") # Be careful about printing API keys
    print(f"Request Payload Model: {payload['model']}")
    print(f"Request Payload Messages Count: {len(payload['messages'])}")

    try:
        response = requests.post(base_url, headers=headers, json=payload, timeout=180) # 3 min timeout
        response.raise_for_status()  # Raises an HTTPError for bad responses (4XX or 5XX)

        response_data = response.json()

        if is_anthropic:
            if response_data.get("content") and isinstance(response_data["content"], list) and len(response_data["content"]) > 0:
                llm_response_content = response_data["content"][0].get("text", "")
            else:
                print(f"Warning: Anthropic response format unexpected or content is empty. Full response: {response_data}")
                llm_response_content = ""
        else: # OpenAI-like
            if response_data.get("choices") and isinstance(response_data["choices"], list) and len(response_data["choices"]) > 0:
                message = response_data["choices"][0].get("message", {})
                llm_response_content = message.get("content", "")
            else:
                print(f"Warning: OpenAI-like response format unexpected or choices are empty. Full response: {response_data}")
                llm_response_content = ""

        print(f"LLM Response (snippet): {llm_response_content[:200]}...")

    except requests.exceptions.RequestException as e:
        print(f"Error calling LLM API: {e}")
        # In case of error, append an error message to history to avoid breaking the flow
        # or allow retry mechanisms if implemented later.
        error_content = f"API Call Error: {str(e)}"
        llm_response_content = error_content # Return error message to be recorded
        # Fall-through to append this error as assistant message

    llm_response = {"role": "assistant", "content": llm_response_content}
    conversation_history.append(llm_response) # Modify history in-place
    return llm_response_content

def run_segment_1(test_case_data: TestCaseData, llm_config: dict, conversation_history: list) -> str:
    """Runs Segment 1: API Ingestion & Distillation."""
    print("\n--- Running Segment 1: API Ingestion & Distillation ---")

    # System prompt for Segment 1, LLM should act as a programming agent
    # This part of the prompt is implicitly part of the user message in the template.
    # For APIs that support a dedicated system prompt, we can extract it.
    # The prompt template starts with "You are a core programming agent..."
    # We can make this the system prompt.

    # The user prompt is the rest of the template.
    api_spec_formatted_prompt = SEGMENT_1_PROMPT_TEMPLATE.format(api_spec_content=test_case_data.segment1_api_spec_input)

    # Extract system prompt part if desired, or manage within user message based on API type
    # For simplicity, let's assume the initial part of SEGMENT_1_PROMPT_TEMPLATE can be the system prompt.
    # This is a bit of a heuristic.
    system_prompt_seg1 = "You are a core programming agent. Your goal is to follow instructions precisely."
    user_prompt_seg1 = api_spec_formatted_prompt # The template is already structured as a full user prompt after the intro.

    # If the API is not Anthropic, the system prompt is added by call_llm.
    # For Anthropic, it's passed as a parameter.
    # The conversation history should start fresh for segment 1, or with a system message if not passed as param.

    current_turn_user_message = {"role": "user", "content": user_prompt_seg1}
    conversation_history.append(current_turn_user_message)

    llm_output = call_llm(
        llm_config['model_id'],
        llm_config['api_key'],
        llm_config['base_url'],
        conversation_history, # call_llm will append assistant's response to this
        system_prompt=system_prompt_seg1,
        anthropic_version=llm_config.get("anthropic_version", "2023-06-01")
    )
    # TODO: Add preliminary validation of llm_output (e.g., is it valid JSON?)
    return llm_output

def run_segment_2(test_case_data: TestCaseData, llm_config: dict, conversation_history: list, segment1_llm_output: str) -> str:
    """Runs Segment 2: Component Assembly."""
    print("\n--- Running Segment 2: Component Assembly ---")
    # Segment 1 output (API spec JSON) is already in conversation_history (as assistant response)

    prompt_content = SEGMENT_2_PROMPT_TEMPLATE.format(
        snippet_a_content=test_case_data.segment2_snippet_A_py,
        snippet_b_content=test_case_data.segment2_snippet_B_py
    )
    current_turn_user_message = {"role": "user", "content": prompt_content}
    conversation_history.append(current_turn_user_message)

    llm_output = call_llm(
        llm_config['model_id'],
        llm_config['api_key'],
        llm_config['base_url'],
        conversation_history, # call_llm will append assistant's response
        system_prompt=None, # System prompt usually set at the beginning of a conversation
        anthropic_version=llm_config.get("anthropic_version", "2023-06-01")
    )
    # TODO: Add preliminary validation (e.g., is it valid Python code?)
    return llm_output

def run_segment_3(test_case_data: TestCaseData, llm_config: dict, conversation_history: list, segment2_llm_output: str) -> str:
    """Runs Segment 3: Interruption & Adaptation."""
    print("\n--- Running Segment 3: Interruption & Adaptation ---")
    # Segment 2 output (Python function) is already in conversation_history (as assistant response)

    prompt_content = SEGMENT_3_PROMPT_TEMPLATE
    current_turn_user_message = {"role": "user", "content": prompt_content}
    conversation_history.append(current_turn_user_message)

    llm_output = call_llm(
        llm_config['model_id'],
        llm_config['api_key'],
        llm_config['base_url'],
        conversation_history, # call_llm will append assistant's response
        system_prompt=None,
        anthropic_version=llm_config.get("anthropic_version", "2023-06-01")
    )
    # TODO: Add preliminary validation
    return llm_output

def run_gauntlet(args):
    """Main function to run the LLM Agility Gauntlet."""
    test_case_path = pathlib.Path(args.test_case_path)
    output_dir = pathlib.Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    test_case_data = load_test_case_files(test_case_path)

    llm_config = {
        "model_id": args.model_id,
        "api_key": args.api_key,
        "base_url": args.base_url,
        # Add anthropic_version if provided, or other provider-specific configs
        "anthropic_version": getattr(args, 'anthropic_version', '2023-06-01')
    }

    # Initialize conversation_history.
    # For some models (like OpenAI), a system prompt can be the first message.
    # For Anthropic, system prompt is a top-level parameter to `call_llm`.
    # The `call_llm` function will handle placing the system prompt correctly.
    conversation_history = []
    results = {
        "model_id": args.model_id,
        "test_case": str(test_case_path),
        "timestamp_start": time.time(),
        "segments": {},
        "conversation_history": conversation_history,
        "preliminary_scores": {} # Placeholder for future scoring
    }

    # Segment 1
    s1_output = run_segment_1(test_case_data, llm_config, conversation_history)
    results["segments"]["segment_1"] = {"llm_output": s1_output}
    # TODO: Add scoring for segment 1

    # Segment 2
    s2_output = run_segment_2(test_case_data, llm_config, conversation_history, s1_output)
    results["segments"]["segment_2"] = {"llm_output": s2_output}
    # TODO: Add scoring for segment 2

    # Segment 3
    s3_output = run_segment_3(test_case_data, llm_config, conversation_history, s2_output)
    results["segments"]["segment_3"] = {"llm_output": s3_output}
    # TODO: Add scoring for segment 3

    results["timestamp_end"] = time.time()
    results["duration_seconds"] = results["timestamp_end"] - results["timestamp_start"]

    # Sanitize model_id for filename
    sanitized_model_id = re.sub(r'[^a-zA-Z0-9_-]', '_', args.model_id)
    timestamp_str = time.strftime("%Y%m%d_%H%M%S")
    results_filename = f"gauntlet_result_{sanitized_model_id}_{timestamp_str}.json"
    results_filepath = output_dir / results_filename

    print(f"\n--- Saving results to: {results_filepath} ---")
    with open(results_filepath, 'w') as f:
        json.dump(results, f, indent=2)

    print("Gauntlet run complete.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run the LLM Agility Gauntlet.")
    parser.add_argument("--model_id", required=True, help="Identifier for the LLM model.")
    parser.add_argument("--api_key", required=True, help="API key for the LLM provider.")
    parser.add_argument("--base_url", required=True, help="Base URL for the LLM API.")
    parser.add_argument("--test_case_path", required=True, help="Path to the test case directory.")
    parser.add_argument("--output_dir", required=True, help="Directory to save the results JSON file.")
    parser.add_argument("--anthropic_version", default="2023-06-01", help="Anthropic API version (if using Anthropic).")


    if len(sys.argv) == 1:
        # Example usage for quick testing if no args are provided
        # Note: This example will fail if not using a mock server or if run from a different directory
        # as test_case_path is relative.
        print("No arguments provided. Running with example default arguments for placeholder run.")
        example_args = [
            "--model_id", "mock-model",
            "--api_key", "NO_KEY_NEEDED_FOR_MOCK",
            "--base_url", "http://localhost:1234/mock",
            "--test_case_path", "./test_cases/case_001/", # Relative to script location
            "--output_dir", "./gauntlet_results/"
        ]
        # Ensure example paths are relative to this script's location for robustness
        script_dir = pathlib.Path(__file__).parent.resolve()
        example_test_case_path = str(script_dir / "test_cases/case_001/")
        example_output_dir = str(script_dir / "gauntlet_results/")

        # Check if example test case path exists before trying to run
        if not (script_dir / "test_cases/case_001/").exists():
            print(f"Warning: Example test case path {example_test_case_path} does not exist. Skipping example run.")
            parser.print_help() # Print help if example can't run
            sys.exit(0) # Exit cleanly as this is just for placeholder

        example_args[7] = example_test_case_path
        example_args[9] = example_output_dir

        args = parser.parse_args(example_args)
        print(f"Running with example arguments: {args}")
    else:
        args = parser.parse_args()

    run_gauntlet(args)
