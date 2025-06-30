"""
Test module for performing basic HTTP checks using curl.

This module, `HTTPTestModule`, provides methods that generate bash commands
(specifically `curl` commands) and corresponding output processing functions
to verify HTTP properties like URL status codes and HTTP versions.
It's designed to be used with the TestRunner, which executes these commands
and processes their results.
"""
import re
from dgm.audit_tester.assertions import assert_equal, AuditAssertionError

class HTTPTestModule:
    """
    Contains test methods for auditing HTTP endpoints.

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
        Initializes the HTTPTestModule.

        Args:
            reporter: An instance of the Reporter class for logging test results.
        """
        self.reporter = reporter

    def check_url_status_command(self, url: str, expected_status_code: int):
        """
        Prepares command and processing logic for checking a URL's HTTP status code.

        Args:
            url (str): The URL to check.
            expected_status_code (int): The expected HTTP status code.

        Returns:
            dict: A dictionary containing the test_name, curl command string,
                  a function to process the command's output, and the type ("bash").
                  The `process_output_func` asserts if the actual status code
                  matches `expected_status_code`.
        """
        command = f'curl -s -o /dev/null -w "%{{http_code}}" "{url}"'
        test_name = f"HTTP Status: {url} == {expected_status_code}"

        def process_output(stdout: str, stderr: str, exit_code: int):
            """Processes curl output to check HTTP status code."""
            self.reporter.start_test_case(test_name)
            actual_status_code_str = stdout.strip()
            details = {
                "url": url,
                "expected_status_code": expected_status_code,
                "command": command,
                "curl_exit_code": exit_code,
                "curl_stderr": stderr.strip(),
                "actual_http_code_stdout": actual_status_code_str
            }
            try:
                if exit_code != 0:
                    # Curl process itself failed (e.g., could not resolve host, network error).
                    # The HTTP status code from stdout might be empty or "000" in such cases.
                    raise AuditAssertionError(f"Curl command failed with exit code {exit_code}. Stderr: '{stderr.strip()}'. HTTP code output: '{actual_status_code_str}'")

                if not actual_status_code_str.isdigit():
                    raise AuditAssertionError(f"Did not receive a valid HTTP status code from curl. Output: '{actual_status_code_str}'")

                actual_status_code = int(actual_status_code_str)
                assert_equal(actual_status_code, expected_status_code,
                             f"URL: {url} - Expected HTTP status {expected_status_code}, got {actual_status_code}.")
                self.reporter.add_test_result(test_name, "passed", f"HTTP status code {actual_status_code} as expected for {url}.", details)

            except AuditAssertionError as e:
                self.reporter.add_test_result(test_name, "failed", str(e), details)
            except Exception as e: # Catch any other unexpected error during processing
                self.reporter.add_test_result(test_name, "error", f"Unexpected error processing test '{test_name}': {type(e).__name__} - {e}", details)
            finally:
                self.reporter.end_test_case(test_name)

        return {"test_name": test_name, "command": command, "process_output_func": process_output, "type": "bash"}

    def check_http_version_command(self, url: str, expected_version: str):
        """
        Prepares command and processing logic for checking the HTTP version of a server's response.

        Args:
            url (str): The URL to check.
            expected_version (str): The expected HTTP version string (e.g., "HTTP/1.1", "HTTP/2").

        Returns:
            dict: A dictionary containing the test_name, curl command string,
                  a function to process the command's output, and the type ("bash").
                  The `process_output_func` asserts if the detected HTTP version
                  matches `expected_version`.
        """
        # Curl's -I gets headers, -s for silent, -L to follow redirects.
        # `grep` extracts the first HTTP version line.
        command = f'curl -s -I -L "{url}" | grep -i "^HTTP/" | head -n 1'
        test_name = f"HTTP Version: {url} supports {expected_version}"

        def process_output(stdout: str, stderr: str, exit_code: int):
            """Processes curl output to check HTTP version."""
            self.reporter.start_test_case(test_name)
            output_line = stdout.strip()
            details = {
                "url": url,
                "expected_version": expected_version,
                "command": command,
                "curl_command_exit_code": exit_code, # Exit code of the entire pipe
                "curl_stderr": stderr.strip(), # Stderr from curl (grep/head might also produce stderr)
                "received_http_line": output_line
            }
            try:
                # `grep` exits with 1 if no lines match, which is a valid outcome (version not found).
                # `head` usually exits 0 unless there's an error.
                # If `exit_code` is non-zero AND `stdout` is empty, it's likely grep found nothing or curl failed.
                if exit_code != 0 and not output_line:
                    if stderr: # If curl produced stderr, that's more indicative of a curl problem.
                        raise AuditAssertionError(f"Curl command potentially failed (exit code {exit_code}, stderr: '{stderr.strip()}') or no HTTP version line found by grep.")
                    else: # Grep found no matching lines.
                        raise AuditAssertionError(f"No HTTP version line found in the response headers from {url}.")

                if not output_line: # Should be caught above, but as a safeguard.
                    raise AuditAssertionError(f"No HTTP version line output from command for {url}.")

                # Example output_line: "HTTP/1.1 200 OK" or "HTTP/2 200"
                match = re.match(r"^(HTTP/\d+(\.\d)?).*", output_line, re.IGNORECASE)
                if not match:
                    raise AuditAssertionError(f"Could not parse HTTP version from line: '{output_line}' for URL {url}.")

                actual_version = match.group(1).upper() # Normalize (e.g. http/2 -> HTTP/2)

                # Normalize expected_version for robust comparison (e.g., "2" -> "HTTP/2")
                normalized_expected_version = expected_version.upper()
                if not normalized_expected_version.startswith("HTTP/"):
                    normalized_expected_version = "HTTP/" + normalized_expected_version

                assert_equal(actual_version, normalized_expected_version,
                             f"URL: {url} - Expected HTTP version '{normalized_expected_version}', got '{actual_version}'.")
                self.reporter.add_test_result(test_name, "passed", f"HTTP version '{actual_version}' as expected for {url}.", details)

            except AuditAssertionError as e:
                self.reporter.add_test_result(test_name, "failed", str(e), details)
            except Exception as e:
                self.reporter.add_test_result(test_name, "error", f"Unexpected error processing test '{test_name}': {type(e).__name__} - {e}", details)
            finally:
                self.reporter.end_test_case(test_name)

        return {"test_name": test_name, "command": command, "process_output_func": process_output, "type": "bash"}

