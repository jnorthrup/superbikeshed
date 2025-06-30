"""
This module defines the Reporter class for the Audit Tester framework.

The Reporter is responsible for collecting and structuring test results
throughout a test run and generating various forms of reports (e.g., console, JSON).
"""
import datetime
import json

class Reporter:
    """
    Manages the collection of test case results and generation of reports.

    The Reporter tracks the start and end of test runs and individual test cases,
    accumulates results (status, messages, details), and can output these
    in different formats. Currently, it supports console output and JSON.

    Attributes:
        run_start_time (datetime.datetime | None): Timestamp of when the current test run started.
        test_cases (list[dict]): A list of dictionaries, where each dictionary stores
                                 data for a completed test case.
        current_test_case_data (dict | None): Stores data for the test case that is
                                              currently executing.
    """
    def __init__(self):
        """Initializes a new Reporter instance with an empty state."""
        self.run_start_time = None
        self.test_cases = []
        self.current_test_case_data = None

    def start_test_run(self):
        """Marks the beginning of a new test run.

        Resets internal state for test cases and records the start time.
        Prints a message to the console indicating the start of the run.
        """
        self.run_start_time = datetime.datetime.now()
        self.test_cases = [] # Reset list of test cases for the new run
        self.current_test_case_data = None # Ensure no lingering current test case
        print(f"[{self.run_start_time.isoformat()}] Test Run Started")

    def start_test_case(self, test_name: str):
        """Marks the beginning of an individual test case.

        If a previous test case was still marked as running, it's automatically ended.
        Initializes data for the new test case including its name and start time.

        Args:
            test_name (str): The name of the test case to start.
        """
        if self.current_test_case_data:
            # This case should ideally not happen if end_test_case is called properly for each test.
            print(f"Warning: Starting new test case '{test_name}' before previous one ('{self.current_test_case_data['name']}') ended. Auto-ending previous.")
            self.end_test_case(self.current_test_case_data['name'])

        print(f"  Starting Test Case: {test_name}")
        self.current_test_case_data = {
            "name": test_name,
            "start_time": datetime.datetime.now(),
            "end_time": None,
            "status": "running", # Initial status, expected to be updated by add_test_result or end_test_case
            "message": "",
            "details": None,
            "steps": [] # Placeholder for future use: more granular logging within a test case
        }

    def add_test_result(self, test_name: str, status: str, message: str, details: dict = None):
        """Adds or updates the result for the currently active test case.

        It's primarily intended to set the final status (e.g., "passed", "failed", "error")
        and outcome message for the `test_name` that is currently active.
        If called for a different `test_name`, a warning is printed.

        Args:
            test_name (str): The name of the test case this result pertains to.
            status (str): The status of the test (e.g., "passed", "failed", "error", "skipped").
            message (str): A descriptive message about the test outcome.
            details (dict, optional): Additional structured data related to the test result. Defaults to None.
        """
        if not self.current_test_case_data or self.current_test_case_data["name"] != test_name:
            print(f"Warning: Adding result for '{test_name}' but current test is '{self.current_test_case_data['name'] if self.current_test_case_data else 'None'}'. This might indicate an issue or a general event log.")
            # For now, if it's not the current test, log as a general event.
            # A more robust implementation might find the test case in self.test_cases if already ended
            # or handle it as a distinct event not tied to the current_test_case_data lifecycle.
            # However, the primary design is that add_test_result is called *before* end_test_case for the active test.
            print(f"  General Result Event: Test: {test_name}, Status: {status.upper()}, Message: {message}")
            if details: print(f"    Details: {details}")
            return

        # Update the currently active test case
        self.current_test_case_data["status"] = status
        self.current_test_case_data["message"] = message
        self.current_test_case_data["details"] = details
        print(f"    Test: {test_name}, Status: {status.upper()}, Message: {message}")
        if details:
            print(f"      Details: {json.dumps(details, indent=2) if isinstance(details, dict) else details}")


    def end_test_case(self, test_name: str):
        """Marks the end of an individual test case.

        Records the end time and finalizes the status. If no explicit status was set via
        `add_test_result` while the test was "running", its status is marked as "completed".
        The test case data is then moved from `current_test_case_data` to the `test_cases` list.

        Args:
            test_name (str): The name of the test case to end.
        """
        if not self.current_test_case_data or self.current_test_case_data["name"] != test_name:
            print(f"Warning: Attempting to end test case '{test_name}', but it's not the currently active one ('{self.current_test_case_data['name'] if self.current_test_case_data else 'None'}'). Data might be inconsistent.")
            # Attempt to find and end it if it was missed, otherwise, this call might be erroneous.
            target_case = next((tc for tc in self.test_cases if tc["name"] == test_name and tc["end_time"] is None), None)
            if target_case: # If found in the list of already (partially) added cases but not ended
                target_case["end_time"] = datetime.datetime.now()
                if target_case["status"] == "running":
                    target_case["status"] = "completed" # Default status if not explicitly set
                    target_case["message"] = target_case.get("message","") + " (Ended with warning: was not current test)"
                print(f"  Ended Test Case (found out of order): {test_name}, Status: {target_case['status']}")
            return # Exit, as current_test_case_data is not what we expected

        self.current_test_case_data["end_time"] = datetime.datetime.now()
        if self.current_test_case_data["status"] == "running":
            # If no explicit status (pass/fail/error) was set by add_test_result
            self.current_test_case_data["status"] = "completed"
            self.current_test_case_data["message"] = "Test case ended without an explicit final status."

        self.test_cases.append(self.current_test_case_data)
        print(f"  Ended Test Case: {test_name}, Status: {self.current_test_case_data['status']}")
        self.current_test_case_data = None # Clear current test


    def end_test_run(self):
        """Marks the end of the entire test run.

        Ensures any still-active test case is properly ended.
        Calculates and prints a summary of test results to the console.
        """
        run_end_time = datetime.datetime.now()
        duration = run_end_time - (self.run_start_time if self.run_start_time else run_end_time)

        if self.current_test_case_data: # Auto-end if a test case was left running
            print(f"Warning: Test run ended but test case '{self.current_test_case_data['name']}' was still active. Auto-ending it.")
            self.end_test_case(self.current_test_case_data['name'])

        print(f"[{run_end_time.isoformat()}] Test Run Finished. Duration: {duration}")
        self._print_summary()

    def _print_summary(self):
        """Internal helper to print a summary of test results to the console."""
        if not self.test_cases:
            print("\n--- Test Summary ---")
            print("No test cases were executed in this run.")
            print("--------------------\n")
            return

        passed_count = sum(1 for tc in self.test_cases if tc["status"] == "passed")
        failed_count = sum(1 for tc in self.test_cases if tc["status"] == "failed")
        errored_count = sum(1 for tc in self.test_cases if tc["status"] == "error")
        skipped_count = sum(1 for tc in self.test_cases if tc["status"] == "skipped")
        completed_count = sum(1 for tc in self.test_cases if tc["status"] == "completed")

        # Any other statuses would fall into 'other_count'
        other_count = len(self.test_cases) - passed_count - failed_count - errored_count - skipped_count - completed_count


        print("\n--- Test Summary ---")
        print(f"Total Tests Executed: {len(self.test_cases)}")
        print(f"  Passed:    {passed_count}")
        print(f"  Failed:    {failed_count}")
        print(f"  Errored:   {errored_count}")
        print(f"  Skipped:   {skipped_count}")
        if completed_count > 0 : print(f"  Completed (no explicit status): {completed_count}")
        if other_count > 0: print(f"  Other Statuses: {other_count}")
        print("--------------------\n")

    def generate_report(self, report_format: str = "console"):
        """
        Generates a report of the test run in the specified format.

        Args:
            report_format (str, optional): The desired report format.
                                           Currently supports "console" and "json".
                                           Defaults to "console".

        Returns:
            str | None: For "json" format, returns the JSON string.
                        For "console" or unsupported formats, returns None.
        """
        if report_format.lower() == "console":
            print("\n--- Generating Console Report ---")
            if self.run_start_time:
                 print(f"Run Started: {self.run_start_time.isoformat()}")
            else:
                print("Run start time not recorded.")

            for tc in self.test_cases:
                duration_s = 'N/A'
                if tc.get('start_time') and tc.get('end_time'):
                    duration = (tc['end_time'] - tc['start_time'])
                    duration_s = f"{duration.total_seconds():.3f}s"

                print(f"\nTest: {tc.get('name', 'Unnamed Test')}")
                print(f"  Status:   {tc.get('status', 'UNKNOWN').upper()}")
                print(f"  Message:  {tc.get('message', '')}")
                if tc.get('details'):
                    details_str = json.dumps(tc['details'], indent=2) if isinstance(tc['details'], dict) else str(tc['details'])
                    print(f"  Details:  {details_str}")
                print(f"  Duration: {duration_s}")

            self._print_summary() # Re-print summary at the end of the console report
            return None

        elif report_format.lower() == "json":
            # Ensure run_end_time is captured for the JSON report if not already set by end_test_run
            # This might be slightly later than the actual end_test_run if called separately.
            run_end_actual = datetime.datetime.now()

            report_data = {
                "run_start_time": self.run_start_time.isoformat() if self.run_start_time else None,
                "run_end_time": run_end_actual.isoformat(),
                "test_cases": [
                    {
                        "name": tc.get("name"),
                        "start_time": tc.get("start_time").isoformat() if tc.get("start_time") else None,
                        "end_time": tc.get("end_time").isoformat() if tc.get("end_time") else None,
                        "duration_seconds": (tc['end_time'] - tc['start_time']).total_seconds() if tc.get('end_time') and tc.get('start_time') else None,
                        "status": tc.get("status"),
                        "message": tc.get("message"),
                        "details": tc.get("details"), # Details can be any dict, kept as is.
                    } for tc in self.test_cases
                ],
                "summary": {
                    "total_tests": len(self.test_cases),
                    "passed": sum(1 for tc in self.test_cases if tc.get("status") == "passed"),
                    "failed": sum(1 for tc in self.test_cases if tc.get("status") == "failed"),
                    "errored": sum(1 for tc in self.test_cases if tc.get("status") == "error"),
                    "skipped": sum(1 for tc in self.test_cases if tc.get("status") == "skipped"),
                    "completed": sum(1 for tc in self.test_cases if tc.get("status") == "completed"),
                }
            }
            print("\n--- Generating JSON Report ---")
            json_report = json.dumps(report_data, indent=2)
            print(json_report) # Print to console for visibility during run
            return json_report
        else:
            print(f"Report format '{report_format}' not supported yet.")
            return None


