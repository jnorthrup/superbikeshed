"""
Test module for auditing common command-line tools like `curl` and `aria2c`.

This module, `CmdToolsTestModule`, provides methods that generate bash commands
for these tools and include corresponding output processing functions.
It allows testing functionalities such as file downloads, HTTP header inspection,
and basic command execution checks (e.g., for cleanup tasks).
It is designed for use with the TestRunner.
"""
import json
import re # Not strictly used in current version, but often useful for parsing command output.
from dgm.audit_tester.assertions import assert_true, assert_equal, assert_in, AuditAssertionError

class CmdToolsTestModule:
    """
    Contains test methods for auditing command-line tools, primarily `curl` and `aria2c`.

    Each public method ending with `_command` is designed to be called by the
    TestRunner. These methods return a dictionary containing:
    - "test_name" (str): A descriptive name for the test instance.
    - "command" (str): The bash command to be executed.
    - "process_output_func" (callable): A function that takes (stdout, stderr, exit_code)
                                       from the command execution and performs assertions,
                                       reporting results via the reporter.
    - "type" (str): Typically "bash", indicating the command type.
    """
    def __init__(self, reporter):
        """
        Initializes the CmdToolsTestModule.

        Args:
            reporter: An instance of the Reporter class for logging test results.
        """
        self.reporter = reporter

    def check_run_arbitrary_command_command(self, command: str, expected_exit_code: int = 0):
        """
        Prepares an arbitrary bash command for execution and checks its exit code.

        This is a general-purpose method useful for running simple commands,
        including cleanup tasks (e.g., `rm`).

        Args:
            command (str): The arbitrary bash command string to execute.
            expected_exit_code (int, optional): The expected exit code of the command.
                                                Defaults to 0 (success).
        Returns:
            dict: A dictionary for the TestRunner, including the command and a
                  `process_output_func` to assert the command's exit code.
        """
        # Truncate command in test_name if it's too long for readability
        display_command = command[:70] + "..." if len(command) > 70 else command
        test_name = f"Run Command: '{display_command}' (expecting exit {expected_exit_code})"

        def process_output(stdout: str, stderr: str, exit_code: int):
            """Processes output for an arbitrary command, focusing on exit code."""
            self.reporter.start_test_case(test_name)
            details = {
                "full_command": command, # Log the full command in details
                "expected_exit_code": expected_exit_code,
                "actual_exit_code": exit_code,
                "stdout": stdout.strip(),
                "stderr": stderr.strip()
            }
            try:
                assert_equal(exit_code, expected_exit_code,
                             f"Command '{command}' - Expected exit code {expected_exit_code}, got {exit_code}. Stderr: '{stderr.strip()}'")
                self.reporter.add_test_result(test_name, "passed", f"Command executed with expected exit code {exit_code}.", details)
            except AuditAssertionError as e:
                self.reporter.add_test_result(test_name, "failed", str(e), details)
            except Exception as e:
                self.reporter.add_test_result(test_name, "error", f"Unexpected error processing test '{test_name}': {type(e).__name__} - {e}", details)
            finally:
                self.reporter.end_test_case(test_name)

        return {"test_name": test_name, "command": command, "process_output_func": process_output, "type": "bash"}


    def check_curl_download_command(self, url: str, output_file: str, expected_http_code: int = 200, extra_options: str = ""):
        """
        Prepares command for downloading a file using curl and checks the HTTP status code.

        Args:
            url (str): The URL to download from.
            output_file (str): The local file path to save the downloaded content.
            expected_http_code (int, optional): The expected HTTP status code from the download attempt.
                                                Defaults to 200.
            extra_options (str, optional): Additional options to pass to the `curl` command.

        Returns:
            dict: A dictionary for the TestRunner, including the curl command and a
                  `process_output_func` to assert the HTTP status code. File creation
                  is implicitly assumed if the HTTP status code is as expected.
        """
        command = f'curl -L -s -o "{output_file}" -w "%{{http_code}}" {extra_options} "{url}"'.strip()
        test_name = f"Curl Download: {url} to {output_file} (expecting HTTP {expected_http_code})"

        def process_output(stdout: str, stderr: str, exit_code: int):
            """Processes curl download output (HTTP code and errors)."""
            self.reporter.start_test_case(test_name)
            actual_http_code_str = stdout.strip() # Curl -w "%{http_code}" outputs only the code
            details = {
                "url": url,
                "output_file": output_file,
                "expected_http_code": expected_http_code,
                "actual_http_code_output": actual_http_code_str,
                "command": command,
                "curl_process_exit_code": exit_code,
                "curl_stderr": stderr.strip()
            }
            try:
                if exit_code != 0:
                    raise AuditAssertionError(f"Curl process failed with exit code {exit_code}. Stderr: '{stderr.strip()}'. HTTP code output (if any): '{actual_http_code_str}'")

                if not actual_http_code_str.isdigit():
                    raise AuditAssertionError(f"Did not receive a valid HTTP status code from curl -w. Output: '{actual_http_code_str}'")

                actual_http_code = int(actual_http_code_str)
                assert_equal(actual_http_code, expected_http_code,
                             f"URL '{url}': Expected HTTP status {expected_http_code}, got {actual_http_code}.")

                # Note: Actual file existence/content check is not performed here.
                # It's assumed that if curl exits 0 and HTTP code is as expected, the download to output_file was successful.
                self.reporter.add_test_result(test_name, "passed", f"File download from {url} to '{output_file}' resulted in HTTP status {actual_http_code} as expected.", details)

            except AuditAssertionError as e:
                self.reporter.add_test_result(test_name, "failed", str(e), details)
            except Exception as e:
                self.reporter.add_test_result(test_name, "error", f"Unexpected error processing test '{test_name}': {type(e).__name__} - {e}", details)
            finally:
                self.reporter.end_test_case(test_name)

        return {"test_name": test_name, "command": command, "process_output_func": process_output, "type": "bash"}

    def check_curl_header_command(self, url: str, header_name: str, expected_header_value_contains: str = ""):
        """
        Prepares command to fetch HTTP headers with curl and check a specific header.

        Args:
            url (str): The URL from which to fetch headers.
            header_name (str): The name of the HTTP header to inspect (case-insensitive).
            expected_header_value_contains (str, optional): A substring expected to be found
                                                            in the value of the specified header.
                                                            If empty, only presence of the header is checked.
                                                            Defaults to "".
        Returns:
            dict: A dictionary for the TestRunner, including the curl command (`curl -I`)
                  and a `process_output_func` to parse headers and perform assertions.
        """
        command = f'curl -s -I -L "{url}"' # -L to follow redirects for header checks too
        test_name = f"Curl Check Header: {url} - Header '{header_name}'"
        if expected_header_value_contains:
            test_name += f" containing '{expected_header_value_contains}'"

        def process_output(stdout: str, stderr: str, exit_code: int):
            """Processes curl -I output to check for specific headers."""
            self.reporter.start_test_case(test_name)
            headers_raw = stdout.strip()
            details = {
                "url": url,
                "header_name_to_check": header_name,
                "expected_value_substring": expected_header_value_contains,
                "command": command,
                "curl_exit_code": exit_code,
                "curl_stderr": stderr.strip(),
                "headers_received_snippet": headers_raw[:300] # Log a snippet
            }
            try:
                if exit_code != 0:
                    raise AuditAssertionError(f"Curl command to fetch headers failed with exit code {exit_code}. Stderr: '{stderr.strip()}'")

                found_header = False
                header_value_matches_substring = not bool(expected_header_value_contains) # True if no substring check needed

                parsed_headers = {}
                # HTTP headers are case-insensitive. Normalize to lower for reliable check.
                for line in headers_raw.splitlines():
                    if ":" in line: # Basic header line check
                        key, value = line.split(":", 1)
                        parsed_headers[key.strip().lower()] = value.strip()

                lower_header_name_to_check = header_name.lower()
                actual_header_value = "" # Initialize
                if lower_header_name_to_check in parsed_headers:
                    found_header = True
                    actual_header_value = parsed_headers[lower_header_name_to_check]
                    if expected_header_value_contains: # Only check substring if it's provided
                        if expected_header_value_contains in actual_header_value:
                            header_value_matches_substring = True
                        else:
                            header_value_matches_substring = False
                            details["actual_header_value_for_mismatch"] = actual_header_value

                assert_true(found_header, f"Header '{header_name}' not found in response from {url}.")
                # This assertion only triggers if expected_header_value_contains was set
                if expected_header_value_contains:
                    assert_true(header_value_matches_substring, f"Header '{header_name}' found, but its value '{actual_header_value}' does not contain the expected substring '{expected_header_value_contains}'.")

                self.reporter.add_test_result(test_name, "passed", f"Header '{header_name}' check passed for {url}.", details)

            except AuditAssertionError as e:
                self.reporter.add_test_result(test_name, "failed", str(e), details)
            except Exception as e:
                self.reporter.add_test_result(test_name, "error", f"Unexpected error processing test '{test_name}': {type(e).__name__} - {e}", details)
            finally:
                self.reporter.end_test_case(test_name)

        return {"test_name": test_name, "command": command, "process_output_func": process_output, "type": "bash"}

    def check_aria2c_download_command(self, url: str, output_dir: str = ".", output_filename: str = None, extra_options: str = ""):
        """
        Prepares command for downloading a file using aria2c and checks for success.

        Args:
            url (str): The URL to download from.
            output_dir (str, optional): The directory to save the downloaded file. Defaults to ".".
            output_filename (str, optional): The desired filename for the downloaded file.
                                             If None, aria2c uses the filename from the URL.
            extra_options (str, optional): Additional options to pass to the `aria2c` command.

        Returns:
            dict: A dictionary for the TestRunner, including the aria2c command and a
                  `process_output_func` to assert a successful exit code (0).
                  File existence is implicitly assumed on success.
        """
        filename_option = f'-o "{output_filename}"' if output_filename else ""
        # Create directory if it doesn't exist; aria2c -d creates the dir but -- σανιτυ check.
        command = f'mkdir -p "{output_dir}" && aria2c -d "{output_dir}" {filename_option} --console-log-level=error --summary-interval=0 --quiet=true {extra_options} "{url}"'.strip()

        # Determine expected final path for logging/reporting purposes
        if output_filename:
            final_output_path = f"{output_dir}/{output_filename}"
        else:
            # Basic extraction of filename from URL for reporting; might not be perfect for all URLs.
            url_filename = url.split('/')[-1].split('?')[0].split('#')[0]
            final_output_path = f"{output_dir}/{url_filename if url_filename else 'downloaded_file'}"

        test_name = f"Aria2c Download: {url} to '{final_output_path}'"

        def process_output(stdout: str, stderr: str, exit_code: int):
            """Processes aria2c output, primarily checking exit code."""
            self.reporter.start_test_case(test_name)
            details = {
                "url": url,
                "output_dir": output_dir,
                "output_filename_param": output_filename, # What was passed as param
                "final_output_path_expected": final_output_path, # Derived path
                "command": command,
                "aria2c_exit_code": exit_code,
                "stdout": stdout.strip(),
                "stderr": stderr.strip()
            }
            try:
                assert_equal(exit_code, 0, f"aria2c download process for '{url}' failed with exit code {exit_code}. Stderr: '{stderr.strip()}'. Stdout: '{stdout.strip()}'")
                # Note: Actual file existence/content check is not performed by this module.
                self.reporter.add_test_result(test_name, "passed", f"aria2c download for '{url}' completed with exit code 0. Output assumed at '{final_output_path}'.", details)
            except AuditAssertionError as e:
                self.reporter.add_test_result(test_name, "failed", str(e), details)
            except Exception as e:
                self.reporter.add_test_result(test_name, "error", f"Unexpected error processing test '{test_name}': {type(e).__name__} - {e}", details)
            finally:
                self.reporter.end_test_case(test_name)

        return {"test_name": test_name, "command": command, "process_output_func": process_output, "type": "bash"}

    def check_aria2c_metalink_download_command(self, metalink_file_url: str, output_dir: str = "."):
        """
        Prepares command for downloading files specified in a metalink using aria2c.

        Args:
            metalink_file_url (str): The URL of the .metalink file.
            output_dir (str, optional): The directory to save the downloaded files. Defaults to ".".

        Returns:
            dict: A dictionary for the TestRunner, including the aria2c command and a
                  `process_output_func` to assert a successful exit code (0).
                  Existence of downloaded files is implicitly assumed on success.
        """
        command = f'mkdir -p "{output_dir}" && aria2c -d "{output_dir}" --console-log-level=error --summary-interval=0 --quiet=true "{metalink_file_url}"'.strip()
        test_name = f"Aria2c Metalink Download: {metalink_file_url} to '{output_dir}'"

        def process_output(stdout: str, stderr: str, exit_code: int):
            """Processes aria2c metalink download output."""
            self.reporter.start_test_case(test_name)
            details = {
                "metalink_file_url": metalink_file_url,
                "output_dir": output_dir,
                "command": command,
                "aria2c_exit_code": exit_code,
                "stdout": stdout.strip(),
                "stderr": stderr.strip()
            }
            try:
                assert_equal(exit_code, 0, f"aria2c metalink download process for '{metalink_file_url}' failed with exit code {exit_code}. Stderr: '{stderr.strip()}'. Stdout: '{stdout.strip()}'")
                # Note: Verification of specific files from metalink is not done here.
                self.reporter.add_test_result(test_name, "passed", f"aria2c metalink download for '{metalink_file_url}' completed with exit code 0. Files assumed in '{output_dir}'.", details)

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
        def start_test_case(self, name): print(f"\n[Reporter] Start: {name}") # Added newline
        def add_test_result(self, name, status, msg, details): print(f"  [Reporter] Result: {name} [{status.upper()}] - {msg} {json.dumps(details) if details else ''}")
        def end_test_case(self, name): print(f"  [Reporter] End: {name}")

    print("--- CMD_TOOLS_TEST_MODULE IF __NAME__ == __MAIN__ (Conceptual Tests) ---")
    reporter = DummyReporter()
    cmd_module = CmdToolsTestModule(reporter=reporter)
    test_url_google = "https://www.google.com"
    test_dl_url_1mb = "https://proof.ovh.net/files/1Mb.dat"
    test_dl_url_404 = "http://example.com/notfound.dat" # URL likely to be 404
    test_dl_url_host_error = "http://thishostdoesnotexistatallhopefully.com/file.dat"


    # 1. Arbitrary command
    print("\n--- Testing check_run_arbitrary_command_command ---")
    cmd_echo = cmd_module.check_run_arbitrary_command_command("echo 'hello test'", 0)
    print(f"Generated: {cmd_echo['command']}")
    cmd_echo['process_output_func'](stdout="hello test", stderr="", exit_code=0)

    cmd_fail_exit = cmd_module.check_run_arbitrary_command_command("false", 0) # `false` command exits 1
    print(f"Generated: {cmd_fail_exit['command']}")
    cmd_fail_exit['process_output_func'](stdout="", stderr="", exit_code=1)


    # 2. Curl Download
    print("\n--- Testing check_curl_download_command ---")
    curl_dl_ok = cmd_module.check_curl_download_command(test_dl_url_1mb, "test_1Mb.dat", 200)
    print(f"Generated: {curl_dl_ok['command']}")
    curl_dl_ok['process_output_func'](stdout="200", stderr="", exit_code=0)

    curl_dl_expect_404 = cmd_module.check_curl_download_command(test_dl_url_404, "test_notfound.dat", 404)
    print(f"Generated: {curl_dl_expect_404['command']}")
    curl_dl_expect_404['process_output_func'](stdout="404", stderr="", exit_code=0)

    curl_dl_got_500 = cmd_module.check_curl_download_command(test_dl_url_1mb, "test_1Mb_got_500.dat", 200) # Expect 200, get 500
    print(f"Generated: {curl_dl_got_500['command']}")
    curl_dl_got_500['process_output_func'](stdout="500", stderr="", exit_code=0)

    curl_dl_host_err = cmd_module.check_curl_download_command(test_dl_url_host_error, "test_curl_err.dat", 200)
    print(f"Generated: {curl_dl_host_err['command']}")
    curl_dl_host_err['process_output_func'](stdout="000", stderr="Could not resolve host", exit_code=6)


    # 3. Curl Header
    print("\n--- Testing check_curl_header_command ---")
    sample_google_headers = "HTTP/1.1 200 OK\nDate: Mon, 01 Jan 2024 12:00:00 GMT\nContent-Type: text/html; charset=UTF-8\nServer: gws"
    curl_header_ct = cmd_module.check_curl_header_command(test_url_google, "Content-Type", "text/html")
    print(f"Generated: {curl_header_ct['command']}")
    curl_header_ct['process_output_func'](stdout=sample_google_headers, stderr="", exit_code=0)

    curl_header_server_exists = cmd_module.check_curl_header_command(test_url_google, "Server") # Check only for presence
    print(f"Generated: {curl_header_server_exists['command']}")
    curl_header_server_exists['process_output_func'](stdout=sample_google_headers, stderr="", exit_code=0)

    curl_header_ct_wrong_val = cmd_module.check_curl_header_command(test_url_google, "Content-Type", "application/json")
    print(f"Generated: {curl_header_ct_wrong_val['command']}")
    curl_header_ct_wrong_val['process_output_func'](stdout=sample_google_headers, stderr="", exit_code=0)

    curl_header_missing = cmd_module.check_curl_header_command(test_url_google, "X-NonExistent-Header")
    print(f"Generated: {curl_header_missing['command']}")
    curl_header_missing['process_output_func'](stdout=sample_google_headers, stderr="", exit_code=0)

    # 4. Aria2c Download
    print("\n--- Testing check_aria2c_download_command ---")
    aria_dl_ok = cmd_module.check_aria2c_download_command(test_dl_url_1mb, "aria_test_dir_main", "aria_1Mb.dat")
    print(f"Generated: {aria_dl_ok['command']}")
    aria_dl_ok['process_output_func'](stdout="", stderr="", exit_code=0)

    aria_dl_fail = cmd_module.check_aria2c_download_command(test_dl_url_404, "aria_test_dir_main") # URL likely to fail in aria2c
    print(f"Generated: {aria_dl_fail['command']}")
    aria_dl_fail['process_output_func'](stdout="", stderr="Download failed. HTTP response 404", exit_code=3) # aria2c exit 3 for resource error

    # 5. Aria2c Metalink Download
    print("\n--- Testing check_aria2c_metalink_download_command ---")
    metalink_url_example = "https://example.com/some_files.metalink" # Placeholder
    aria_meta_ok = cmd_module.check_aria2c_metalink_download_command(metalink_url_example, "aria_metalink_out_main")
    print(f"Generated: {aria_meta_ok['command']}")
    aria_meta_ok['process_output_func'](stdout="", stderr="", exit_code=0)

    print("\n--- END CMD_TOOLS_TEST_MODULE IF __NAME__ == __MAIN__ (Conceptual Tests) ---")
