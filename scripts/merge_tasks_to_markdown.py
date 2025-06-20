import json

def load_tasks(filename):
    with open(filename, 'r') as f:
        return json.load(f)

def format_task_to_markdown(task):
    checkbox = "[x]" if task['status'] == "completed" else "[ ]"
    return f"- {checkbox} {task['description']}"

def group_tasks_by_section(tasks):
    sections = {}
    ordered_section_names = []
    for task in tasks:
        section_name = task['section']
        if section_name not in sections:
            sections[section_name] = []
            ordered_section_names.append(section_name)
        sections[section_name].append(task)
    return sections, ordered_section_names

def generate_markdown_from_tasks(claude_tasks_grouped, claude_section_order,
                                 deep_self_hosting_tasks_grouped, deep_self_hosting_section_order,
                                 original_claude_md_lines):
    new_markdown_lines = []

    first_task_section_header_found_idx = -1
    for idx, line in enumerate(original_claude_md_lines):
        is_task_section_header = False
        for section_name_iter in claude_section_order:
            if line.startswith(f"## {claude_section_order.index(section_name_iter) + 1}. {section_name_iter}") or \
               line.startswith(f"## {section_name_iter}"):
                is_task_section_header = True
                break
        if is_task_section_header:
            first_task_section_header_found_idx = idx
            break
        new_markdown_lines.append(line)

    if first_task_section_header_found_idx == -1 and claude_section_order: # if claude sections exist but not found
        # This case means the original CLAUDE.md might not have the expected section headers.
        # Fallback: if no claude task section headers are found, append original lines that are not "Implementation Priorities"
        # and then proceed to add tasks. This is a bit of a guess.
        temp_lines = []
        for line in original_claude_md_lines:
            if line.strip() == "## Implementation Priorities":
                break
            temp_lines.append(line)
        new_markdown_lines = temp_lines # Replace whatever was added before with this more conservative approach.
    elif first_task_section_header_found_idx == -1 and not claude_section_order: # No claude tasks to begin with
        # Add all original lines before "Implementation Priorities"
        temp_lines = []
        for line in original_claude_md_lines:
            if line.strip() == "## Implementation Priorities":
                break
            temp_lines.append(line)
        new_markdown_lines = temp_lines


    for section_name in claude_section_order:
        if section_name in claude_tasks_grouped:
            original_header = f"## {section_name}"
            for i, s_name in enumerate(claude_section_order):
                if s_name == section_name:
                    numbered_header = f"## {i+1}. {s_name}"
                    # Check if this numbered header actually exists in the original lines before first_task_section_header_found_idx
                    # or if the unnumbered one exists.
                    # This is to correctly reproduce the original header style.
                    original_doc_header_segment = original_claude_md_lines[:first_task_section_header_found_idx if first_task_section_header_found_idx !=-1 else len(original_claude_md_lines)]
                    if any(numbered_header in line for line in original_doc_header_segment) or \
                       any(numbered_header in line for line in original_claude_md_lines[first_task_section_header_found_idx:] if line.startswith("## ")): # Check also after the identified start
                        original_header = numbered_header
                    elif not any(f"## {i+1}. " in line for line in original_doc_header_segment) and any(f"## {s_name}" in line for line in original_doc_header_segment): # unnumbered exists
                        original_header = f"## {s_name}"
                    break
            new_markdown_lines.append(original_header)
            for task_item in claude_tasks_grouped[section_name]:
                new_markdown_lines.append(format_task_to_markdown(task_item))
            new_markdown_lines.append("")

    for section_name in deep_self_hosting_section_order:
        if section_name in deep_self_hosting_tasks_grouped:
            header = f"### {section_name}" # Correctly use section_name from this loop
            new_markdown_lines.append(header)
            for task_item in deep_self_hosting_tasks_grouped[section_name]: # Use a different var name for items
                new_markdown_lines.append(format_task_to_markdown(task_item))
            new_markdown_lines.append("")

    implementation_priorities_start_idx = -1
    for idx, line in enumerate(original_claude_md_lines):
        if line.strip() == "## Implementation Priorities":
            implementation_priorities_start_idx = idx
            break

    if implementation_priorities_start_idx != -1:
        if (claude_tasks_grouped or deep_self_hosting_tasks_grouped) and \
           (new_markdown_lines and not new_markdown_lines[-1].strip() == ""):
            new_markdown_lines.append("")
        new_markdown_lines.extend(original_claude_md_lines[implementation_priorities_start_idx:])

    final_lines = []
    if not new_markdown_lines: # If all lists were empty and no original content before priorities
        return "\n".join(original_claude_md_lines) # return original if nothing processed

    for i, line in enumerate(new_markdown_lines):
        if i > 0 and line.strip() == "" and new_markdown_lines[i-1].strip() == "":
            continue
        final_lines.append(line)

    return "\n".join(final_lines)


if __name__ == "__main__":
    claude_tasks_data = load_tasks("claude_tasks.json")
    deep_self_hosting_tasks_data = load_tasks("tasks.json")

    claude_tasks_grouped, claude_section_order = group_tasks_by_section(claude_tasks_data)

    temp_deep_self_hosting_tasks = []
    for task_dsh in deep_self_hosting_tasks_data: # Changed variable name to avoid conflict
        temp_deep_self_hosting_tasks.append({
            "section": task_dsh["section"].replace(" Tasks", ""),
            "description": task_dsh["description"],
            "status": task_dsh["status"]
        })

    deep_self_hosting_tasks_grouped, deep_self_hosting_section_order = group_tasks_by_section(temp_deep_self_hosting_tasks)

    with open("Trikeshed/CLAUDE.md", "r") as f:
        original_claude_md_content = f.read()
    original_claude_md_lines = original_claude_md_content.splitlines()

    new_markdown_content = generate_markdown_from_tasks(
        claude_tasks_grouped, claude_section_order,
        deep_self_hosting_tasks_grouped, deep_self_hosting_section_order,
        original_claude_md_lines
    )

    with open("Trikeshed/CLAUDE.md", "w") as f:
        f.write(new_markdown_content)

    print("Successfully merged tasks and updated Trikeshed/CLAUDE.md")
