import json
import argparse
import sys
import os

def main():
    parser = argparse.ArgumentParser(description="Mock Benchmark Runner")
    parser.add_argument("--request_json", type=str, required=True, help="JSON string of BenchmarkInvocation")
    args = parser.parse_args()

    try:
        request_data = json.loads(args.request_json)
    except json.JSONDecodeError as e:
        response = {
            "benchmarkId": "unknown_benchmark_due_to_parse_error",
            "status": "ERROR",
            "scores": [],
            "logOutput": None,
            "errorDetails": f"Failed to parse request_json: {str(e)}"
        }
        print(json.dumps(response))
        sys.exit(1) # Indicate error

    benchmark_id = request_data.get("benchmarkId", "unknown_benchmark")
    workspace_path = request_data.get("workspacePath", ".") # Default to current dir if not provided

    # Simulate checking for a specific file in the workspace to influence outcome
    # This makes the test more interactive with the workspace.
    trigger_file_path = os.path.join(workspace_path, "trigger_failure.txt")

    if "error_please" in benchmark_id.lower():
        response = {
            "benchmarkId": benchmark_id,
            "status": "ERROR",
            "scores": [],
            "logOutput": "Simulating a benchmark script error.",
            "errorDetails": "Simulated error from mock_benchmark_runner.py due to benchmarkId"
        }
    elif os.path.exists(trigger_file_path):
         response = {
            "benchmarkId": benchmark_id,
            "status": "FAILED_TESTS",
            "scores": [{"metricName": "pass_rate", "value": 0.0}, {"metricName": "reason", "value": 0.0}], # Value for string not ideal
            "logOutput": f"Benchmark failed because trigger file '{trigger_file_path}' was found.",
            "errorDetails": "Trigger file indicated failure."
        }
    elif "timeout_please" in benchmark_id.lower():
        # This script can't truly simulate a timeout that JvmProcessExecutionService would catch.
        # It can only simulate a script that runs long and then reports a timeout-like status.
        # The actual timeout would be handled by the calling Kotlin service.
        # For testing, we can have it exit with a specific error code or message.
        response = {
            "benchmarkId": benchmark_id,
            "status": "ERROR", # Or a custom "SIMULATED_TIMEOUT" if the test can check for it
            "scores": [],
            "logOutput": "Simulating a long run that would have timed out.",
            "errorDetails": "Mock script simulated taking too long."
        }
        # To test actual timeout, the Kotlin test should set a short timeout for a script that `time.sleep(long_time)`.
        # This script won't use time.sleep to avoid making tests slow.
    else:
        response = {
            "benchmarkId": benchmark_id,
            "status": "PASSED",
            "scores": [
                {"metricName": "pass_rate", "value": 1.0},
                {"metricName": "execution_time_ms", "value": 123.45}
            ],
            "logOutput": f"Mock benchmark run for {benchmark_id} in workspace {workspace_path} completed successfully.",
            "errorDetails": None
        }

    print(json.dumps(response))

if __name__ == "__main__":
    main()
