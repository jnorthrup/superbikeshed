"""
Test module for performing basic CouchDB server and database checks using curl.

This module, `CouchDBTestModule`, provides methods that generate bash commands
(specifically `curl` commands targeting a CouchDB HTTP API) and corresponding
output processing functions. These are used to verify server status,
database existence, creation, and deletion. It's designed for use with the
TestRunner, which executes these commands and processes their results.
"""
import json
from dgm.audit_tester.assertions import assert_true, assert_equal, assert_in, AuditAssertionError

class CouchDBTestModule:
    """
    Contains test methods for auditing a CouchDB instance via its HTTP API.

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
        Initializes the CouchDBTestModule.

        Args:
            reporter: An instance of the Reporter class for logging test results.
        """
        self.reporter = reporter

    def check_server_running_command(self, base_url: str):
        """
        Prepares command and processing logic for checking if a CouchDB server is running.

        It targets the server's root URL (`/`) which typically returns a welcome JSON.

        Args:
            base_url (str): The base URL of the CouchDB server (e.g., "http://localhost:5984").

        Returns:
            dict: A dictionary for the TestRunner, including the curl command to get
                  server info and a `process_output_func` to assert the "couchdb: Welcome"
                  message in the JSON response.
        """
        command = f'curl -s "{base_url}/"'
        test_name = f"CouchDB Server Running: {base_url}"

        def process_output(stdout: str, stderr: str, exit_code: int):
            """Processes curl output to verify CouchDB welcome message."""
            self.reporter.start_test_case(test_name)
            details = {
                "base_url": base_url,
                "command": command,
                "curl_exit_code": exit_code,
                "curl_stderr": stderr.strip(),
                "stdout_response": stdout.strip() # Log the raw JSON response
            }
            try:
                if exit_code != 0:
                    raise AuditAssertionError(f"Curl command failed with exit code {exit_code}. Stderr: '{stderr.strip()}'")

                try:
                    response_json = json.loads(stdout)
                except json.JSONDecodeError as je:
                    raise AuditAssertionError(f"Failed to decode JSON response from CouchDB server: {je}. Response (first 1000 chars): '{stdout[:1000]}'")

                assert_in("couchdb", response_json, "Key 'couchdb' not found in CouchDB root response.")
                assert_equal(response_json["couchdb"], "Welcome", "CouchDB 'Welcome' message not found or incorrect in root response.")
                # Optionally, could also check for 'version' key: assert_in("version", response_json)
                self.reporter.add_test_result(test_name, "passed", "CouchDB server is running and responded with the expected welcome message.", details)

            except AuditAssertionError as e:
                self.reporter.add_test_result(test_name, "failed", str(e), details)
            except Exception as e:
                self.reporter.add_test_result(test_name, "error", f"Unexpected error processing test '{test_name}': {type(e).__name__} - {e}", details)
            finally:
                self.reporter.end_test_case(test_name)

        return {"test_name": test_name, "command": command, "process_output_func": process_output, "type": "bash"}

    def check_database_exists_command(self, base_url: str, db_name: str, expected_status_code: int = 200):
        """
        Prepares command and processing logic for checking if a CouchDB database exists.

        This method can check for expected existence (default HTTP 200) or
        non-existence (by specifying `expected_status_code=404`).

        Args:
            base_url (str): The base URL of the CouchDB server.
            db_name (str): The name of the database to check.
            expected_status_code (int, optional): The HTTP status code expected from the server.
                                                 Defaults to 200 (database exists). Use 404 to
                                                 assert that a database does *not* exist.
        Returns:
            dict: A dictionary for the TestRunner, including the curl command to check
                  database status and a `process_output_func` to assert the HTTP status code.
        """
        command = f'curl -s -w "%{{http_code}}" -o /dev/null "{base_url}/{db_name}"'
        test_name = f"CouchDB Database Status: {base_url}/{db_name} (expecting HTTP {expected_status_code})"

        def process_output(stdout: str, stderr: str, exit_code: int):
            """Processes curl output to check database existence via HTTP status code."""
            self.reporter.start_test_case(test_name)
            actual_status_code_str = stdout.strip() # Curl -w "%{http_code}" outputs only the code to stdout
            details = {
                "base_url": base_url,
                "db_name": db_name,
                "command": command,
                "expected_http_status_code": expected_status_code,
                "actual_http_status_code_output": actual_status_code_str,
                "curl_process_exit_code": exit_code,
                "curl_stderr": stderr.strip()
            }
            try:
                # Curl itself should exit 0 if the request was made, even for a 404.
                # A non-zero exit_code here implies a curl operational error (e.g., host not found).
                if exit_code != 0:
                     raise AuditAssertionError(f"Curl command failed operationally (exit code {exit_code}, stderr: '{stderr.strip()}'). No HTTP status code obtained reliably.")

                if not actual_status_code_str.isdigit():
                    raise AuditAssertionError(f"Did not receive a valid HTTP status code from curl -w. Output: '{actual_status_code_str}'")

                actual_status_code = int(actual_status_code_str)
                assert_equal(actual_status_code, expected_status_code,
                             f"Database '{db_name}' at {base_url}: Expected HTTP status {expected_status_code}, but received {actual_status_code}.")

                msg_status = "exists" if expected_status_code == 200 else f"does not exist or is inaccessible (status {expected_status_code})"
                self.reporter.add_test_result(test_name, "passed", f"Database '{db_name}' {msg_status} as expected (HTTP status {actual_status_code}).", details)

            except AuditAssertionError as e:
                self.reporter.add_test_result(test_name, "failed", str(e), details)
            except Exception as e:
                self.reporter.add_test_result(test_name, "error", f"Unexpected error processing test '{test_name}': {type(e).__name__} - {e}", details)
            finally:
                self.reporter.end_test_case(test_name)

        return {"test_name": test_name, "command": command, "process_output_func": process_output, "type": "bash"}

    def check_create_database_command(self, base_url: str, db_name: str):
        """
        Prepares command and processing logic for creating a CouchDB database.

        Args:
            base_url (str): The base URL of the CouchDB server.
            db_name (str): The name for the new database.

        Returns:
            dict: A dictionary for the TestRunner, including the curl command to
                  create the database (HTTP PUT) and a `process_output_func` to
                  assert success (expects `{"ok":true}` in JSON response, or handles errors).
        """
        command = f'curl -s -X PUT "{base_url}/{db_name}"'
        test_name = f"CouchDB Create Database: {base_url}/{db_name}"

        def process_output(stdout: str, stderr: str, exit_code: int):
            """Processes curl output for database creation."""
            self.reporter.start_test_case(test_name)
            response_body_str = stdout.strip()
            details = {
                "base_url": base_url,
                "db_name": db_name,
                "command": command,
                "response_body": response_body_str,
                "curl_exit_code": exit_code,
                "curl_stderr": stderr.strip()
            }
            try:
                # CouchDB usually returns HTTP 201 (Created) or 202 (Accepted) on success,
                # and 412 (Precondition Failed) if DB already exists. Curl exit code should be 0.
                if exit_code != 0:
                    raise AuditAssertionError(f"Curl command potentially failed (exit code {exit_code}, stderr: '{stderr.strip()}'). Response: '{response_body_str}'")

                try:
                    response_json = json.loads(response_body_str)
                except json.JSONDecodeError as je:
                    raise AuditAssertionError(f"Failed to decode JSON response for DB creation: {je}. Response: '{response_body_str[:1000]}'")

                # Check for specific CouchDB error responses before generic 'ok' check
                if "error" in response_json:
                    if response_json.get("error") == "file_exists":
                        # This is a specific CouchDB response when DB already exists.
                        # Depending on test intent, this might be a "pass" or "fail".
                        # For a "create" command, usually it means it didn't freshly create.
                        raise AuditAssertionError(f"Database '{db_name}' already exists. Server response: {response_body_str}")
                    elif response_json.get("error") == "illegal_database_name":
                        raise AuditAssertionError(f"Invalid database name '{db_name}'. Server reason: '{response_json.get('reason', 'unknown')}'")
                    else: # Other CouchDB errors
                        raise AuditAssertionError(f"CouchDB reported an error creating database '{db_name}': {response_body_str}")

                assert_in("ok", response_json, "Key 'ok' not found in create database JSON response.")
                assert_true(response_json["ok"], f"Create database operation for '{db_name}' was not 'ok'. Response: {response_body_str}")

                self.reporter.add_test_result(test_name, "passed", f"Database '{db_name}' creation reported 'ok:true'.", details)

            except AuditAssertionError as e:
                self.reporter.add_test_result(test_name, "failed", str(e), details)
            except Exception as e:
                self.reporter.add_test_result(test_name, "error", f"Unexpected error processing test '{test_name}': {type(e).__name__} - {e}", details)
            finally:
                self.reporter.end_test_case(test_name)

        return {"test_name": test_name, "command": command, "process_output_func": process_output, "type": "bash"}

    def check_delete_database_command(self, base_url: str, db_name: str):
        """
        Prepares command and processing logic for deleting a CouchDB database.

        Args:
            base_url (str): The base URL of the CouchDB server.
            db_name (str): The name of the database to delete.

        Returns:
            dict: A dictionary for the TestRunner, including the curl command to
                  delete the database (HTTP DELETE) and a `process_output_func` to
                  assert success (expects `{"ok":true}` or handles errors like "not_found").
        """
        command = f'curl -s -X DELETE "{base_url}/{db_name}"'
        test_name = f"CouchDB Delete Database: {base_url}/{db_name}"

        def process_output(stdout: str, stderr: str, exit_code: int):
            """Processes curl output for database deletion."""
            self.reporter.start_test_case(test_name)
            response_body_str = stdout.strip()
            details = {
                "base_url": base_url,
                "db_name": db_name,
                "command": command,
                "response_body": response_body_str,
                "curl_exit_code": exit_code,
                "curl_stderr": stderr.strip()
            }
            try:
                # CouchDB returns HTTP 200 on successful deletion, or 404 if not found.
                # Curl exit code should be 0 if server responds.
                if exit_code != 0:
                    raise AuditAssertionError(f"Curl command potentially failed (exit code {exit_code}, stderr: '{stderr.strip()}'). Response: '{response_body_str}'")

                try:
                    response_json = json.loads(response_body_str)
                except json.JSONDecodeError as je:
                    raise AuditAssertionError(f"Failed to decode JSON response for DB deletion: {je}. Response: '{response_body_str[:1000]}'")

                # Check for specific CouchDB error responses
                if "error" in response_json:
                    if response_json.get("error") == "not_found":
                        # This means the DB didn't exist to be deleted.
                        # For a "delete" command, this is usually a failure condition unless specifically expected.
                        raise AuditAssertionError(f"Database '{db_name}' does not exist, cannot delete. Server response: {response_body_str}")
                    else: # Other CouchDB errors
                        raise AuditAssertionError(f"CouchDB reported an error deleting database '{db_name}': {response_body_str}")

                assert_in("ok", response_json, "Key 'ok' not found in delete database JSON response.")
                assert_true(response_json["ok"], f"Delete database operation for '{db_name}' was not 'ok'. Response: {response_body_str}")

                self.reporter.add_test_result(test_name, "passed", f"Database '{db_name}' deletion reported 'ok:true'.", details)

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

    print("--- COUCHDB_TEST_MODULE IF __NAME__ == __MAIN__ (Conceptual Tests) ---")
    reporter = DummyReporter()
    couch_module = CouchDBTestModule(reporter=reporter)
    base_url = "http://localhost:5984"
    test_db = "ci_test_db_module_main" # Use a different name for main block tests

    # 1. Check server running
    server_check = couch_module.check_server_running_command(base_url)
    print(f"\nGenerated command for server check: {server_check['command']}")
    print("Simulating server check (success):")
    server_check['process_output_func'](stdout='{"couchdb":"Welcome","version":"3.x.x"}', stderr="", exit_code=0)
    print("Simulating server check (failure - bad JSON):")
    server_check['process_output_func'](stdout='Not a JSON', stderr="", exit_code=0)
    print("Simulating server check (failure - curl error):")
    server_check['process_output_func'](stdout='', stderr="Failed to connect", exit_code=7)

    # 2. Check database exists (e.g., a system database like _users)
    db_exists_check = couch_module.check_database_exists_command(base_url, "_users", expected_status_code=200)
    print(f"\nGenerated command for DB exists check: {db_exists_check['command']}")
    print("Simulating DB exists check (_users, success):")
    db_exists_check['process_output_func'](stdout="200", stderr="", exit_code=0)

    # 3. Check database NOT exists (for a non-existent DB)
    db_not_exists_check = couch_module.check_database_exists_command(base_url, "non_existent_db_for_sure", expected_status_code=404)
    print(f"\nGenerated command for DB NOT exists check: {db_not_exists_check['command']}")
    print("Simulating DB NOT exists check (non_existent_db_for_sure, success - 404 expected):")
    db_not_exists_check['process_output_func'](stdout="404", stderr="", exit_code=0)
    print("Simulating DB NOT exists check (non_existent_db_for_sure, failure - 200 received when 404 expected):")
    db_not_exists_check['process_output_func'](stdout="200", stderr="", exit_code=0)

    # 4. Create database
    db_create_check = couch_module.check_create_database_command(base_url, test_db)
    print(f"\nGenerated command for DB create '{test_db}': {db_create_check['command']}")
    print("Simulating DB create (success):")
    db_create_check['process_output_func'](stdout='{"ok":true}', stderr="", exit_code=0)
    print("Simulating DB create (failure - already exists as per CouchDB error):")
    db_create_check['process_output_func'](stdout='{"error":"file_exists","reason":"The database could not be created, the file already exists."}', stderr="", exit_code=0)
    print("Simulating DB create (failure - invalid name as per CouchDB error):")
    db_create_invalid_name = couch_module.check_create_database_command(base_url, "_invalid_db")
    db_create_invalid_name['process_output_func'](stdout='{"error":"illegal_database_name","reason":"Name must begin with a lowercase letter."}', stderr="", exit_code=0)


    # 5. Delete database
    db_delete_check = couch_module.check_delete_database_command(base_url, test_db)
    print(f"\nGenerated command for DB delete '{test_db}': {db_delete_check['command']}")
    print("Simulating DB delete (success):")
    db_delete_check['process_output_func'](stdout='{"ok":true}', stderr="", exit_code=0)
    print("Simulating DB delete (failure - not found as per CouchDB error):")
    db_delete_check['process_output_func'](stdout='{"error":"not_found","reason":"Database does not exist."}', stderr="", exit_code=0)

    print("\nNote: These local simulations don't use `run_in_bash_session` or TestRunner.")
    print("--- END COUCHDB_TEST_MODULE IF __NAME__ == __MAIN__ ---")
