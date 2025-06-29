import re
import json
import sys # Import sys to write directly to stdout

def extract_specific_tasks(markdown_content):
    relevant_tasks = []
    current_section = None
    section_regex_general = r"^(?:##|###)\s+(.+)"
    task_regex = r"-\s+\[( |x)\]\s+(.+)"

    keywords = ["ipfs", "quic"]
    difficulty_descriptors = ["hard", "difficult", "challenging", "unnoticed"]

    lines = markdown_content.splitlines()

    for line in lines:
        section_match = re.match(section_regex_general, line)
        if section_match:
            section_name_full = section_match.group(1).strip()
            section_name_cleaned = re.sub(r"^\d+\.\s+", "", section_name_full)
            current_section = section_name_cleaned
            continue

        task_match = re.match(task_regex, line)
        if task_match and current_section:
            status_char = task_match.group(1)
            description = task_match.group(2).strip()

            status = "completed" if status_char == "x" else "pending"

            found_keyword = None
            for kw in keywords:
                if kw.lower() in current_section.lower() or kw.lower() in description.lower():
                    found_keyword = kw
                    break

            if found_keyword:
                notes = []
                for desc_kw in difficulty_descriptors:
                    if desc_kw.lower() in description.lower():
                        notes.append(f"Contains difficulty descriptor: '{desc_kw}'")

                task_info = {
                    "section": current_section,
                    "description": description,
                    "status": status,
                    "keyword": found_keyword
                }
                if notes:
                    task_info["notes"] = notes
                relevant_tasks.append(task_info)

    return relevant_tasks

if __name__ == "__main__":
    with open("Trikeshed/CLAUDE.md", "r") as f:
        content = f.read()

    identified_tasks = extract_specific_tasks(content)

    # Print ONLY the JSON output to stdout
    json.dump(identified_tasks, sys.stdout, indent=2)