if __name__ == '__main__':
    # Example usage demonstrating various features of the Reporter
    reporter = Reporter()

    # Start a test run
    reporter.start_test_run()

    # Test Case 1: Passed
    reporter.start_test_case("Test_Login_Success")
    reporter.add_test_result("Test_Login_Success", "passed", "User logged in successfully.", {"user_id": "user123"})
    reporter.end_test_case("Test_Login_Success")

    # Test Case 2: API Test, Passed
    reporter.start_test_case("Test_API_GetData_Valid")
    reporter.add_test_result("Test_API_GetData_Valid", "passed", "Data fetched and matches schema.", {"endpoint": "/api/data", "items_returned": 10})
    reporter.end_test_case("Test_API_GetData_Valid")

    # Test Case 3: Failed
    reporter.start_test_case("Test_Payment_Fails_InvalidCard")
    reporter.add_test_result("Test_Payment_Fails_InvalidCard", "failed", "Payment declined", {"error_code": "5001", "reason": "Card number invalid."})
    reporter.end_test_case("Test_Payment_Fails_InvalidCard")

    # Test Case 4: Errored (e.g., an exception during test setup/execution, not assertion failure)
    reporter.start_test_case("Test_System_Crash_Scenario")
    reporter.add_test_result("Test_System_Crash_Scenario", "error", "Test could not be completed due to system exception.", {"exception_type": "NullPointerException", "stack_trace_snippet": "at system.core.line50"})
    reporter.end_test_case("Test_System_Crash_Scenario")

    # Test Case 5: Skipped
    reporter.start_test_case("Test_Feature_Not_Implemented")
    reporter.add_test_result("Test_Feature_Not_Implemented", "skipped", "Feature XYZ is pending implementation.", {"ticket_id": "FEAT-101"})
    reporter.end_test_case("Test_Feature_Not_Implemented")

    # Test Case 6: Ends without explicit status (should become 'completed')
    reporter.start_test_case("Test_Only_Starts_And_Ends")
    reporter.end_test_case("Test_Only_Starts_And_Ends")

    # Test Case 7: Test that was started but run ends before it's explicitly ended
    reporter.start_test_case("Test_Lingering_Active_Test")
    # reporter.end_test_case("Test_Lingering_Active_Test") # Simulate not ending it

    # End the test run (this will auto-end "Test_Lingering_Active_Test")
    reporter.end_test_run()

    # Generate reports
    print("\n--- Requesting Console Report (Post-Run) ---")
    reporter.generate_report(report_format="console")

    print("\n--- Requesting JSON Report (Post-Run) ---")
    json_output = reporter.generate_report(report_format="json")
    # In a real script, you might save json_output to a file:
    # if json_output:
    #     with open("final_audit_report.json", "w") as f:
    #         f.write(json_output)
    #     print("\nJSON report saved to final_audit_report.json")

    print("\n--- Reporter example finished ---")
