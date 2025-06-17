"""
Test module for performing basic IPFS (InterPlanetary File System) checks.

This module, `IPFSTestModule`, provides methods that generate bash commands
(specifically `curl` commands targeting an IPFS node's HTTP API) and
corresponding output processing functions. These are used to verify node
identity, adding and retrieving content (cat), and managing pinned objects.
It's designed for use with the TestRunner.
"""
import json
import re # For CID validation
from dgm.audit_tester.assertions import assert_true, assert_equal, assert_in, AuditAssertionError

class IPFSTestModule:
    """
    Contains test methods for auditing an IPFS node via its HTTP API.

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
        Initializes the IPFSTestModule.

        Args:
            reporter: An instance of the Reporter class for logging test results.
        """
        self.reporter = reporter

    def _is_valid_cid(self, cid_string: str) -> bool:
        """
        Performs a basic validation check for common IPFS CID formats.

        Note:
            This is a simplified check. IPFS CIDs can have various versions,
            bases, and multicodecs. This check covers common v0 (Qm...) and
            some v1 prefixes (bafy..., baga...). A comprehensive check would
            require a dedicated CID library. For PeerIDs (which are CIDs),
            the format is typically a base58btc encoded multihash (like Qm...).

        Args:
            cid_string (str): The string to validate as a CID.

        Returns:
            bool: True if the string matches common CID patterns, False otherwise.
        """
        if not isinstance(cid_string, str):
            return False
        # CID v0: Starts with "Qm", 46 characters long, base58btc encoded.
        if cid_string.startswith("Qm") and len(cid_string) == 46:
            return True
        # CID v1: Common prefixes include bafy (base32), baga (base64url), etc.
        # Length can vary for v1 CIDs.
        if cid_string.startswith("bafy") or \
           cid_string.startswith("baga") or \
           cid_string.startswith("bafk") or \
           cid_string.startswith("zb"): # Example of other base prefixes
            return True
        return False


    def check_node_id_command(self, api_url: str):
        """
        Prepares command for checking IPFS node ID (PeerID).

        Args:
            api_url (str): The base URL of the IPFS node's HTTP API (e.g., "http://localhost:5001").

        Returns:
            dict: A dictionary for the TestRunner, including the curl command to get
                  the node's ID and a `process_output_func` to assert the presence and
                  basic validity of the "ID" field (PeerID) in the JSON response.
                  The PeerID is a CID.
        """
        command = f'curl -s -X POST "{api_url}/api/v0/id"'
        test_name = f"IPFS Node ID: {api_url}"

        def process_output(stdout: str, stderr: str, exit_code: int):
            """Processes IPFS id output."""
            self.reporter.start_test_case(test_name)
            response_body = stdout.strip()
            details = {
                "api_url": api_url,
                "command": command,
                "curl_exit_code": exit_code,
                "curl_stderr": stderr.strip(),
                "stdout_response": response_body
            }
            try:
                if exit_code != 0:
                    raise AuditAssertionError(f"Curl command failed with exit code {exit_code}. Stderr: '{stderr.strip()}'")

                try:
                    response_json = json.loads(response_body)
                except json.JSONDecodeError as je:
                    raise AuditAssertionError(f"Failed to decode JSON response from IPFS id API: {je}. Response: '{response_body[:1000]}'")

                assert_in("ID", response_json, "Key 'ID' (PeerID) not found in IPFS id response.")
                node_id = response_json["ID"]
                assert_true(isinstance(node_id, str), f"Node ID (PeerID) '{node_id}' is not a string.")
                assert_true(len(node_id) > 10, f"Node ID (PeerID) '{node_id}' is unexpectedly short.")
                # PeerIDs are CIDs, typically base58btc (Qm...).
                assert_true(self._is_valid_cid(node_id), f"Node ID '{node_id}' does not look like a valid Peer ID (CID). Review _is_valid_cid for PeerID specifics if this fails for valid PeerIDs.")

                self.reporter.add_test_result(test_name, "passed", f"Successfully retrieved IPFS Node ID: {node_id}", details)

            except AuditAssertionError as e:
                self.reporter.add_test_result(test_name, "failed", str(e), details)
            except Exception as e:
                self.reporter.add_test_result(test_name, "error", f"Unexpected error processing test '{test_name}': {type(e).__name__} - {e}", details)
            finally:
                self.reporter.end_test_case(test_name)

        return {"test_name": test_name, "command": command, "process_output_func": process_output, "type": "bash"}

    def check_add_string_command(self, api_url: str, test_string: str):
        """
        Prepares command for adding a string to IPFS and getting its CID.

        Args:
            api_url (str): The base URL of the IPFS node's HTTP API.
            test_string (str): The string content to add to IPFS.

        Returns:
            dict: A dictionary for the TestRunner, including the command to add the string
                  and a `process_output_func` to assert that the JSON response contains
                  a "Hash" key with a valid CID.
        """
        # Using `echo -n` might be better to avoid trailing newline if not desired,
        # but for basic string tests, `echo` is fine. The hash will differ.
        # For safety with special characters in test_string, a more robust method
        # might involve writing string to a temp file and using -F file=@temp_file_path,
        # but `echo ... | curl -F file=@-` is common for simplicity.
        command = f'echo "{test_string}" | curl -s -X POST -F file=@- "{api_url}/api/v0/add"'
        test_name = f"IPFS Add String: '{test_string[:30]}...'" # Truncate for display name

        def process_output(stdout: str, stderr: str, exit_code: int):
            """Processes IPFS add output."""
            self.reporter.start_test_case(test_name)
            response_body = stdout.strip()
            details = {
                "api_url": api_url,
                "test_string_snippet": test_string[:50], # Log a snippet
                "command": command,
                "curl_exit_code": exit_code,
                "curl_stderr": stderr.strip(),
                "stdout_response": response_body
            }
            try:
                if exit_code != 0:
                    raise AuditAssertionError(f"Curl command for 'add' failed with exit code {exit_code}. Stderr: '{stderr.strip()}'")

                try:
                    response_json = json.loads(response_body)
                except json.JSONDecodeError as je:
                    raise AuditAssertionError(f"Failed to decode JSON response from IPFS add API: {je}. Response: '{response_body[:1000]}'")

                assert_in("Hash", response_json, "Key 'Hash' (CID) not found in IPFS add response.")
                added_cid = response_json["Hash"]
                assert_true(self._is_valid_cid(added_cid), f"Added Hash (CID) '{added_cid}' from IPFS add response is not a valid CID format.")

                self.reporter.add_test_result(test_name, "passed", f"String added to IPFS successfully. CID: {added_cid}", {**details, "cid_added": added_cid})

            except AuditAssertionError as e:
                self.reporter.add_test_result(test_name, "failed", str(e), details)
            except Exception as e:
                self.reporter.add_test_result(test_name, "error", f"Unexpected error processing test '{test_name}': {type(e).__name__} - {e}", details)
            finally:
                self.reporter.end_test_case(test_name)

        return {"test_name": test_name, "command": command, "process_output_func": process_output, "type": "bash"}

    def check_cat_content_command(self, api_url: str, cid: str, expected_content: str):
        """
        Prepares command for retrieving content (cat) from IPFS by its CID.

        Args:
            api_url (str): The base URL of the IPFS node's HTTP API.
            cid (str): The CID of the content to retrieve.
            expected_content (str): The expected string content of the IPFS object.

        Returns:
            dict: A dictionary for the TestRunner, including the command to cat the content
                  and a `process_output_func` to assert that the retrieved content
                  matches `expected_content`.
        """
        command = f'curl -s -X POST "{api_url}/api/v0/cat?arg={cid}"'
        test_name = f"IPFS Cat Content: {cid}"

        def process_output(stdout: str, stderr: str, exit_code: int):
            """Processes IPFS cat output."""
            # stdout from IPFS cat is the raw content, not JSON.
            self.reporter.start_test_case(test_name)
            actual_content = stdout # stdout is the direct content for `cat`
            details = {
                "api_url": api_url,
                "cid": cid,
                "command": command,
                "curl_exit_code": exit_code,
                "curl_stderr": stderr.strip(),
                "expected_content_snippet": expected_content[:50] + ("..." if len(expected_content) > 50 else ""),
                "actual_content_snippet": actual_content[:50] + ("..." if len(actual_content) > 50 else "")
            }
            try:
                if exit_code != 0:
                    # IPFS cat can fail if CID not found, or if daemon isn't running/reachable.
                    # Stderr often contains the error message from IPFS daemon.
                    raise AuditAssertionError(f"Curl command for 'cat' failed with exit code {exit_code}. Stderr: '{stderr.strip()}'. Stdout: '{actual_content[:200]}'")

                assert_equal(actual_content, expected_content, f"Content for CID '{cid}' did not match expected content.")
                self.reporter.add_test_result(test_name, "passed", f"Content for CID '{cid}' matches expected content.", details)

            except AuditAssertionError as e:
                self.reporter.add_test_result(test_name, "failed", str(e), details)
            except Exception as e:
                self.reporter.add_test_result(test_name, "error", f"Unexpected error processing test '{test_name}': {type(e).__name__} - {e}", details)
            finally:
                self.reporter.end_test_case(test_name)

        return {"test_name": test_name, "command": command, "process_output_func": process_output, "type": "bash"}

    def check_pin_object_command(self, api_url: str, cid_to_pin: str):
        """
        Prepares command for pinning an object in IPFS.

        Args:
            api_url (str): The base URL of the IPFS node's HTTP API.
            cid_to_pin (str): The CID of the object to pin.

        Returns:
            dict: A dictionary for the TestRunner, including the command to pin the object
                  and a `process_output_func` to assert that the pin operation was successful
                  (i.e., the CID appears in the "Pins" array of the JSON response).
        """
        command = f'curl -s -X POST "{api_url}/api/v0/pin/add?arg={cid_to_pin}"'
        test_name = f"IPFS Pin Object: {cid_to_pin}"

        def process_output(stdout: str, stderr: str, exit_code: int):
            """Processes IPFS pin/add output."""
            self.reporter.start_test_case(test_name)
            response_body = stdout.strip()
            details = {
                "api_url": api_url,
                "cid_to_pin": cid_to_pin,
                "command": command,
                "curl_exit_code": exit_code,
                "curl_stderr": stderr.strip(),
                "stdout_response": response_body
            }
            try:
                if exit_code != 0:
                    raise AuditAssertionError(f"Curl command for 'pin/add' failed with exit code {exit_code}. Stderr: '{stderr.strip()}'")

                try:
                    response_json = json.loads(response_body)
                except json.JSONDecodeError as je:
                    # Check if this is an IPFS API error structure for invalid CID
                    if "invalid path" in response_body.lower() and "invalid cid" in response_body.lower():
                         raise AuditAssertionError(f"IPFS API reported error for pin/add (likely invalid CID '{cid_to_pin}'): {response_body}")
                    raise AuditAssertionError(f"Failed to decode JSON response from IPFS pin/add: {je}. Response: '{response_body[:1000]}'")

                assert_in("Pins", response_json, "Key 'Pins' not found in IPFS pin/add response.")
                assert_true(isinstance(response_json["Pins"], list), f"'Pins' field in pin/add response is not a list. Got: {response_json['Pins']}")
                assert_in(cid_to_pin, response_json["Pins"], f"Target CID '{cid_to_pin}' not found in 'Pins' array of pin/add response. Response: {response_body}")

                self.reporter.add_test_result(test_name, "passed", f"Object '{cid_to_pin}' reported as pinned successfully.", details)

            except AuditAssertionError as e:
                self.reporter.add_test_result(test_name, "failed", str(e), details)
            except Exception as e:
                self.reporter.add_test_result(test_name, "error", f"Unexpected error processing test '{test_name}': {type(e).__name__} - {e}", details)
            finally:
                self.reporter.end_test_case(test_name)

        return {"test_name": test_name, "command": command, "process_output_func": process_output, "type": "bash"}

    def check_verify_pin_command(self, api_url: str, pinned_cid: str):
        """
        Prepares command for verifying if a CID is pinned locally on an IPFS node.

        Uses ` /api/v0/pin/ls?arg=<cid>` which returns information if the CID is pinned,
        or an error if it's not (or if the CID is invalid).

        Args:
            api_url (str): The base URL of the IPFS node's HTTP API.
            pinned_cid (str): The CID to verify.

        Returns:
            dict: A dictionary for the TestRunner, including the command and a
                  `process_output_func` to assert that the CID is listed as pinned
                  in the JSON response and check its pin type.
        """
        command = f'curl -s -X POST "{api_url}/api/v0/pin/ls?arg={pinned_cid}"'
        test_name = f"IPFS Verify Pin: {pinned_cid}"

        def process_output(stdout: str, stderr: str, exit_code: int):
            """Processes IPFS pin/ls output."""
            self.reporter.start_test_case(test_name)
            response_body = stdout.strip()
            details = {
                "api_url": api_url,
                "pinned_cid": pinned_cid,
                "command": command,
                "curl_exit_code": exit_code,
                "curl_stderr": stderr.strip(),
                "stdout_response": response_body
            }
            try:
                # If CID is not pinned, IPFS `pin/ls <cid>` often returns non-zero exit from curl
                # and a JSON error like: {"Message":"path '<cid>' is not pinned","Code":0,"Type":"error"}
                # If CID is pinned, it returns JSON with "Keys".
                if exit_code != 0:
                    # Attempt to parse stdout as JSON even if exit code is non-zero, as IPFS might send error JSON
                    try:
                        error_json = json.loads(response_body)
                        if "Message" in error_json and "is not pinned" in error_json["Message"]:
                             raise AuditAssertionError(f"CID '{pinned_cid}' is reported as not pinned by IPFS API. Response: {response_body}")
                    except json.JSONDecodeError:
                        pass # Not a JSON error, just fall through to generic curl error
                    raise AuditAssertionError(f"Curl command for 'pin/ls' failed (exit code {exit_code}). Stderr: '{stderr.strip()}'. Response: '{response_body}'")

                try:
                    response_json = json.loads(response_body)
                except json.JSONDecodeError as je:
                    raise AuditAssertionError(f"Failed to decode JSON response from IPFS pin/ls: {je}. Response: '{response_body[:1000]}'")

                assert_in("Keys", response_json, "Key 'Keys' not found in IPFS pin/ls response.")
                assert_true(isinstance(response_json["Keys"], dict), f"'Keys' field in pin/ls response is not a dictionary. Got: {response_json['Keys']}")
                assert_in(pinned_cid, response_json["Keys"], f"CID '{pinned_cid}' not found as a key in 'Keys' map of pin/ls response. This means it's not reported as pinned.")

                pin_info = response_json["Keys"][pinned_cid]
                assert_in("Type", pin_info, f"Pin info for CID '{pinned_cid}' does not contain 'Type' field.")
                pin_type = pin_info["Type"]
                # Common pin types: "recursive", "direct", "indirect", "all" (less common from this specific call)
                valid_pin_types = ["recursive", "direct", "indirect", "all"]
                assert_true(pin_type.lower() in valid_pin_types, f"Pin type '{pin_type}' for CID '{pinned_cid}' is not one of the recognized types {valid_pin_types}.")

                self.reporter.add_test_result(test_name, "passed", f"CID '{pinned_cid}' is pinned with type '{pin_type}'.", {**details, "pin_type": pin_type})

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
        def start_test_case(self, name): print(f"\n[Reporter] Start: {name}") # Added newline for readability
        def add_test_result(self, name, status, msg, details): print(f"  [Reporter] Result: {name} [{status.upper()}] - {msg} {json.dumps(details) if details else ''}")
        def end_test_case(self, name): print(f"  [Reporter] End: {name}")

    print("--- IPFS_TEST_MODULE IF __NAME__ == __MAIN__ (Conceptual Tests) ---")
    reporter = DummyReporter()
    ipfs_module = IPFSTestModule(reporter=reporter)
    api_url = "http://localhost:5001"
    test_string_content = "Hello IPFS from Audit Tester module's main example"

    # Example CIDs (replace with actual CIDs for more realistic local testing if you have an IPFS node)
    # This is a well-known CID for the text "Hello World\n"
    known_cid_hw = "QmZ4d7p2U9sSYK7VVaYQifdx3Y3zG21x73X8jTCy2F1YPc"
    known_cid_hw_content = "Hello World\n"
    # A simulated CID that might be returned by the 'add' command in a real scenario
    simulated_added_cid = "QmSimulatedHashForHelloIPFSMainTest1234567890ab" # 46 chars

    # 1. Check Node ID
    node_id_check = ipfs_module.check_node_id_command(api_url)
    print(f"Generated command for node ID: {node_id_check['command']}")
    print("Simulating Node ID (success with valid PeerID format):")
    # Using a Qm... style ID of correct length for simulation
    node_id_check['process_output_func'](stdout='{"ID": "QmValidPeerIdFormatExample1234567890abcdefghijkl", "PublicKey": "...", "Addresses":[]}', stderr="", exit_code=0)

    # 2. Add String
    add_string_check = ipfs_module.check_add_string_command(api_url, test_string_content)
    print(f"Generated command for add string: {add_string_check['command']}")
    print("Simulating Add String (success with valid CID format):")
    add_string_check['process_output_func'](stdout=f'{{"Name":"file.txt", "Hash":"{simulated_added_cid}", "Size":"{len(test_string_content)}"}}', stderr="", exit_code=0)

    # 3. Cat Content (using known CID)
    cat_content_check = ipfs_module.check_cat_content_command(api_url, known_cid_hw, known_cid_hw_content)
    print(f"Generated command for cat content: {cat_content_check['command']}")
    print("Simulating Cat Content (success):")
    cat_content_check['process_output_func'](stdout=known_cid_hw_content, stderr="", exit_code=0)
    print("Simulating Cat Content (failure - wrong content):")
    cat_content_check['process_output_func'](stdout="This is not the droids you are looking for.", stderr="", exit_code=0)
    print("Simulating Cat Content (failure - IPFS error, e.g. CID not found):")
    cat_content_check['process_output_func'](stdout="", stderr="Error: merkledag: not found", exit_code=1)


    # 4. Pin Object (using known CID)
    pin_object_check = ipfs_module.check_pin_object_command(api_url, known_cid_hw)
    print(f"Generated command for pin object: {pin_object_check['command']}")
    print("Simulating Pin Object (success):")
    pin_object_check['process_output_func'](stdout=f'{{"Pins":["{known_cid_hw}"]}}', stderr="", exit_code=0)
    print("Simulating Pin Object (failure - invalid CID format from API):")
    pin_object_invalid_cid = ipfs_module.check_pin_object_command(api_url, "InvalidCID")
    pin_object_invalid_cid['process_output_func'](stdout='{"Type":"error", "Message":"invalid path \\"InvalidCID\\": invalid cid: selected encoding not supported"}', stderr="Error: invalid path...", exit_code=0) # IPFS API might return 200 OK with error JSON

    # 5. Verify Pin (using known CID)
    verify_pin_check = ipfs_module.check_verify_pin_command(api_url, known_cid_hw)
    print(f"Generated command for verify pin: {verify_pin_check['command']}")
    print("Simulating Verify Pin (success - CID is pinned):")
    verify_pin_check['process_output_func'](stdout=f'{{"Keys":{{"{known_cid_hw}":{{"Type":"recursive"}}}}}}', stderr="", exit_code=0)

    print("Simulating Verify Pin (failure - CID not pinned, specific IPFS error JSON):")
    # IPFS `pin/ls <cid_not_pinned>` returns 0 but with an error-like JSON body.
    # The module's process_output_func should ideally catch this based on content if exit_code is 0.
    # However, the current simulation in main.py for "not pinned" returns exit_code 1 to make it clearer.
    verify_pin_check['process_output_func'](stdout=f'{{"Message":"path \'{known_cid_hw}\' is not pinned","Code":0,"Type":"error"}}', stderr="", exit_code=0) # Simulate API returning 0 but error content


    print("\nNote: These local simulations don't use `run_in_bash_session` or the TestRunner.")
    print("--- END IPFS_TEST_MODULE IF __NAME__ == __MAIN__ ---")
