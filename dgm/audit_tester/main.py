"""
Main entry point for the Audit Tester framework.

This script initializes and runs the test execution process. It handles:
1.  Loading test configurations from `config.yaml`.
2.  Initializing the `Reporter` for capturing test results.
3.  Initializing the `TestRunner` with necessary dependencies, including a
    bash command execution function.
4.  Orchestrating the test run based on the loaded configuration.
5.  Generating final reports (console and JSON).

A crucial part of this script is the `run_bash_command_via_tool` function,
which currently **simulates** the execution of external bash commands. This
is a placeholder for agent-based execution where an agent would intercept
these command requests, run them using available tools (like `run_in_bash_session`),
and feed the results back. This simulation allows the framework's logic to be
developed and tested independently of live services or direct agent intervention
for each command during development cycles.
"""
import json
import os
from dgm.audit_tester.runner import TestRunner
from dgm.audit_tester.reporter import Reporter
from dgm.audit_tester.config_loader import load_config

# --- AGENT INTERACTION REQUIRED FOR BASH COMMANDS ---
# Global dictionary to store predefined or stateful simulation results.
# This can be manipulated by different simulation branches if needed (e.g., CouchDB create/delete).
SIMULATED_BASH_RESULTS = {}

def run_bash_command_via_tool(command_to_run: str) -> dict:
    """
    Simulates the execution of a bash command and returns its output.

    This function is passed to the `TestRunner` to handle bash command execution
    requests from test modules. In a live environment with an agent, this function
    would trigger the agent to use a tool like `run_in_bash_session`. The agent
    would then need to parse the tool's output and return it in the expected
    dictionary format: `{'stdout': str, 'stderr': str, 'exit_code': int}`.

    Currently, this function contains hardcoded simulation logic for various
    commands used by the test modules (HTTP, CouchDB, IPFS, CmdTools, K2Script).
    It prints messages indicating that agent action would be required for real execution.

    Args:
        command_to_run (str): The bash command string to be "executed".

    Returns:
        dict: A dictionary containing the simulated 'stdout', 'stderr', and 'exit_code'.
    """
    global SIMULATED_BASH_RESULTS # Allow modification of global for stateful simulations

    print(f"AGENT_ACTION_REQUIRED: Execute the following bash command using 'run_in_bash_session':")
    print(f"COMMAND: {command_to_run}")

    # Initialize result to a default error state to ensure it's always defined.
    result = {"stdout": "", "stderr": "Command not found in simulation map", "exit_code": 127}

    # Allow specific command overrides for dynamic test scenarios
    if command_to_run in SIMULATED_BASH_RESULTS:
        result = SIMULATED_BASH_RESULTS[command_to_run]
    # --- HTTPTestModule specific simulations ---
    elif "thisshouldnotexistforsure whatsoever.com" in command_to_run:
        result = {"stdout": "000", "stderr": "curl: (6) Could not resolve host", "exit_code": 6}
    elif "https://www.google.com" in command_to_run and "http_code" in command_to_run:
        result = {"stdout": "200", "stderr": "", "exit_code": 0}
    elif "http://example.com/nonexistentpage" in command_to_run and "http_code" in command_to_run:
        result = {"stdout": "404", "stderr": "", "exit_code": 0}
    elif "https://www.google.com" in command_to_run and "HTTP/" in command_to_run:
        result = {"stdout": "HTTP/2 200 \r\ncontent-type: text/html", "stderr": "", "exit_code": 0}
    elif "http://example.com" in command_to_run and "HTTP/" in command_to_run:
        result = {"stdout": "HTTP/1.1 200 OK\r\nServer: Mock", "stderr": "", "exit_code": 0}
    elif "invalid.host.name.that.does.not.exist" in command_to_run:
        result = {"stdout": "", "stderr": "curl: (6) Could not resolve host: invalid.host.name.that.does.not.exist", "exit_code": 6}

    # --- CouchDB Simulations ---
    elif "localhost:5984" in command_to_run:
        if command_to_run == 'curl -s "http://localhost:5984/"':
            result = {"stdout": '{"couchdb":"Welcome","version":"3.3.2"}', "stderr": "", "exit_code": 0}
        elif command_to_run == 'curl -s -w "%{http_code}" -o /dev/null "http://localhost:5984/_users"':
            result = {"stdout": "200", "stderr": "", "exit_code": 0}
        elif command_to_run == 'curl -s -X PUT "http://localhost:5984/_audit_test_db_invalid"':
            result = {"stdout": '{"error":"illegal_database_name","reason":"Name \'_audit_test_db_invalid\' is not valid."}', "stderr": "", "exit_code": 0}
        elif command_to_run == 'curl -s -X PUT "http://localhost:5984/audit_test_db"':
            if "audit_test_db_created" not in SIMULATED_BASH_RESULTS:
                result = {"stdout": '{"ok":true}', "stderr": "", "exit_code": 0}
                SIMULATED_BASH_RESULTS["audit_test_db_created"] = True
                SIMULATED_BASH_RESULTS["audit_test_db_exists_code"] = "200"
            else:
                result = {"stdout": '{"error":"file_exists","reason":"The database could not be created, the file already exists."}', "stderr": "", "exit_code": 0}
        elif command_to_run == 'curl -s -w "%{http_code}" -o /dev/null "http://localhost:5984/audit_test_db"':
            result = {"stdout": SIMULATED_BASH_RESULTS.get("audit_test_db_exists_code", "404"), "stderr": "", "exit_code": 0}
        elif command_to_run == 'curl -s -X DELETE "http://localhost:5984/non_existent_db_for_sure"':
             result = {"stdout": '{"error":"not_found","reason":"Database does not exist."}', "stderr": "", "exit_code": 0}
        elif command_to_run == 'curl -s -X DELETE "http://localhost:5984/audit_test_db"':
            if SIMULATED_BASH_RESULTS.get("audit_test_db_exists_code") == "200":
                result = {"stdout": '{"ok":true}', "stderr": "", "exit_code": 0}
                SIMULATED_BASH_RESULTS["audit_test_db_exists_code"] = "404"
                SIMULATED_BASH_RESULTS.pop("audit_test_db_created", None)
            else:
                result = {"stdout": '{"error":"not_found","reason":"Database does not exist."}', "stderr": "", "exit_code": 0}
        else:
            print(f"WARNING: No specific CouchDB simulation for: {command_to_run}.")
            result = {"stdout": "", "stderr": "CouchDB command not specifically simulated", "exit_code": 1}

    # --- IPFS Simulations ---
    elif "localhost:5001" in command_to_run and "/api/v0/" in command_to_run:
        if "/api/v0/id" in command_to_run:
            valid_sim_node_id = "QmSimulatedNodeId123abcxyz123abcxyz123abcxyz123"
            result = {"stdout": f'{{"ID": "{valid_sim_node_id}", "PublicKey": "CAAS...", "Addresses": ["/ip4/127.0.0.1/tcp/4001/p2p/{valid_sim_node_id}"]}}', "stderr": "", "exit_code": 0}
        elif "/api/v0/add" in command_to_run:
            valid_sim_add_hash = "QmSimuHashValueForAddedString123abcxyz123abcxy"
            result = {"stdout": f'{{"Name":"simulated_file.txt", "Hash":"{valid_sim_add_hash}", "Size":"30"}}', "stderr": "", "exit_code": 0}
        elif "/api/v0/cat?arg=QmNonExistentCidHashValueThatIsNotPresent12345" in command_to_run:
            result = {"stdout": "", "stderr": "Error: merkledag: not found", "exit_code": 1}
        elif "/api/v0/cat?arg=QmZ4d7p2U9sSYK7VVaYQifdx3Y3zG21x73X8jTCy2F1YPc" in command_to_run:
            result = {"stdout": "Hello World\n", "stderr": "", "exit_code": 0}
        elif "/api/v0/cat?arg=" in command_to_run:
            result = {"stdout": "Simulated content for unknown CID", "stderr": "", "exit_code": 0}
        elif "/api/v0/pin/add?arg=InvalidCIDFormatNotEvenAHash" in command_to_run:
            result = {"stdout": '{"Type": "error", "Message": "invalid path \\"InvalidCIDFormatNotEvenAHash\\": invalid cid: selected encoding not supported", "Code": 0}', "stderr":"Error: invalid path...", "exit_code":1}
        elif "/api/v0/pin/add?arg=" in command_to_run:
            cid_match = command_to_run.split("arg=")[-1].replace('"', '')
            result = {"stdout": f'{{"Pins":["{cid_match}"]}}', "stderr": "", "exit_code": 0}
            SIMULATED_BASH_RESULTS[f"pinned_{cid_match}"] = True
        elif "/api/v0/pin/ls?arg=" in command_to_run:
            cid_match = command_to_run.split("arg=")[-1].replace('"', '')
            if SIMULATED_BASH_RESULTS.get(f"pinned_{cid_match}"):
                result = {"stdout": f'{{"Keys":{{"{cid_match}":{{"Type":"recursive"}}}}}}', "stderr": "", "exit_code": 0}
            else:
                result = {"stdout": f'{{"Message":"path \'{cid_match}\' is not pinned","Code":0,"Type":"error"}}', "stderr": "", "exit_code": 1}
        else:
            print(f"WARNING: No specific IPFS simulation for: {command_to_run}.")
            result = {"stdout": "", "stderr": "IPFS command not specifically simulated", "exit_code": 1}

    # --- K2Script Simulations (via curl) ---
    elif "localhost:8090" in command_to_run and "curl " in command_to_run:
        if "/hello" in command_to_run and ("-X GET" in command_to_run or "-X" not in command_to_run):
            result = {"stdout": "Response body: Hello from k2script servlet!200", "stderr": "", "exit_code": 0}
        elif "/data" in command_to_run and "-X POST" in command_to_run:
            result = {"stdout": "{\"status\":\"created_simulated\"}201", "stderr": "", "exit_code": 0}
        elif "/update" in command_to_run and "-X PUT" in command_to_run:
            result = {"stdout": "{\"status\":\"updated_simulated\"}200", "stderr": "", "exit_code": 0}
        elif "/error" in command_to_run:
            result = {"stdout": "Internal Server Error simulation500", "stderr": "", "exit_code": 0}
        elif "/notfound" in command_to_run:
            result = {"stdout": "Resource not found.404", "stderr": "", "exit_code": 0}
        else:
            print(f"WARNING: No specific K2Script simulation for: {command_to_run}.")
            result = {"stdout": "K2Script generic error response500", "stderr": "K2Script command not specifically simulated", "exit_code": 0}

    # --- CmdTools Simulations (curl part) ---
    elif "curl " in command_to_run:
        if "-w \"%{http_code}\"" in command_to_run and "proof.ovh.net/files/1Mb.dat" in command_to_run:
            result = {"stdout": "200", "stderr": "", "exit_code": 0}
        elif "-w \"%{http_code}\"" in command_to_run and "example.com/definitely-not-here.txt" in command_to_run:
            result = {"stdout": "404", "stderr": "", "exit_code": 0}
        elif ('-I "https://www.google.com"' in command_to_run) or ('-I -L "https://www.google.com"' in command_to_run): # Made condition more robust
            result = {"stdout": "HTTP/1.1 200 OK\nContent-Type: text/html; charset=UTF-8\nServer: gws", "stderr": "", "exit_code": 0}
        else:
            print(f"WARNING: No specific generic Curl simulation for: {command_to_run}")
            result = {"stdout": "Generic curl success", "stderr": "", "exit_code": 0}

    # --- CmdTools Simulations (aria2c and rm) ---
    elif command_to_run.startswith("rm -f curl_test_1Mb.dat") or command_to_run.startswith("rm -f curl_test_404.dat"):
        result = {"stdout": "", "stderr": "", "exit_code": 0}
    elif "aria2c " in command_to_run:
        if "proof.ovh.net/files/10Mb.dat" in command_to_run:
            result = {"stdout": "aria2c simulated success", "stderr": "", "exit_code": 0}
        elif "ftp://invalidprotocolforsimulation/file.dat" in command_to_run:
            result = {"stdout": "", "stderr": "Download failed. Unsupported protocol.", "exit_code": 12}
        elif "example.com/some.metalink" in command_to_run:
             result = {"stdout": "aria2c metalink simulated success", "stderr": "", "exit_code": 0}
        else:
            print(f"WARNING: No specific Aria2c simulation for: {command_to_run}")
            result = {"stdout": "aria2c generic success", "stderr": "", "exit_code": 0}
    elif command_to_run.startswith("rm -rf aria2c_downloads_sim") or \
         command_to_run.startswith("rm -rf aria2c_metalink_downloads_sim") or \
         command_to_run.startswith("rm -rf aria2c_error_downloads_sim"):
        result = {"stdout": "", "stderr": "", "exit_code": 0}

    # --- Fallback for unknown commands ---
    # This 'else' must be the last part of the conditional chain.
    else:
        print(f"WARNING: No specific simulation defined for command: {command_to_run}. Using default error response.")
        # `result` is already preset to a default error, so no need to re-assign unless it was matched above.

    # Common print for all handled cases
    # This structure ensures 'result' is defined. The initial default handles truly unmatched commands.
    print(f"SIMULATION: For command '{command_to_run}' -> {json.dumps(result)}")
    print(f"AGENT_INFO: Provide these results back to the TestRunner's process_output_func: {json.dumps(result)}")
    return result


