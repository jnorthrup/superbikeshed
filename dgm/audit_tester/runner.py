"""
This module defines the TestRunner class for the Audit Tester framework.

The TestRunner is the core orchestrator for test execution. It loads test
modules, interprets test definitions from the configuration, executes the tests,
and uses a Reporter instance to log results.
"""
import importlib
import inspect
import os # Potentially for future dynamic module loading from a directory path

# Placeholder for a real Trikeshed client, if it were to be used directly by the runner.
# Currently, modules requiring Trikeshed (like QuicHttp3TestModule) use their own
# internal simulation or expect a client instance to be passed to them.
ActualTrikeshedClient = None

class TestRunner:
    """
    Manages the loading of test modules and execution of test cases.

    The TestRunner takes test definitions (typically from `config.yaml`) and
    dynamically calls methods on appropriate test modules. It distinguishes
    between:
    1.  "Command-style" test methods: These methods (e.g., `check_something_command`)
        return a dictionary containing a bash command and a function to process its output.
        The TestRunner uses a provided `run_bash_func` to execute the command and then
        invokes the processing function with the command's output.
    2.  "Direct-style" test methods: These methods (e.g., `check_something_else`)
        perform their checks and reporting directly, often using internal clients or simulations.

    Attributes:
        reporter (Reporter): An instance of the Reporter class for logging test results.
        config (dict): A dictionary containing the loaded test configuration.
        test_modules (dict[str, object]): A dictionary mapping module class names to
                                          their instantiated objects.
        run_bash_func (callable): A function provided by the caller (e.g., `main.py`)
                                  that can execute a bash command string and return its
                                  stdout, stderr, and exit code as a dictionary.
        trikeshed_client (object | None): An optional Trikeshed client instance. Passed to
                                         modules that might require it (e.g., QuicHttp3TestModule).
                                         Can be a mock or placeholder if real client not available.
    """
    def __init__(self, reporter, config, run_bash_func, trikeshed_client=None):
        """
        Initializes the TestRunner.

        Args:
            reporter: An instance of the Reporter class.
            config (dict): The test configuration dictionary.
            run_bash_func (callable): A function to execute bash commands.
                                      Expected to take a command string and return a dict:
                                      `{'stdout': str, 'stderr': str, 'exit_code': int}`.
            trikeshed_client (object, optional): An instance of a Trikeshed client,
                                                 or a mock/placeholder. Defaults to None.
        """
        self.reporter = reporter
        self.config = config
        self.test_modules = {} # Stores instantiated test module objects
        self.run_bash_func = run_bash_func
        self.trikeshed_client = trikeshed_client

        if not self.trikeshed_client:
            # If no real client is provided, use a simple placeholder.
            # Modules are responsible for handling a None or placeholder client (e.g., by using internal simulation).
            print("TestRunner: No real Trikeshed client provided; using a placeholder for module instantiation.")
            self.trikeshed_client = object()

    def load_test_modules(self):
        """
        Loads and instantiates all defined test modules.

        Modules are specified in `module_names_to_load`. It attempts to import each module,
        find the specified class, and instantiate it, passing necessary dependencies like
        the reporter and any specific clients (e.g., Trikeshed client).
        Errors during loading are reported via the Reporter.
        """
        # Centralized list of modules to load.
        # Future enhancement: could make this list configurable or discover modules dynamically.
        module_names_to_load = {
            "HTTPTestModule": "dgm.audit_tester.modules.http_tests",
            "QuicHttp3TestModule": "dgm.audit_tester.modules.quic_http3_tests",
            "CouchDBTestModule": "dgm.audit_tester.modules.couchdb_tests",
            "IPFSTestModule": "dgm.audit_tester.modules.ipfs_tests",
            "CmdToolsTestModule": "dgm.audit_tester.modules.cmd_tools_tests",
            "K2ScriptTestModule": "dgm.audit_tester.modules.k2script_tests"
        }

        for class_name, module_path in module_names_to_load.items():
            try:
                module_spec = importlib.import_module(module_path)
                module_class = getattr(module_spec, class_name, None)

                if module_class and inspect.isclass(module_class):
                    # Instantiate module, passing dependencies.
                    # Specific modules might require specific clients.
                    if class_name == "QuicHttp3TestModule":
                        self.test_modules[class_name] = module_class(
                            reporter=self.reporter,
                            trikeshed_client_instance=self.trikeshed_client # Pass the (potentially mock) client
                        )
                    # Example for other modules if they had specific dependencies:
                    # elif class_name == "AnotherModuleWithSpecificClient":
                    #    self.test_modules[class_name] = module_class(
                    #        reporter=self.reporter,
                    #        specific_client=self.some_other_client_instance
                    #    )
                    else: # Default instantiation for modules needing only the reporter
                        self.test_modules[class_name] = module_class(reporter=self.reporter)
                    print(f"Loaded test module: {class_name}")
                else:
                    msg = f"Class '{class_name}' not found in module '{module_path}'."
                    print(f"Error: {msg}")
                    self.reporter.add_test_result("ModuleLoading", "error", msg, {"module_path": module_path, "class_name": class_name})

            except ImportError as e:
                msg = f"Failed to import module '{module_path}': {e}"
                print(f"Error: {msg}")
                self.reporter.add_test_result("ModuleLoading", "error", msg, {"module_path": module_path})
            except Exception as e: # Catch-all for other instantiation errors
                msg = f"Unexpected error loading module '{module_path}' (class '{class_name}'): {e}"
                print(f"Error: {msg}")
                self.reporter.add_test_result("ModuleLoading", "error", msg, {"module_path": module_path, "class_name": class_name})


    def execute_test(self, test_definition: dict):
        """
        Executes a single test based on its definition.

        The `test_definition` dictionary specifies the module, function (method),
        arguments, and an optional descriptive test name.

        The method determines if the target test function in the module is
        "command-style" (e.g., `some_check_command`) or "direct-style"
        (e.g., `another_check`).
        - For command-style: It calls the method to get command info, uses
          `self.run_bash_func` to execute the bash command, and then calls the
          module's `process_output_func` with the results.
        - For direct-style: It calls the method directly. These methods are
          expected to handle their own assertions and reporting using the Reporter.

        Args:
            test_definition (dict): A dictionary defining the test, expected to contain:
                - "module" (str): The class name of the test module.
                - "function" (str): The base name of the test function in the module.
                - "args" (dict, optional): Arguments to pass to the test function.
                - "test_name" (str, optional): A descriptive name for reporting.
                                              If not provided, one is generated.
        """
        module_name = test_definition.get("module")
        function_name = test_definition.get("function")
        args = test_definition.get("args", {})

        # Use explicit test_name from config if provided, otherwise generate one.
        # This name is primarily for the runner's top-level error reporting if a method can't be found/called.
        # Individual modules/methods typically define more specific test names for the reporter.
        explicit_test_name = test_definition.get("test_name", f"{module_name}.{function_name}")

        module_instance = self.test_modules.get(module_name)

        if not module_instance:
            msg = f"Test module '{module_name}' not found or not loaded."
            self.reporter.start_test_case(explicit_test_name)
            self.reporter.add_test_result(explicit_test_name, "error", msg, {"module_name": module_name, "function_name": function_name})
            self.reporter.end_test_case(explicit_test_name)
            print(f"Error: {msg}")
            return

        # Determine if it's a "_command" style method (for bash) or direct execution method
        command_method_name = function_name + "_command"
        direct_method_name = function_name

        method_to_call = None
        is_command_style = False

        if hasattr(module_instance, command_method_name) and callable(getattr(module_instance, command_method_name)):
            method_to_call = getattr(module_instance, command_method_name)
            is_command_style = True
        elif hasattr(module_instance, direct_method_name) and callable(getattr(module_instance, direct_method_name)):
            method_to_call = getattr(module_instance, direct_method_name)
            is_command_style = False

        if not method_to_call:
            msg = f"Test function '{function_name}' (expected as '{command_method_name}' or '{direct_method_name}') not found in module '{module_name}'."
            self.reporter.start_test_case(explicit_test_name)
            self.reporter.add_test_result(explicit_test_name, "error", msg, test_definition)
            self.reporter.end_test_case(explicit_test_name)
            print(f"Error: {msg}")
            return

        try:
            if is_command_style:
                # This path is for bash-based tests (e.g., HTTPTestModule, IPFSTestModule).
                # The module's "_command" method returns a dictionary with:
                #  - "command": The bash command string to execute.
                #  - "process_output_func": A callable to process the command's output.
                #  - "type": Should be "bash".
                #  - "test_name": A specific name for this test instance.
                command_info = method_to_call(**args)

                if command_info.get("type") == "bash":
                    bash_command = command_info["command"]
                    # Use the more specific test name from command_info for reporting this execution.
                    current_test_name_for_bash = command_info.get("test_name", explicit_test_name)
                    print(f"Executing bash command for test '{current_test_name_for_bash}': {bash_command}")

                    # `self.run_bash_func` is provided by main.py and handles actual execution (often simulated).
                    bash_output = self.run_bash_func(bash_command)

                    # The module's `process_output_func` is responsible for assertions and detailed reporting
                    # (start_test_case, add_test_result, end_test_case) for this specific command.
                    command_info["process_output_func"](
                        stdout=bash_output.get('stdout', ''),
                        stderr=bash_output.get('stderr', ''),
                        exit_code=bash_output.get('exit_code', -1)
                    )
                else:
                    # This case handles if a "_command" method doesn't specify "bash" type correctly.
                    unsupported_type_test_name = command_info.get("test_name", explicit_test_name)
                    self.reporter.start_test_case(unsupported_type_test_name)
                    self.reporter.add_test_result(
                        unsupported_type_test_name, "skipped",
                        f"Unsupported command type '{command_info.get('type')}' from _command method in '{module_name}'.", command_info)
                    self.reporter.end_test_case(unsupported_type_test_name)
            else:
                # This path is for "direct-style" tests (e.g., QuicHttp3TestModule).
                # These methods perform their operations and reporting internally.
                print(f"Executing direct test: {module_name}.{function_name}")
                # The direct method is expected to call reporter methods (start, add_result, end) itself.
                # It might return some info, but its primary job is self-contained execution and reporting.
                # The common pattern is for it to return a dict like {"type": "direct", "executed": True/False}
                test_execution_info = method_to_call(**args)

                # Basic check if the direct method seemed to execute or returned an unexpected value.
                if not test_execution_info or not isinstance(test_execution_info, dict) or not test_execution_info.get("executed"):
                    # This implies the direct method might have failed before its own reporting,
                    # or it doesn't follow the optional convention of returning execution status.
                    # The reporter calls within the method are the primary source of truth for test status.
                    print(f"Warning: Direct test method {module_name}.{function_name} returned '{test_execution_info}'. Relaying on its internal reporting.")
                    # If it failed very early (before its own start_test_case), we might log a generic error.
                    # However, this could lead to duplicate error reporting if the method *did* report.
                    # For now, we assume direct methods are robust enough for their own reporting.

        except Exception as e:
            # This is a fallback error handler for unexpected issues during the test method call itself
            # (e.g., problem with `run_bash_func`, or an unhandled exception within a direct method
            # before it could report an "error" status itself).
            # It's crucial that `process_output_func` and direct methods handle their own exceptions
            # and report them cleanly via the Reporter to avoid this generic catch if possible.
            self.reporter.start_test_case(explicit_test_name) # Ensure test case is started if error was very early.
            self.reporter.add_test_result(explicit_test_name, "error", f"Framework error during execution of test '{explicit_test_name}': {type(e).__name__} - {e}", {"details": str(e)})
            self.reporter.end_test_case(explicit_test_name)
            print(f"Error (framework level) executing test '{explicit_test_name}': {e}")


    def run_tests(self, test_definitions: list[dict]):
        """
        Runs a series of tests based on their definitions.

        This is the main entry point for test execution by the runner.
        It initializes a test run with the reporter, loads all configured
        test modules, and then iterates through each test definition,
        calling `execute_test` for it. Finally, it finalizes the test run
        with the reporter.

        Args:
            test_definitions (list[dict]): A list of test definition dictionaries,
                                           as loaded from `config.yaml`.

        Returns:
            list[dict]: The list of test case results collected by the reporter during the run.
        """
        self.reporter.start_test_run()
        self.load_test_modules()

        if not self.test_modules:
            print("Critical Error: No test modules were loaded. Aborting test run.")
            self.reporter.add_test_result("TestRunner Setup", "error", "No test modules loaded. Check module paths and class names.", None)
            self.reporter.end_test_run()
            return self.reporter.test_cases

        if not test_definitions:
            print("No test definitions provided to run_tests. Test run will be empty.")
            # Report this as a general finding during the run.
            self.reporter.add_test_result(
                "Test Definitions",
                "warning",
                "No tests were specified in the 'tests_to_run' configuration.",
                None
            )

        for test_def in test_definitions:
            self.execute_test(test_def)

        self.reporter.end_test_run()
        return self.reporter.test_cases # Return the collected results


