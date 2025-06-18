import re
import json # Import the json module

def parse_markdown_tasks(markdown_content):
    tasks = []
    current_section = None
    for line in markdown_content.splitlines():
        section_match = re.match(r"###\s+(.+)\s+\((\d+)\s+tasks\)", line)
        if section_match:
            current_section = section_match.group(1)
            continue

        task_match = re.match(r"-\s+\[( |x)\]\s+(.+)", line)
        if task_match and current_section:
            status = "completed" if task_match.group(1) == "x" else "pending"
            description = task_match.group(2)
            tasks.append({
                "section": current_section,
                "description": description,
                "status": status
            })
    return tasks

if __name__ == "__main__":
    with open("DEEP_SELF_HOSTING_TASKS.md", "r") as f:
        content = f.read()
    parsed_tasks = parse_markdown_tasks(content)
    # Output the parsed tasks as JSON to a file
    with open("tasks.json", "w") as f:
        json.dump(parsed_tasks, f, indent=2)
    print("Tasks parsed and saved to tasks.json")
