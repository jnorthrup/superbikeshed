# Audit Tester

Audit tester for various protocols and services. This framework is designed to be modular, allowing for the easy addition of new test suites for different targets.

## Overview

The Audit Tester consists of several key components:

-   **`main.py`**: The main entry point for running tests. It loads configurations, initializes the test runner, and orchestrates the test execution process.
    -   **Simulation Logic**: Currently, `main.py` includes a crucial simulation layer (`run_bash_command_via_tool`) for external bash commands (like `curl`, `aria2c`, etc.). This allows testing the framework's logic without requiring live services or direct agent intervention for each command during development. For actual end-to-end testing against live services, this simulation would need to be replaced or augmented by an agent capable of executing these commands and providing their output back to the framework.
-   **`runner.py`**: Contains the `TestRunner` class, responsible for discovering test modules, loading test definitions from the configuration, executing tests, and collecting results via the reporter.
-   **`reporter.py`**: Defines the `Reporter` class used to log test progress and generate summary reports (console, JSON).
-   **`config_loader.py`**: Provides functionality to load test configurations from `config.yaml`.
-   **`assertions.py`**: Contains custom assertion functions (e.g., `assert_true`, `assert_equal`) that raise `AuditAssertionError` for test failures.
-   **`config.yaml`**: The main configuration file where test cases are defined. Each test case specifies a module, a function (test method) within that module, and arguments for the test.
-   **`modules/`**: This directory houses the individual test modules, each targeting a specific protocol, service, or tool.

## Modules

The following test modules are currently available:

-   **`http_tests.py` (`HTTPTestModule`)**: For basic HTTP checks like URL status and HTTP version, using `curl` commands.
-   **`quic_http3_tests.py` (`QuicHttp3TestModule`)**: For QUIC handshake and HTTP/3 requests. Currently uses internal simulation for Trikeshed client interactions.
-   **`couchdb_tests.py` (`CouchDBTestModule`)**: For CouchDB checks like server status, database existence, creation, and deletion, using `curl` against the CouchDB API.
-   **`ipfs_tests.py` (`IPFSTestModule`)**: For IPFS interactions like node ID, adding/catting content, and pinning, using `curl` against the IPFS HTTP API.
-   **`cmd_tools_tests.py` (`CmdToolsTestModule`)**: For testing generic command-line tools like `curl` (for downloads, headers) and `aria2c` (for downloads). Also includes a utility to run arbitrary commands and check their exit codes.
-   **`k2script_tests.py` (`K2ScriptTestModule`)**: For testing k2script servlets, assuming they expose HTTP endpoints. Uses `curl` commands to interact with these endpoints.

## Defining Tests in `config.yaml`

Tests are defined as a list under the `tests_to_run` key in `config.yaml`. Each entry in the list is a dictionary specifying:
-   `module`: The name of the test module class (e.g., `HTTPTestModule`).
-   `function`: The name of the test method to call within the module (e.g., `check_url_status`).
-   `test_name` (optional): A descriptive name for the test, used in reporting.
-   `args`: A dictionary of arguments to pass to the test method.

Example:
```yaml
tests_to_run:
  - module: HTTPTestModule
    function: check_url_status
    test_name: "Google Homepage Status Check"
    args:
      url: "https://www.google.com"
      expected_status_code: 200
```

## Running Tests

Execute `main.py` to run the tests defined in `config.yaml`. Ensure any dependencies (like `PyYAML`) are installed.

```bash
python -m dgm.audit_tester.main
```

The output will include live logging of test execution and a final summary. A JSON report is also saved to `dgm/audit_tester/reports/audit_report.json`.
