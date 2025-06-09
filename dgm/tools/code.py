import os
import re
from pathlib import Path


def tool_info():
    return {
        "name": "code",
        "description": "Create, read, edit, and manage code files. Supports viewing files, creating new files, and making precise edits using search/replace operations.",
        "input_schema": {
            "type": "object",
            "properties": {
                "command": {
                    "type": "string",
                    "description": "The operation to perform",
                    "enum": ["view", "create", "str_replace", "view_range"]
                },
                "path": {
                    "type": "string",
                    "description": "Path to the file"
                },
                "file_text": {
                    "type": "string",
                    "description": "Content for new file (create command)"
                },
                "old_str": {
                    "type": "string",
                    "description": "String to search for (str_replace command)"
                },
                "new_str": {
                    "type": "string",
                    "description": "String to replace with (str_replace command)"
                },
                "view_range": {
                    "type": "array",
                    "description": "Line range to view [start, end] (view_range command)",
                    "items": {"type": "integer"}
                }
            },
            "required": ["command", "path"]
        }
    }


def validate_path(path: str, command: str) -> Path:
    """Validate and return Path object, ensuring it's within current directory."""
    try:
        path_obj = Path(path).resolve()
        cwd = Path.cwd().resolve()
        
        # Ensure path is within current working directory
        try:
            path_obj.relative_to(cwd)
        except ValueError:
            raise ValueError(f"Path {path} is outside the current directory")
            
        return path_obj
    except Exception as e:
        raise ValueError(f"Invalid path {path}: {str(e)}")


def read_file(path: Path) -> str:
    """Read file content safely."""
    try:
        with open(path, 'r', encoding='utf-8') as f:
            return f.read()
    except UnicodeDecodeError:
        # Try with different encoding for binary files
        with open(path, 'r', encoding='latin-1') as f:
            return f.read()


def write_file(path: Path, content: str):
    """Write content to file safely."""
    # Create parent directories if they don't exist
    path.parent.mkdir(parents=True, exist_ok=True)
    
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)


def format_output(content: str, path: str, init_line: int = 1) -> str:
    """Format file content with line numbers."""
    lines = content.splitlines()
    if not lines:
        return f"File {path} is empty"
    
    # Calculate width for line numbers
    max_line = init_line + len(lines) - 1
    width = len(str(max_line))
    
    formatted_lines = []
    for i, line in enumerate(lines, init_line):
        formatted_lines.append(f"{i:>{width}}│{line}")
    
    return f"File: {path}\n" + "\n".join(formatted_lines)


def view_path(path_obj: Path) -> str:
    """View file or directory contents."""
    if path_obj.is_file():
        content = read_file(path_obj)
        return format_output(content, str(path_obj))
    elif path_obj.is_dir():
        try:
            items = []
            for item in sorted(path_obj.iterdir()):
                if item.is_dir():
                    items.append(f"📁 {item.name}/")
                else:
                    size = item.stat().st_size
                    items.append(f"📄 {item.name} ({size} bytes)")
            
            if not items:
                return f"Directory {path_obj} is empty"
            
            return f"Directory: {path_obj}\n" + "\n".join(items)
        except PermissionError:
            return f"Permission denied accessing directory {path_obj}"
    else:
        return f"Path {path_obj} does not exist"


def maybe_truncate(content: str, max_length: int = 10000) -> str:
    """Truncate content if it's too long."""
    if len(content) <= max_length:
        return content
    
    truncated = content[:max_length]
    return truncated + f"\n\n... (truncated, showing first {max_length} characters of {len(content)} total)"


def tool_function(command: str, path: str, file_text: str = None, old_str: str = None, 
                 new_str: str = None, view_range: list = None) -> str:
    """
    Execute code editing commands.
    
    Commands:
    - view: Display file or directory contents
    - create: Create a new file with given content
    - str_replace: Replace old_str with new_str in the file
    - view_range: View specific line range of a file
    """
    try:
        path_obj = validate_path(path, command)
        
        if command == "view":
            result = view_path(path_obj)
            return maybe_truncate(result)
            
        elif command == "create":
            if file_text is None:
                return "Error: file_text is required for create command"
            
            if path_obj.exists():
                return f"Error: File {path} already exists. Use str_replace to edit existing files."
            
            write_file(path_obj, file_text)
            return f"File created successfully: {path}"
            
        elif command == "str_replace":
            if old_str is None or new_str is None:
                return "Error: both old_str and new_str are required for str_replace command"
            
            if not path_obj.exists():
                return f"Error: File {path} does not exist. Use create command to create new files."
            
            content = read_file(path_obj)
            
            if old_str not in content:
                # Show context around potential matches
                lines = content.splitlines()
                suggestions = []
                for i, line in enumerate(lines, 1):
                    if any(word in line.lower() for word in old_str.lower().split()[:3]):
                        suggestions.append(f"Line {i}: {line}")
                
                error_msg = f"Error: old_str not found in {path}"
                if suggestions:
                    error_msg += f"\n\nPossible similar lines:\n" + "\n".join(suggestions[:5])
                return error_msg
            
            # Count occurrences
            count = content.count(old_str)
            if count > 1:
                return f"Error: old_str appears {count} times in {path}. Please be more specific to match exactly once."
            
            new_content = content.replace(old_str, new_str)
            write_file(path_obj, new_content)
            
            return f"File edited successfully: {path}\nReplaced 1 occurrence of the specified text."
            
        elif command == "view_range":
            if not path_obj.exists():
                return f"Error: File {path} does not exist"
            
            if view_range is None or len(view_range) != 2:
                return "Error: view_range must be a list of two integers [start, end]"
            
            start_line, end_line = view_range
            if start_line < 1:
                return "Error: Line numbers start from 1"
            
            content = read_file(path_obj)
            lines = content.splitlines()
            
            if start_line > len(lines):
                return f"Error: File only has {len(lines)} lines, cannot start at line {start_line}"
            
            # Adjust end_line if it's beyond file length
            end_line = min(end_line, len(lines))
            
            selected_lines = lines[start_line-1:end_line]
            selected_content = "\n".join(selected_lines)
            
            result = format_output(selected_content, f"{path} (lines {start_line}-{end_line})", start_line)
            return maybe_truncate(result)
            
        else:
            return f"Error: Unknown command '{command}'. Available commands: view, create, str_replace, view_range"
            
    except Exception as e:
        return f"Error: {str(e)}"