if __name__ == '__main__':
    # This __main__ block is for basic, conceptual testing of TestRunner.
    # It uses a DummyReporter and a mock bash function, as the real `run_bash_func`
    # (and thus actual bash command execution) is managed by `main.py` or an agent.
    class DummyReporter:
        def __init__(self): self.test_cases = []
        def start_test_run(self): print("--- Test Run Started (DummyReporter) ---")
        def end_test_run(self): print("--- Test Run Ended (DummyReporter) ---")
        def start_test_case(self, name): print(f"  [Start DummyCase] {name}")
        def end_test_case(self, name): print(f"  [End DummyCase]   {name}")
        def add_test_result(self, name, status, msg, details):
            print(f"    [{status.upper()}] {name}: {msg} {details if details else ''}")
            self.test_cases.append({"name": name, "status": status, "message": msg, "details": details})
        def generate_report(self, fmt): print(f"DummyReporter: Generating report in format: {fmt}")

    print("--- RUNNER.PY IF __NAME__ == __MAIN__ (Conceptual Test) ---")

    dummy_reporter_instance = DummyReporter()
    dummy_config_data = {} # Config data would normally be loaded from config.yaml

    def mock_run_bash_func_for_runner_test(command_str):
        """A simplified mock bash executor for runner's internal test."""
        print(f"  [MockBashToolInRunner] Executing: {command_str}")
        if "check_url_status" in command_str and "google.com" in command_str : # Simplistic match
             return {"stdout": "200", "stderr": "", "exit_code": 0} # HTTP status as stdout
        elif "check_http_version" in command_str and "google.com" in command_str:
             return {"stdout": "HTTP/1.1 200 OK\nServer: gws", "stderr": "", "exit_code": 0}
        # Add more specific mocks if other bash commands are tested directly here
        return {"stdout": "Mocked Stdout", "stderr": "Mocked Stderr", "exit_code": 0}

    # For QuicHttp3TestModule, its methods have internal simulation if client is basic object.
    mock_trikeshed_for_runner_test = object()

    # Instantiate the runner
    runner = TestRunner(
        reporter=dummy_reporter_instance,
        config=dummy_config_data,
        run_bash_func=mock_run_bash_func_for_runner_test,
        trikeshed_client=mock_trikeshed_for_runner_test
    )

    # Define a small set of diverse test definitions for this conceptual test
    sample_test_definitions_for_runner = [
        {
            "module": "HTTPTestModule",
            "function": "check_url_status", # Will use _command variant
            "test_name": "RunnerMain_GoogleStatus",
            "args": {"url": "https://www.google.com", "expected_status_code": 200}
        },
        {
            "module": "QuicHttp3TestModule",
            "function": "check_quic_handshake", # Direct execution
            "test_name": "RunnerMain_QuicRocksHandshake",
            "args": {"host": "quic.rocks", "port": 4433}
        },
        { # Example of a non-existent function to test error handling
            "module": "HTTPTestModule",
            "function": "non_existent_http_function",
            "test_name": "RunnerMain_NonExistentFunc",
            "args": {}
        },
        { # Example of a non-existent module
            "module": "NonExistentModule",
            "function": "some_function",
            "test_name": "RunnerMain_NonExistentModule",
            "args": {}
        }
    ]

    print("\nRunning tests via TestRunner's __main__ example...")
    results = runner.run_tests(sample_test_definitions_for_runner)
    print("\nTest run completed in TestRunner's __main__ example.")
    print(f"Number of test results collected: {len(results)}")
    # Example: print one successful and one error/failed if present
    for r in results[:2]: print(r)
    print("--- END RUNNER.PY IF __NAME__ == __MAIN__ ---")
