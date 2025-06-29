import re
import json

def parse_claude_markdown_tasks(markdown_content):
    tasks = []
    current_section = None
    # Regex to capture section headers like "## 1. Core Protocol Support" or "## Implementation Priorities"
    section_regex = r"##\s+(?:\d+\.\s+)?(.+)"
    # Regex to capture task items like "- [x] HTTP/1.1 basic implementation"
    task_regex = r"-\s+\[( |x)\]\s+(.+)"

    for line in markdown_content.splitlines():
        section_match = re.match(section_regex, line)
        if section_match:
            current_section = section_match.group(1).strip()
            # Skip "Implementation Priorities" and its sub-phases as they are not tasks lists
            if "priorities" in current_section.lower():
                current_section = None # Reset current_section so tasks under it are not captured
            continue

        if current_section: # Only look for tasks if we are inside a relevant section
            task_match = re.match(task_regex, line)
            if task_match:
                status = "completed" if task_match.group(1) == "x" else "pending"
                description = task_match.group(2).strip()
                tasks.append({
                    "section": current_section,
                    "description": description,
                    "status": status
                })
    return tasks

if __name__ == "__main__":
    with open("Trikeshed/CLAUDE.md", "r") as f:
        content = f.read()
    parsed_tasks = parse_claude_markdown_tasks(content)
    with open("claude_tasks.json", "w") as f:
        json.dump(parsed_tasks, f, indent=2)
    print("Claude tasks parsed and saved to claude_tasks.json")
