import json

MOCK_API_RESPONSES = {
    "POST": {
        "/v1/blob/new": {
            "conditions": lambda headers, payload: headers.get("X-API-Key") and payload and "payload" in payload,
            "response_data": {"blob_id": "mock_blob_seg2_123", "timestamp": 1678886400}
        },
        "/v2/blob": {
            "conditions": lambda headers, payload: headers.get("X-API-Key") and payload and "payload" in payload and payload.get("version") == "0.8_alpha",
            "response_data": {"blob_id": "mock_blob_seg3_456", "timestamp": 1678886500}
        }
    }
}

def make_api_request(method: str, url: str, headers: dict, json_payload: dict = None) -> dict:
    # print(f"Mock API Call: {method} {url}") # Optional: for debugging test runner
    # print(f"Headers: {headers}")
    # print(f"Payload: {json_payload}")

    method_routes = MOCK_API_RESPONSES.get(method.upper())
    if not method_routes:
        raise RuntimeError(f"Mock API: Unhandled method {method.upper()}")

    route_config = method_routes.get(url)
    if not route_config:
        for path_key, config_val in method_routes.items():
            if url.endswith(path_key): # Allow for LLM prepending a hypothetical base URL
                route_config = config_val
                break
        if not route_config:
                raise RuntimeError(f"Mock API: Unhandled URL {url} for method {method.upper()}")

    if route_config["conditions"](headers, json_payload):
        # print(f"Mock API Response: {route_config['response_data']}") # Optional
        return route_config["response_data"]
    else:
        error_msg = f"Mock API: Conditions not met for {method.upper()} {url}. Headers: {headers}, Payload: {json_payload}"
        # print(error_msg) # Optional
        raise RuntimeError(error_msg)

# Example usage for testing the mock itself (optional)
# These local imports are for the __main__ block only
if __name__ == '__main__':
    import base64

    # Define read_data_file and simple_encrypt for testing expected functions
    # These are copies of what's in segment2_snippet_A.py and segment2_snippet_B.py
    def read_data_file(filepath: str) -> bytes:
        return b"Sample file content for " + filepath.encode('utf-8')
    def simple_encrypt(data_string: str) -> str:
        return data_string[::-1]

    # Test Segment 2 expected function
    try:
        print("Testing Segment 2 expected function call:")
        # This is the content of segment2_expected_function.py
        s2_func_code = '''
import base64

# def read_data_file(filepath: str) -> bytes: (defined above)
# def simple_encrypt(data_string: str) -> str: (defined above)
# def make_api_request(method: str, url: str, headers: dict, json_payload: dict = None) -> dict: (this file's function)

def process_and_post(filepath: str, api_key: str) -> str:
    raw_bytes = read_data_file(filepath)
    utf8_string = raw_bytes.decode('utf-8')
    encrypted_string = simple_encrypt(utf8_string)
    base64_payload = base64.b64encode(encrypted_string.encode('utf-8')).decode('utf-8')

    api_url_path = "/v1/blob/new"

    headers = {
        "X-API-Key": api_key
    }
    json_payload = {
        "payload": base64_payload
    }

    try:
        response = make_api_request(method="POST", url=api_url_path, headers=headers, json_payload=json_payload)
        if "blob_id" in response:
            return response["blob_id"]
        else:
            raise RuntimeError(f"API response missing 'blob_id'. Response: {response}")
    except Exception as e:
        raise RuntimeError(f"API call failed or returned unexpected response: {str(e)}")
'''
        # Prepare the execution context for the S2 function
        s2_exec_globals = {
            "make_api_request": make_api_request, # The mock from this file
            "read_data_file": read_data_file,     # Helper
            "simple_encrypt": simple_encrypt,     # Helper
            "base64": base64
        }
        exec(s2_func_code, s2_exec_globals)
        process_and_post_s2 = s2_exec_globals['process_and_post']

        result_s2 = process_and_post_s2("test.txt", "key123")
        assert result_s2 == "mock_blob_seg2_123"
        print("Segment 2 expected function call mock success.")
    except Exception as e:
        print(f"Segment 2 expected function call mock error: {e}")
        import traceback
        traceback.print_exc()

    # Test Segment 3 expected function
    try:
        print("\nTesting Segment 3 expected function call:")
        # This is the content of segment3_expected_function.py
        s3_func_code = '''
import base64

# def read_data_file(filepath: str) -> bytes: (defined above)
# def make_api_request(method: str, url: str, headers: dict, json_payload: dict = None) -> dict: (this file's function)

def process_and_post(filepath: str, api_key: str) -> str:
    raw_bytes = read_data_file(filepath)
    utf8_string = raw_bytes.decode('utf-8')

    base64_encoded_once = base64.b64encode(utf8_string.encode('utf-8')).decode('utf-8')
    final_payload = base64.b64encode(base64_encoded_once.encode('utf-8')).decode('utf-8')

    api_url_path = "/v2/blob"

    headers = {
        "X-API-Key": api_key
    }
    json_payload = {
        "payload": final_payload,
        "version": "0.8_alpha"
    }

    try:
        response = make_api_request(method="POST", url=api_url_path, headers=headers, json_payload=json_payload)
        if "blob_id" in response:
            return response["blob_id"]
        else:
            raise RuntimeError(f"API response missing 'blob_id'. Response: {response}")
    except Exception as e:
        raise RuntimeError(f"API call failed or returned unexpected response: {str(e)}")
'''
        # Prepare the execution context for the S3 function
        s3_exec_globals = {
            "make_api_request": make_api_request, # The mock from this file
            "read_data_file": read_data_file,     # Helper
            "base64": base64
        }
        exec(s3_func_code, s3_exec_globals)
        process_and_post_s3 = s3_exec_globals['process_and_post']

        result_s3 = process_and_post_s3("test.txt", "key123")
        assert result_s3 == "mock_blob_seg3_456"
        print("Segment 3 expected function call mock success.")
    except Exception as e:
        print(f"Segment 3 expected function call mock error: {e}")
        import traceback
        traceback.print_exc()