if __name__ == '__main__':
    # This block is for basic, conceptual testing of the module's command generation
    # and output processing logic. It does not involve actual bash execution.
    class DummyReporter:
        def start_test_case(self, name): print(f"\n[Reporter] Start: {name}")
        def add_test_result(self, name, status, msg, details): print(f"  [Reporter] Result: {name} [{status.upper()}] - {msg} {details if details else ''}")
        def end_test_case(self, name): print(f"  [Reporter] End: {name}")

    print("--- HTTP_TEST_MODULE IF __NAME__ == __MAIN__ (Conceptual Tests) ---")
    reporter = DummyReporter()
    http_module = HTTPTestModule(reporter=reporter)

    # Test check_url_status_command
    print("\n--- Testing check_url_status_command ---")
    status_test_info = http_module.check_url_status_command("https://www.google.com", 200)
    print(f"Generated command for status check: {status_test_info['command']}")

    print("Simulating status check (success):")
    status_test_info['process_output_func'](stdout="200", stderr="", exit_code=0)

    print("Simulating status check (failure - wrong status):")
    status_test_info['process_output_func'](stdout="404", stderr="", exit_code=0)

    print("Simulating status check (failure - curl error, e.g. host not found):")
    status_test_info['process_output_func'](stdout="000", stderr="curl: (6) Could not resolve host: www.google.com.invalid", exit_code=6)

    print("Simulating status check (failure - non-digit output):")
    status_test_info['process_output_func'](stdout="InvalidOutput", stderr="", exit_code=0)

    # Test check_http_version_command
    print("\n--- Testing check_http_version_command ---")
    version_test_info_h2 = http_module.check_http_version_command("https://www.google.com", "HTTP/2")
    print(f"Generated command for HTTP/2 version check: {version_test_info_h2['command']}")
    print("Simulating version check (HTTP/2 success):")
    version_test_info_h2['process_output_func'](stdout="HTTP/2 200 OK \r\nServer: gws", stderr="", exit_code=0)

    version_test_info_h1 = http_module.check_http_version_command("http://example.com", "HTTP/1.1")
    print(f"Generated command for HTTP/1.1 version check: {version_test_info_h1['command']}")
    print("Simulating version check (HTTP/1.1 success):")
    version_test_info_h1['process_output_func'](stdout="HTTP/1.1 200 OK\r\nContent-Type: text/html", stderr="", exit_code=0)

    print("Simulating version check (failure - unexpected version for Google H2 test):")
    version_test_info_h2['process_output_func'](stdout="HTTP/1.1 200 OK\r\nContent-Type: text/html", stderr="", exit_code=0)

    print("Simulating version check (failure - no HTTP line found by grep, e.g., grep exit code 1):")
    # Grep not finding a match results in empty stdout and often exit code 1 for the pipe.
    version_test_info_h1['process_output_func'](stdout="", stderr="", exit_code=1)

    print("Simulating version check (failure - unparseable HTTP line):")
    version_test_info_h1['process_output_func'](stdout="Malformed HTTP Response Line", stderr="", exit_code=0)

    print("Simulating version check (failure - curl command error):")
    version_test_info_h1['process_output_func'](stdout="", stderr="curl: (7) Failed to connect to host", exit_code=7)

    print("\nNote: These local simulations don't use `run_in_bash_session` or the TestRunner.")
    print("--- END HTTP_TEST_MODULE IF __NAME__ == __MAIN__ ---")
