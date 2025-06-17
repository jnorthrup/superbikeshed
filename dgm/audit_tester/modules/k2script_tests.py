"""
Test module for auditing k2script servlets that expose HTTP endpoints.

This module, `K2ScriptTestModule`, assumes that a k2script server (e.g.,
one built with Ktor like in `setup_http_server.kts`) is already running
at a specified base URL. The tests then use `curl` commands to interact
with configured servlet endpoints to verify their responses.

This module follows a "command-style" pattern, where test methods generate
bash commands and associated output processing functions for the TestRunner.
"""
import json
from dgm.audit_tester.assertions import assert_true, assert_equal, assert_in, AuditAssertionError

class K2ScriptTestModule:
    """
    Contains test methods for auditing k2script servlets via HTTP interactions.

    Assumes that the k2script servlets are accessible via HTTP GET, POST, etc.,
    requests to a running k2script server. Test methods generate `curl` commands
    to perform these interactions.

    Each public method ending with `_command` is designed to be called by the
    TestRunner. These methods return a dictionary containing:
    - "test_name" (str): A descriptive name for the test instance.
    - "command" (str): The bash command (using `curl`) to be executed.
    - "process_output_func" (callable): A function that takes (stdout, stderr, exit_code)
                                       from the command execution and performs assertions,
                                       reporting results via the reporter.
    - "type" (str): Typically "bash", indicating the command type.
    """
    def __init__(self, reporter):
        """
        Initializes the K2ScriptTestModule.

        Args:
            reporter: An instance of the Reporter class for logging test results.
        """
        self.reporter = reporter

    def check_servlet_endpoint_command(self,
                                     server_base_url: str,
                                     request_path: str,
                                     http_method: str = "GET",
                                     expected_status_code: int = 200,
                                     expected_response_contains: str = None,
                                     extra_curl_options: str = ""):
        """
        Prepares a curl command to test a k2script HTTP servlet endpoint.

        It assumes the k2script server is already running at `server_base_url`.
        The curl command is constructed to make an HTTP request and capture both
        the response body and the HTTP status code.

        Args:
            server_base_url (str): The base URL of the running k2script server
                                   (e.g., "http://localhost:8090").
            request_path (str): The specific path of the servlet endpoint to test
                                (e.g., "/hello", "/data").
            http_method (str, optional): The HTTP method to use (GET, POST, PUT, DELETE, etc.).
                                         Defaults to "GET".
            expected_status_code (int, optional): The expected HTTP status code from the servlet.
                                                  Defaults to 200.
            expected_response_contains (str, optional): A substring expected to be present
                                                        in the servlet's response body.
                                                        If None, the body content is not checked for this.
                                                        Defaults to None.
            extra_curl_options (str, optional): Any additional options to pass directly to the
                                                `curl` command (e.g., for setting headers or data).
                                                Defaults to "".

        Returns:
            dict: A dictionary for the TestRunner, including the curl command and a
                  `process_output_func` to assert the HTTP status code and optionally
                  the presence of `expected_response_contains` in the response body.
        """
        # Ensure request_path starts with a slash if not empty.
        # Also handles if server_base_url might have a trailing slash.
        clean_base_url = server_base_url.rstrip('/')
        if not request_path.startswith("/") and request_path:
            request_path = "/" + request_path

        full_url = f"{clean_base_url}{request_path}"

        # Construct curl command:
        # -s: silent mode
        # -w "%{http_code}": appends HTTP status code to stdout after body
        # -X <METHOD>: specifies the HTTP method
        # extra_curl_options: allows for custom headers, data, etc.
        # Use .strip() to remove trailing space if extra_curl_options is empty.
        command = f'curl -s -w "%{{http_code}}" -X {http_method.upper()} {extra_curl_options} "{full_url}"'.strip()

        test_name_parts = [f"K2Script Endpoint: {http_method.upper()} {full_url} (HTTP {expected_status_code})"]
        if expected_response_contains:
            test_name_parts.append(f"(Body: '{expected_response_contains[:25]}...')")
        test_name = " ".join(test_name_parts)


        def process_output(stdout: str, stderr: str, exit_code: int):
            """Processes curl output to check k2script servlet response."""
            self.reporter.start_test_case(test_name)

            # stdout from `curl -w "%{http_code}"` is <response_body><http_code_3_digits>
            actual_body = ""
            actual_http_code_str = ""

            if len(stdout) >= 3 and stdout[-3:].isdigit(): # Check if last 3 chars are digits
                actual_body = stdout[:-3].strip()
                actual_http_code_str = stdout[-3:]
            else: # Could not reliably parse http_code from stdout
                actual_body = stdout.strip()
                # actual_http_code_str remains empty, assertion for digit will fail if code was expected.

            details = {
                "target_url": full_url,
                "http_method": http_method,
                "expected_http_status": expected_status_code,
                "expected_body_substring": expected_response_contains,
                "curl_command": command,
                "curl_process_exit_code": exit_code,
                "curl_stderr": stderr.strip(),
                "received_http_status_output": actual_http_code_str,
                "received_body_snippet": actual_body[:200] # Log a snippet
            }

            try:
                if exit_code != 0: # Curl process itself failed (e.g., host unreachable)
                    raise AuditAssertionError(f"Curl process failed with exit code {exit_code}. Stderr: '{stderr.strip()}'. Raw stdout (if any): '{stdout.strip()}'")

                if not actual_http_code_str.isdigit():
                    raise AuditAssertionError(f"Did not receive a valid 3-digit HTTP status code from curl -w. Raw stdout: '{stdout.strip()}'")

                actual_http_code = int(actual_http_code_str)
                assert_equal(actual_http_code, expected_status_code,
                             f"URL: {full_url} - Expected HTTP status {expected_status_code}, got {actual_http_code}.")

                if expected_response_contains is not None:
                    assert_in(expected_response_contains, actual_body,
                              f"Response body for {full_url} (status {actual_http_code}) did not contain expected string '{expected_response_contains}'. Body (first 500 chars): '{actual_body[:500]}...'")

                self.reporter.add_test_result(test_name, "passed", f"Servlet endpoint {full_url} responded as expected (HTTP Status: {actual_http_code}).", details)

            except AuditAssertionError as e:
                self.reporter.add_test_result(test_name, "failed", str(e), details)
            except Exception as e: # Catch any other unexpected error
                self.reporter.add_test_result(test_name, "error", f"Unexpected error processing test '{test_name}': {type(e).__name__} - {e}", details)
            finally:
                self.reporter.end_test_case(test_name)

        return {"test_name": test_name, "command": command, "process_output_func": process_output, "type": "bash"}