def main():
    """
    Main function to initialize and run the audit tester.

    Orchestrates the test execution by:
    1. Setting up the script's base directory.
    2. Initializing the `Reporter`.
    3. Loading test configurations from `config.yaml` via `load_config`.
    4. Instantiating the `TestRunner` with the reporter, config, the
       `run_bash_command_via_tool` (for simulated bash execution), and a
       placeholder Trikeshed client.
    5. Retrieving test definitions from the loaded configuration.
    6. Clearing any state from previous simulated runs (important for consistency if `main` could be called multiple times).
    7. Invoking `runner.run_tests()` to execute all defined tests.
    8. Generating console and JSON reports.
    9. Saving the JSON report to a file in the `reports/` directory.
    """
    print("Initializing Audit Tester...")
    script_dir = os.path.dirname(__file__) #Directory of main.py

    # Initialize Reporter
    reporter = Reporter()

    # Load Configuration
    config_file_path = os.path.join(script_dir, "config.yaml")
    config = load_config(config_file_path)
    if not config:
        print(f"Warning: Configuration file '{config_file_path}' empty or not loaded. Using default empty config.")
        config = {}

    # Initialize TestRunner
    mock_trikeshed_client_for_main = object() # Placeholder, as QuicHttp3TestModule uses internal simulation
    runner = TestRunner(
        reporter=reporter,
        config=config,
        run_bash_func=run_bash_command_via_tool,
        trikeshed_client=mock_trikeshed_client_for_main
    )

    tests_to_execute = config.get("tests_to_run", [])

    if not tests_to_execute:
        print("No tests specified in config's 'tests_to_run' key. Nothing to run.")
    else:
        print(f"\nStarting test execution for {len(tests_to_execute)} test definition(s)...")

        # Reset global simulation states before a new run for idempotency
        # (important if main() or run_bash_command_via_tool could be called multiple times in a session)
        global SIMULATED_BASH_RESULTS
        SIMULATED_BASH_RESULTS.pop("audit_test_db_created", None)
        SIMULATED_BASH_RESULTS.pop("audit_test_db_exists_code", None)
        keys_to_clear = [k for k in SIMULATED_BASH_RESULTS if k.startswith("pinned_")]
        for k in keys_to_clear: SIMULATED_BASH_RESULTS.pop(k)

        runner.run_tests(tests_to_execute)

    # Generate and Save Reports
    print("\nGenerating final report...")
    reporter.generate_report(report_format="console") # Prints to console
    json_report_output = reporter.generate_report(report_format="json") # Gets JSON string

    if json_report_output:
        # Ensure the 'reports' directory exists relative to this script's location
        reports_dir = os.path.join(script_dir, "reports")
        os.makedirs(reports_dir, exist_ok=True)
        report_file_path = os.path.join(reports_dir, "audit_report.json")
        try:
            with open(report_file_path, "w") as f:
                f.write(json_report_output)
            print(f"JSON report saved to: {report_file_path}")
        except IOError as e:
            print(f"Error saving JSON report to file '{report_file_path}': {e}")

    print("\nAudit Tester run finished.")
    print("Reminder: Bash commands were SIMULATED. Agent intervention is required for actual execution.")

if __name__ == "__main__":
    # This block executes when the script is run directly.
    # It's good practice to check for essential heavy dependencies if they are critical for basic operation,
    # though PyYAML is quite standard.
    try:
        import yaml
    except ImportError:
        print("CRITICAL ERROR: PyYAML library not found. This is required to load `config.yaml`.")
        print("Please install it, e.g., using: pip install PyYAML")
        # Consider exiting if PyYAML is absolutely critical for any operation of the script.
        # For this script, load_config handles it gracefully by returning empty config,
        # but the run would be meaningless.

    main()
