import json
import argparse
import sys

def main():
    parser = argparse.ArgumentParser(description="Mock Solution Proposer")
    parser.add_argument("--request_json", type=str, required=True, help="JSON string of DgmTaskForPython")
    args = parser.parse_args()

    try:
        request_data = json.loads(args.request_json)
    except json.JSONDecodeError as e:
        response = {
            "taskId": "unknown_task_due_to_parse_error",
            "changedFiles": [],
            "llmOutputLog": None,
            "status": "ERROR",
            "errorMessage": f"Failed to parse request_json: {str(e)}"
        }
        print(json.dumps(response))
        sys.exit(1) # Indicate error

    task_id = request_data.get("taskId", "unknown_task")

    # Basic error simulation: if prompt contains "error_please"
    if "error_please" in request_data.get("prompt", "").lower():
        response = {
            "taskId": task_id,
            "changedFiles": [],
            "llmOutputLog": "Simulating an error based on prompt.",
            "status": "ERROR",
            "errorMessage": "Simulated error from mock_solution_proposer.py"
        }
    elif "no_change_please" in request_data.get("prompt", "").lower():
        response = {
            "taskId": task_id,
            "changedFiles": [], # No changes
            "llmOutputLog": "Simulating a no-change scenario.",
            "status": "SUCCESS", # Still success, but no changes
            "errorMessage": None
        }
    else:
        # Simulate a successful response with some changes
        original_files = request_data.get("codeFiles", [])
        changed_files = []
        if original_files:
            first_file_path = original_files[0].get("filePath", "unknown_file.kt")
            changed_files.append({
                "filePath": first_file_path,
                "content": "// Mock change by solution_proposer.py\n" + original_files[0].get("content", "")
            })
        changed_files.append({
            "filePath": "new_file_from_proposer.kt",
            "content": "fun addedByProposer() = println(\"Hello from mock proposer!\")"
        })

        response = {
            "taskId": task_id,
            "changedFiles": changed_files,
            "llmOutputLog": f"Mock LLM log for task {task_id}: successfully processed files.",
            "status": "SUCCESS",
            "errorMessage": None
        }

    print(json.dumps(response))

if __name__ == "__main__":
    main()