if __name__ == '__main__':
    # This block is for basic, conceptual testing of the module's command generation
    # and output processing logic. It does not involve actual bash execution.
    class DummyReporter:
        def start_test_case(self, name): print(f"\n[Reporter] Start: {name}") # Added newline
        def add_test_result(self, name, status, msg, details): print(f"  [Reporter] Result: {name} [{status.upper()}] - {msg} {json.dumps(details) if details else ''}")
        def end_test_case(self, name): print(f"  [Reporter] End: {name}")

    print("--- K2SCRIPT_TEST_MODULE IF __NAME__ == __MAIN__ (Conceptual Tests) ---")
    reporter = DummyReporter()
    k2_module = K2ScriptTestModule(reporter=reporter)

    sim_base_url = "http://localhost:8090" # Simulated k2script server

    # Test 1: Simple GET, expecting 200 and specific text in body
    test1_info = k2_module.check_servlet_endpoint_command(
        server_base_url=sim_base_url,
        request_path="/hello",
        http_method="GET",
        expected_status_code=200,
        expected_response_contains="Hello from k2script servlet"
    )
    print(f"Generated command (Test 1 GET /hello): {test1_info['command']}")
    print("Simulating Test 1 (success):")
    test1_info['process_output_func'](stdout="Response body: Hello from k2script servlet!200", stderr="", exit_code=0)

    print("Simulating Test 1 (failure - wrong status code):")
    test1_info['process_output_func'](stdout="Response body: Hello from k2script servlet!500", stderr="", exit_code=0)

    print("Simulating Test 1 (failure - wrong body content):")
    test1_info['process_output_func'](stdout="Response body: Unexpected content here200", stderr="", exit_code=0)

    # Test 2: POST request, expecting 201, no specific body content check
    test2_info = k2_module.check_servlet_endpoint_command(
        server_base_url=sim_base_url,
        request_path="/data",
        http_method="POST",
        expected_status_code=201,
        extra_curl_options="-d '{\"key\":\"value\"}' -H 'Content-Type: application/json'" # Example POST data
    )
    print(f"Generated command (Test 2 POST /data): {test2_info['command']}")
    print("Simulating Test 2 (success):")
    test2_info['process_output_func'](stdout='{"message":"Resource created"}201', stderr="", exit_code=0)

    # Test 3: Curl process error (e.g., host not found)
    test3_info = k2_module.check_servlet_endpoint_command(
        server_base_url="http://nonexistentk2server.audit:1234", # Invalid host
        request_path="/any_endpoint"
    )
    print(f"Generated command (Test 3 Curl Error): {test3_info['command']}")
    print("Simulating Test 3 (curl process error):")
    test3_info['process_output_func'](stdout="", stderr="curl: (6) Could not resolve host: nonexistentk2server.audit", exit_code=6)

    # Test 4: Endpoint returns empty body, but correct status code (e.g., 204 No Content)
    test4_info = k2_module.check_servlet_endpoint_command(
        server_base_url=sim_base_url,
        request_path="/empty_response",
        http_method="DELETE",
        expected_status_code=204
    )
    print(f"Generated command (Test 4 Empty Body 204): {test4_info['command']}")
    print("Simulating Test 4 (success - empty body, 204 status):")
    test4_info['process_output_func'](stdout="204", stderr="", exit_code=0) # Curl output is just the status code

    print("\n--- END K2SCRIPT_TEST_MODULE IF __NAME__ == __MAIN__ ---")
